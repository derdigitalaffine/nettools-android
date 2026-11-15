package de.derdigitalaffine.nettools

import org.junit.Assert.*
import org.junit.Test

class SnmpV3InspectorTest {
    @Test fun acceptsValidAuthPrivCredentials() {
        validateSnmpV3Credentials(SnmpV3Credentials("monitor","eightchars","privacy12"))
    }
    @Test fun rejectsMissingUsernameAndShortPasswords() {
        val invalid=listOf(
            SnmpV3Credentials("","eightchars","privacy12"),
            SnmpV3Credentials("monitor","short","privacy12"),
            SnmpV3Credentials("monitor","eightchars","short"),
            SnmpV3Credentials("x".repeat(40),"eightchars","privacy12")
        )
        invalid.forEach { credentials ->
            try { validateSnmpV3Credentials(credentials);fail("Accepted invalid credentials") }
            catch (_:IllegalArgumentException) {}
        }
    }
}
