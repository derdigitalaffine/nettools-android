package de.derdigitalaffine.nettools

import org.junit.Assert.*
import org.junit.Test

class PortScannerTest {
    @Test fun parsesRangesAndDeduplicates() {
        assertEquals(listOf(22,80,81,82,443),parsePortList("443, 80-82, 22, 80"))
    }
    @Test fun rejectsInvalidPorts() {
        listOf("","0","65536","100-99","1-65535","abc","22,").forEach { invalid ->
            try { parsePortList(invalid);fail("Accepted $invalid") } catch (_:IllegalArgumentException) {}
        }
    }
    @Test fun acceptsMaximumRange() {
        assertEquals(128,parsePortList("1000-1127").size)
    }
    @Test fun rejectsOverLimit() {
        try { parsePortList("1000-1128");fail("Accepted 129 ports") } catch (_:IllegalArgumentException) {}
    }
}
