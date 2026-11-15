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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
                map.cameraPosition = CameraPosition.Builder().target(LatLng(51.0,10.0)).zoom(5.5).build()
            }
        }
        setContent {
            val background=Color(0xFF0B1822)
            val foreground=Color(0xFFE9F1F4)
            val teal=Color(0xFF17A2A4)
            val scheme=darkColorScheme(primary=teal,onPrimary=background,background=background,onBackground=foreground)
            var chosen by remember { mutableIntStateOf(0) }
            MaterialTheme(colorScheme=scheme) {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(factory={mapView},modifier=Modifier.fillMaxSize())
                    Surface(
                        color=background.copy(alpha=.95f),
                        shape=RoundedCornerShape(bottomStart=20.dp,bottomEnd=20.dp),
                        modifier=Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    ) {
                        Column(Modifier.padding(start=14.dp,end=14.dp,top=18.dp,bottom=14.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                IconButton(onClick={finish()}) {
                                    Icon(Icons.Outlined.ArrowBack,contentDescription="Zurück")
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("Karten-Workbench",style=MaterialTheme.typography.titleLarge)
                                    Text("MapLibre · basemap.de",color=Color(0xFF9BADB9),style=MaterialTheme.typography.labelMedium)
                                }
                            }
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                styles.forEachIndexed { index,entry ->
                                    FilterChip(
                                        selected=chosen==index,
                                        onClick={
                                            chosen=index
                                            mapView.getMapAsync { it.setStyle(entry.second) }
                                        },
                                        label={Text(entry.first)}
                                    )
                                }
                            }
                        }
                    }
                    Column(
                        modifier=Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        verticalArrangement=Arrangement.spacedBy(10.dp)
                    ) {
                        FilledIconButton(onClick={
                            mapView.getMapAsync { it.animateCamera(CameraUpdateFactory.zoomIn()) }
                        }) { Icon(Icons.Outlined.Add,contentDescription="Hineinzoomen") }
                        FilledIconButton(onClick={
                            mapView.getMapAsync { it.animateCamera(CameraUpdateFactory.zoomOut()) }
                        }) { Icon(Icons.Outlined.Remove,contentDescription="Herauszoomen") }
                    }
                    Surface(
                        modifier=Modifier.align(Alignment.BottomStart).padding(16.dp),
                        shape=RoundedCornerShape(12.dp),
                        color=background.copy(alpha=.90f)
                    ) {
                        Text("Amtliche Kartendaten: basemap.de",modifier=Modifier.padding(10.dp),
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
