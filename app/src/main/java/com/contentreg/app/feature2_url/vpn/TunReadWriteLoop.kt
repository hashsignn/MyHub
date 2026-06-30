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
    private val blockedProvider: () -> Set<String>,
    private val upstreamDns: InetAddress,
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

            val responseDns = if (name != null && DnsPacketHandler.isHostBlocked(name, blockedProvider())) {
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

    private fun forwardUpstream(dns: ByteArray): ByteArray? = runCatching {
        DatagramSocket().use { socket ->
            if (!protect(socket)) return null // must bypass our own VPN
            socket.soTimeout = UPSTREAM_TIMEOUT_MS
            socket.send(DatagramPacket(dns, dns.size, upstreamDns, DNS_PORT))
            val buf = ByteArray(MAX_PACKET)
            val reply = DatagramPacket(buf, buf.size)
            socket.receive(reply)
            buf.copyOf(reply.length)
        }
    }.getOrNull()

    companion object {
        private const val TAG = "TunReadWriteLoop"
        private const val MAX_PACKET = 32_767
        private const val DNS_PORT = 53
        private const val UPSTREAM_TIMEOUT_MS = 5_000
    }
}
