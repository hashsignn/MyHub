package com.contentreg.app.feature2_url.vpn

import com.contentreg.app.feature2_url.registry.UrlNormalizer

/**
 * M2.1 — DNS-level inspection for the domain filter.
 *
 * Scope: IPv4 + UDP + DNS (the common case for a no-root DNS filter). The VPN routes only DNS to
 * the TUN, so this only ever sees DNS packets. Two parts are intentionally split:
 *  - **Pure logic** ([parseFirstQuestionName], [isHostBlocked], [buildBlockedDnsResponse]) — no
 *    Android, unit-tested.
 *  - **Packet framing** ([buildResponsePacket]) — IPv4/UDP header + checksums; deterministic but
 *    best validated on a real device (per the roadmap, packet handling is the steepest part).
 *
 * Out of scope for this skeleton: IPv6, DNS-over-TCP, DoH/DoT (those bypass plain DNS and are noted
 * as known limits in the ADR).
 */
object DnsPacketHandler {

    private const val IPV4_VERSION = 4
    private const val PROTO_UDP = 17
    private const val DNS_PORT = 53
    private const val DNS_HEADER_LEN = 12

    // ---- Pure DNS logic (unit-tested) ----

    /**
     * Parses the first question's QNAME from a DNS message [dns] (the UDP payload). Questions never
     * use name compression, so a straight label walk is safe. Returns null on a malformed message.
     */
    fun parseFirstQuestionName(dns: ByteArray): String? {
        if (dns.size < DNS_HEADER_LEN + 1) return null
        val qdCount = ((dns[4].toInt() and 0xFF) shl 8) or (dns[5].toInt() and 0xFF)
        if (qdCount < 1) return null

        var offset = DNS_HEADER_LEN
        val labels = StringBuilder()
        while (offset < dns.size) {
            val len = dns[offset].toInt() and 0xFF
            if (len == 0) break
            if (len and 0xC0 != 0) return null // compression pointer — not expected in a question
            offset += 1
            if (offset + len > dns.size) return null
            if (labels.isNotEmpty()) labels.append('.')
            for (i in 0 until len) labels.append((dns[offset + i].toInt() and 0xFF).toChar())
            offset += len
        }
        return labels.toString().ifEmpty { null }
    }

    /**
     * True if [host] (or any parent domain of it) is in the [blocked] set of normalized domains, so
     * blocking `example.com` also blocks `cdn.example.com`. [blocked] must already be normalized
     * (see [UrlNormalizer.normalizeDomain]).
     */
    fun isHostBlocked(host: String, blocked: Set<String>): Boolean =
        UrlNormalizer.hostMatchesSet(host, blocked)

    /**
     * Turns a DNS query payload into a refusal response: copies the header + question, sets QR=1,
     * RA=1, RCODE=3 (NXDOMAIN), and zeroes all the record counts. The querying app then sees the
     * name as non-existent and the connection never starts.
     */
    fun buildBlockedDnsResponse(query: ByteArray): ByteArray {
        val response = query.copyOf()
        // Flags: byte 2 keeps Opcode/RD, sets QR. byte 3 sets RA + RCODE=3.
        response[2] = ((response[2].toInt() and 0x01) or 0x80).toByte()
        response[3] = 0x83.toByte()
        // ANCOUNT/NSCOUNT/ARCOUNT = 0 (QDCOUNT at [4..5] left as-is).
        for (i in 6..11) response[i] = 0
        return response
    }

    // ---- Packet inspection / framing (validate on device) ----

