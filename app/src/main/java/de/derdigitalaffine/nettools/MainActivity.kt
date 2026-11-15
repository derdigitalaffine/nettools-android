package de.derdigitalaffine.nettools

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.usb.UsbManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale

private fun Context.has(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

class MainActivity : ComponentActivity() {
    private val bleRows = mutableStateListOf<String>()
    private var scanning by mutableStateOf(false)
    private var position by mutableStateOf("Noch kein GPS-Fix")
    private val sensorRows = mutableStateMapOf<String, String>()
    private var sensorManager: SensorManager? = null
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private val sensorListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        override fun onSensorChanged(event: SensorEvent) {
            val name = event.sensor.name
            sensorRows[name] = event.values.joinToString(", ") { "%.2f".format(Locale.US, it) }
        }
    }
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val address = result.device.address
            val label = "$address | RSSI ${result.rssi} dBm | ${result.scanRecord?.serviceUuids?.joinToString() ?: "keine Service-UUID"}"
            bleRows.removeAll { it.startsWith(address) }
            bleRows.add(0, label)
        }
        override fun onScanFailed(errorCode: Int) {
            scanning = false
            bleRows.add(0, "Scan fehlgeschlagen: Android-Code $errorCode")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(SensorManager::class.java)
        locationManager = getSystemService(LocationManager::class.java)
        setContent {
            MaterialTheme {
                NetToolsWorkbench(
                    bleRows = bleRows, scanning = scanning, position = position,
                    sensorRows = sensorRows, getSensors = { sensorManager?.getSensorList(Sensor.TYPE_ALL)?.map { it.name + " — " + it.vendor } ?: emptyList() },
                    startBle = ::startBle, stopBle = ::stopBle,
                    startLocation = ::startLocation, startSensors = ::startSensors,
                    stopSensors = ::stopSensors
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBle() {
        stopBle()
        if (Build.VERSION.SDK_INT >= 31 &&
            (!has(Manifest.permission.BLUETOOTH_SCAN) || !has(Manifest.permission.BLUETOOTH_CONNECT))) {
            bleRows.add(0, "Bluetooth-Berechtigung fehlt")
            return
        }
        if (Build.VERSION.SDK_INT < 31 && !has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            bleRows.add(0, "Standortberechtigung für BLE-Scan erforderlich")
            return
        }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter?.isEnabled != true) {
            bleRows.add(0, "Bluetooth ist ausgeschaltet oder nicht vorhanden")
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) { bleRows.add(0, "BLE-Scanner nicht verfügbar"); return }
        try {
            bleRows.clear()
            scanner.startScan(scanCallback)
            scanning = true
        } catch (e: SecurityException) {
            scanning = false
            bleRows.add(0, "Bluetooth-Zugriff nicht erlaubt: ${e.message}")
        } catch (e: IllegalStateException) {
            scanning = false
            bleRows.add(0, "BLE-Scan fehlgeschlagen: ${e.message}")
        }
    }
    @SuppressLint("MissingPermission")
    private fun stopBle() {
        if (!scanning) return
        try {
            getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) { }
        scanning = false
    }

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION) && !has(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            position = "Standortberechtigung fehlt"
            return
        }
        val manager = locationManager ?: return
        locationListener?.let { manager.removeUpdates(it) }
        val listener = LocationListener { location: Location ->
            position = "Lat %.6f, Lon %.6f · Genauigkeit %.1f m · Höhe %.1f m".format(
                Locale.US, location.latitude, location.longitude, location.accuracy, location.altitude)
        }
        locationListener = listener
        try {
            val provider = if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER
                else LocationManager.NETWORK_PROVIDER
            manager.requestLocationUpdates(provider, 1500L, 0f, listener)
            position = "Suche Position über $provider …"
        } catch (e: Exception) {
            position = "Standort nicht verfügbar: ${e.message}"
        }
    }

    private fun startSensors() {
        sensorManager?.getSensorList(Sensor.TYPE_ALL)?.filter {
            it.reportingMode == Sensor.REPORTING_MODE_CONTINUOUS ||
                it.reportingMode == Sensor.REPORTING_MODE_ON_CHANGE
        }?.forEach { sensorManager?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }
    private fun stopSensors() { sensorManager?.unregisterListener(sensorListener) }
    override fun onStop() {
        stopBle()
        stopSensors()
        locationListener?.let { locationManager?.removeUpdates(it) }
        locationListener = null
        super.onStop()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(
    bleRows: List<String>, scanning: Boolean, position: String,
    sensorRows: Map<String, String>, getSensors: () -> List<String>,
    startBle: () -> Unit, stopBle: () -> Unit,
    startLocation: () -> Unit, startSensors: () -> Unit, stopSensors: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var host by remember { mutableStateOf("example.org") }
    var port by remember { mutableStateOf("443") }
    var result by remember { mutableStateOf("Test starten") }
    var netInfo by remember { mutableStateOf(networkSummary(ctx)) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val options = listOf("Netz", "Diagnose", "Bluetooth", "GPS/Sensoren", "USB")
    Scaffold(topBar = { TopAppBar(title = { Text("NetTools · Alpha") }) }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp)) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                options.forEachIndexed { index, name ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(name) })
                }
            }
            Spacer(Modifier.height(14.dp))
            when (tab) {
                0 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Aktuelle Netzwerkinformationen", style = MaterialTheme.typography.titleLarge)
                    Text(netInfo)
                    Button(onClick = { netInfo = networkSummary(ctx) }) { Text("Aktualisieren") }
                    Text("Die angezeigten Informationen stammen aus den Android-Netzwerk-APIs.")
                }
                1 -> Column {
                    OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Hostname oder IP") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("TCP-Port") }, singleLine = true)
                    Row {
                        Button(onClick = {
                            result = "DNS wird abgefragt …"
                            scope.launch {
                                result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        InetAddress.getAllByName(host.trim()).joinToString("\n") { it.hostAddress ?: "?" }
                                    }.getOrElse { "DNS-Fehler: ${it.message}" }
                                }
                            }
                        }) { Text("DNS") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            result = "TCP-Verbindung wird getestet …"
                            scope.launch {
                                result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val started = System.nanoTime()
                                        Socket().use { socket ->
                                            socket.connect(InetSocketAddress(host.trim(), port.toInt(),), 3500)
                                        }
                                        "TCP erfolgreich · %.1f ms".format(Locale.US, (System.nanoTime() - started) / 1e6)
                                    }.getOrElse { "TCP fehlgeschlagen: ${it.message}" }
                                }
                            }
                        }) { Text("TCP testen") }
                    }
                    Text("DNS nutzt den Systemresolver. TCP ist kein ICMP-Ping.", style = MaterialTheme.typography.bodySmall)
                    Text(result)
                }
                2 -> Column {
                    Text("Bluetooth LE · Geräte entdecken", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = {
                        val needed = if (Build.VERSION.SDK_INT >= 31)
                            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                        if (needed.all(ctx::has)) startBle() else permissions.launch(needed)
                    }) { Text(if (scanning) "Erneut suchen" else "BLE-Scan starten") }
                    if (scanning) TextButton(onClick = stopBle) { Text("Scan stoppen") }
                    Text("RSSI ist kein verlässlicher Distanzmesser. Scan nur im Vordergrund.")
                    LazyColumn { items(bleRows) { Text(it, Modifier.padding(vertical = 5.dp)) } }
                }
                3 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Standort & Gerätesensoren", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = {
                        if (ctx.has(Manifest.permission.ACCESS_FINE_LOCATION) ||
                            ctx.has(Manifest.permission.ACCESS_COARSE_LOCATION)) startLocation()
                        else permissions.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                    }) { Text("GPS-Position abfragen") }
                    Text(position)
                    Button(onClick = { ctx.startActivity(Intent(ctx, MapActivity::class.java)) }) {
                        Text("MapLibre · basemap.de-Karte")
                    }
                    Row {
                        Button(onClick = startSensors) { Text("Sensoren starten") }
                        TextButton(onClick = stopSensors) { Text("Stoppen") }
                    }
                    Text("Verfügbare Sensoren: ${getSensors().size}")
                    sensorRows.toSortedMap().forEach { (key, value) ->
                        Text("$key: $value", Modifier.padding(vertical = 3.dp))
                    }
                }
                4 -> {
                    val usb = ctx.getSystemService(UsbManager::class.java)
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text("USB-Geräte", style = MaterialTheme.typography.titleLarge)
                        Text("Angeschlossene Geräte: ${usb?.deviceList?.size ?: 0}")
                        usb?.deviceList?.values?.forEach {
                            Text("VID ${it.vendorId}, PID ${it.productId}, Interfaces ${it.interfaceCount}")
                        }
                        Text("USB-Rohcapture und LLDP benötigen zusätzliche Hardware/Agenten; noch nicht implementiert.")
                    }
                }
            }
        }
    }
}

private fun networkSummary(ctx: Context): String {
    val manager = ctx.getSystemService(ConnectivityManager::class.java) ?: return "Kein ConnectivityManager"
    val network = manager.activeNetwork ?: return "Kein aktives Netz"
    val link: LinkProperties? = manager.getLinkProperties(network)
    val caps: NetworkCapabilities? = manager.getNetworkCapabilities(network)
    val transport = when {
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WLAN"
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobilfunk"
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
        else -> "Andere"
    }
    return buildString {
        appendLine("Verbindung: $transport")
        appendLine("Interface: ${link?.interfaceName ?: "unbekannt"}")
        appendLine("IP-Adressen: ${link?.linkAddresses?.joinToString() ?: "unbekannt"}")
        appendLine("DNS: ${link?.dnsServers?.joinToString { it.hostAddress ?: "?" } ?: "unbekannt"}")
        appendLine("Routen: ${link?.routes?.joinToString() ?: "unbekannt"}")
    }
}
