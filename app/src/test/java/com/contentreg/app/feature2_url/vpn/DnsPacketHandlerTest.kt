package com.contentreg.app.feature2_url.vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M2.1 — pure DNS parse + domain-match + refusal-response logic (the device-independent core). */
class DnsPacketHandlerTest {

    /** Builds a minimal DNS query message for [name] (header + one question). */
    private fun dnsQuery(name: String): ByteArray {
        val out = ArrayList<Byte>()
        // Header: id=0x1234, flags=0x0100 (standard query, RD), QDCOUNT=1, others 0.
        out.addAll(listOf(0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00).map { it.toByte() })
        for (label in name.split('.')) {
            out.add(label.length.toByte())
            for (c in label) out.add(c.code.toByte())
        }
        out.add(0) // root
        out.addAll(listOf(0x00, 0x01, 0x00, 0x01).map { it.toByte() }) // QTYPE=A, QCLASS=IN
        return out.toByteArray()
    }

    /**
     * Builds a minimal valid IPv4/UDP packet wrapping [dnsPayload]. Used for the packet-framing
     * tests that cover [DnsPacketHandler.extractDnsPayload] and [DnsPacketHandler.buildResponsePacket].
     * UDP checksum is left 0 (disabled — valid per RFC 768) to keep the helper simple.
     */
    private fun buildQueryPacket(
        dnsPayload: ByteArray,
        srcIp: ByteArray = byteArrayOf(10, 111.toByte(), 222.toByte(), 1),
        dstIp: ByteArray = byteArrayOf(10, 111.toByte(), 222.toByte(), 2),
        srcPort: Int = 12345,
        dstPort: Int = 53,
    ): ByteArray {
        val udpLen = 8 + dnsPayload.size
        val totalLen = 20 + udpLen
        val out = ByteArray(totalLen)
        out[0] = 0x45.toByte()  // version=4, IHL=5
        out[2] = ((totalLen ushr 8) and 0xFF).toByte()
        out[3] = (totalLen and 0xFF).toByte()
        out[8] = 64             // TTL
        out[9] = 17             // Protocol=UDP
        srcIp.copyInto(out, 12)
        dstIp.copyInto(out, 16)
        // Compute IP checksum (field at 10-11 starts as 0).
        var ipSum = 0L
        for (i in 0 until 20 step 2) {
            ipSum += ((out[i].toInt() and 0xFF) shl 8) or (out[i + 1].toInt() and 0xFF)
        }
        while (ipSum shr 16 != 0L) ipSum = (ipSum and 0xFFFF) + (ipSum shr 16)
        val ipChk = (ipSum.inv() and 0xFFFF).toInt()
        out[10] = ((ipChk ushr 8) and 0xFF).toByte()
        out[11] = (ipChk and 0xFF).toByte()
        // UDP header.
        out[20] = ((srcPort ushr 8) and 0xFF).toByte()
        out[21] = (srcPort and 0xFF).toByte()
        out[22] = ((dstPort ushr 8) and 0xFF).toByte()
        out[23] = (dstPort and 0xFF).toByte()
        out[24] = ((udpLen ushr 8) and 0xFF).toByte()
        out[25] = (udpLen and 0xFF).toByte()
        // UDP checksum at [26-27] left 0 (disabled).
        dnsPayload.copyInto(out, 28)
        return out
    }

