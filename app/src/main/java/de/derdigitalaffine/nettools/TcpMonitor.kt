package de.derdigitalaffine.nettools

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** TCP connect monitoring, NOT ICMP echo. No background service: stops when composable leaves composition. */
data class TcpSample(val timestamp: Long, val latencyMs: Float?, val error: String? = null)
data class TcpStats(val count: Int, val successful: Int, val failed: Int, val min: Float?, val max: Float?, val avg: Float?, val jitter: Float?) {
    val lossPercent: Double get() = if (count == 0) 0.0 else failed * 100.0 / count
}
fun tcpStats(samples: List<TcpSample>): TcpStats {
    val valid = samples.mapNotNull { it.latencyMs }
    val deltas = valid.zipWithNext { a, b -> kotlin.math.abs(b - a) }
    return TcpStats(samples.size, valid.size, samples.size-valid.size,
        valid.minOrNull(), valid.maxOrNull(), valid.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
        deltas.takeIf { it.isNotEmpty() }?.average()?.toFloat())
}
private fun tcpProbe(host: String, port: Int, timeoutMs: Int): TcpSample {
    val start = System.nanoTime()
    return try {
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
        TcpSample(System.currentTimeMillis(), (System.nanoTime()-start)/1_000_000f)
    } catch (e: Exception) {
        TcpSample(System.currentTimeMillis(), null, e.javaClass.simpleName + ": " + (e.message ?: "nicht erreichbar"))
    }
}
private fun metricText(ms: Float?): String = ms?.let { "%.1f ms".format(Locale.US, it) } ?: "—"
private fun percentText(value: Double) = "%.1f %%".format(Locale.US,value)

