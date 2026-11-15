package de.derdigitalaffine.nettools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale

data class DnsRecord(val name:String,val type:Int,val ttl:Long,val data:String)
data class DnsResponse(val rcode:Int,val records:List<DnsRecord>,val truncated:Boolean)
private fun u16(b:ByteArray,i:Int):Int { require(i>=0 && i+1<b.size); return ((b[i].toInt() and 255) shl 8) or (b[i+1].toInt() and 255) }
private fun u32(b:ByteArray,i:Int):Long { require(i>=0 && i+3<b.size); return (u16(b,i).toLong() shl 16) or u16(b,i+2).toLong() }
private fun dnsName(b:ByteArray,start:Int):Pair<String,Int> {
    var offset=start
    var next=-1
    var jumps=0
    val parts=mutableListOf<String>()
    while(true) {
        require(offset in b.indices && jumps++<80) { "Ungültige DNS-Namenskompression" }
        val length=b[offset].toInt() and 255
        if(length==0) { offset++;break }
        if(length and 0xC0 == 0xC0) {
            require(offset+1<b.size)
            val pointer=((length and 0x3F) shl 8) or (b[offset+1].toInt() and 255)
            if(next<0) next=offset+2
            require(pointer<offset) { "DNS-Kompressionsschleife" }
            offset=pointer
        } else {
            require(length in 1..63 && offset+1+length<=b.size) { "Ungültiges DNS-Label" }
            parts.add(String(b,offset+1,length,Charsets.US_ASCII))
            offset+=1+length
        }
    }
    return Pair(parts.joinToString("."),if(next>=0) next else offset)
}
fun encodeDnsQuery(host:String,type:Int,id:Int=0x4A31):ByteArray {
    require(type in 1..65535 && id in 0..65535)
    val labels=host.trim().trimEnd('.').split('.')
    require(labels.isNotEmpty() && labels.all { it.isNotEmpty() && it.length<=63 && it.all { c-> c.code in 33..126 && c!='.' } })
    val bytes=ByteArrayOutputStream()
    fun write16(v:Int) { bytes.write((v ushr 8) and 255);bytes.write(v and 255) }
    write16(id);write16(0x0100);write16(1);write16(0);write16(0);write16(0)
    labels.forEach { val label=it.toByteArray(Charsets.US_ASCII);bytes.write(label.size);bytes.write(label) }
    bytes.write(0);write16(type);write16(1)
    require(bytes.size()<=512)
    return bytes.toByteArray()
}
fun parseDnsResponse(b:ByteArray,expectedId:Int):DnsResponse {
    require(b.size>=12 && u16(b,0)==expectedId && (u16(b,2) and 0x8000)!=0) { "Ungültige DNS-Antwort/Transaktions-ID" }
    val flags=u16(b,2)
    val questions=u16(b,4)
    val answers=u16(b,6)
    require(questions<=16 && answers<=200) { "DNS-Antwort zu groß" }
    var pos=12
    repeat(questions) { pos=dnsName(b,pos).second;require(pos+4<=b.size);pos+=4 }
    val records=mutableListOf<DnsRecord>()
    repeat(answers) {
        val (name,after)=dnsName(b,pos)
        pos=after
        require(pos+10<=b.size)
        val type=u16(b,pos)
        val klass=u16(b,pos+2)
        val ttl=u32(b,pos+4)
        val len=u16(b,pos+8)
        pos+=10
        require(pos+len<=b.size)
        val start=pos
        val data=when(type) {
            1 -> if(len==4) InetAddress.getByAddress(b.copyOfRange(start,start+4)).hostAddress else null
            28 -> if(len==16) InetAddress.getByAddress(b.copyOfRange(start,start+16)).hostAddress else null
            2,5,12 -> dnsName(b,start).first
            15 -> if(len>=3) "${u16(b,start)} ${dnsName(b,start+2).first}" else null
            16 -> {
                var at=start
                val txt=mutableListOf<String>()
                while(at<start+len) {
                    val n=b[at].toInt() and 255;at++
                    require(at+n<=start+len)
                    txt.add(String(b,at,n,Charsets.UTF_8));at+=n
                }
                txt.joinToString(" | ")
            }
            33 -> if(len>=7) "${u16(b,start)} ${u16(b,start+2)} ${u16(b,start+4)} ${dnsName(b,start+6).first}" else null
            6 -> {
                val (mname,p1)=dnsName(b,start)
                val (rname,p2)=dnsName(b,p1)
                if(p2+20<=start+len) "$mname $rname serial=${u32(b,p2)}" else null
            }
            257 -> if(len>=3) {
                val tagLen=b[start+1].toInt() and 255
                if(tagLen+2<=len) "${b[start].toInt() and 255} ${String(b,start+2,tagLen)} ${String(b,start+2+tagLen,len-2-tagLen)}" else null
            } else null
            else -> null
        } ?: b.copyOfRange(start,start+len).joinToString("") { "%02x".format(it.toInt() and 255) }.take(150)
        if(klass==1) records.add(DnsRecord(name,type,ttl,data))
        pos=start+len
    }
    return DnsResponse(flags and 15,records,(flags and 0x0200)!=0)
}
private fun queryDns(host:String,type:Int,server:String):DnsResponse {
    val id=(System.nanoTime().toInt() and 65535)
    val request=encodeDnsQuery(host,type,id)
    val address=InetAddress.getByName(server)
    val reply=ByteArray(4096)
    val size=DatagramSocket().use { sock ->
        sock.soTimeout=3000
        sock.connect(address,53)
        sock.send(DatagramPacket(request,request.size))
        val packet=DatagramPacket(reply,reply.size)
        sock.receive(packet)
        packet.length
    }
    var response=parseDnsResponse(reply.copyOf(size),id)
    if(response.truncated) {
        Socket().use { sock ->
            sock.connect(InetSocketAddress(address,53),3000)
            sock.soTimeout=3000
            val out=sock.getOutputStream()
            out.write(byteArrayOf((request.size ushr 8).toByte(),request.size.toByte()))
            out.write(request);out.flush()
            val input=sock.getInputStream()
            val header=ByteArray(2)
            readFully(input,header)
            val len=u16(header,0)
            require(len in 12..65535)
            val full=ByteArray(len)
            readFully(input,full)
            response=parseDnsResponse(full,id)
        }
    }
    return response
}
private fun readFully(input:java.io.InputStream,target:ByteArray) {
    var pos=0
    while(pos<target.size) {
        val n=input.read(target,pos,target.size-pos)
        require(n>0) { "Unvollständige DNS-TCP-Antwort" }
        pos+=n
    }
}
private val dnsTypes=listOf("A" to 1,"AAAA" to 28,"MX" to 15,"TXT" to 16,"NS" to 2,"CNAME" to 5,"SOA" to 6,"SRV" to 33,"PTR" to 12,"CAA" to 257)
@Composable fun DnsExplorer() {
    val scope=rememberCoroutineScope()
    var host by rememberSaveable { mutableStateOf("example.org") }
    var resolver by rememberSaveable { mutableStateOf("1.1.1.1") }
    var selected by rememberSaveable { mutableIntStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf<DnsResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("DNS-Record-Explorer",style=MaterialTheme.typography.titleMedium)
        Text("Direkte DNS-Abfrage über UDP/53, bei abgeschnittenen Antworten TCP/53. Öffentlicher Resolver frei wählbar.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(host,{host=it},label={Text("Domain (ASCII/Punycode)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri),
            modifier=Modifier.fillMaxWidth(),singleLine=true)
        OutlinedTextField(resolver,{resolver=it},label={Text("DNS-Resolver-IP")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri),
            modifier=Modifier.fillMaxWidth(),singleLine=true)
        Text("Record-Typ",style=MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp),modifier=Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            dnsTypes.take(5).forEach { (label,type) ->
                FilterChip(selected=selected==type,onClick={selected=type},label={Text(label)})
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp),modifier=Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            dnsTypes.drop(5).forEach { (label,type) ->
                FilterChip(selected=selected==type,onClick={selected=type},label={Text(label)})
            }
        }
        Button(onClick={
            busy=true;output=null;error=null
            scope.launch {
                val result=withContext(Dispatchers.IO) { runCatching { queryDns(host.trim(),selected,resolver.trim()) } }
                output=result.getOrNull()
                error=result.exceptionOrNull()?.let { it.javaClass.simpleName+": "+(it.localizedMessage ?: "Fehler") }
                busy=false
            }
        },enabled=!busy && host.isNotBlank() && resolver.isNotBlank(),modifier=Modifier.fillMaxWidth()) {
            Text("DNS-Records abfragen")
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text("DNS-Abfrage fehlgeschlagen: $it",color=MaterialTheme.colorScheme.error) }
        output?.let { response ->
            Text("DNS-RCODE: ${response.rcode} · ${response.records.size} Antworten")
            if(response.records.isEmpty()) Text("Keine Records dieses Typs in der Antwort.")
            response.records.forEach { record ->
                HorizontalDivider()
                Text("${record.name} · Typ ${record.type} · TTL ${record.ttl}s",style=MaterialTheme.typography.labelMedium)
                Text(record.data,style=MaterialTheme.typography.bodyMedium)
            }
        }
        Text("Datenschutz: Der gewählte Resolver erhält die abgefragte Domain. Keine DNS-over-HTTPS-Verbindung.",
            style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
