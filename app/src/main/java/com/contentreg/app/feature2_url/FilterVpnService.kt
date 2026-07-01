package com.contentreg.app.feature2_url

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.contentreg.app.core.data.di.ServiceLocator
import com.contentreg.app.feature2_url.classifier.Blocklist
import com.contentreg.app.feature2_url.classifier.ClassificationReason
import com.contentreg.app.feature2_url.classifier.UrlClassifier
import com.contentreg.app.feature2_url.registry.BlockEntrySource
import com.contentreg.app.feature2_url.registry.UrlNormalizer
import com.contentreg.app.feature2_url.vpn.TunReadWriteLoop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress

/**
 * M2.1 — local, no-root DNS-filtering VPN. Routes only DNS (a single virtual resolver IP) through
 * the TUN; everything else uses the normal network. Blocked domains come from the registry; allowed
 * queries are forwarded upstream via a protected socket. No traffic leaves the device except the
 * forwarded DNS query to the upstream resolver.
 *
 * Skeleton status: lifecycle, consent, TUN setup, and the DNS decision path are complete; the
 * packet framing (DnsPacketHandler) should be validated on a device. TCP/IP forwarding, IPv6, and
 * DoH are out of scope here (see ADR 0001).
 */
class FilterVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunInterface: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private var loop: TunReadWriteLoop? = null

    @Volatile private var blockedDomains: Set<String> = emptySet()
    private var classifier: UrlClassifier? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                stopSelf()
                START_NOT_STICKY
            }
            else -> {
                startVpn()
                START_STICKY
            }
        }
    }

    /**
     * Reads the first DNS server from the active network's link properties *before* the VPN
     * establishes, so we can forward queries to the device's own resolver. Returns null if none is
     * found (public fallbacks are then used by [upstreamServers]).
     */
    private fun detectUpstreamDns(): InetAddress? {
        return try {
            val cm = getSystemService(ConnectivityManager::class.java)
            val lp = cm.getLinkProperties(cm.activeNetwork)
            val server = lp?.dnsServers?.firstOrNull()
            if (server != null) Log.i(TAG, "Upstream DNS detected: $server")
            else Log.w(TAG, "No DNS in link properties; using public fallbacks")
            server
        } catch (e: Exception) {
            Log.w(TAG, "DNS detection failed: ${e.message}; using public fallbacks")
            null
        }
    }

    /**
     * Ordered upstream resolvers to try: the device's own resolver first (works on real networks and
     * on the emulator, where it's the 10.0.2.3 gateway), then public fallbacks. Trying several means
     * one unreachable resolver (e.g. 1.1.1.1 blocked on some emulators) no longer leaves the browser
     * with no DNS at all.
     */
    private fun upstreamServers(): List<InetAddress> {
        val servers = mutableListOf<InetAddress>()
        detectUpstreamDns()?.let { servers += it }
        for (fallback in FALLBACK_DNS) {
            runCatching { InetAddress.getByName(fallback) }.getOrNull()
                ?.let { if (it !in servers) servers += it }
        }
        Log.i(TAG, "Upstream DNS chain: $servers")
        return servers
    }

    private fun startVpn() {
        if (tunInterface != null) return

        // Resolve the upstream chain before establish() changes the active network.
        val upstream = upstreamServers()

        // Keep the in-memory blocklist snapshot in sync with the registry.
        scope.launch {
            ServiceLocator.registryRepository.blockedDomains.collect { blockedDomains = it }
        }
        // Classifier for unseen domains (curated list + keyword heuristics), loaded once.
        classifier = UrlClassifier(blocklist = Blocklist.load(this))

        val pfd = Builder()
            .setSession("ContentReg URL filter")
            .addAddress(VPN_ADDRESS, 24)
            .addDnsServer(VIRTUAL_DNS)
            .addRoute(VIRTUAL_DNS, 32) // only DNS to our virtual resolver is captured
            .establish()

        if (pfd == null) {
            Log.e(TAG, "establish() returned null — VPN not authorized?")
            stopSelf()
            return
        }
        tunInterface = pfd
        isRunning = true

        val tunLoop = TunReadWriteLoop(
            tunInput = FileInputStream(pfd.fileDescriptor),
            tunOutput = FileOutputStream(pfd.fileDescriptor),
            protect = { socket -> protect(socket) },
            isHostBlocked = ::decideBlock,
            upstreamServers = upstream,
        )
        loop = tunLoop
        worker = Thread(tunLoop::run, "dns-filter-loop").apply { start() }
        Log.i(TAG, "VPN filter started")
    }

    /**
     * Block decision for one DNS query. Registry snapshot first (lookup-before-classify); for an
     * unseen host, run the classifier and — if it says block — persist it so future lookups are
     * instant (M2.3 "a new bad URL is classified and written to the registry").
     */
    private fun decideBlock(host: String): Boolean {
        if (UrlNormalizer.hostMatchesSet(host, blockedDomains)) return true
        val result = classifier?.classifyHost(host) ?: return false
        if (!result.shouldBlock) return false
        val source = when (result.reason) {
            ClassificationReason.KEYWORD_HEURISTIC -> BlockEntrySource.HEURISTIC
            else -> BlockEntrySource.BLOCKLIST
        }
        scope.launch { ServiceLocator.registryRepository.addDomain(host, source) }
        return true
    }

    private fun stopVpn() {
        loop?.stop()
        worker?.interrupt()
        worker = null
        loop = null
        runCatching { tunInterface?.close() }
        tunInterface = null
        isRunning = false
        Log.i(TAG, "VPN filter stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        scope.cancel()
    }

    companion object {
        private const val TAG = "FilterVpnService"
        private const val VPN_ADDRESS = "10.111.222.1"
        private const val VIRTUAL_DNS = "10.111.222.2"

        // Public resolvers tried after the device's own. 8.8.8.8 first — it's reachable on the
        // Android emulator where 1.1.1.1 is sometimes blocked.
        private val FALLBACK_DNS = listOf("8.8.8.8", "1.1.1.1")

        private const val ACTION_START = "com.contentreg.app.VPN_START"
        private const val ACTION_STOP = "com.contentreg.app.VPN_STOP"

        /** Best-effort running flag for UI; the authoritative state is the live VPN itself. */
        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            context.startService(
                Intent(context, FilterVpnService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, FilterVpnService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
