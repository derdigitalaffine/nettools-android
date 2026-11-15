package de.derdigitalaffine.nettools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapView
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng

/** MapLibre workstation backed by official basemap.de vector styles. Network needed for tiles. */
class MapActivity : ComponentActivity() {
    private lateinit var mapView: MapView
    private var styleLoading by mutableStateOf(true)
    private val styles = listOf(
        "Farbe" to "https://sgx.geodatenzentrum.de/gdz_basemapde_vektor/styles/bm_web_col.json",
        "Grau" to "https://sgx.geodatenzentrum.de/gdz_basemapde_vektor/styles/bm_web_gry.json"
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            map.setStyle(styles.first().second) {
                styleLoading=false
                map.cameraPosition = CameraPosition.Builder().target(LatLng(51.0,10.0)).zoom(5.5).build()
            }
        }
        setContent {
            val dark=intent.getBooleanExtra("dark",true)
            var chosen by remember { mutableIntStateOf(0) }
            NetToolsTheme(dark=dark) {
                Box(Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                    AndroidView(factory={mapView},modifier=Modifier.fillMaxSize())
                    Surface(
                        color=MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha=0.97f),
                        shape=RoundedCornerShape(22.dp),
                        shadowElevation=8.dp,
                        modifier=Modifier.align(Alignment.TopCenter).fillMaxWidth()
                            .padding(horizontal=12.dp,vertical=12.dp)
                    ) {
                        Column(Modifier.padding(horizontal=12.dp,vertical=10.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                IconButton(onClick={finish()},modifier=Modifier.size(48.dp)) {
                                    Icon(Icons.Outlined.ArrowBack,contentDescription="Zur Diagnose zurück")
                                }
                                Icon(Icons.Outlined.Map,contentDescription=null,
                                    tint=MaterialTheme.colorScheme.primary,
                                    modifier=Modifier.size(25.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Karten-Workbench",style=MaterialTheme.typography.titleMedium)
                                    Text("MapLibre · basemap.de",
                                        color=MaterialTheme.colorScheme.onSurfaceVariant,
                                        style=MaterialTheme.typography.labelSmall)
                                }
                            }
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                                styles.forEachIndexed { index,entry ->
                                    FilterChip(
                                        selected=chosen==index,
                                        modifier=Modifier.weight(1f),
                                        onClick={
                                            if(chosen!=index) {
                                                chosen=index
                                                styleLoading=true
                                                mapView.getMapAsync { map ->
                                                    map.setStyle(entry.second) { styleLoading=false }
                                                }
                                            }
                                        },
                                        label={Text(if(index==0) "Karte · Farbe" else "Karte · Grau")}
                                    )
                                }
                            }
                            if(styleLoading) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                        }
                    }
                    Column(
                        modifier=Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        verticalArrangement=Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalIconButton(onClick={
                            mapView.getMapAsync { map ->
                                map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(51.0,10.0),5.5))
                            }
                        },modifier=Modifier.size(52.dp)) {
                            Icon(Icons.Outlined.Home,contentDescription="Kartenansicht zurücksetzen")
                        }
                        FilledIconButton(onClick={
                            mapView.getMapAsync { it.animateCamera(CameraUpdateFactory.zoomIn()) }
                        },modifier=Modifier.size(52.dp)) {
                            Icon(Icons.Outlined.Add,contentDescription="Hineinzoomen")
                        }
                        FilledIconButton(onClick={
                            mapView.getMapAsync { it.animateCamera(CameraUpdateFactory.zoomOut()) }
                        },modifier=Modifier.size(52.dp)) {
                            Icon(Icons.Outlined.Remove,contentDescription="Herauszoomen")
                        }
                    }
                    Surface(
                        modifier=Modifier.align(Alignment.BottomStart).padding(start=14.dp,bottom=18.dp),
                        shape=RoundedCornerShape(12.dp),
                        color=MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha=0.96f),
                        shadowElevation=3.dp
                    ) {
                        Text("Kartendaten: basemap.de",
                            modifier=Modifier.padding(horizontal=12.dp,vertical=8.dp),
                            color=MaterialTheme.colorScheme.onSurfaceVariant,
                            style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
}
