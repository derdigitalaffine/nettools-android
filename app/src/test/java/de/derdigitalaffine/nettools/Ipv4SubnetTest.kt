package de.derdigitalaffine.nettools

import org.junit.Assert.*
import org.junit.Test

class Ipv4SubnetTest {
    @Test fun typical24() {
        val r = ipv4Subnet("192.168.7.42/24")
        assertEquals("192.168.7.0",r.network)
        assertEquals("255.255.255.0",r.netmask)
        assertEquals("192.168.7.255",r.broadcast)
        assertEquals("192.168.7.1",r.first)
        assertEquals("192.168.7.254",r.last)
        assertEquals(254L,r.usableHosts)
    }
    @Test fun pointToPoint31() {
        val r = ipv4Subnet("10.0.0.3/31")
        assertEquals("10.0.0.2",r.network)
        assertEquals("10.0.0.3",r.broadcast)
        assertEquals(2L,r.usableHosts)
    }
    @Test fun singleHost32() {
        val r = ipv4Subnet("203.0.113.8/32")
        assertEquals("203.0.113.8",r.network)
        assertEquals("203.0.113.8",r.first)
        assertEquals(1L,r.usableHosts)
    }
    @Test fun zeroPrefix() {
        val r = ipv4Subnet("10.20.30.40/0")
        assertEquals("0.0.0.0",r.network)
        assertEquals("255.255.255.255",r.broadcast)
        assertEquals(4294967296L,r.count)
    }
    @Test fun invalidInputs() {
        listOf("256.2.3.4/24","a.b.c.d/12","1.2.3.4/33","1.2.3.4","1.2.3/24").forEach {
            try { ipv4Subnet(it); fail("Should reject $it") } catch (_: IllegalArgumentException) { }
        }
    }
}
