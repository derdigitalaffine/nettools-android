package de.derdigitalaffine.nettools

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.snmp4j.PDU
import org.snmp4j.fluent.SnmpBuilder
import org.snmp4j.fluent.SnmpCompletableFuture
import org.snmp4j.fluent.TargetBuilder
import org.snmp4j.smi.GenericAddress
import java.util.concurrent.TimeUnit

data class SnmpV3Credentials(val user:String,val authPassword:String,val privacyPassword:String)

fun validateSnmpV3Credentials(credentials:SnmpV3Credentials) {
    require(credentials.user.toByteArray(Charsets.UTF_8).size in 1..32) { "Benutzername: 1–32 Byte" }
    require(credentials.authPassword.toByteArray(Charsets.UTF_8).size in 8..128) { "Auth-Passwort: 8–128 Byte" }
    require(credentials.privacyPassword.toByteArray(Charsets.UTF_8).size in 8..128) { "Priv-Passwort: 8–128 Byte" }
}

private fun snmpV3Get(host:String,port:Int,oid:String,creds:SnmpV3Credentials):SnmpValue {
    require(host.isNotBlank() && port in 1..65535)
    validateSnmpV3Credentials(creds)
    encodeOid(oid)
    val address=GenericAddress.parse("udp:"+
        (if(host.contains(':') && !host.startsWith('[')) "["+host+"]" else host)+"/"+port)
        ?: throw IllegalArgumentException("Ungültige Zieladresse")
    val builder=SnmpBuilder()
    val session=builder.udp().v3().usm().threads(2).build()
    try {
        session.listen()
        val engineId=session.discoverAuthoritativeEngineID(address,1500)
            ?: throw IllegalStateException("SNMPv3 Engine-ID nicht erkannt")
        val targetBuilder=builder.target(address)
        val target=targetBuilder.user(creds.user,engineId)
            .auth(TargetBuilder.AuthProtocol.hmac192sha256)
            .authPassphrase(creds.authPassword)
            .priv(TargetBuilder.PrivProtocol.aes128)
            .privPassphrase(creds.privacyPassword)
            .done()
            .timeout(1500)
            .retries(1)
            .build()
        val pdu=targetBuilder.pdu().type(PDU.GET).oids(oid.trim().trimStart('.')).build()
        val response=SnmpCompletableFuture.send(session,target,pdu).get(7000,TimeUnit.MILLISECONDS)
            ?: throw IllegalStateException("Keine SNMPv3-Antwort")
        require(response.errorStatus==PDU.noError) { "SNMP-Fehlerstatus "+response.errorStatus }
        val binding=response.variableBindings.firstOrNull()
            ?: throw IllegalStateException("Antwort enthält keine OID")
        require(binding.oid.toString()==oid.trim().trimStart('.')) { "Falsche Antwort-OID" }
        return SnmpValue(binding.oid.toString(),binding.variable.syntax,binding.variable.toString().take(2048))
    } finally {
        session.close()
    }
}

@Composable fun SnmpV3Inspector() {
    val scope=rememberCoroutineScope()
    var host by remember { mutableStateOf("192.168.1.1") }
    var portText by remember { mutableStateOf("161") }
    var user by remember { mutableStateOf("") }
    var auth by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf("") }
    var oid by remember { mutableStateOf("1.3.6.1.2.1.1.1.0") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SnmpValue?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val port=portText.toIntOrNull()?.takeIf { it in 1..65535 }
    val validCreds=runCatching { validateSnmpV3Credentials(SnmpV3Credentials(user,auth,privacy)) }.isSuccess
    val validOid=remember(oid) { runCatching { encodeOid(oid) }.isSuccess }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("SNMPv3 · AuthPriv",style=MaterialTheme.typography.titleMedium)
        Text("Sichere, lesende GET-Abfrage mit SHA-256-Authentifizierung und AES-128-Verschlüsselung. Zugangsdaten werden nicht gespeichert.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(host,{host=it},label={Text("SNMP-Gerät / IP")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(portText,{portText=it},label={Text("UDP-Port")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(user,{user=it},label={Text("SNMPv3-Benutzer")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(auth,{auth=it},label={Text("Authentifizierungspasswort")},singleLine=true,
            visualTransformation=PasswordVisualTransformation(),enabled=!busy,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(privacy,{privacy=it},label={Text("Verschlüsselungspasswort")},singleLine=true,
            visualTransformation=PasswordVisualTransformation(),enabled=!busy,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(oid,{oid=it},label={Text("OID")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
        if(user.isNotEmpty() && !validCreds) {
            Text("Benutzername max. 32 Byte; beide Passwörter mindestens 8 Byte.",
                color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
        }
        Button(onClick={
            val p=port ?: return@Button
            val credentials=SnmpV3Credentials(user,auth,privacy)
            val target=host.trim()
            val requestedOid=oid.trim()
            busy=true;error=null;result=null
            scope.launch {
                val response=withContext(Dispatchers.IO) {
                    runCatching { snmpV3Get(target,p,requestedOid,credentials) }
                }
                result=response.getOrNull()
                error=response.exceptionOrNull()?.localizedMessage
                busy=false
            }
        },enabled=!busy && host.isNotBlank() && port!=null && validCreds && validOid,
            modifier=Modifier.fillMaxWidth()) { Text("Verschlüsselte SNMPv3-Abfrage") }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text("SNMPv3-Fehler: "+it,color=MaterialTheme.colorScheme.error) }
        result?.let {
            Text(it.oid,style=MaterialTheme.typography.labelMedium)
            Text(it.value,style=MaterialTheme.typography.bodyMedium)
            Text("ASN.1-Typ: 0x"+it.type.toString(16),style=MaterialTheme.typography.labelSmall)
        }
    }
}
