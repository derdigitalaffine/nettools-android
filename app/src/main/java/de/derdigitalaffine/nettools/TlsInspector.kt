package de.derdigitalaffine.nettools

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import java.text.DateFormat
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

data class TlsResult(
    val protocol:String,
    val cipherSuite:String,
    val subject:String,
    val issuer:String,
    val notBefore:String,
    val notAfter:String,
    val chainLength:Int,
    val fingerprint:String
)
/** Uses the system's default trust manager and hostname verification. Never disables TLS validation. */
fun inspectTls(host:String,port:Int):TlsResult {
    require(host.isNotBlank() && port in 1..65535)
    val factory=SSLSocketFactory.getDefault() as SSLSocketFactory
    val raw=java.net.Socket()
    try {
        raw.connect(InetSocketAddress(host,port),4000)
        raw.soTimeout=4000
        (factory.createSocket(raw,host,port,true) as SSLSocket).use { socket ->
            val params=socket.sslParameters
            params.endpointIdentificationAlgorithm="HTTPS"
            socket.sslParameters=params
            socket.startHandshake()
            val session=socket.session
            val chain=session.peerCertificates
            val cert=chain.firstOrNull() as? X509Certificate
                ?: throw SSLPeerUnverifiedException("Kein X.509-Zertifikat")
            val fingerprint=java.security.MessageDigest.getInstance("SHA-256")
                .digest(cert.encoded).joinToString(":") { "%02X".format(it.toInt() and 255) }
            val df=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT)
            return TlsResult(
                session.protocol,session.cipherSuite,
                cert.subjectX500Principal.name,cert.issuerX500Principal.name,
                df.format(cert.notBefore),df.format(cert.notAfter),
                chain.size,fingerprint
            )
        }
    } finally { runCatching { raw.close() } }
}
@Composable
fun TlsInspectorPanel(defaultHost:String) {
    val scope=rememberCoroutineScope()
    var host by remember { mutableStateOf(defaultHost) }
    var port by remember { mutableStateOf("443") }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<TlsResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("TLS-Zertifikatsprüfung",style=MaterialTheme.typography.titleMedium)
        Text("Prüft den echten TLS-Handshake mit Systemvertrauen und Hostname-Verifikation.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(host,{host=it},label={Text("TLS-Hostname")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(port,{port=it},label={Text("Port")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Button(onClick={
            val h=host.trim()
            val p=port.toIntOrNull() ?: return@Button
            loading=true;result=null;error=null
            scope.launch {
                val response=withContext(Dispatchers.IO) { runCatching { inspectTls(h,p) } }
                result=response.getOrNull()
                error=response.exceptionOrNull()?.let { it.javaClass.simpleName+": "+(it.localizedMessage ?: "TLS-Fehler") }
                loading=false
            }
        },enabled=!loading && host.isNotBlank() && port.toIntOrNull() in 1..65535,
            modifier=Modifier.fillMaxWidth()) { Text("TLS-Verbindung prüfen") }
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text("Prüfung fehlgeschlagen: $it",color=MaterialTheme.colorScheme.error) }
        result?.let { r ->
            TlsDetail("Protokoll",r.protocol)
            TlsDetail("Cipher Suite",r.cipherSuite)
            TlsDetail("Subject",r.subject)
            TlsDetail("Aussteller",r.issuer)
            TlsDetail("Gültig ab",r.notBefore)
            TlsDetail("Gültig bis",r.notAfter)
            TlsDetail("Zertifikate in der Kette",r.chainLength.toString())
            TlsDetail("SHA-256 Fingerprint",r.fingerprint)
        }
    }
}
@Composable private fun TlsDetail(label:String,value:String) {
    Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value,style=MaterialTheme.typography.bodyMedium)
}
