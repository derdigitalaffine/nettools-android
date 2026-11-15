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
        assertEquals(1024,parsePortList("1000-2023").size)
    }
    @Test fun supportsMoreThan128SeparatePorts() {
        val ports=(1000..1199).joinToString(",")
        assertEquals(200,parsePortList(ports).size)
    }
    @Test fun rejectsVeryLongInput() {
        try { parsePortList("1,".repeat(5000));fail("Overlong list accepted") }
        catch (_:IllegalArgumentException) { }
    }
    @Test fun rejectsOverLimit() {
        try { parsePortList("1000-2024");fail("Accepted 1025 ports") } catch (_:IllegalArgumentException) {}
    }
}
