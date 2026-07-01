package com.contentreg.app.feature2_url.vpn

import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * M2.1 — the TUN read/write loop for the DNS filter.
 *
 * Reads one IP packet at a time from the TUN, and for DNS queries either answers NXDOMAIN (blocked
 * domain) or forwards the query to a real upstream resolver through a [protect]-ed socket (so the
 * forwarded query bypasses our own VPN) and relays the reply back down the TUN.
 *
 * This is a deliberately simple, single-threaded, blocking forwarder — correct in shape, but a
 * production filter would use non-blocking I/O and parallel in-flight queries. Validate on device.
 */
class TunReadWriteLoop(
    private val tunInput: FileInputStream,
    private val tunOutput: FileOutputStream,
    private val protect: (DatagramSocket) -> Boolean,
    private val isHostBlocked: (host: String) -> Boolean,
    private val upstreamServers: List<InetAddress>,
) {

    @Volatile private var running = true

    fun stop() {
        running = false
    }

    fun run() {
        val buffer = ByteArray(MAX_PACKET)
        while (running) {
            val read = try {
                tunInput.read(buffer)
            } catch (e: IOException) {
                if (running) Log.w(TAG, "TUN read ended", e)
                break
            }
            if (read <= 0) continue

            val packet = buffer.copyOf(read)
            val dns = DnsPacketHandler.extractDnsPayload(packet) ?: continue
            val name = DnsPacketHandler.parseFirstQuestionName(dns)

            val responseDns = if (name != null && isHostBlocked(name)) {
                Log.d(TAG, "Blocking DNS for $name")
                DnsPacketHandler.buildBlockedDnsResponse(dns)
            } else {
                forwardUpstream(dns) ?: continue
            }

            runCatching {
                val reply = DnsPacketHandler.buildResponsePacket(packet, responseDns)
                synchronized(tunOutput) { tunOutput.write(reply) }
            }.onFailure { Log.w(TAG, "Failed writing DNS reply", it) }
        }
    }

    /** Tries each upstream resolver in order; returns the first reply, or null if all fail. */
    private fun forwardUpstream(dns: ByteArray): ByteArray? {
        for (server in upstreamServers) {
            val reply = queryServer(dns, server)
            if (reply != null) return reply
        }
        Log.w(TAG, "All ${upstreamServers.size} upstream DNS server(s) failed for this query")
        return null
    }

    private fun queryServer(dns: ByteArray, server: InetAddress): ByteArray? = try {
        DatagramSocket().use { socket ->
            if (!protect(socket)) {
                Log.w(TAG, "protect() returned false — upstream query dropped")
                return null
            }
            socket.soTimeout = UPSTREAM_TIMEOUT_MS
            socket.send(DatagramPacket(dns, dns.size, server, DNS_PORT))
            val buf = ByteArray(MAX_PACKET)
            val reply = DatagramPacket(buf, buf.size)
            socket.receive(reply)
            buf.copyOf(reply.length)
        }
    } catch (e: Exception) {
        Log.w(TAG, "upstream $server failed: ${e.javaClass.simpleName}: ${e.message}")
        null
    }

    companion object {
        private const val TAG = "TunReadWriteLoop"
        private const val MAX_PACKET = 32_767
        private const val DNS_PORT = 53
        // Per-server timeout kept short so the browser doesn't hang; we may try several servers.
        private const val UPSTREAM_TIMEOUT_MS = 2_000
    }
}
