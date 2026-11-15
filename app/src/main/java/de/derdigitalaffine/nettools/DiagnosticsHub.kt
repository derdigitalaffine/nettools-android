package de.derdigitalaffine.nettools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal data class DiagnosticTool(
    val id:String,
    val name:String,
    val subtitle:String,
    val category:String,
    val symbol:ImageVector,
    val capability:String
)

internal val diagnosticTools=listOf(
    DiagnosticTool("quick","Schnelltest","DNS-Auflösung und TCP-Latenz", "Netzwerk", Icons.Outlined.Bolt, "Lokal"),
    DiagnosticTool("ping","ICMP-Ping","Echo-Antworten, RTT und Paketverlust","Netzwerk",Icons.Outlined.NetworkPing,"System-Ping"),
    DiagnosticTool("route","Traceroute","Zwischenstationen und Laufzeiten","Netzwerk",Icons.Outlined.Route,"System-Ping"),
    DiagnosticTool("dns","DNS-Explorer","Records und eigene DNS-Resolver","Netzwerk",Icons.Outlined.Dns,"UDP / TCP"),
    DiagnosticTool("subnet","Subnetzrechner","IPv4-Netzgrenzen und Hostbereiche","Netzwerk",Icons.Outlined.AccountTree,"Offline"),
    DiagnosticTool("monitor","Live-Monitoring","TCP-Zeitreihen und CSV-Export","Monitoring",Icons.Outlined.ShowChart,"TCP"),
    DiagnosticTool("ports","Portscanner","Non-Root TCP-Scan und Diensthinweise","Sicherheit",Icons.Outlined.Radar,"Non-Root"),
    DiagnosticTool("tls","TLS & Zertifikate","Verschlüsselung und Zertifikatskette","Sicherheit",Icons.Outlined.VerifiedUser,"Validiert"),
    DiagnosticTool("nmap","Nmap-Berichte","XML-Scanergebnisse lokal auswerten","Sicherheit",Icons.Outlined.Description,"Offline"),
    DiagnosticTool("snmp","SNMP GET","Read-only Einzel-OID-Abfrage","Management",Icons.Outlined.SettingsEthernet,"v2c"),
    DiagnosticTool("walk","SNMP-Walk","MIB-Teilbäume und CSV-Export","Management",Icons.Outlined.AltRoute,"v2c"),
    DiagnosticTool("snmpv3","SNMPv3 AuthPriv","SHA-256 und AES-128","Management",Icons.Outlined.AdminPanelSettings,"Verschlüsselt")
)
internal fun filterDiagnosticTools(
    query:String,
    category:String,
    favoritesOnly:Boolean,
    favorites:Set<String>
):List<DiagnosticTool> = diagnosticTools.filter { tool ->
    (category=="Alle" || tool.category==category) &&
        (!favoritesOnly || tool.id in favorites) &&
        (query.isBlank() || listOf(tool.name,tool.subtitle,tool.category,tool.capability)
            .any { it.contains(query.trim(),ignoreCase=true) })
}

