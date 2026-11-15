package de.derdigitalaffine.nettools

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale

data class PortResult(val port:Int,val open:Boolean,val latencyMs:Double?,val reason:String)
fun parsePortList(input:String):List<Int> {
    val ports=linkedSetOf<Int>()
    require(input.isNotBlank()) { "Keine Ports angegeben" }
    input.split(',').forEach { part ->
        val token=part.trim()
        if('-' in token) {
            val pieces=token.split('-')
            require(pieces.size==2) { "Ungültiger Portbereich: $token" }
            val start=pieces[0].trim().toIntOrNull()
            val end=pieces[1].trim().toIntOrNull()
            require(start!=null && end!=null && start in 1..65535 && end in start..65535) {
                "Ungültiger Portbereich: $token"
            }
            require(end-start<=127) { "Maximal 128 Ports je Bereich" }
            for(p in start..end) { ports.add(p);require(ports.size<=128) { "Maximal 128 Ports pro Lauf" } }
        } else {
            val p=token.toIntOrNull()
            require(p!=null && p in 1..65535) { "Ungültiger Port: $token" }
            ports.add(p)
            require(ports.size<=128) { "Maximal 128 Ports pro Lauf" }
        }
    }
    return ports.sorted()
}
private fun probePort(host:String,port:Int,timeoutMs:Int):PortResult {
    val start=System.nanoTime()
    return try {
        Socket().use { it.connect(InetSocketAddress(host,port),timeoutMs) }
        PortResult(port,true,(System.nanoTime()-start)/1_000_000.0,"TCP-Verbindung erfolgreich")
    } catch (e:Exception) {
        PortResult(port,false,null,e.javaClass.simpleName)
    }
}
@Composable fun PortScanner() {
    val scope=rememberCoroutineScope()
    var host by remember { mutableStateOf("192.168.1.1") }
    var portsText by remember { mutableStateOf("22,53,80,443,445,8080,8443") }
    var timeoutText by remember { mutableStateOf("700") }
    var active by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var showClosed by remember { mutableStateOf(false) }
    val results=remember { mutableStateListOf<PortResult>() }
    val parsed=remember(portsText) { runCatching { parsePortList(portsText) } }
    val timeout=timeoutText.toIntOrNull()?.takeIf { it in 200..5000 }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("TCP-Portprüfung",style=MaterialTheme.typography.titleMedium)
        Text("Gezielte, begrenzte TCP-Connect-Prüfung. Nur Systeme testen, für die du berechtigt bist. Kein Stealth-Scan.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(host,{host=it},label={Text("Zielhost / IP")},singleLine=true,
            enabled=!active,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(portsText,{portsText=it},label={Text("Ports (Komma oder Bereich, max. 128)")},
            singleLine=true,enabled=!active,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(timeoutText,{timeoutText=it},label={Text("Timeout je Port (200–5000 ms)")},
            singleLine=true,enabled=!active,modifier=Modifier.fillMaxWidth())
        parsed.exceptionOrNull()?.let { Text(it.localizedMessage ?: "Ungültige Portliste",color=MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={
                if(active) { job?.cancel();job=null;active=false }
                else {
                    val targets=parsed.getOrNull() ?: return@Button
                    val ms=timeout ?: return@Button
                    val target=host.trim()
                    results.clear();active=true
                    job=scope.launch {
                        try {
                            for(port in targets) {
                                if(!isActive) break
                                val item=withContext(Dispatchers.IO) { probePort(target,port,ms) }
                                results.add(item)
                            }
                        } catch (_:CancellationException) {
                        } finally { active=false }
                    }
                }
            },enabled=active || (host.isNotBlank() && parsed.isSuccess && timeout!=null),modifier=Modifier.weight(1f)) {
                Text(if(active) "Stoppen" else "Ports prüfen")
            }
            OutlinedButton(onClick={results.clear()},enabled=!active && results.isNotEmpty()) { Text("Leeren") }
        }
        if(active) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(results.isNotEmpty()) {
            val opened=results.count { it.open }
            Text("${results.size} geprüft · $opened offen · ${results.size-opened} geschlossen / nicht erreichbar",
                style=MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=!showClosed,onClick={showClosed=false},label={Text("Nur offene")})
                FilterChip(selected=showClosed,onClick={showClosed=true},label={Text("Alle")})
            }
            results.filter { showClosed || it.open }.forEach { result ->
                HorizontalDivider()
                Text("${result.port}/TCP · ${if(result.open) "Offen" else "Nicht erreichbar"}",
                    style=MaterialTheme.typography.titleSmall,
                    color=if(result.open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Text(result.latencyMs?.let { "%.1f ms".format(Locale.US,it) } ?: result.reason,
                    style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}
