package de.derdigitalaffine.nettools

import org.junit.Assert.*
import org.junit.Test

class ServiceIdentificationTest {
    @Test fun limitsServiceProbesToExplicitSafePorts() {
        assertTrue(supportsSafeServiceProbe(22))
        assertTrue(supportsSafeServiceProbe(443))
        assertTrue(supportsSafeServiceProbe(8080))
        assertFalse(supportsSafeServiceProbe(3306))
        assertFalse(supportsSafeServiceProbe(6379))
    }
    @Test fun recognizesSshAndHttpEvidence() {
        assertEquals("SSH-Banner: SSH-2.0-OpenSSH_9.9",
            classifyServiceBanner(22,"SSH-2.0-OpenSSH_9.9"))
        assertEquals("HTTP-Antwort: HTTP/1.1 200 OK",
            classifyServiceBanner(8080,"HTTP/1.1 200 OK"))
    }
    @Test fun doesntGuessUnknownService() {
        assertEquals("Keine aussagekräftige Antwort",classifyServiceBanner(22,""))
        assertEquals("Unbekanntes Banner: hello",classifyServiceBanner(22,"hello"))
    }
}
