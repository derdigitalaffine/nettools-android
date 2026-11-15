package de.derdigitalaffine.nettools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale

private val scanProfiles=listOf(
    "Web" to "80,443,8080,8081,8443,8000,8008,8888",
    "Infrastruktur" to "21,22,23,25,53,67,68,110,123,135,137-139,143,161,389,443,445,465,587,636,993,995,1433,1521,2049,3306,3389,5432,5900,6379,8080,8443,9090",
    "Alle 1–1024" to "1-1024",
    "Eigene" to "22,53,80,443,445,8080,8443"
)
private val portServiceHints=mapOf(
    20 to "FTP-Daten",21 to "FTP",22 to "SSH",23 to "Telnet",25 to "SMTP",
    53 to "DNS",80 to "HTTP",110 to "POP3",123 to "NTP (normalerweise UDP)",
    135 to "RPC",139 to "NetBIOS",143 to "IMAP",161 to "SNMP (normalerweise UDP)",
    389 to "LDAP",443 to "HTTPS",445 to "SMB",465 to "SMTPS",587 to "Submission",
    636 to "LDAPS",993 to "IMAPS",995 to "POP3S",1433 to "MSSQL",1521 to "Oracle",
    2049 to "NFS",3306 to "MySQL",3389 to "RDP",5432 to "PostgreSQL",
    5900 to "VNC",6379 to "Redis",8080 to "HTTP-Alt",8443 to "HTTPS-Alt",9090 to "Web/Monitoring"
)

