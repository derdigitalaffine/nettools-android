package de.derdigitalaffine.nettools

import org.junit.Assert.*
import org.junit.Test

class SnmpWalkTest {
    private val root="1.3.6.1.2.1.1"

    @Test fun acceptsStrictSuccessorWithinSubtree() {
        assertTrue(isValidSnmpWalkSuccessor(root,root,SnmpValue("$root.1.0",4,"test")))
        assertTrue(isValidSnmpWalkSuccessor(root,"$root.1.0",SnmpValue("$root.2.0",4,"test")))
    }
    @Test fun stopsOnSiblingAndRepeatedOids() {
        assertFalse(isValidSnmpWalkSuccessor(root,"$root.1.0",SnmpValue("1.3.6.1.2.1.11.1",4,"bad")))
        assertFalse(isValidSnmpWalkSuccessor(root,"$root.1.0",SnmpValue("$root.1.0",4,"duplicate")))
        assertFalse(isValidSnmpWalkSuccessor(root,"$root.2.0",SnmpValue("$root.1.0",4,"reverse")))
    }
    @Test fun stopsOnEndOfMib() {
        assertFalse(isValidSnmpWalkSuccessor(root,root,SnmpValue(root,0x82,"endOfMibView")))
    }
    @Test fun parsesSyntheticResponse() {
        val reply=byteArrayOf(
            0x30,0x2A, 0x02,0x01,0x01, 0x04,0x06,112,117,98,108,105,99,
            0xA2.toByte(),0x1D, 0x02,0x01,0x2A, 0x02,0x01,0x00, 0x02,0x01,0x00,
            0x30,0x14, 0x30,0x12, 0x06,0x08,43,6,1,2,1,1,1,0,
            0x04,0x04,116,101,115,116
        )
        val parsed=parseSnmpResponse(reply,42)
        assertEquals("1.3.6.1.2.1.1.1.0",parsed.oid)
        assertEquals("test",parsed.value)
        assertEquals(4,parsed.type)
    }
}
