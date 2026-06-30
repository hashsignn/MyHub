package com.contentreg.app.feature2_url.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
