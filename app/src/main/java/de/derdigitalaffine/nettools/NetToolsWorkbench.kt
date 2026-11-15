package de.derdigitalaffine.nettools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale

private data class Page(val title:String, val label:String, val symbol: androidx.compose.ui.graphics.vector.ImageVector)
private val pages = listOf(
    Page("Übersicht","Start",Icons.Outlined.Dashboard),
    Page("Netzwerkdiagnose","Diagnose",Icons.Outlined.MonitorHeart),
    Page("Bluetooth & BLE","Funk",Icons.Outlined.Bluetooth),
    Page("GNSS & Sensorik","Sensoren",Icons.Outlined.Explore),
    Page("USB & Hardware","Hardware",Icons.Outlined.Usb)
)
private fun Context.permissionOk(p: String) =
    ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

@Composable
fun NetToolsWorkbench(
    bleRows: List<String>, scanning: Boolean, position: String,
    sensorRows: Map<String,String>, getSensors: () -> List<String>,
    startBle: () -> Unit, stopBle: () -> Unit,
    startLocation: () -> Unit, startSensors: () -> Unit, stopSensors: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by rememberSaveableIndex()
    var host by remember { mutableStateOf("example.org") }
    var port by remember { mutableStateOf("443") }
    var running by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("Noch keine Messung durchgeführt") }
    val timings = remember { mutableStateListOf<Float>() }
    var network by remember { mutableStateOf(networkSnapshot(ctx)) }
    var usbRefresh by remember { mutableIntStateOf(0) }
    var sensorActive by remember { mutableStateOf(false) }
    var requestedPermissionFor by remember { mutableStateOf<String?>(null) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if(grants.values.isNotEmpty() && grants.values.all { it }) {
            when(requestedPermissionFor) {
                "ble" -> startBle()
                "gps" -> startLocation()
            }
        }
        requestedPermissionFor=null
    }
    var darkTheme by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(UiPreferenceStore.isDark(ctx)) }
    val tabStates=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    NetToolsTheme(dark=darkTheme) {
        BoxWithConstraints(Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
            val wide = maxWidth >= 740.dp
            val expanded = maxWidth >= 1160.dp
            val select: (Int)->Unit = { selected = it }
            val content: @Composable () -> Unit = {
                Column(Modifier.fillMaxSize()) {
                    Header(pages[selected].title,darkTheme,{
                        darkTheme=!darkTheme
                        UiPreferenceStore.setDark(ctx,darkTheme)
                    })
                    tabStates.SaveableStateProvider(selected) {
                    when(selected) {
                        0 -> Overview(
                            network=network, bleCount=bleRows.size, sensorCount=getSensors().size,
                            open=select, refresh={ network = networkSnapshot(ctx) }
                        )
                        1 -> Diagnostics(
                            host=host, setHost={host=it}, port=port, setPort={port=it},
                            output=output, timings=timings, running=running,
                            dns={
                                if (!running) {
                                    running=true; output="DNS-Auflösung läuft …"
                                    scope.launch {
                                        output=withContext(Dispatchers.IO) { resolveName(host) }
                                        running=false
                                    }
                                }
                            },
                            tcp={
                                if (!running) {
                                    running=true; output="TCP-Handshake wird gemessen …"
                                    scope.launch {
                                        val result=withContext(Dispatchers.IO) { connectTcp(host,port) }
                                        output=result.first
                                        if(result.second!=null) {
                                            timings.add(result.second!!)
                                            if(timings.size>40) timings.removeAt(0)
                                        }
                                        running=false
                                    }
                                }
                            }
                        )
                        2 -> BleScreen(
                            rows=bleRows, scanning=scanning, start={
                                val needed= if(Build.VERSION.SDK_INT>=31)
                                    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                                else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                                if(needed.all(ctx::permissionOk)) startBle() else { requestedPermissionFor="ble"; permissions.launch(needed) }
                            }, stop=stopBle
                        )
                        3 -> SensorsScreen(
                            position=position, sensors=sensorRows, deviceCount=getSensors().size,
                            active=sensorActive,
                            locate={
                                if (ctx.permissionOk(Manifest.permission.ACCESS_COARSE_LOCATION) ||
                                    ctx.permissionOk(Manifest.permission.ACCESS_FINE_LOCATION)) startLocation()
                                else { requestedPermissionFor="gps"; permissions.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) }
                            },
                            openMap={ctx.startActivity(Intent(ctx, MapActivity::class.java).putExtra("dark",darkTheme))},
                            start={sensorActive=true;startSensors()}, stop={sensorActive=false;stopSensors()}
                        )
                        else -> UsbScreen(ctx,usbRefresh) { usbRefresh++ }
                    }
                    }
                }
            }
            if(expanded) {
                Row(Modifier.fillMaxSize()) {
                    Surface(Modifier.width(250.dp).fillMaxHeight(),
                        color=MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxSize().padding(horizontal=12.dp,vertical=18.dp)) {
                            Row(Modifier.padding(start=12.dp,bottom=20.dp),
                                verticalAlignment=Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Hub,contentDescription=null,
                                    tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(30.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("NETTOOLS",style=MaterialTheme.typography.titleMedium,
                                        color=MaterialTheme.colorScheme.onSurface)
                                    Text("Netzwerk-Workbench",style=MaterialTheme.typography.labelSmall,
                                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            pages.forEachIndexed { index,page ->
                                NavigationDrawerItem(
                                    label={Text(page.title,maxLines=1,overflow=TextOverflow.Ellipsis)},
                                    selected=selected==index,onClick={selected=index},
                                    icon={Icon(page.symbol,contentDescription=null)},
                                    modifier=Modifier.padding(vertical=3.dp)
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                            Text("LOKALE NETZWERKTOOLS",
                                Modifier.padding(horizontal=14.dp,vertical=14.dp),
                                color=MaterialTheme.colorScheme.onSurfaceVariant,
                                style=MaterialTheme.typography.labelSmall)
                        }
                    }
                    VerticalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    Box(Modifier.weight(1f)) { content() }
                }
            } else if(wide) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(containerColor=MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor=MaterialTheme.colorScheme.onSurface) {
                        Spacer(Modifier.height(18.dp))
                        Icon(Icons.Outlined.Hub,contentDescription=null,
                            tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(30.dp))
                        Spacer(Modifier.height(25.dp))
                        pages.forEachIndexed { index,page ->
                            NavigationRailItem(
                                selected=selected==index,onClick={selected=index},
                                icon={Icon(page.symbol,contentDescription=null)},
                                label={Text(page.label)},alwaysShowLabel=true
                            )
                        }
                    }
                    VerticalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    Box(Modifier.weight(1f)) { content() }
                }
            } else {
                Scaffold(containerColor=MaterialTheme.colorScheme.background,
                    contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={
                        NavigationBar(containerColor=MaterialTheme.colorScheme.surfaceContainerLow) {
                            pages.forEachIndexed { index,page ->
                                NavigationBarItem(
                                    selected=selected==index,onClick={selected=index},
                                    icon={Icon(page.symbol,contentDescription=page.label)},
                                    label={Text(page.label,maxLines=1)},alwaysShowLabel=true
                                )
                            }
                        }
                    }) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding)) { content() }
                    }
            }
        }
    }
}
@Composable private fun rememberSaveableIndex(): MutableState<Int> = androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }

