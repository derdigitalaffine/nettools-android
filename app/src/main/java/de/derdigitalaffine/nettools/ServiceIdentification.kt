package de.derdigitalaffine.nettools

import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

data class ServiceIdentification(val port:Int,val method:String,val details:String)

private val httpProbePorts=setOf(80,8000,8008,8080,8081,8888,9090)
private val tlsProbePorts=setOf(443,8443,9443)
private val bannerProbePorts=setOf(21,22,25,110,143,587)

fun supportsSafeServiceProbe(port:Int):Boolean =
    port in httpProbePorts || port in tlsProbePorts || port in bannerProbePorts

fun classifyServiceBanner(port:Int,banner:String):String {
    val clean=banner.trim().take(240)
    return when {
        clean.startsWith("SSH-") -> "SSH-Banner: "+clean
        clean.startsWith("HTTP/") -> "HTTP-Antwort: "+clean
        port==21 && clean.startsWith("220") -> "FTP-Serverbanner: "+clean
        (port==25 || port==587) && clean.startsWith("220") -> "SMTP-Serverbanner: "+clean
        port==110 && clean.startsWith("+OK") -> "POP3-Banner: "+clean
        port==143 && clean.startsWith("* OK") -> "IMAP-Banner: "+clean
        clean.isNotBlank() -> "Unbekanntes Banner: "+clean
        else -> "Keine aussagekräftige Antwort"
    }
}
private fun limitedLine(socket:Socket):String {
    val bytes=ByteArray(240)
    var position=0
    val input=socket.getInputStream()
    while(position<bytes.size) {
        val next=input.read()
        if(next<0 || next==10) break
        if(next!=13) bytes[position++]=next.toByte()
    }
    return String(bytes,0,position,Charsets.UTF_8)
}
fun identifyOpenPortService(host:String,port:Int):ServiceIdentification {
    require(host.isNotBlank() && host.none { it=='\r' || it=='\n' || it.code<32 })
    require(supportsSafeServiceProbe(port)) { "Für diesen Port ist kein sicherer Standard-Probe definiert" }
    return try {
        if(port in tlsProbePorts) {
            val socket=(SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket() as SSLSocket
            socket.use {
                it.soTimeout=1500
                it.connect(InetSocketAddress(host,port),1500)
                val params:SSLParameters=it.sslParameters
                params.endpointIdentificationAlgorithm="HTTPS"
                it.sslParameters=params
                it.startHandshake()
                ServiceIdentification(port,"TLS",
                    "Zertifikat geprüft · "+it.session.protocol+" · "+it.session.cipherSuite)
            }
        } else {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host,port),1500)
                socket.soTimeout=1500
                if(port in httpProbePorts) {
                    val request="HEAD / HTTP/1.0\r\nHost: "+host+"\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
                    socket.getOutputStream().flush()
                }
                val banner=limitedLine(socket)
                ServiceIdentification(port,if(port in httpProbePorts) "HTTP HEAD" else "Passiver Banner",
                    classifyServiceBanner(port,banner))
            }
        }
    } catch (e:Exception) {
        ServiceIdentification(port,"Nicht bestimmbar",e.javaClass.simpleName+": "+(e.localizedMessage ?: "Keine Antwort").take(160))
    }
}
