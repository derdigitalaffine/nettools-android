package de.derdigitalaffine.nettools

import org.junit.Assert.*
import org.junit.Test

class TcpStatsTest {
    @Test fun emptySamples() {
        val stats=tcpStats(emptyList())
        assertEquals(0,stats.count)
        assertEquals(0.0,stats.lossPercent,0.0001)
        assertNull(stats.avg)
        assertNull(stats.jitter)
    }
    @Test fun successesAndFailures() {
        val stats=tcpStats(listOf(
            TcpSample(1,10f),TcpSample(2,null,"timeout"),
            TcpSample(3,20f),TcpSample(4,30f)
        ))
        assertEquals(4,stats.count)
        assertEquals(3,stats.successful)
        assertEquals(1,stats.failed)
        assertEquals(25.0,stats.lossPercent,0.0001)
        assertEquals(10f,stats.min!!,0.001f)
        assertEquals(30f,stats.max!!,0.001f)
        assertEquals(20f,stats.avg!!,0.001f)
        assertEquals(10f,stats.jitter!!,0.001f)
    }
    @Test fun onlyFailures() {
        val stats=tcpStats(listOf(TcpSample(1,null,"timeout"),TcpSample(2,null,"refused")))
        assertEquals(100.0,stats.lossPercent,0.0001)
        assertNull(stats.min)
        assertNull(stats.jitter)
    }
}