@Composable
internal fun DiagnosticsHub(
    toolContent:@Composable (String)->Unit
) {
    val context=LocalContext.current
    val detailsState=rememberSaveableStateHolder()
    var selection by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Alle") }
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    var savedFavorites by rememberSaveable { mutableStateOf(UiPreferenceStore.favorites(context)) }
    val favorites=remember(savedFavorites) { savedFavorites.split(',').filter { it.isNotBlank() }.toSet() }
    val shown=remember(query,category,onlyFavorites,favorites) {
        filterDiagnosticTools(query,category,onlyFavorites,favorites)
    }
    val categories=listOf("Alle","Netzwerk","Monitoring","Sicherheit","Management")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide=maxWidth>=760.dp
        val spacious=maxWidth>=1100.dp
        val showCatalog=wide || selection==null
        val highlighted=selection ?: if(wide) "quick" else null
        BackHandler(enabled=!wide && selection!=null) { selection=null }
        Row(Modifier.fillMaxSize()) {
            if(showCatalog) {
                Column(
                    Modifier.then(if(wide) Modifier.width(if(spacious) 360.dp else 300.dp) else Modifier.fillMaxSize())
                        .padding(start=if(wide) 20.dp else 16.dp,end=if(wide) 12.dp else 16.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=14.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("WERKZEUGE",style=MaterialTheme.typography.labelSmall,
                                color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                            Text("Diagnosezentrale",style=MaterialTheme.typography.titleLarge,
                                fontWeight=FontWeight.Bold)
                            Text("${diagnosticTools.size} spezialisierte Werkzeuge",
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick={onlyFavorites=!onlyFavorites}) {
                            Icon(if(onlyFavorites) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                contentDescription=if(onlyFavorites) "Alle Werkzeuge" else "Nur Favoriten",
                                tint=if(onlyFavorites) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    OutlinedTextField(
                        value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),
                        leadingIcon={Icon(Icons.Outlined.Search,contentDescription=null)},
                        trailingIcon={
                            if(query.isNotBlank()) IconButton(onClick={query=""}) {
                                Icon(Icons.Outlined.Close,contentDescription="Suche leeren")
                            }
                        },label={Text("Werkzeug suchen")},placeholder={Text("z. B. DNS, SNMP, TLS")},
                        singleLine=true,shape=RoundedCornerShape(18.dp)
                    )
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        categories.forEach { item ->
                            FilterChip(selected=category==item,onClick={category=item},
                                label={Text(item)},leadingIcon=if(category==item) {
                                    {Icon(Icons.Outlined.Check,contentDescription=null,modifier=Modifier.size(16.dp))}
                                } else null)
                        }
                    }
                    if(shown.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(vertical=36.dp),
                            horizontalAlignment=Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.SearchOff,contentDescription=null,
                                tint=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(40.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Keine passenden Werkzeuge",fontWeight=FontWeight.SemiBold)
                            Text("Suche oder Filter anpassen",
                                color=MaterialTheme.colorScheme.onSurfaceVariant,
                                style=MaterialTheme.typography.bodySmall)
                            TextButton(onClick={query="";category="Alle";onlyFavorites=false}) { Text("Filter zurücksetzen") }
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp),
                            verticalArrangement=Arrangement.spacedBy(9.dp)) {
                            items(shown,key={it.id}) { tool ->
                                DiagnosticToolRow(
                                    tool=tool,selected=highlighted==tool.id,favorite=tool.id in favorites,
                                    onSelect={selection=tool.id},
                                    onFavorite={
                                        val changed=favorites.toMutableSet()
                                        if(!changed.add(tool.id)) changed.remove(tool.id)
                                        savedFavorites=changed.sorted().joinToString(",")
                                        UiPreferenceStore.setFavorites(context,changed)
                                    }
                                )
                            }
                        }
                    }
                }
            }
            if(wide) {
                VerticalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.55f))
            }
            if(wide || selection!=null) {
                val current=selection ?: "quick"
                val tool=diagnosticTools.firstOrNull { it.id==current } ?: diagnosticTools.first()
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal=if(wide) 20.dp else 12.dp,vertical=8.dp),
                        verticalAlignment=Alignment.CenterVertically
                    ) {
                        if(!wide) {
                            IconButton(onClick={selection=null}) {
                                Icon(Icons.Outlined.ArrowBack,contentDescription="Zur Werkzeugübersicht")
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(tool.category.uppercase(),style=MaterialTheme.typography.labelSmall,
                                color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                            Text(tool.name,style=MaterialTheme.typography.titleLarge,
                                fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                        Surface(
                            color=MaterialTheme.colorScheme.primaryContainer,
                            shape=RoundedCornerShape(12.dp)
                        ) {
                            Text(tool.capability,Modifier.padding(horizontal=10.dp,vertical=6.dp),
                                style=MaterialTheme.typography.labelSmall,
                                color=MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal=if(wide) 24.dp else 16.dp,vertical=18.dp),
                        verticalArrangement=Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier=Modifier.fillMaxWidth(),
                            color=MaterialTheme.colorScheme.surfaceContainerLow,
                            shape=RoundedCornerShape(22.dp),
                            border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.45f))
                        ) {
                            Column(Modifier.padding(if(wide) 24.dp else 16.dp)) {
                                Text(tool.subtitle,style=MaterialTheme.typography.bodyMedium,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(16.dp))
                                detailsState.SaveableStateProvider(tool.id) { toolContent(tool.id) }
                            }
                        }
                        if(tool.id=="ports" || tool.id=="snmp" || tool.id=="walk" || tool.id=="snmpv3") {
                            Text("Nur Systeme abfragen, für die du eine Berechtigung besitzt.",
                                style=MaterialTheme.typography.labelSmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticToolRow(
    tool:DiagnosticTool,selected:Boolean,favorite:Boolean,onSelect:()->Unit,onFavorite:()->Unit
) {
    val colors=MaterialTheme.colorScheme
    Surface(
        modifier=Modifier.fillMaxWidth(),
        onClick=onSelect,shape=RoundedCornerShape(18.dp),
        color=if(selected) colors.primaryContainer.copy(alpha=0.55f) else colors.surfaceContainerLow,
        border=BorderStroke(1.dp,if(selected) colors.primary.copy(alpha=0.6f) else colors.outlineVariant.copy(alpha=0.45f))
    ) {
        Row(Modifier.fillMaxWidth().padding(start=12.dp,end=5.dp,top=10.dp,bottom=10.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Surface(color=if(selected) colors.primaryContainer else colors.secondaryContainer,
                shape=RoundedCornerShape(14.dp)) {
                Box(Modifier.size(44.dp),contentAlignment=Alignment.Center) {
                    Icon(tool.symbol,contentDescription=null,modifier=Modifier.size(23.dp),
                        tint=if(selected) colors.primary else colors.onSecondaryContainer)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tool.name,fontWeight=FontWeight.SemiBold,
                    style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(tool.subtitle,style=MaterialTheme.typography.bodySmall,
                    color=colors.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            IconButton(onClick=onFavorite,modifier=Modifier.size(44.dp)) {
                Icon(if(favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                    contentDescription=if(favorite) "Favorit entfernen" else "Als Favorit markieren",
                    tint=if(favorite) colors.primary else colors.onSurfaceVariant,
                    modifier=Modifier.size(20.dp))
            }
            Icon(Icons.Outlined.ChevronRight,contentDescription=null,
                tint=colors.onSurfaceVariant,modifier=Modifier.size(17.dp))
        }
    }
}
