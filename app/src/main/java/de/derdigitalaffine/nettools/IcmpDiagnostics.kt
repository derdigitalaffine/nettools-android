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
import java.util.concurrent.TimeUnit

data class PingResult(val sequence:Int,val address:String?,val latencyMs:Double?,val message:String)
private val timeRegex=Regex("""time[=<]([0-9]+(?:[.,][0-9]+)?)\s*ms""",RegexOption.IGNORE_CASE)
private val fromRegex=Regex("""from\s+([^ :]+)""",RegexOption.IGNORE_CASE)
fun parsePingLine(sequence:Int,line:String):PingResult {
    val latency=timeRegex.find(line)?.groupValues?.get(1)?.replace(',','.')?.toDoubleOrNull()
    val address=fromRegex.find(line)?.groupValues?.get(1)
    return PingResult(sequence,address,latency,line.trim().take(240))
}
private fun pingOnce(host:String,sequence:Int):PingResult {
    val process=ProcessBuilder("ping","-n","-c","1","-W","2",host)
        .redirectErrorStream(true).start()
    return try {
        val finished=process.waitFor(3500,TimeUnit.MILLISECONDS)
        if(!finished) {
            process.destroyForcibly()
            PingResult(sequence,null,null,"Zeitüberschreitung")
        } else {
            val lines=process.inputStream.bufferedReader().use { it.readText().lines() }
            val matched=lines.firstOrNull { timeRegex.containsMatchIn(it) }
            if(matched!=null) parsePingLine(sequence,matched)
            else PingResult(sequence,null,null,lines.firstOrNull { it.contains("unreachable",true)||it.contains("denied",true)||it.contains("unknown",true) }
                ?: "Keine ICMP-Antwort (Exit ${process.exitValue()})")
        }
    } finally { if(process.isAlive) process.destroyForcibly() }
}
@Composable fun IcmpDiagnostics() {
    val scope=rememberCoroutineScope()
    var host by remember { mutableStateOf("1.1.1.1") }
    var count by remember { mutableStateOf("4") }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val results=remember { mutableStateListOf<PingResult>() }
    val total=count.toIntOrNull()?.takeIf { it in 1..20 }
    val successes=results.count { it.latencyMs!=null }
    val average=results.mapNotNull { it.latencyMs }.takeIf { it.isNotEmpty() }?.average()
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("ICMP Echo / Ping",style=MaterialTheme.typography.titleMedium)
        Text("Echte ICMP-Echo-Anfragen über das Android-Systemprogramm ping. Verfügbarkeit hängt von der Geräte-Firmware ab.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(host,{host=it},label={Text("Zielhost / IP")},singleLine=true,
            enabled=!running,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(count,{count=it},label={Text("Anzahl (1–20)")},singleLine=true,
            enabled=!running,modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={
                if(running) { job?.cancel();job=null;running=false }
                else {
                    val n=total ?: return@Button
                    val target=host.trim()
                    results.clear();running=true
                    job=scope.launch {
                        try {
                            repeat(n) { index ->
                                if(!isActive) return@launch
                                val result=withContext(Dispatchers.IO) {
                                    runCatching { pingOnce(target,index+1) }.getOrElse {
                                        PingResult(index+1,null,null,it.localizedMessage ?: "ping nicht verfügbar")
                                    }
                                }
                                results.add(result)
                            }
                        } catch (_: CancellationException) {
                            // User stopped the run.
                        } finally { running=false }
                    }
                }
            },enabled=running || (host.isNotBlank() && total!=null),modifier=Modifier.weight(1f)) {
                Text(if(running) "Stoppen" else "Ping starten")
            }
            OutlinedButton(onClick={results.clear()},enabled=!running && results.isNotEmpty()) { Text("Leeren") }
        }
        if(running) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(results.isNotEmpty()) {
            Text("Antworten: $successes / ${results.size} · Verlust: ${"%.0f".format(java.util.Locale.US,100.0*(results.size-successes)/results.size)} %")
            Text("Ø RTT: ${average?.let { "%.2f ms".format(java.util.Locale.US,it) } ?: "—"}")
            results.forEach { result ->
                HorizontalDivider()
                Text("#${result.sequence} · ${result.latencyMs?.let { "%.2f ms".format(java.util.Locale.US,it) } ?: "Keine Antwort"}",
                    style=MaterialTheme.typography.labelLarge)
                Text(result.message,style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}
