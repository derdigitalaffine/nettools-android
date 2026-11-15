package de.derdigitalaffine.nettools

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun oidParts(oid:String):List<Long> =
    oid.trim().trimStart('.').split('.').map { it.toLongOrNull() ?: throw IllegalArgumentException("Ungültige OID") }

fun isValidSnmpWalkSuccessor(root:String,current:String,next:SnmpValue):Boolean {
    if(next.type in 0x80..0x82) return false
    val rootParts=oidParts(root)
    val candidate=oidParts(next.oid)
    if(candidate.size<=rootParts.size || candidate.take(rootParts.size)!=rootParts) return false
    val previous=oidParts(current)
    val comparable=candidate.zip(previous).firstOrNull { it.first!=it.second }
    return if(comparable!=null) comparable.first>comparable.second else candidate.size>previous.size
}

private val walkProfiles=listOf(
    "System" to "1.3.6.1.2.1.1",
    "Interfaces" to "1.3.6.1.2.1.2.2.1",
    "IP" to "1.3.6.1.2.1.4",
    "Host" to "1.3.6.1.2.1.25",
    "LLDP" to "1.0.8802.1.1.2.1.4"
)

@Composable fun SnmpWalkPanel() {
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    var host by rememberSaveable { mutableStateOf("192.168.1.1") }
    var portText by rememberSaveable { mutableStateOf("161") }
    var community by remember { mutableStateOf("public") }
    var root by rememberSaveable { mutableStateOf("1.3.6.1.2.1.1") }
    var limitText by rememberSaveable { mutableStateOf("50") }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var status by remember { mutableStateOf("Bereit") }
    var error by remember { mutableStateOf<String?>(null) }
    val records=remember { mutableStateListOf<SnmpValue>() }
    val port=portText.toIntOrNull()?.takeIf { it in 1..65535 }
    val maxRecords=limitText.toIntOrNull()?.takeIf { it in 1..200 }
    val validOid=remember(root) { runCatching { encodeOid(root) }.isSuccess }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("SNMP-Walk · GETNEXT",style=MaterialTheme.typography.titleMedium)
        Text("Read-only Walk über SNMPv2c. Maximal 200 Antworten; stoppt an der Teilbaumgrenze, bei endOfMibView oder ungültiger Reihenfolge.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(host,{host=it},label={Text("SNMP-Gerät / IP")},enabled=!busy,singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(portText,{portText=it},label={Text("UDP-Port")},enabled=!busy,singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(community,{community=it},label={Text("Community")},enabled=!busy,singleLine=true,
            visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        Text("MIB-Auswahl",style=MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            walkProfiles.forEach { (name,oid) ->
                FilterChip(selected=root==oid,onClick={if(!busy) root=oid},label={Text(name)},enabled=!busy)
            }
        }
        OutlinedTextField(root,{root=it},label={Text("Teilbaum-OID")},enabled=!busy,singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(limitText,{limitText=it},label={Text("Maximale Einträge (1–200)")},enabled=!busy,singleLine=true,modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={
                if(busy) {
                    job?.cancel()
                    job=null
                    busy=false
                    status="Abgebrochen"
                } else {
                    val p=port ?: return@Button
                    val max=maxRecords ?: return@Button
                    val target=host.trim()
                    val password=community
                    val prefix=root.trim().trimStart('.')
                    records.clear();error=null;busy=true;status="Walk wird ausgeführt"
                    job=scope.launch {
                        try {
                            var current=prefix
                            for(index in 1..max) {
                                if(!isActive) break
                                val next=withContext(Dispatchers.IO) {
                                    snmpQuery(target,p,password,current,getNext=true)
                                }
                                if(!isValidSnmpWalkSuccessor(prefix,current,next)) {
                                    status=if(next.type==0x82) "Ende der MIB erreicht" else "Teilbaumgrenze oder Wiederholung erreicht"
                                    break
                                }
                                records.add(next)
                                current=next.oid
                                status=records.size.toString()+" Einträge gelesen"
                            }
                            if(records.size>=max) status="Eintragslimit ("+max+") erreicht"
                        } catch (_:CancellationException) {
                            status="Abgebrochen"
                        } catch (e:Exception) {
                            error=e.localizedMessage ?: e.javaClass.simpleName
                            status="Walk fehlgeschlagen / unvollständig"
                        } finally {
                            busy=false
                        }
                    }
                }
            },enabled=busy || (host.isNotBlank() && community.isNotBlank() && port!=null && maxRecords!=null && validOid),
                modifier=Modifier.weight(1f)) {
                Text(if(busy) "Stoppen" else "SNMP-Walk starten")
            }
            OutlinedButton(onClick={
                val lines=buildString {
                    append("OID,ASN1_Type,Value\n")
                    records.forEach { r ->
                        fun quote(s:String)="\""+s.replace("\"","\"\"").replace("\r"," ").replace("\n"," ")+"\""
                        append(quote(r.oid));append(',');append(r.type);append(',');append(quote(r.value));append('\n')
                    }
                }
                runCatching {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type="text/csv"
                        putExtra(Intent.EXTRA_TEXT,lines)
                    },"SNMP-Walk exportieren"))
                }.onFailure { error=it.localizedMessage }
            },enabled=!busy && records.isNotEmpty()) { Text("CSV teilen") }
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(status,style=MaterialTheme.typography.labelMedium)
        error?.let { Text("Fehler: "+it,color=MaterialTheme.colorScheme.error) }
        records.forEach { record ->
            HorizontalDivider()
            Text(record.oid,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            Text(record.value,style=MaterialTheme.typography.bodyMedium)
        }
        Text("Hinweis: SNMPv2c überträgt Community und Antworten unverschlüsselt. SNMPv3/AuthPriv wird gesondert benötigt.",
            color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
    }
}
