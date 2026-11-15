package de.derdigitalaffine.nettools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

private val Accent = Color(0xFF17A2A4)
private val Ink = Color(0xFFE9F1F4)
private val Muted = Color(0xFF9BADB9)
private val Surface = Color(0xFF152530)
private val Base = Color(0xFF0B1822)
private val Nav = Color(0xFF101F29)

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
    val theme = darkColorScheme(
        primary=Accent, onPrimary=Base, secondary=Accent, background=Base,
        onBackground=Ink, surface=Surface, onSurface=Ink, surfaceVariant=Nav, onSurfaceVariant=Muted,
        outline=Color(0xFF344856), error=Color(0xFFFF7F88)
    )
    MaterialTheme(colorScheme=theme) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Base).safeDrawingPadding()) {
            val wide = maxWidth >= 740.dp
            val select: (Int)->Unit = { selected = it }
            val content: @Composable () -> Unit = {
                Column(Modifier.fillMaxSize()) {
                    Header(pages[selected].title)
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
                            openMap={ctx.startActivity(Intent(ctx, MapActivity::class.java))},
                            start={sensorActive=true;startSensors()}, stop={sensorActive=false;stopSensors()}
                        )
                        else -> UsbScreen(ctx,usbRefresh) { usbRefresh++ }
                    }
                }
            }
            if(wide) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(containerColor=Nav, contentColor=Ink) {
                        Spacer(Modifier.height(18.dp))
                        Icon(Icons.Outlined.Router, contentDescription=null, tint=Accent, modifier=Modifier.size(30.dp))
                        Spacer(Modifier.height(30.dp))
                        pages.forEachIndexed { index,page ->
                            NavigationRailItem(
                                selected=selected==index, onClick={selected=index},
                                icon={Icon(page.symbol,contentDescription=null)}, label={Text(page.label)},
                                alwaysShowLabel=true
                            )
                        }
                    }
                    VerticalDivider(color=Color(0xFF29404A))
                    Box(Modifier.weight(1f)) { content() }
                }
            } else {
                Scaffold(containerColor=Base, contentWindowInsets=WindowInsets(0,0,0,0), bottomBar={
                    NavigationBar(containerColor=Nav) {
                        pages.forEachIndexed { index,page ->
                            NavigationBarItem(
                                selected=selected==index, onClick={selected=index},
                                icon={Icon(page.symbol, contentDescription=page.label)},
                                label={Text(page.label, maxLines=1)}, alwaysShowLabel=true
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
private fun Header(title:String) {
    Row(
        modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=18.dp),
        verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween
    ) {
        Column {
            Text("NETTOOLS  /  WORKSPACE",style=MaterialTheme.typography.labelSmall,
                fontWeight=FontWeight.Bold,color=Accent)
            Text(title,style=MaterialTheme.typography.headlineSmall,
                fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        Surface(shape=RoundedCornerShape(14.dp),color=Surface) {
            Row(Modifier.padding(horizontal=12.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.Shield,contentDescription=null,tint=Accent,modifier=Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("LOCAL",style=MaterialTheme.typography.labelSmall,color=Ink,fontWeight=FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Panel(title:String, subtitle:String?=null, modifier:Modifier=Modifier, body:@Composable ColumnScope.()->Unit) {
    Surface(modifier=modifier.fillMaxWidth(),color=Surface,shape=RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            if(subtitle!=null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle,style=MaterialTheme.typography.bodySmall,color=Muted)
            }
            Spacer(Modifier.height(14.dp))
            body()
        }
    }
}
@Composable private fun Metric(label:String,value:String,modifier:Modifier=Modifier) {
    Surface(modifier=modifier,color=Nav,shape=RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(label,style=MaterialTheme.typography.labelMedium,color=Muted)
            Spacer(Modifier.height(6.dp))
            Text(value,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=2)
        }
    }
}
@Composable private fun Section(label:String,detail:String) {
    Spacer(Modifier.height(13.dp))
    Text(label,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold)
    Text(detail,style=MaterialTheme.typography.bodySmall,color=Muted)
    Spacer(Modifier.height(8.dp))
}
@Composable private fun Notice(text:String) {
    Surface(color=Nav,shape=RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.Top) {
            Icon(Icons.Outlined.Info,contentDescription=null,tint=Accent,modifier=Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text,style=MaterialTheme.typography.bodySmall,color=Muted)
        }
    }
}
@Composable private fun WorkspaceList(body:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(start=20.dp,end=20.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=body)
}

@Composable
private fun Overview(network:NetworkView,bleCount:Int,sensorCount:Int,open:(Int)->Unit,refresh:()->Unit) {
    WorkspaceList {
        Panel("Netzwerkstatus","Aktive Android-Verbindung · live bei Aktualisierung") {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.WifiTethering,contentDescription=null,tint=Accent,
                    modifier=Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(network.transport,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Text(network.interfaceName,color=Muted)
                }
                FilledTonalIconButton(onClick=refresh) {
                    Icon(Icons.Outlined.Refresh,contentDescription="Netzwerk aktualisieren")
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Metric("IP-Adressen",network.addresses.size.toString(),Modifier.weight(1f))
                Metric("DNS-Server",network.dns.size.toString(),Modifier.weight(1f))
                Metric("Routen",network.routes.size.toString(),Modifier.weight(1f))
            }
            Section("IP-ADRESSEN",network.addresses.ifEmpty { listOf("Nicht verfügbar") }.joinToString("\n"))
            Section("DNS-RESOLVER",network.dns.ifEmpty { listOf("Nicht verfügbar") }.joinToString("\n"))
            Notice("Für Quellinterfaces und VPNs können sich Routen unterscheiden. Angezeigt wird die aktive Standardverbindung.")
        }
        Panel("Schnellzugriff","Direkt zu den wichtigsten Werkzeugen") {
            val shortcuts=listOf(
                Triple("DNS & TCP","Resolver und Verbindung prüfen",1),
                Triple("Bluetooth LE","Geräte entdecken · $bleCount Ergebnisse",2),
                Triple("GNSS & Sensoren","$sensorCount Sensoren verfügbar",3),
                Triple("USB & Ethernet","Hardware erkennen",4)
            )
            shortcuts.forEach { (name,desc,index) ->
                Row(Modifier.fillMaxWidth().clickable { open(index) }.padding(vertical=12.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name,fontWeight=FontWeight.SemiBold)
                        Text(desc,color=Muted,style=MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Outlined.ChevronRight,contentDescription="Öffnen",tint=Accent)
                }
                if(index!=shortcuts.last().third) HorizontalDivider(color=Color(0xFF2C404B))
            }
        }
        Notice("Alpha-Ausbau: Ping, Traceroute, Paketmitschnitt, LLDP und SmokePing sind Roadmap-Funktionen und werden hier nicht als verfügbar dargestellt.")
    }
}

@Composable
private fun Diagnostics(
    host:String,setHost:(String)->Unit,port:String,setPort:(String)->Unit,
    output:String,timings:List<Float>,running:Boolean,dns:()->Unit,tcp:()->Unit
) {
    WorkspaceList {
        Panel("Zielsystem","DNS-Auflösung und TCP-Connect über den aktuellen Android-Netzwerkpfad") {
            OutlinedTextField(host,setHost,label={Text("Hostname oder IP-Adresse")},modifier=Modifier.fillMaxWidth(),
                singleLine=true,shape=RoundedCornerShape(14.dp),placeholder={Text("example.org")})
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(port,setPort,label={Text("TCP-Port")},modifier=Modifier.fillMaxWidth(),
                singleLine=true,shape=RoundedCornerShape(14.dp))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Button(onClick=dns,enabled=!running && host.isNotBlank(),modifier=Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Dns,contentDescription=null,modifier=Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("DNS")
                }
                Button(onClick=tcp,enabled=!running && host.isNotBlank() && port.toIntOrNull() in 1..65535,
                    modifier=Modifier.weight(1f)) {
                    Icon(Icons.Outlined.NetworkCheck,contentDescription=null,modifier=Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("TCP")
                }
            }
            if(running) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        Panel("Diagnoseausgabe","Ergebnisse des letzten Messlaufs") {
            Text(output,color=Ink,style=MaterialTheme.typography.bodyMedium)
        }
        Panel("Verbindungszeiten","Erfolgreiche TCP-Connect-Messungen · Millisekunden") {
            if(timings.isEmpty()) {
                Notice("Noch keine TCP-Messwerte. Starte einen TCP-Test, um den Verlauf zu sehen.")
            } else {
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Metric("Letzte",formatMs(timings.last()),Modifier.weight(1f))
                    Metric("Minimum",formatMs(timings.min()),Modifier.weight(1f))
                    Metric("Maximum",formatMs(timings.max()),Modifier.weight(1f))
                }
                Spacer(Modifier.height(15.dp))
                Timeline(timings)
            }
        }
        Panel("Live-Monitoring","TCP-Connect-Verfügbarkeit · Zeitreihen · CSV-Export") {
            TcpMonitor()
        }
        Panel("Nmap-orientierter TCP-Scan","Non-Root TCP Connect · bis 1024 Ports · parallel") {
            PortScanner()
        }
        Panel("SNMP-Geräteanalyse","SNMPv2c Read-only · OID-Abfrage über UDP/161") {
            SnmpInspector()
        }
        Panel("ICMP Ping","Echte Echo-Anfragen mit RTT und Verlustquote") {
            IcmpDiagnostics()
        }
        Panel("Traceroute","TTL-basierte Hop-Analyse mit Live-Ergebnissen") {
            RouteDiagnostics()
        }
        Panel("DNS-Explorer","A, AAAA, MX, TXT, NS, CNAME, SOA, SRV, PTR und CAA") {
            DnsExplorer()
        }
        Panel("TLS / HTTPS","Zertifikat, TLS-Version, Cipher und SHA-256-Fingerprint") {
            TlsInspectorPanel(host)
        }
        SubnetPanel()
        Notice("Die TCP-Verbindungszeit ist keine ICMP-Roundtrip-Zeit. DNS-Ergebnisse stammen aktuell vom Android-Systemresolver.")
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
                    style=MaterialTheme.typography.bodySmall,color=Muted)
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
    Canvas(Modifier.fillMaxWidth().height(125.dp).background(Nav, RoundedCornerShape(12.dp)).padding(14.dp)) {
        val path=Path()
        values.forEachIndexed { i,value ->
            val x= if(values.size==1) size.width/2f else i.toFloat()/(values.size-1)*size.width
            val y=size.height-(value-min)/(max-min)*size.height
            if(i==0) path.moveTo(x,y) else path.lineTo(x,y)
        }
        drawPath(path,Accent,style=Stroke(width=3.dp.toPx(),cap=StrokeCap.Round))
        values.forEachIndexed { i,value ->
            val x=if(values.size==1) size.width/2f else i.toFloat()/(values.size-1)*size.width
            val y=size.height-(value-min)/(max-min)*size.height
            drawCircle(Accent,radius=3.dp.toPx(),center=Offset(x,y))
        }
    }
}

@Composable private fun BleScreen(rows:List<String>,scanning:Boolean,start:()->Unit,stop:()->Unit) {
    WorkspaceList {
        Panel("BLE-Scanner","Bluetooth Low Energy · Advertisement-Discovery") {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.BluetoothSearching,contentDescription=null,tint=Accent,modifier=Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if(scanning) "Scan aktiv" else "Bereit zum Scannen",fontWeight=FontWeight.Bold)
                    Text("${rows.size} erkannte Adressen",style=MaterialTheme.typography.bodySmall,color=Muted)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Button(onClick=start,modifier=Modifier.weight(1f)) { Text(if(scanning) "Neu starten" else "Scan starten") }
                OutlinedButton(onClick=stop,enabled=scanning) { Text("Stoppen") }
            }
            Spacer(Modifier.height(12.dp))
            Notice("Bluetooth-Scan und Standortdaten benötigen Android-Freigaben. Es werden keine Geräte automatisch verbunden.")
        }
        Panel("Gefundene Geräte","MAC/zufällige Adresse, RSSI und annoncierte Service-UUIDs") {
            if(rows.isEmpty()) Text("Noch keine BLE-Geräte entdeckt.",color=Muted)
            rows.forEachIndexed { index,row ->
                Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Bluetooth,contentDescription=null,tint=Accent)
                    Spacer(Modifier.width(11.dp))
                    Text(row,modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                }
                if(index<rows.lastIndex) HorizontalDivider(color=Color(0xFF2C404B))
            }
        }
        Notice("Ein BLE-Scan ist kein Bluetooth-Paketsniffer. GATT-Inspektion, Bonding und Classic-RFCOMM befinden sich noch in der Roadmap.")
    }
}

@Composable
private fun SensorsScreen(
    position:String,sensors:Map<String,String>,deviceCount:Int,active:Boolean,
    locate:()->Unit,openMap:()->Unit,start:()->Unit,stop:()->Unit
) {
    WorkspaceList {
        Panel("GNSS & Position","Standortinformation nur auf ausdrückliche Anforderung") {
            Icon(Icons.Outlined.MyLocation,contentDescription=null,tint=Accent,modifier=Modifier.size(36.dp))
            Spacer(Modifier.height(10.dp))
            Text(position,style=MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick=locate,modifier=Modifier.fillMaxWidth()) { Text("Position bestimmen") }
            OutlinedButton(onClick=openMap,modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Map,contentDescription=null)
                Spacer(Modifier.width(8.dp))
                Text("MapLibre · basemap.de")
            }
            Notice("Die Karte lädt externe Kartenkacheln. Standort wird nicht automatisch an den Kartenanbieter gesendet.")
        }
        Panel("Gerätesensoren","Android SensorManager · nur unterstützte Hardware") {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Metric("Verfügbar","$deviceCount Sensoren",Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                if(active) OutlinedButton(onClick=stop) { Text("Stoppen") }
                else Button(onClick=start) { Text("Live starten") }
            }
            Spacer(Modifier.height(10.dp))
            if(sensors.isEmpty()) {
                Text("Live-Messung noch nicht gestartet.",color=Muted)
            } else {
                sensors.toSortedMap().forEach { (name,values) ->
                    Text(name,fontWeight=FontWeight.SemiBold)
                    Text(values,color=Muted,style=MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun UsbScreen(ctx:Context,refresh:Int,onRefresh:()->Unit) {
    val devices=remember(refresh) { ctx.getSystemService(UsbManager::class.java)?.deviceList?.values?.toList() ?: emptyList() }
    WorkspaceList {
        Panel("USB Host","Erkannte USB-Geräte am Smartphone oder Tablet") {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Metric("Geräte","${devices.size}",Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                FilledTonalIconButton(onClick=onRefresh) {
                    Icon(Icons.Outlined.Refresh,contentDescription="USB-Geräte neu einlesen")
                }
            }
            Spacer(Modifier.height(12.dp))
            if(devices.isEmpty()) Text("Keine USB-Geräte erkannt. Adapter verbinden und aktualisieren.",color=Muted)
            devices.forEach {
                Text("VID %04X · PID %04X".format(it.vendorId,it.productId),fontWeight=FontWeight.Bold)
                Text("${it.interfaceCount} USB-Interface(s) · Klasse ${it.deviceClass}",color=Muted)
                Spacer(Modifier.height(10.dp))
            }
        }
        Notice("Die Geräteerkennung ist noch keine serielle Konsole und kein Rohmitschnitt. Ethernet benötigt Android-Treiber; LLDP benötigt echten Layer-2-Zugriff oder eine externe Sonde.")
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
