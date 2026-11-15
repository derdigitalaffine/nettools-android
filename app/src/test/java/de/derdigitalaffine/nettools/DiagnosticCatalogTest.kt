package de.derdigitalaffine.nettools

import org.junit.Assert.*
import org.junit.Test

class DiagnosticCatalogTest {
    @Test fun allToolsHaveStableUniqueIds() {
        assertEquals(diagnosticTools.size,diagnosticTools.map { it.id }.distinct().size)
        assertTrue(diagnosticTools.size>=12)
        assertTrue(diagnosticTools.all {
            it.id.isNotBlank() && it.name.isNotBlank() && it.subtitle.isNotBlank() &&
                it.category.isNotBlank() && it.capability.isNotBlank()
        })
    }
    @Test fun searchMatchesNameDescriptionAndCapability() {
        val dns=filterDiagnosticTools("dns","Alle",false,emptySet())
        assertTrue(dns.any { it.id=="dns" })
        val auth=filterDiagnosticTools("AES","Alle",false,emptySet())
        assertTrue(auth.any { it.id=="snmpv3" })
        assertTrue(filterDiagnosticTools("non-root","Alle",false,emptySet()).any { it.id=="ports" })
    }
    @Test fun categoryAndFavoritesAreCombinedCorrectly() {
        val only=filterDiagnosticTools("","Sicherheit",true,setOf("ports","ping","tls"))
        assertEquals(listOf("ports","tls"),only.map { it.id })
        assertEquals(0,filterDiagnosticTools("","Netzwerk",true,emptySet()).size)
    }
    @Test fun emptySearchFindsEverything() {
        assertEquals(diagnosticTools.size,filterDiagnosticTools("","Alle",false,emptySet()).size)
        assertEquals(diagnosticTools.size,filterDiagnosticTools("   ","Alle",false,emptySet()).size)
    }
}