    /** True iff the IP header checksum in [packet] is valid (sum of all 16-bit words = 0xFFFF). */
    private fun verifyIpChecksum(packet: ByteArray, ihl: Int): Boolean {
        var sum = 0L
        for (i in 0 until ihl step 2) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum and 0xFFFF) == 0xFFFFL
    }

    /**
     * Recomputes the UDP checksum (with pseudo-header) for [packet] and compares to the stored
     * value. A stored 0 is accepted as "checksum disabled" (RFC 768).
     */
    private fun verifyUdpChecksum(packet: ByteArray, udpOff: Int, udpLen: Int): Boolean {
        val stored = ((packet[udpOff + 6].toInt() and 0xFF) shl 8) or (packet[udpOff + 7].toInt() and 0xFF)
        if (stored == 0) return true // 0 means checksum disabled — skip
        val tmp = packet.copyOf()
        tmp[udpOff + 6] = 0; tmp[udpOff + 7] = 0
        var sum = 0L
        for (i in 12..18 step 2) {
            sum += ((tmp[i].toInt() and 0xFF) shl 8) or (tmp[i + 1].toInt() and 0xFF)
        }
        sum += 17L + udpLen.toLong() // zero+protocol + UDP length (pseudo-header)
        var i = udpOff; val end = udpOff + udpLen
        while (i + 1 < end) {
            sum += ((tmp[i].toInt() and 0xFF) shl 8) or (tmp[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) sum += (tmp[i].toInt() and 0xFF) shl 8 // odd trailing byte
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        val computed = (sum.inv() and 0xFFFF).toInt().let { if (it == 0) 0xFFFF else it }
        return stored == computed
    }

    // ---- Pure DNS logic tests ----

    @Test
    fun `parses the question name`() {
        assertEquals("www.example.com", DnsPacketHandler.parseFirstQuestionName(dnsQuery("www.example.com")))
        assertEquals("a.b.c.co", DnsPacketHandler.parseFirstQuestionName(dnsQuery("a.b.c.co")))
    }

    @Test
    fun `returns null for a too-short message`() {
        assertNull(DnsPacketHandler.parseFirstQuestionName(ByteArray(5)))
    }

    @Test
    fun `host match covers subdomains and normalizes`() {
        val blocked = setOf("example.com")
        assertTrue(DnsPacketHandler.isHostBlocked("example.com", blocked))
        assertTrue(DnsPacketHandler.isHostBlocked("WWW.Example.com", blocked))
        assertTrue(DnsPacketHandler.isHostBlocked("cdn.assets.example.com", blocked))
        assertFalse(DnsPacketHandler.isHostBlocked("notexample.com", blocked))
        assertFalse(DnsPacketHandler.isHostBlocked("example.org", blocked))
    }

    @Test
    fun `empty blocklist blocks nothing`() {
        assertFalse(DnsPacketHandler.isHostBlocked("example.com", emptySet()))
    }

    @Test
    fun `blocked response keeps id and question but sets NXDOMAIN`() {
        val query = dnsQuery("example.com")
        val resp = DnsPacketHandler.buildBlockedDnsResponse(query)
        // Same transaction id.
        assertEquals(query[0], resp[0])
        assertEquals(query[1], resp[1])
        // QR set, RCODE = 3 (NXDOMAIN).
        assertTrue((resp[2].toInt() and 0x80) != 0)
        assertEquals(3, resp[3].toInt() and 0x0F)
        // All record counts zero (bytes 6..11), QDCOUNT (4..5) preserved = 1.
        assertEquals(1, ((resp[4].toInt() and 0xFF) shl 8) or (resp[5].toInt() and 0xFF))
        for (i in 6..11) assertEquals(0, resp[i].toInt())
    }

    // ---- Packet framing tests (extractDnsPayload + buildResponsePacket) ----
    // These cover the layer flagged in PROGRESS.md as "needs on-device validation."

    @Test
    fun `extractDnsPayload returns dns payload from a valid ipv4-udp-dns packet`() {
        val dns = dnsQuery("pornhub.com")
        val packet = buildQueryPacket(dns)
        val extracted = DnsPacketHandler.extractDnsPayload(packet)
        assertNotNull(extracted)
        assertArrayEquals(dns, extracted)
    }

    @Test
    fun `extractDnsPayload returns null for non-53 destination port`() {
        val dns = dnsQuery("example.com")
        assertNull(DnsPacketHandler.extractDnsPayload(buildQueryPacket(dns, dstPort = 80)))
    }

    @Test
    fun `extractDnsPayload returns null for a too-short packet`() {
        assertNull(DnsPacketHandler.extractDnsPayload(ByteArray(27)))
    }

    @Test
    fun `buildResponsePacket swaps source and destination ips and ports`() {
        val dns = dnsQuery("pornhub.com")
        val request = buildQueryPacket(dns)
        val blocked = DnsPacketHandler.buildBlockedDnsResponse(dns)
        val resp = DnsPacketHandler.buildResponsePacket(request, blocked)

        val ihl = (resp[0].toInt() and 0x0F) * 4
        // Src IP in response = original dst IP (the virtual DNS server 10.111.222.2).
        assertArrayEquals(byteArrayOf(10, 111.toByte(), 222.toByte(), 2), resp.copyOfRange(12, 16))
        // Dst IP in response = original src IP (the VPN client 10.111.222.1).
        assertArrayEquals(byteArrayOf(10, 111.toByte(), 222.toByte(), 1), resp.copyOfRange(16, 20))
        // Src port in response = 53 (DNS server).
        assertEquals(53, ((resp[ihl].toInt() and 0xFF) shl 8) or (resp[ihl + 1].toInt() and 0xFF))
        // Dst port in response = original query source port (12345).
        assertEquals(12345, ((resp[ihl + 2].toInt() and 0xFF) shl 8) or (resp[ihl + 3].toInt() and 0xFF))
    }

    @Test
    fun `buildResponsePacket produces a valid ip checksum`() {
        val dns = dnsQuery("example.com")
        val resp = DnsPacketHandler.buildResponsePacket(
            buildQueryPacket(dns),
            DnsPacketHandler.buildBlockedDnsResponse(dns),
        )
        val ihl = (resp[0].toInt() and 0x0F) * 4
        assertTrue("IP checksum invalid", verifyIpChecksum(resp, ihl))
    }

    @Test
    fun `buildResponsePacket produces a valid udp checksum`() {
        val dns = dnsQuery("example.com")
        val resp = DnsPacketHandler.buildResponsePacket(
            buildQueryPacket(dns),
            DnsPacketHandler.buildBlockedDnsResponse(dns),
        )
        val ihl = (resp[0].toInt() and 0x0F) * 4
        val udpLen = ((resp[ihl + 4].toInt() and 0xFF) shl 8) or (resp[ihl + 5].toInt() and 0xFF)
        assertTrue("UDP checksum invalid", verifyUdpChecksum(resp, ihl, udpLen))
    }

    @Test
    fun `full round-trip blocked domain yields nxdomain in a valid parseable response packet`() {
        val dns = dnsQuery("pornhub.com")
        val queryPacket = buildQueryPacket(dns)
        // Simulate TunReadWriteLoop decision path.
        val extracted = DnsPacketHandler.extractDnsPayload(queryPacket)!!
        val name = DnsPacketHandler.parseFirstQuestionName(extracted)!!
        assertEquals("pornhub.com", name)
        assertTrue(DnsPacketHandler.isHostBlocked(name, setOf("pornhub.com")))
        val nxdomain = DnsPacketHandler.buildBlockedDnsResponse(extracted)
        val responsePacket = DnsPacketHandler.buildResponsePacket(queryPacket, nxdomain)
        // Must be a valid IPv4 packet.
        assertEquals(0x45.toByte(), responsePacket[0])
        val ihl = (responsePacket[0].toInt() and 0x0F) * 4
        assertTrue("IP checksum invalid in round-trip response", verifyIpChecksum(responsePacket, ihl))
        val udpLen = ((responsePacket[ihl + 4].toInt() and 0xFF) shl 8) or (responsePacket[ihl + 5].toInt() and 0xFF)
        assertTrue("UDP checksum invalid in round-trip response", verifyUdpChecksum(responsePacket, ihl, udpLen))
        // DNS RCODE must be 3 (NXDOMAIN).
        val respDns = responsePacket.copyOfRange(ihl + 8, responsePacket.size)
        assertEquals(3, respDns[3].toInt() and 0x0F)
        // Transaction ID must be preserved.
        assertEquals(dns[0], respDns[0])
        assertEquals(dns[1], respDns[1])
    }
}