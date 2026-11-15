package de.derdigitalaffine.nettools

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

data class ImportedNmapPort(val number:Int,val protocol:String,val state:String,val service:String)
data class ImportedNmapHost(val address:String,val name:String,val status:String,val ports:List<ImportedNmapPort>)
private fun Element.descendants(name:String):List<Element> {
    val nodes=getElementsByTagName(name)
    return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
}
private fun Element.attribute(name:String):String=getAttribute(name).take(140)

fun parseNmapXml(xml:String):List<ImportedNmapHost> {
    require(xml.length in 1..2_000_000) { "Nmap-XML ist leer oder größer als 2 MB" }
    val factory=DocumentBuilderFactory.newInstance()
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true)
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true)
    factory.setFeature("http://xml.org/sax/features/external-general-entities",false)
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false)
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false)
    factory.isXIncludeAware=false
    factory.isExpandEntityReferences=false
    val doc=ByteArrayInputStream(xml.toByteArray(StandardCharsets.UTF_8)).use {
        factory.newDocumentBuilder().parse(it)
    }
    require(doc.documentElement.tagName=="nmaprun") { "Kein Nmap-XML-Bericht" }
    val hostElements=doc.documentElement.descendants("host")
    require(hostElements.size<=128) { "Maximal 128 Hosts je Import" }
    return hostElements.map { host ->
        val address=host.descendants("address").firstOrNull { it.attribute("addrtype")=="ipv4" }
            ?: host.descendants("address").firstOrNull()
        val name=host.descendants("hostname").firstOrNull()?.attribute("name").orEmpty()
        val status=host.descendants("status").firstOrNull()?.attribute("state").orEmpty()
        val ports=host.descendants("port").mapNotNull { port ->
            val number=port.attribute("portid").toIntOrNull()?.takeIf { it in 1..65535 }
                ?: return@mapNotNull null
            val state=port.descendants("state").firstOrNull()?.attribute("state").orEmpty()
            val protocol=port.attribute("protocol")
            val svc=port.descendants("service").firstOrNull()
            val service=listOfNotNull(svc?.attribute("name"),svc?.attribute("product"),
                svc?.attribute("version")).filter { !it.isNullOrBlank() }.joinToString(" · ")
            ImportedNmapPort(number,protocol,state,service)
        }
        require(ports.size<=2048) { "Maximal 2048 Ports je Host" }
        ImportedNmapHost(address?.attribute("addr") ?: "Unbekannt",name,status,ports)
    }
}

private fun importNmapReport(context:Context,uri:android.net.Uri):List<ImportedNmapHost> {
    val bytes=ByteArrayOutputStream()
    context.contentResolver.openInputStream(uri)?.use { input ->
        val chunk=ByteArray(8192)
        while(true) {
            val read=input.read(chunk)
            if(read<0) break
            bytes.write(chunk,0,read)
            require(bytes.size()<=2_000_000) { "Datei größer als 2 MB" }
        }
    } ?: throw IllegalArgumentException("Datei kann nicht geöffnet werden")
    return parseNmapXml(bytes.toString("UTF-8"))
}

@Composable fun NmapXmlImportPanel() {
    val context=androidx.compose.ui.platform.LocalContext.current
    val scope=rememberCoroutineScope()
    var hosts by remember { mutableStateOf<List<ImportedNmapHost>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) {
            busy=true;error=null
            scope.launch {
                val parsed=withContext(Dispatchers.IO) { runCatching { importNmapReport(context,uri) } }
                hosts=parsed.getOrNull() ?: emptyList()
                error=parsed.exceptionOrNull()?.localizedMessage
                busy=false
            }
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Nmap-Berichte importieren",style=MaterialTheme.typography.titleMedium)
        Text("Lokalen Nmap-XML-Bericht (-oX) ohne Netzwerkzugriff einlesen. Das native Nmap-Programm ist nicht Bestandteil dieser APK.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick={picker.launch(arrayOf("text/xml","application/xml","application/octet-stream"))},
            enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("Nmap-XML-Datei auswählen") }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text("Import fehlgeschlagen: "+it,color=MaterialTheme.colorScheme.error) }
        if(hosts.isNotEmpty()) {
            val openPorts=hosts.sumOf { host -> host.ports.count { it.state=="open" } }
            Text(hosts.size.toString()+" Hosts · "+openPorts+" offene Ports",style=MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=!showAll,onClick={showAll=false},label={Text("Offene Ports")})
                FilterChip(selected=showAll,onClick={showAll=true},label={Text("Alle Ports")})
            }
            hosts.forEach { host ->
                HorizontalDivider()
                Text(host.address+(if(host.name.isNotBlank()) " · "+host.name else ""),
                    style=MaterialTheme.typography.titleSmall)
                Text("Host: "+host.status,style=MaterialTheme.typography.bodySmall)
                host.ports.filter { showAll || it.state=="open" }.take(128).forEach { port ->
                    Text(port.number.toString()+"/"+port.protocol+" · "+port.state+
                        (if(port.service.isNotBlank()) " · "+port.service else ""),
                        style=MaterialTheme.typography.bodySmall)
                }
                if(host.ports.count { showAll || it.state=="open" }>128) {
                    Text("Weitere Ports ausgeblendet – insgesamt "+host.ports.size+" eingelesen.",
                        style=MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
