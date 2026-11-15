package de.derdigitalaffine.nettools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class RouteHop(val ttl:Int,val address:String?,val latencyMs:Double?,val raw:String,val reached:Boolean)
private val routeTime=Regex("""time[=<]([0-9]+(?:[.,][0-9]+)?)\s*ms""",RegexOption.IGNORE_CASE)
private val routeFrom=Regex("""(?:From|from)\s+([^ :]+)""")
fun parseRouteHop(ttl:Int,output:String,exitCode:Int):RouteHop {
    val line=output.lines().firstOrNull { it.contains("Time to live exceeded",true) ||
        it.contains("Time exceeded",true) || routeTime.containsMatchIn(it) ||
        it.contains("unreachable",true) } ?: output.lines().firstOrNull { it.isNotBlank() }.orEmpty()
    val latency=routeTime.find(line)?.groupValues?.get(1)?.replace(',','.')?.toDoubleOrNull()
    val address=routeFrom.find(line)?.groupValues?.get(1)
        ?: Regex("""(?:bytes from|bytes from )\s+([^ :]+)""",RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)
    val exceeded=line.contains("exceeded",true)
    return RouteHop(ttl,address,latency,line.trim().take(240),exitCode==0 && latency!=null && !exceeded)
}
private fun probeHop(host:String,ttl:Int):RouteHop {
    val process=ProcessBuilder("ping","-n","-c","1","-W","2","-t",ttl.toString(),host)
        .redirectErrorStream(true).start()
    return try {
        if(!process.waitFor(3500,TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            RouteHop(ttl,null,null,"Timeout / keine ICMP-Rückmeldung",false)
        } else {
            val output=process.inputStream.bufferedReader().use { it.readText() }
            parseRouteHop(ttl,output,process.exitValue())
        }
    } finally { if(process.isAlive) process.destroyForcibly() }
}
@Composable fun RouteDiagnostics() {
    val scope=rememberCoroutineScope()
    var host by rememberSaveable { mutableStateOf("1.1.1.1") }
    var hopsText by rememberSaveable { mutableStateOf("12") }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val hops=remember { mutableStateListOf<RouteHop>() }
    val maxHops=hopsText.toIntOrNull()?.takeIf { it in 1..30 }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Traceroute / Hop-Analyse",style=MaterialTheme.typography.titleMedium)
        Text("Schrittweise TTL-Erhöhung mit ICMP Echo. Manche Router antworten nicht; Android-Firmwares können ping -t sperren.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(host,{host=it},label={Text("Zielhost / IP")},singleLine=true,
            enabled=!running,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(hopsText,{hopsText=it},label={Text("Maximale Hops (1–30)")},singleLine=true,
            enabled=!running,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
            modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={
                if(running) { job?.cancel();job=null;running=false }
                else {
                    val limit=maxHops ?: return@Button
                    val target=host.trim()
                    hops.clear();running=true
                    job=scope.launch {
                        try {
                            for(ttl in 1..limit) {
                                if(!isActive) break
                                val hop=withContext(Dispatchers.IO) {
                                    runCatching { probeHop(target,ttl) }.getOrElse {
                                        RouteHop(ttl,null,null,it.localizedMessage ?: "ping -t nicht verfügbar",false)
                                    }
                                }
                                hops.add(hop)
                                if(hop.reached) break
                            }
                        } catch (_: CancellationException) {
                        } finally { running=false }
                    }
                }
            },enabled=running || (host.isNotBlank() && maxHops!=null),modifier=Modifier.weight(1f)) {
                Text(if(running) "Stoppen" else "Route verfolgen")
            }
            OutlinedButton(onClick={hops.clear()},enabled=!running && hops.isNotEmpty()) { Text("Leeren") }
        }
        if(running) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(hops.isNotEmpty()) {
            Text("${hops.size} Hops untersucht · ${if(hops.last().reached) "Ziel erreicht" else "Ziel noch nicht bestätigt"}",
                style=MaterialTheme.typography.labelLarge)
            hops.forEach { hop ->
                HorizontalDivider()
                Text("${hop.ttl}. ${hop.address ?: "*"}   ${hop.latencyMs?.let { "%.2f ms".format(java.util.Locale.US,it) } ?: "—"}",
                    style=MaterialTheme.typography.titleSmall)
                Text(hop.raw,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
