package de.derdigitalaffine.nettools

/** Pure IPv4 CIDR calculator, independent of Android. */
data class SubnetResult(
    val input: String,
    val network: String,
    val netmask: String,
    val broadcast: String,
    val first: String,
    val last: String,
    val prefix: Int,
    val count: Long,
    val usableHosts: Long
)
fun ipv4Subnet(text: String): SubnetResult {
    val parts = text.trim().split("/")
    require(parts.size == 2) { "Format: IPv4-Adresse/Präfix, z. B. 192.168.1.10/24" }
    val prefix = parts[1].toIntOrNull()
    require(prefix != null && prefix in 0..32) { "Präfix muss zwischen 0 und 32 liegen" }
    val octets = parts[0].split(".")
    require(octets.size == 4) { "IPv4 benötigt vier Oktette" }
    val octetValues = octets.map {
        require(it.isNotEmpty() && it.length <= 3 && it.all(Char::isDigit)) { "Ungültiges IPv4-Oktett" }
        val n = it.toIntOrNull()
        require(n != null && n in 0..255) { "IPv4-Oktett außerhalb 0–255" }
        n
    }
    val ip = octetValues.fold(0L) { acc, n -> (acc shl 8) or n.toLong() }
    val full = 0xFFFFFFFFL
    val mask = if (prefix == 0) 0L else (full shl (32 - prefix)) and full
    val network = ip and mask
    val broadcast = network or (full xor mask)
    val count = 1L shl (32 - prefix)
    val usable = if (prefix >= 31) count else count - 2
    val first = if (prefix >= 31) network else network + 1
    val last = if (prefix >= 31) broadcast else broadcast - 1
    fun display(value: Long) = (24 downTo 0 step 8).joinToString(".") { shift ->
        ((value ushr shift) and 255).toString()
    }
    return SubnetResult(text, display(network), display(mask), display(broadcast),
        display(first), display(last), prefix, count, usable)
}