data class PortResult(val port:Int,val open:Boolean,val latencyMs:Double?,val reason:String)
fun parsePortList(input:String):List<Int> {
    val ports=linkedSetOf<Int>()
    require(input.isNotBlank()) { "Keine Ports angegeben" }
    require(input.length<=8192) { "Portliste zu lang" }
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
            require(end-start<=1023) { "Maximal 1024 Ports je Bereich" }
            for(p in start..end) { ports.add(p);require(ports.size<=1024) { "Maximal 1024 Ports pro Lauf" } }
        } else {
            val p=token.toIntOrNull()
            require(p!=null && p in 1..65535) { "Ungültiger Port: $token" }
            ports.add(p)
            require(ports.size<=1024) { "Maximal 1024 Ports pro Lauf" }
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
    var concurrencyText by remember { mutableStateOf("16") }
    val context=LocalContext.current
    var completed by remember { mutableIntStateOf(0) }
    var shownLimit by remember { mutableIntStateOf(60) }
    var shareError by remember { mutableStateOf<String?>(null) }
    val results=remember { mutableStateListOf<PortResult>() }
    val parsed=remember(portsText) { runCatching { parsePortList(portsText) } }
    val timeout=timeoutText.toIntOrNull()?.takeIf { it in 200..5000 }
    val concurrency=concurrencyText.toIntOrNull()?.takeIf { it in 1..32 }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Nmap-orientierter TCP-Connect-Scan",style=MaterialTheme.typography.titleMedium)
        Text("Non-Root: parallele TCP-Connect-Prüfung (-sT-Prinzip), kein natives Nmap und kein SYN-Scan. Nur autorisierte Ziele testen.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(host,{host=it},label={Text("Zielhost / IP")},singleLine=true,
            enabled=!active,modifier=Modifier.fillMaxWidth())
        Text("Scanprofile",style=MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            scanProfiles.forEach { (name,ports) ->
                FilterChip(selected=portsText==ports,onClick={ portsText=ports },label={ Text(name) },enabled=!active)
            }
        }
        OutlinedTextField(portsText,{portsText=it},label={Text("Ports (Komma oder Bereich, max. 1024)")},
            singleLine=true,enabled=!active,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(timeoutText,{timeoutText=it},label={Text("Timeout je Port (200–5000 ms)")},
            singleLine=true,enabled=!active,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(concurrencyText,{concurrencyText=it},label={Text("Parallelität (1–32)")},singleLine=true,enabled=!active,modifier=Modifier.fillMaxWidth())
        parsed.exceptionOrNull()?.let { Text(it.localizedMessage ?: "Ungültige Portliste",color=MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={
                if(active) { job?.cancel();job=null;active=false }
                else {
                    val targets=parsed.getOrNull() ?: return@Button
                    val ms=timeout ?: return@Button
                    val parallel=concurrency ?: return@Button
                    val target=host.trim()
                    results.clear();completed=0;shownLimit=60;shareError=null;active=true
                    job=scope.launch {
                        try {
                            val limiter=Semaphore(parallel)
                            targets.map { port ->
                                async(Dispatchers.IO) {
                                    limiter.withPermit {
                                        val item=probePort(target,port,ms)
                                        withContext(Dispatchers.Main) {
                                            results.add(item)
                                            completed++
                                        }
                                    }
                                }
                            }.awaitAll()
                        } catch (_:CancellationException) {
                        } finally { active=false }
                    }
                }
            },enabled=active || (host.isNotBlank() && parsed.isSuccess && timeout!=null && concurrency!=null),modifier=Modifier.weight(1f)) {
                Text(if(active) "Stoppen" else "Ports prüfen")
            }
            OutlinedButton(onClick={results.clear()},enabled=!active && results.isNotEmpty()) { Text("Leeren") }
        }
        if(active) {
            Text("Fortschritt: $completed / ${parsed.getOrNull()?.size ?: 0}")
            LinearProgressIndicator(progress={ completed.toFloat() / (parsed.getOrNull()?.size ?: 1).coerceAtLeast(1) },modifier=Modifier.fillMaxWidth())
        }
        if(results.isNotEmpty() && !active) {
            OutlinedButton(onClick={
                val csv=buildString {
                    append("Port,Protocol,State,LatencyMs,Note\n")
                    results.sortedBy { it.port }.forEach { r ->
                        val ms=r.latencyMs?.let { "%.2f".format(Locale.US,it) } ?: ""
                        append(r.port);append(",tcp,");append(if(r.open) "open" else "not-open")
                        append(',');append(ms);append(',');append(r.reason);append('\n')
                    }
                }
                runCatching {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type="text/csv";putExtra(Intent.EXTRA_TEXT,csv)
                    },"Portergebnisse teilen"))
                }.onFailure { shareError=it.localizedMessage ?: "Export fehlgeschlagen" }
            },modifier=Modifier.fillMaxWidth()) { Text("Ergebnisse als CSV teilen") }
        }
        shareError?.let { Text("CSV-Export: "+it,color=MaterialTheme.colorScheme.error) }
        if(results.isNotEmpty()) {
            val opened=results.count { it.open }
            Text("${results.size} geprüft · $opened offen · ${results.size-opened} geschlossen / nicht erreichbar",
                style=MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=!showClosed,onClick={showClosed=false},label={Text("Nur offene")})
                FilterChip(selected=showClosed,onClick={showClosed=true},label={Text("Alle")})
            }
            val visible=results.sortedBy { it.port }.filter { showClosed || it.open }
            visible.take(shownLimit).forEach { result ->
                HorizontalDivider()
                Text("${result.port}/TCP · ${if(result.open) "Offen" else "Nicht erreichbar"}",
                    style=MaterialTheme.typography.titleSmall,
                    color=if(result.open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Text((result.latencyMs?.let { "%.1f ms".format(Locale.US,it) } ?: result.reason) + " · Standardport-Hinweis: " + (portServiceHints[result.port] ?: "nicht zugeordnet"),
                    style=MaterialTheme.typography.bodySmall)
            }
            if(visible.size>shownLimit) {
                OutlinedButton(onClick={shownLimit+=60},modifier=Modifier.fillMaxWidth()) {
                    Text("Weitere 60 anzeigen · "+shownLimit+" / "+visible.size)
                }
            }
            if(visible.isEmpty()) Text("Keine Treffer für den gewählten Filter.",
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