@Composable
private fun Header(title:String,dark:Boolean,onToggleTheme:()->Unit) {
    Surface(color=MaterialTheme.colorScheme.background) {
        Row(
            modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),
            verticalAlignment=Alignment.CenterVertically
        ) {
            Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(48.dp),contentAlignment=Alignment.Center) {
                    Icon(Icons.Outlined.Hub,contentDescription=null,
                        tint=MaterialTheme.colorScheme.onPrimaryContainer,modifier=Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("NETTOOLS  /  ANDROID",style=MaterialTheme.typography.labelSmall,
                    fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                Text(title,style=MaterialTheme.typography.titleLarge,
                    fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            IconButton(onClick=onToggleTheme,modifier=Modifier.size(48.dp)) {
                Icon(if(dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    contentDescription=if(dark) "Helles Design aktivieren" else "Dunkles Design aktivieren",
                    tint=MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))
}

@Composable
private fun Panel(title:String, subtitle:String?=null, modifier:Modifier=Modifier, body:@Composable ColumnScope.()->Unit) {
    Surface(modifier=modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.surfaceContainerLow,
        shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))) {
        Column(Modifier.padding(18.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            if(subtitle!=null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(14.dp))
            body()
        }
    }
}
@Composable private fun Metric(label:String,value:String,modifier:Modifier=Modifier) {
    Surface(modifier=modifier.heightIn(min=78.dp),color=MaterialTheme.colorScheme.surfaceContainer,
        shape=RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=2)
        }
    }
}
@Composable private fun Section(label:String,detail:String) {
    Spacer(Modifier.height(13.dp))
    Text(label,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold)
    Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
}
@Composable private fun Notice(text:String) {
    Surface(color=MaterialTheme.colorScheme.secondaryContainer.copy(alpha=0.38f),
        shape=RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.Top) {
            Icon(Icons.Outlined.Info,contentDescription=null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun WorkspaceList(body:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(start=20.dp,end=20.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=body)
}

@Composable
private fun Overview(network:NetworkView,bleCount:Int,sensorCount:Int,open:(Int)->Unit,refresh:()->Unit) {
    var showRoutes by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val connected=network.transport!="Nicht verbunden"
    WorkspaceList {
        Surface(shape=RoundedCornerShape(26.dp),
            color=MaterialTheme.colorScheme.primaryContainer,
            modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Surface(shape=RoundedCornerShape(17.dp),
                        color=MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.1f)) {
                        Box(Modifier.size(56.dp),contentAlignment=Alignment.Center) {
                            Icon(if(connected) Icons.Outlined.WifiTethering else Icons.Outlined.SignalWifiOff,
                                contentDescription=null,modifier=Modifier.size(29.dp),
                                tint=MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("AKTIVE VERBINDUNG",style=MaterialTheme.typography.labelSmall,
                            color=MaterialTheme.colorScheme.onPrimaryContainer,fontWeight=FontWeight.Bold)
                        Text(network.transport,style=MaterialTheme.typography.headlineSmall,
                            color=MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(network.interfaceName,
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.8f))
                    }
                    IconButton(onClick=refresh,modifier=Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Refresh,contentDescription="Netzwerk aktualisieren",
                            tint=MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(if(connected) "Geräte- und Netzstatus auf einen Blick. Starte die passenden Werkzeuge direkt darunter."
                    else "Keine aktive Standardverbindung erkannt. Lokale Werkzeuge funktionieren weiterhin.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.88f))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow=maxWidth<430.dp
            if(narrow) {
                Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                        Metric("IP-Adressen",network.addresses.size.toString(),Modifier.weight(1f))
                        Metric("DNS-Server",network.dns.size.toString(),Modifier.weight(1f))
                    }
                    Metric("Routen",network.routes.size.toString(),Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                    Metric("IP-Adressen",network.addresses.size.toString(),Modifier.weight(1f))
                    Metric("DNS-Server",network.dns.size.toString(),Modifier.weight(1f))
                    Metric("Routen",network.routes.size.toString(),Modifier.weight(1f))
                }
            }
        }
        Panel("Werkzeuge","Gezielte Netzwerkanalyse, Funk und Hardware") {
            pages.drop(1).forEachIndexed { n,page ->
                val summary=when(n) {
                    0 -> "${diagnosticTools.size} Diagnosewerkzeuge · DNS, Ping, SNMP, Scanner"
                    1 -> "$bleCount Bluetooth-Geräte erfasst"
                    2 -> "$sensorCount Sensorschnittstellen verfügbar"
                    else -> "USB- und Ethernetgeräte anzeigen"
                }
                Surface(onClick={open(n+1)},color=MaterialTheme.colorScheme.surfaceContainer,
                    shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=12.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Surface(color=MaterialTheme.colorScheme.secondaryContainer,
                            shape=RoundedCornerShape(13.dp)) {
                            Box(Modifier.size(46.dp),contentAlignment=Alignment.Center) {
                                Icon(page.symbol,contentDescription=null,modifier=Modifier.size(24.dp),
                                    tint=MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(page.title,style=MaterialTheme.typography.titleSmall)
                            Text(summary,style=MaterialTheme.typography.bodySmall,
                                maxLines=2,overflow=TextOverflow.Ellipsis,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Outlined.ChevronRight,contentDescription=null,
                            tint=MaterialTheme.colorScheme.primary)
                    }
                }
                if(n<pages.size-2) Spacer(Modifier.height(9.dp))
            }
        }
        Panel("Verbindungsdetails","Adressen und DNS-Resolver der aktiven Standardverbindung") {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Column {
                    Section("IP-ADRESSEN",
                        network.addresses.ifEmpty { listOf("Keine Adresse") }.joinToString("\n"))
                    Section("DNS-RESOLVER",
                        network.dns.ifEmpty { listOf("Kein Resolver gemeldet") }.joinToString("\n"))
                    OutlinedButton(onClick={showRoutes=!showRoutes},modifier=Modifier.fillMaxWidth()) {
                        Icon(if(showRoutes) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription=null)
                        Spacer(Modifier.width(8.dp))
                        Text(if(showRoutes) "Routen ausblenden" else "${network.routes.size} Routen anzeigen")
                    }
                    if(showRoutes) Section("ROUTEN",
                        network.routes.ifEmpty { listOf("Keine Routen") }.joinToString("\n"))
                }
            }
        }
        Notice("Die App nutzt den aktuellen Android-Netzwerkpfad. Non-Root-TCP-Scans, ICMP, DNS und SNMP sind verfügbar; physischer Layer-2-Mitschnitt und LLDP benötigen zusätzliche Voraussetzungen.")
    }
}

@Composable
private fun Diagnostics(
    host:String,setHost:(String)->Unit,port:String,setPort:(String)->Unit,
    output:String,timings:List<Float>,running:Boolean,dns:()->Unit,tcp:()->Unit
) {
    DiagnosticsHub { id ->
        when(id) {
            "quick" -> {
                Text("Schnelltest",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(host,setHost,label={Text("Hostname oder IP-Adresse")},
                    modifier=Modifier.fillMaxWidth(),singleLine=true,
                    shape=RoundedCornerShape(14.dp),placeholder={Text("example.org")})
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(port,setPort,label={Text("TCP-Port")},
                    modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(14.dp))
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Button(onClick=dns,enabled=!running && host.isNotBlank(),
                        modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                        Icon(Icons.Outlined.Dns,contentDescription=null,modifier=Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("DNS")
                    }
                    Button(onClick=tcp,enabled=!running && host.isNotBlank() && port.toIntOrNull() in 1..65535,
                        modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                        Icon(Icons.Outlined.NetworkCheck,contentDescription=null,modifier=Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("TCP")
                    }
                }
                if(running) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))
                Text("Letztes Ergebnis",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(output,style=MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Text("Verbindungszeiten",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                if(timings.isEmpty()) {
                    Notice("Noch keine TCP-Messwerte. Eine Messung erstellt einen Verlauf.")
                } else {
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Metric("Letzte",formatMs(timings.last()),Modifier.weight(1f))
                        Metric("Min",formatMs(timings.min()),Modifier.weight(1f))
                        Metric("Max",formatMs(timings.max()),Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Timeline(timings)
                }
                Spacer(Modifier.height(10.dp))
                Text("TCP-Connect ist keine ICMP-Roundtrip-Messung.",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            "monitor" -> TcpMonitor()
            "ports" -> PortScanner()
            "nmap" -> NmapXmlImportPanel()
            "snmp" -> SnmpInspector()
            "walk" -> SnmpWalkPanel()
            "snmpv3" -> SnmpV3Inspector()
            "ping" -> IcmpDiagnostics()
            "route" -> RouteDiagnostics()
            "dns" -> DnsExplorer()
            "tls" -> TlsInspectorPanel(host)
            "subnet" -> SubnetPanel()
        }
    }
}
private fun formatMs(f:Float) = "%.1f ms".format(Locale.US,f)
@Composable
private fun SubnetPanel() {
    var cidr by remember { mutableStateOf("192.168.1.42/24") }
    val calculation = remember(cidr) { runCatching { ipv4Subnet(cidr) } }
    Panel("IPv4-Subnetzrechner","Netzmaske, Adressbereich und nutzbare Hosts") {
        OutlinedTextField(value=cidr,onValueChange={cidr=it},label={Text("Adresse / CIDR")},
            singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp))
        Spacer(Modifier.height(12.dp))
        calculation.onSuccess { result ->
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Metric("Netzadresse",result.network,Modifier.weight(1f))
                Metric("Netzmaske",result.netmask,Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Metric("Erster Host",result.first,Modifier.weight(1f))
                Metric("Letzter Host",result.last,Modifier.weight(1f))
            }
            Section("BROADCAST",result.broadcast)
            Text("Adressen: ${result.count} · Nutzbar: ${result.usableHosts}",style=MaterialTheme.typography.bodyMedium)
            if(result.prefix>=31) {
                Spacer(Modifier.height(8.dp))
                Text("/31 verwendet zwei Point-to-Point-Adressen, /32 eine einzelne Hostadresse.",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }.onFailure {
            Text(it.message ?: "Ungültige Adresse",color=MaterialTheme.colorScheme.error,
                style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun Timeline(values:List<Float>) {
    val min=values.minOrNull() ?: 0f
    val max=(values.maxOrNull() ?: 1f).coerceAtLeast(min+1f)
    val graphAccent=MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(125.dp).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)).padding(14.dp)) {
        val path=Path()
        values.forEachIndexed { i,value ->
            val x= if(values.size==1) size.width/2f else i.toFloat()/(values.size-1)*size.width
            val y=size.height-(value-min)/(max-min)*size.height
            if(i==0) path.moveTo(x,y) else path.lineTo(x,y)
        }
        drawPath(path,graphAccent,style=Stroke(width=3.dp.toPx(),cap=StrokeCap.Round))
        values.forEachIndexed { i,value ->
            val x=if(values.size==1) size.width/2f else i.toFloat()/(values.size-1)*size.width
            val y=size.height-(value-min)/(max-min)*size.height
            drawCircle(graphAccent,radius=3.dp.toPx(),center=Offset(x,y))
        }
    }
}

@Composable
private fun WorkspaceEmpty(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,detail:String) {
    Surface(color=MaterialTheme.colorScheme.surfaceContainer,
        shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=26.dp),
            horizontalAlignment=Alignment.CenterHorizontally) {
            Icon(icon,contentDescription=null,modifier=Modifier.size(37.dp),
                tint=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(title,style=MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(detail,color=MaterialTheme.colorScheme.onSurfaceVariant,
                style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun BleScreen(rows:List<String>,scanning:Boolean,start:()->Unit,stop:()->Unit) {
    WorkspaceList {
        Panel("Bluetooth Low Energy","Geräte entdecken · Signalstärke und Service-UUIDs") {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Surface(color=MaterialTheme.colorScheme.primaryContainer,
                    shape=RoundedCornerShape(18.dp)) {
                    Box(Modifier.size(54.dp),contentAlignment=Alignment.Center) {
                        Icon(Icons.Outlined.BluetoothSearching,contentDescription=null,
                            tint=MaterialTheme.colorScheme.onPrimaryContainer,modifier=Modifier.size(29.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if(scanning) "Suche nach Geräten" else "Scanner bereit",
                        style=MaterialTheme.typography.titleMedium)
                    Text("${rows.size} Einträge · ${if(scanning) "Scan aktiv" else "Scan gestoppt"}",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Button(onClick=start,modifier=Modifier.weight(1f).heightIn(min=50.dp)) {
                    Icon(Icons.Outlined.PlayArrow,contentDescription=null)
                    Spacer(Modifier.width(6.dp))
                    Text(if(scanning) "Neu starten" else "Scan starten")
                }
                OutlinedButton(onClick=stop,enabled=scanning,
                    modifier=Modifier.heightIn(min=50.dp)) {
                    Icon(Icons.Outlined.Stop,contentDescription=null)
                    Spacer(Modifier.width(6.dp))
                    Text("Stopp")
                }
            }
            Spacer(Modifier.height(13.dp))
            Notice("Bluetooth- und Standortfreigaben sind geräteabhängig. Die App verbindet erkannte Geräte nicht automatisch.")
        }
        Panel("Geräte in Reichweite","Adressen, RSSI und annoncierte Dienste") {
            if(rows.isEmpty()) {
                WorkspaceEmpty(Icons.Outlined.BluetoothDisabled,
                    "Noch keine Geräte","Starte einen Scan, um BLE-Advertisements zu sehen.")
            } else {
                rows.take(80).forEachIndexed { index,entry ->
                    val values=entry.split(" | ",limit=3)
                    val address=values.firstOrNull().orEmpty()
                    val rssi=values.getOrNull(1).orEmpty()
                    val services=values.getOrNull(2).orEmpty()
                    Surface(color=MaterialTheme.colorScheme.surfaceContainer,
                        shape=RoundedCornerShape(15.dp),modifier=Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(13.dp),verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Bluetooth,contentDescription=null,
                                tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(25.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(address,style=MaterialTheme.typography.titleSmall,
                                    maxLines=2,overflow=TextOverflow.Ellipsis)
                                if(services.isNotBlank()) Text(services,
                                    style=MaterialTheme.typography.bodySmall,
                                    maxLines=2,overflow=TextOverflow.Ellipsis,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                                if(rssi.isNotBlank()) Text(rssi,
                                    style=MaterialTheme.typography.labelMedium,
                                    color=MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if(index<rows.size-1) Spacer(Modifier.height(8.dp))
                }
                if(rows.size>80) Text("Weitere ${rows.size-80} Ergebnisse ausgeblendet.",
                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                    style=MaterialTheme.typography.bodySmall)
            }
        }
        Notice("Bluetooth-Discovery ist kein Funk-Paketmitschnitt. GATT-Verbindungen und Classic-RFCOMM sind noch nicht implementiert.")
    }
}

@Composable
private fun SensorsScreen(
    position:String,sensors:Map<String,String>,deviceCount:Int,active:Boolean,
    locate:()->Unit,openMap:()->Unit,start:()->Unit,stop:()->Unit
) {
    WorkspaceList {
        Panel("GNSS & Position","Standort nur nach ausdrücklicher Freigabe") {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Surface(color=MaterialTheme.colorScheme.primaryContainer,
                    shape=RoundedCornerShape(17.dp)) {
                    Box(Modifier.size(54.dp),contentAlignment=Alignment.Center) {
                        Icon(Icons.Outlined.MyLocation,contentDescription=null,
                            tint=MaterialTheme.colorScheme.onPrimaryContainer,modifier=Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("Position",style=MaterialTheme.typography.titleMedium)
                    Text(position,color=MaterialTheme.colorScheme.onSurfaceVariant,
                        style=MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(17.dp))
            Button(onClick=locate,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)) {
                Icon(Icons.Outlined.GpsFixed,contentDescription=null)
                Spacer(Modifier.width(8.dp));Text("Position bestimmen")
            }
            Spacer(Modifier.height(7.dp))
            OutlinedButton(onClick=openMap,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)) {
                Icon(Icons.Outlined.Map,contentDescription=null)
                Spacer(Modifier.width(8.dp));Text("Karte öffnen · basemap.de")
            }
            Spacer(Modifier.height(12.dp))
            Notice("Die Karte lädt Kacheln von basemap.de. Es erfolgt keine automatische Standortübermittlung an den Kartenanbieter.")
        }
        Panel("Live-Sensordaten","Android SensorManager · unterstützte Gerätesensoren") {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Surface(shape=RoundedCornerShape(13.dp),
                    color=MaterialTheme.colorScheme.secondaryContainer) {
                    Text("$deviceCount verfügbar",Modifier.padding(horizontal=12.dp,vertical=9.dp),
                        style=MaterialTheme.typography.labelMedium,
                        color=MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Spacer(Modifier.weight(1f))
                if(active) OutlinedButton(onClick=stop) {
                    Icon(Icons.Outlined.Stop,contentDescription=null)
                    Spacer(Modifier.width(6.dp));Text("Stoppen")
                } else Button(onClick=start) {
                    Icon(Icons.Outlined.PlayArrow,contentDescription=null)
                    Spacer(Modifier.width(6.dp));Text("Live starten")
                }
            }
            Spacer(Modifier.height(13.dp))
            if(sensors.isEmpty()) {
                WorkspaceEmpty(Icons.Outlined.Sensors,
                    "Keine Live-Messwerte","Starte die Messung, um Sensorwerte einzulesen.")
            } else {
                sensors.toSortedMap().entries.take(80).forEach { (name,values) ->
                    Surface(color=MaterialTheme.colorScheme.surfaceContainer,
                        modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(13.dp)) {
                            Text(name,style=MaterialTheme.typography.titleSmall,
                                maxLines=2,overflow=TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(values,color=MaterialTheme.colorScheme.primary,
                                style=MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun UsbScreen(ctx:Context,refresh:Int,onRefresh:()->Unit) {
    val devices=remember(refresh) {
        ctx.getSystemService(UsbManager::class.java)?.deviceList?.values
            ?.sortedWith(compareBy({it.vendorId},{it.productId})) ?: emptyList()
    }
    WorkspaceList {
        Panel("USB-Geräte","Hostmodus · angeschlossene Adapter und Geräte") {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Surface(color=MaterialTheme.colorScheme.primaryContainer,
                    shape=RoundedCornerShape(17.dp)) {
                    Box(Modifier.size(54.dp),contentAlignment=Alignment.Center) {
                        Icon(Icons.Outlined.Usb,contentDescription=null,
                            tint=MaterialTheme.colorScheme.onPrimaryContainer,modifier=Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("${devices.size} Geräte",style=MaterialTheme.typography.titleLarge)
                    Text("USB-Host-Erkennung",style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalIconButton(onClick=onRefresh,modifier=Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Refresh,contentDescription="USB-Geräte aktualisieren")
                }
            }
            Spacer(Modifier.height(16.dp))
            if(devices.isEmpty()) {
                WorkspaceEmpty(Icons.Outlined.UsbOff,"Kein USB-Gerät erkannt",
                    "OTG-Gerät anschließen und oben aktualisieren.")
            } else devices.forEach { device ->
                Surface(color=MaterialTheme.colorScheme.surfaceContainer,
                    shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(15.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Outlined.DeveloperBoard,contentDescription=null,
                            tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(26.dp))
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text("VID %04X · PID %04X".format(device.vendorId,device.productId),
                                style=MaterialTheme.typography.titleSmall)
                            Text("${device.interfaceCount} Schnittstellen · USB-Klasse ${device.deviceClass}",
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Notice("Die Erkennung allein bietet weder eine serielle Konsole noch Layer-2-Mitschnitt. Für LLDP sind ein unterstütztes Capture-Interface oder ein externer Agent erforderlich.")
    }
}

private data class NetworkView(
    val transport:String,val interfaceName:String,val addresses:List<String>,val dns:List<String>,val routes:List<String>
)
private fun networkSnapshot(ctx:Context):NetworkView {
    val mgr=ctx.getSystemService(android.net.ConnectivityManager::class.java)
    val network=mgr?.activeNetwork
    val link=network?.let { mgr.getLinkProperties(it) }
    val caps=network?.let { mgr.getNetworkCapabilities(it) }
    val type=when {
        caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)==true -> "VPN"
        caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)==true -> "Ethernet"
        caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)==true -> "WLAN"
        caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)==true -> "Mobilfunk"
        else -> "Nicht verbunden"
    }
    return NetworkView(type,link?.interfaceName ?: "Kein aktives Interface",
        link?.linkAddresses?.map { it.toString() } ?: emptyList(),
        link?.dnsServers?.map { it.hostAddress ?: "unbekannt" } ?: emptyList(),
        link?.routes?.map { it.toString() } ?: emptyList())
}
private fun resolveName(host:String):String = runCatching {
    val results=InetAddress.getAllByName(host.trim())
    if(results.isEmpty()) "Keine Adressen" else results.joinToString("\n") {
        "${if(it is java.net.Inet6Address) "AAAA" else "A"} · ${it.hostAddress}"
    }
}.getOrElse { "DNS fehlgeschlagen: ${it.localizedMessage}" }

private fun connectTcp(host:String,port:String):Pair<String,Float?> = runCatching {
    val p=port.toInt()
    require(p in 1..65535) { "Port muss zwischen 1 und 65535 liegen" }
    val start=System.nanoTime()
    Socket().use { it.connect(InetSocketAddress(host.trim(),p),3500) }
    val ms=(System.nanoTime()-start)/1_000_000f
    Pair("TCP-Verbindung zu $host:$p erfolgreich · ${formatMs(ms)}",ms)
}.getOrElse { Pair("TCP-Verbindung fehlgeschlagen: ${it.localizedMessage}",null) }
