package de.derdigitalaffine.nettools

import org.junit.Assert.*
import org.junit.Test

class DnsExplorerTest {
    @Test fun queryEncodesDomainAndType() {
        val bytes=encodeDnsQuery("example.org",28,0x1234)
        assertEquals(0x12,bytes[0].toInt() and 255)
        assertEquals(0x34,bytes[1].toInt() and 255)
        assertEquals(0x01,bytes[5].toInt() and 255)
        assertEquals(28,bytes[bytes.size-3].toInt() and 255)
    }
    @Test fun parseARecord() {
        val response=byteArrayOf(
            0x12,0x34,0x81.toByte(),0x80.toByte(),0,1,0,1,0,0,0,0,
            7,101,120,97,109,112,108,101,3,111,114,103,0,0,1,0,1,
            0xC0.toByte(),0x0C,0,1,0,1,0,0,0,60,0,4,93, (184).toByte(), (215).toByte(),34
        )
        val r=parseDnsResponse(response,0x1234)
        assertEquals(0,r.rcode)
        assertEquals(1,r.records.size)
        assertEquals("example.org",r.records[0].name)
        assertEquals("93.184.215.34",r.records[0].data)
        assertEquals(60L,r.records[0].ttl)
    }
    @Test fun rejectWrongTransactionId() {
        try {
            parseDnsResponse(byteArrayOf(0,0,0x81.toByte(),0x80.toByte(),0,0,0,0,0,0,0,0),0x1234)
            fail("Must reject wrong ID")
        } catch (_: IllegalArgumentException) {}
    }
    @Test fun rejectMalformedDomain() {
        listOf("","a..b","x".repeat(64)+".com").forEach {
            try { encodeDnsQuery(it,1);fail("Invalid $it") } catch (_: IllegalArgumentException) {}
        }
    }
}
