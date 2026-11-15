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
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

data class SnmpValue(val oid:String,val type:Int,val value:String)
private fun berLength(n:Int):ByteArray = if(n<128) byteArrayOf(n.toByte()) else {
    val bytes=mutableListOf<Byte>();var x=n
    while(x>0) { bytes.add(0,(x and 255).toByte());x=x ushr 8 }
    byteArrayOf((0x80 or bytes.size).toByte())+bytes.toByteArray()
}
private fun tlv(tag:Int,data:ByteArray):ByteArray=byteArrayOf(tag.toByte())+berLength(data.size)+data
private fun berInt(value:Int):ByteArray {
    val raw=byteArrayOf((value ushr 24).toByte(),(value ushr 16).toByte(),(value ushr 8).toByte(),value.toByte())
    val trimmed=raw.dropWhile { it==0.toByte() }.toByteArray()
    return tlv(2,if(trimmed.isEmpty()) byteArrayOf(0) else if((trimmed[0].toInt() and 128)!=0) byteArrayOf(0)+trimmed else trimmed)
}
private fun base128(value:Long):ByteArray {
    require(value>=0)
    var v=value
    val out=mutableListOf((v and 127).toByte())
    v=v ushr 7
    while(v>0) { out.add(0,((v and 127) or 128).toByte());v=v ushr 7 }
    return out.toByteArray()
}
fun encodeOid(oid:String):ByteArray {
    val arcs=oid.trim().trimStart('.').split('.').map { it.toLongOrNull() ?: throw IllegalArgumentException("Ungültige OID") }
    require(arcs.size>=2 && arcs.size<=64 && arcs[0] in 0..2 && arcs[1]>=0 &&
        (arcs[0]==2L || arcs[1]<=39) && arcs.all { it in 0..Int.MAX_VALUE.toLong() }) { "Ungültige OID" }
    val out=ByteArrayOutputStream()
    out.write(base128(arcs[0]*40+arcs[1]))
    arcs.drop(2).forEach { out.write(base128(it)) }
    return tlv(6,out.toByteArray())
}
private fun snmpRequest(community:String,oid:String,id:Int,pduTag:Int):ByteArray {
    require(pduTag==0xA0 || pduTag==0xA1)
    require(community.length in 1..128)
    val varbind=tlv(0x30,encodeOid(oid)+tlv(5,byteArrayOf()))
    val pdu=tlv(pduTag,berInt(id)+berInt(0)+berInt(0)+tlv(0x30,varbind))
    return tlv(0x30,berInt(1)+tlv(4,community.toByteArray(StandardCharsets.UTF_8))+pdu)
}
private class BerReader(val bytes:ByteArray,var offset:Int=0) {
    fun next():Pair<Int,ByteArray> {
        require(offset<bytes.size) { "ASN.1-Antwort zu kurz" }
        val tag=bytes[offset++].toInt() and 255
        require(offset<bytes.size)
        val first=bytes[offset++].toInt() and 255
        val len=if(first<128) first else {
            val count=first and 127
            require(count in 1..3 && offset+count<=bytes.size)
            var n=0
            repeat(count) { n=(n shl 8) or (bytes[offset++].toInt() and 255) }
            n
        }
        require(len>=0 && offset+len<=bytes.size) { "Ungültige ASN.1-Länge" }
        return tag to bytes.copyOfRange(offset,offset+len).also { offset+=len }
    }
}
private fun berNumber(bytes:ByteArray):Long {
    require(bytes.isNotEmpty() && bytes.size<=8)
    return bytes.fold(0L) { acc,b -> (acc shl 8) or (b.toLong() and 255) }
}
private fun decodeOid(bytes:ByteArray):String {
    val arcs=mutableListOf<Long>()
    var number=0L
    bytes.forEach { b ->
        number=(number shl 7) or (b.toLong() and 127)
        require(number<=Int.MAX_VALUE.toLong())
        if((b.toInt() and 128)==0) { arcs.add(number);number=0 }
    }
    require(arcs.isNotEmpty() && (bytes.last().toInt() and 128)==0)
    val first=arcs.removeAt(0)
    return (listOf(if(first<40) 0 else if(first<80) 1 else 2,if(first<40) first.toInt() else if(first<80) (first-40).toInt() else (first-80).toInt())+arcs.map { it.toInt() }).joinToString(".")
}
fun parseSnmpResponse(bytes:ByteArray,requestId:Int):SnmpValue {
    val (outerTag,outer)=BerReader(bytes).next()
    require(outerTag==0x30)
    val top=BerReader(outer)
    val (versionTag,version)=top.next()
    require(versionTag==2 && berNumber(version)==1L) { "Keine SNMPv2c-Antwort" }
    require(top.next().first==4)
    val (pduTag,pduBytes)=top.next()
    require(pduTag==0xA2) { "Keine GetResponse-PDU" }
    val pdu=BerReader(pduBytes)
    val (idTag,idBytes)=pdu.next()
    require(idTag==2 && berNumber(idBytes)==requestId.toLong()) { "Falsche Request-ID" }
    val error=berNumber(pdu.next().second)
    require(error==0L) { "SNMP-Fehlerstatus $error" }
    pdu.next()
    val (listTag,listBytes)=pdu.next()
    require(listTag==0x30)
    val (bindTag,bindBytes)=BerReader(listBytes).next()
    require(bindTag==0x30)
    val binding=BerReader(bindBytes)
    val (oidTag,oidBytes)=binding.next()
    require(oidTag==6)
    val (tag,value)=binding.next()
    val formatted=when(tag) {
        4 -> String(value,StandardCharsets.UTF_8).take(512)
        2,0x41,0x42,0x43,0x46 -> berNumber(value).toString()
        6 -> decodeOid(value)
        5 -> "NULL"
        0x40 -> value.joinToString(".") { (it.toInt() and 255).toString() }
        0x80 -> "noSuchObject"
        0x81 -> "noSuchInstance"
        0x82 -> "endOfMibView"
        else -> value.take(64).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    return SnmpValue(decodeOid(oidBytes),tag,formatted)
}
internal fun snmpQuery(host:String,port:Int,community:String,oid:String,getNext:Boolean=false):SnmpValue {
    val id=SecureRandom().nextInt(Int.MAX_VALUE-1)+1
    val payload=snmpRequest(community,oid,id,if(getNext) 0xA1 else 0xA0)
    val address=InetAddress.getByName(host)
    return DatagramSocket().use { socket ->
        socket.soTimeout=3000
        socket.connect(address,port)
        socket.send(DatagramPacket(payload,payload.size))
        val buffer=ByteArray(4096)
        val packet=DatagramPacket(buffer,buffer.size)
        socket.receive(packet)
        val result=parseSnmpResponse(buffer.copyOf(packet.length),id)
        if(!getNext) require(result.oid==oid.trim().trimStart('.')) { "Antwort-OID stimmt nicht überein" }
        result
    }
}
@Composable fun SnmpInspector() {
    val scope=rememberCoroutineScope()
    var host by remember { mutableStateOf("192.168.1.1") }
    var community by remember { mutableStateOf("public") }
    var portText by remember { mutableStateOf("161") }
    var oid by remember { mutableStateOf("1.3.6.1.2.1.1.1.0") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SnmpValue?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("SNMPv2c · Read-only GET",style=MaterialTheme.typography.titleMedium)
        Text("OID-Abfrage über UDP. Community wird nur für die Anfrage verwendet und unverschlüsselt übertragen. Keine SNMP-SET-Befehle.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(host,{host=it},label={Text("SNMP-Gerät / IP")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(portText,{portText=it},label={Text("UDP-Port")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(community,{community=it},label={Text("Community (SNMPv2c)")},singleLine=true,
            visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        OutlinedTextField(oid,{oid=it},label={Text("OID (z. B. sysDescr.0)")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Button(onClick={
            busy=true;error=null;result=null
            scope.launch {
                val response=withContext(Dispatchers.IO) {
                    runCatching { snmpQuery(host.trim(),portText.toInt(),community,oid) }
                }
                result=response.getOrNull()
                error=response.exceptionOrNull()?.localizedMessage
                busy=false
            }
        },enabled=!busy && host.isNotBlank() && community.isNotBlank() && portText.toIntOrNull() in 1..65535,
            modifier=Modifier.fillMaxWidth()) { Text("SNMP GET abfragen") }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text("SNMP-Fehler: $it",color=MaterialTheme.colorScheme.error) }
        result?.let {
            Text(it.oid,style=MaterialTheme.typography.labelMedium)
            Text(it.value,style=MaterialTheme.typography.bodyMedium)
            Text("ASN.1-Typ: 0x${it.type.toString(16)}",style=MaterialTheme.typography.labelSmall)
        }
    }
}
