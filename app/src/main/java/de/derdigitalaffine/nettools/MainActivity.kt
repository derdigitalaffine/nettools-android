package de.derdigitalaffine.nettools

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
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
            NetToolsWorkbench(
                    bleRows = bleRows, scanning = scanning, position = position,
                    sensorRows = sensorRows, getSensors = { sensorManager?.getSensorList(Sensor.TYPE_ALL)?.map { it.name + " — " + it.vendor } ?: emptyList() },
                    startBle = ::startBle, stopBle = ::stopBle,
                    startLocation = ::startLocation, startSensors = ::startSensors,
                    stopSensors = ::stopSensors
                )
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