    /** Returns the UDP payload (DNS message) if [packet] is an IPv4/UDP datagram to port 53. */
    fun extractDnsPayload(packet: ByteArray): ByteArray? {
        if (packet.size < 28) return null
        val version = (packet[0].toInt() and 0xF0) ushr 4
        if (version != IPV4_VERSION) return null
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (packet[9].toInt() and 0xFF != PROTO_UDP) return null
        if (packet.size < ihl + 8) return null
        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)
        if (dstPort != DNS_PORT) return null
        val udpLen = ((packet[ihl + 4].toInt() and 0xFF) shl 8) or (packet[ihl + 5].toInt() and 0xFF)
        val payloadLen = (udpLen - 8).coerceAtLeast(0)
        val start = ihl + 8
        if (start + payloadLen > packet.size) return null
        return packet.copyOfRange(start, start + payloadLen)
    }

    /**
     * Frames [dnsResponse] back into an IPv4/UDP packet addressed back to the requester: swaps the
     * source/destination IPs and ports from [requestPacket] and recomputes both checksums.
     */
    fun buildResponsePacket(requestPacket: ByteArray, dnsResponse: ByteArray): ByteArray {
        val ihl = (requestPacket[0].toInt() and 0x0F) * 4
        val totalLen = ihl + 8 + dnsResponse.size
        val out = ByteArray(totalLen)

        // ---- IPv4 header ----
        out[0] = requestPacket[0]            // version + IHL
        out[1] = 0                           // DSCP/ECN
        out[2] = ((totalLen ushr 8) and 0xFF).toByte()
        out[3] = (totalLen and 0xFF).toByte()
        // identification/flags/fragment (4..7) left 0; TTL + protocol:
        out[8] = 64                          // TTL
        out[9] = PROTO_UDP.toByte()
        // Swap source/destination IPs (bytes 12..15 src, 16..19 dst).
        for (i in 0 until 4) {
            out[12 + i] = requestPacket[16 + i]
            out[16 + i] = requestPacket[12 + i]
        }
        writeChecksum(out, 10, ipChecksum(out, 0, ihl))

        // ---- UDP header ----
        val srcPort = ((requestPacket[ihl].toInt() and 0xFF) shl 8) or (requestPacket[ihl + 1].toInt() and 0xFF)
        val dstPort = ((requestPacket[ihl + 2].toInt() and 0xFF) shl 8) or (requestPacket[ihl + 3].toInt() and 0xFF)
        val udpOff = ihl
        val udpLen = 8 + dnsResponse.size
        // Swap ports.
        out[udpOff] = ((dstPort ushr 8) and 0xFF).toByte()
        out[udpOff + 1] = (dstPort and 0xFF).toByte()
        out[udpOff + 2] = ((srcPort ushr 8) and 0xFF).toByte()
        out[udpOff + 3] = (srcPort and 0xFF).toByte()
        out[udpOff + 4] = ((udpLen ushr 8) and 0xFF).toByte()
        out[udpOff + 5] = (udpLen and 0xFF).toByte()
        dnsResponse.copyInto(out, udpOff + 8)
        writeChecksum(out, udpOff + 6, udpChecksum(out, ihl, udpLen))

        return out
    }

    private fun writeChecksum(buf: ByteArray, at: Int, checksum: Int) {
        buf[at] = ((checksum ushr 8) and 0xFF).toByte()
        buf[at + 1] = (checksum and 0xFF).toByte()
    }

    private fun ipChecksum(buf: ByteArray, start: Int, len: Int): Int {
        var sum = 0L
        var i = start
        val end = start + len
        while (i + 1 < end) {
            if (i == 10) { i += 2; continue } // skip the checksum field itself
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        return foldChecksum(sum)
    }

    private fun udpChecksum(buf: ByteArray, udpOff: Int, udpLen: Int): Int {
        var sum = 0L
        // Pseudo-header: src IP (12..15), dst IP (16..19), zero + protocol, UDP length.
        for (i in 12..18 step 2) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
        }
        sum += PROTO_UDP.toLong()
        sum += udpLen.toLong()
        // UDP header + data (checksum field at udpOff+6 is currently 0).
        var i = udpOff
        val end = udpOff + udpLen
        while (i + 1 < end) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) sum += (buf[i].toInt() and 0xFF) shl 8 // odd trailing byte
        val folded = foldChecksum(sum)
        return if (folded == 0) 0xFFFF else folded
    }

    private fun foldChecksum(initial: Long): Int {
        var sum = initial
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }
}
