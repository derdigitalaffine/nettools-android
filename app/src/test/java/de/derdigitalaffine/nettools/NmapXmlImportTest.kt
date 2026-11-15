package de.derdigitalaffine.nettools

import org.junit.Assert.*
import org.junit.Test

class NmapXmlImportTest {
    @Test fun importsNmapReportAndServiceDetails() {
        val xml="""<?xml version="1.0"?>
            <nmaprun scanner="nmap">
              <host><status state="up"/>
                <address addr="192.0.2.7" addrtype="ipv4"/>
                <hostnames><hostname name="switch.example.test"/></hostnames>
                <ports><port protocol="tcp" portid="22">
                  <state state="open"/><service name="ssh" product="OpenSSH" version="9"/>
                </port><port protocol="tcp" portid="23"><state state="closed"/></port></ports>
              </host>
            </nmaprun>"""
        val hosts=parseNmapXml(xml)
        assertEquals(1,hosts.size)
        assertEquals("192.0.2.7",hosts[0].address)
        assertEquals("switch.example.test",hosts[0].name)
        assertEquals("up",hosts[0].status)
        assertEquals(2,hosts[0].ports.size)
        assertEquals(22,hosts[0].ports[0].number)
        assertEquals("open",hosts[0].ports[0].state)
        assertEquals("ssh · OpenSSH · 9",hosts[0].ports[0].service)
    }
    @Test fun refusesDoctypeEntityPayload() {
        val malicious="""<!DOCTYPE nmaprun [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <nmaprun><host><hostname name="&xxe;"/></host></nmaprun>"""
        try { parseNmapXml(malicious);fail("DOCTYPE accepted") } catch (_:Exception) { }
    }
    @Test fun refusesNonNmapFile() {
        try { parseNmapXml("<root/>");fail("Wrong root accepted") } catch (_:IllegalArgumentException) {}
    }
    @Test fun refusesHugeReport() {
        try { parseNmapXml(" ".repeat(2_000_001));fail("Huge XML accepted") }
        catch (_:IllegalArgumentException) {}
    }
}