@Composable
fun TcpMonitor() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var host by rememberSaveable { mutableStateOf("1.1.1.1") }
    var portText by rememberSaveable { mutableStateOf("443") }
    var intervalText by rememberSaveable { mutableStateOf("2") }
    var timeoutText by rememberSaveable { mutableStateOf("1500") }
    var active by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val samples = remember { mutableStateListOf<TcpSample>() }
    val snapshot = samples.toList()
    val stats = remember(snapshot) { tcpStats(snapshot) }
    val validPort = portText.toIntOrNull()?.takeIf { it in 1..65535 }
    val interval = intervalText.toIntOrNull()?.takeIf { it in 1..60 }
    val timeout = timeoutText.toIntOrNull()?.takeIf { it in 200..10000 }
    fun stop() { job?.cancel(); job=null; active=false }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }

    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("TCP-Verfügbarkeitsmonitor",style=MaterialTheme.typography.titleMedium)
        Text("Wiederholte TCP-Verbindungsversuche mit Zeitverlauf, Ausfällen und Schwankung. Kein ICMP-Ping.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(host,{host=it},label={Text("Zielhost / IP")},singleLine=true,
            enabled=!active,modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(portText,{portText=it},label={Text("Port")},singleLine=true,
                enabled=!active,modifier=Modifier.weight(1f))
            OutlinedTextField(intervalText,{intervalText=it},label={Text("Intervall (s)")},singleLine=true,
                enabled=!active,modifier=Modifier.weight(1f))
        }
        OutlinedTextField(timeoutText,{timeoutText=it},label={Text("Timeout (ms)")},
            singleLine=true,enabled=!active,modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={
                if(active) stop() else {
                    val p=validPort ?: return@Button
                    val i=interval ?: return@Button
                    val t=timeout ?: return@Button
                    val target=host.trim()
                    active=true
                    job=scope.launch {
                        try {
                            while(isActive) {
                                val sample=withContext(Dispatchers.IO) { tcpProbe(target,p,t) }
                                samples.add(sample)
                                if(samples.size>300) samples.removeAt(0)
                                delay(i*1000L)
                            }
                        } catch (_: CancellationException) {
                            throw CancellationException()
                        } finally { active=false }
                    }
                }
            },enabled=active || (host.isNotBlank() && validPort!=null && interval!=null && timeout!=null),
                modifier=Modifier.weight(1f)) {
                Text(if(active) "Messung stoppen" else "Messung starten")
            }
            OutlinedButton(onClick={samples.clear()},enabled=!active && samples.isNotEmpty()) { Text("Leeren") }
        }
        if(active) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(samples.isNotEmpty()) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                MonitorMetric("Erfolg","${stats.successful}/${stats.count}",Modifier.weight(1f))
                MonitorMetric("Ausfälle",percentText(stats.lossPercent),Modifier.weight(1f))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                MonitorMetric("Ø TCP",metricText(stats.avg),Modifier.weight(1f))
                MonitorMetric("Jitter*",metricText(stats.jitter),Modifier.weight(1f))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                MonitorMetric("Minimum",metricText(stats.min),Modifier.weight(1f))
                MonitorMetric("Maximum",metricText(stats.max),Modifier.weight(1f))
            }
            MonitorChart(snapshot)
            Text("* Mittlere absolute Änderung aufeinanderfolgender erfolgreicher TCP-Connect-Zeiten; kein RFC-ICMP-Jitter.",
                style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick={
                val csv=buildString {
                    appendLine("timestamp_utc,host,port,latency_ms,success,error")
                    snapshot.forEach { s ->
                        val error=(s.error ?: "").replace(34.toChar().toString(), 34.toChar().toString().repeat(2))
                        appendLine("${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US).apply { timeZone=java.util.TimeZone.getTimeZone("UTC") }.format(Date(s.timestamp))},${host.replace(",","")},${portText},${s.latencyMs ?: ""},${s.latencyMs!=null},\"${error}\"")
                    }
                }
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type="text/csv"
                    putExtra(Intent.EXTRA_TEXT,csv)
                },"Messdaten exportieren"))
            },modifier=Modifier.fillMaxWidth()) { Text("CSV teilen / exportieren") }
            Text("Letzte Messung: " + (snapshot.lastOrNull()?.let {
                if(it.latencyMs!=null) metricText(it.latencyMs) else it.error ?: "Fehler"
            } ?: "—"),style=MaterialTheme.typography.bodySmall)
        } else {
            Text("Noch keine Messwerte. Der Monitor läuft nur bei geöffneter Ansicht.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun MonitorMetric(title:String,value:String,modifier:Modifier) {
    Surface(modifier=modifier,color=MaterialTheme.colorScheme.surfaceVariant,shape=RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(title,style=MaterialTheme.typography.labelSmall)
            Text(value,style=MaterialTheme.typography.titleMedium)
        }
    }
}
@Composable private fun MonitorChart(samples:List<TcpSample>) {
    val visible=samples.takeLast(80)
    val good=visible.mapNotNull { it.latencyMs }
    val ceiling=(good.maxOrNull() ?: 100f).coerceAtLeast(10f)*1.15f
    val accent=MaterialTheme.colorScheme.primary
    val error=MaterialTheme.colorScheme.error
    Canvas(Modifier.fillMaxWidth().height(170.dp)
        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp)) {
        val w=size.width
        val h=size.height
        val path=Path()
        var started=false
        visible.forEachIndexed { i,s ->
            val x=if(visible.size==1) w/2 else i*w/(visible.size-1)
            val ms=s.latencyMs
            if(ms==null) {
                started=false
                drawLine(error,Offset(x,h-12.dp.toPx()),Offset(x,h),strokeWidth=3.dp.toPx())
            } else {
                val y=h-(ms/ceiling).coerceIn(0f,1f)*h
                if(!started) { path.moveTo(x,y); started=true } else path.lineTo(x,y)
                drawCircle(accent,radius=2.5.dp.toPx(),center=Offset(x,y))
            }
        }
        drawPath(path,accent,style=Stroke(width=2.dp.toPx(),cap=StrokeCap.Round))
    }
}
