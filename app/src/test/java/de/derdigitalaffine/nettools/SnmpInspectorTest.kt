package de.derdigitalaffine.nettools

import org.junit.Assert.*
import org.junit.Test

class SnmpInspectorTest {
    @Test fun encodesCommonOids() {
        assertArrayEquals(byteArrayOf(6,8,43,6,1,2,1,1,1,0),encodeOid("1.3.6.1.2.1.1.1.0"))
    }
    @Test fun rejectsInvalidOids() {
        listOf("","1","3.1.2","1.40.2","1..3","-1.3.6").forEach {
            try { encodeOid(it);fail("Accepted $it") } catch (_:IllegalArgumentException) {}
        }
    }
    @Test fun rejectsWrongResponse() {
        try { parseSnmpResponse(byteArrayOf(0x30,0),42);fail("Accepted empty sequence") }
        catch (_:IllegalArgumentException) {}
    }
}
