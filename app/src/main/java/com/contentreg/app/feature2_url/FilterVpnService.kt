package com.contentreg.app.feature2_url

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.contentreg.app.core.data.di.ServiceLocator
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

    private fun startVpn() {
        if (tunInterface != null) return

        // Keep the in-memory blocklist snapshot in sync with the registry.
        scope.launch {
            ServiceLocator.registryRepository.blockedDomains.collect { blockedDomains = it }
        }

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
            blockedProvider = { blockedDomains },
            upstreamDns = InetAddress.getByName(UPSTREAM_DNS),
        )
        loop = tunLoop
        worker = Thread(tunLoop::run, "dns-filter-loop").apply { start() }
        Log.i(TAG, "VPN filter started")
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
        private const val UPSTREAM_DNS = "1.1.1.1"

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
