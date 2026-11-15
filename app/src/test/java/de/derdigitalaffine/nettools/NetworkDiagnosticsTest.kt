package de.derdigitalaffine.nettools

import org.junit.Assert.*
import org.junit.Test

class NetworkDiagnosticsTest {
    @Test fun parseSuccessfulPing() {
        val r=parsePingLine(1,"64 bytes from 1.1.1.1: icmp_seq=1 ttl=56 time=12.4 ms")
        assertEquals(12.4,r.latencyMs!!,0.0001)
        assertEquals("1.1.1.1",r.address)
    }
    @Test fun parseTimeout() {
        assertNull(parsePingLine(2,"Request timeout").latencyMs)
    }
    @Test fun parseIntermediateHop() {
        val r=parseRouteHop(3,"From 192.0.2.1 icmp_seq=1 Time to live exceeded",1)
        assertEquals("192.0.2.1",r.address)
        assertFalse(r.reached)
    }
    @Test fun parseDestinationHop() {
        val r=parseRouteHop(4,"64 bytes from 1.1.1.1: icmp_seq=1 ttl=55 time=14.1 ms",0)
        assertEquals(14.1,r.latencyMs!!,0.0001)
        assertTrue(r.reached)
    }
}
