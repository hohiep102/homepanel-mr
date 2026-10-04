package vn.homepanel.discovery

import vn.homepanel.R

import java.net.InetAddress
import java.net.URI
import vn.homepanel.ha.ServerAddress

const val HA_SERVICE_TYPE = "_home-assistant._tcp."

data class Advertisement(val key: String, val name: String, val type: String, val hosts: List<String>, val port: Int, val attributes: Map<String, String>)
data class DiscoveredServer(val id: String, val name: String, val address: String, val source: String, val version: String? = null)
data class DiscoveryState(val scanning: Boolean = false, val servers: List<DiscoveredServer> = emptyList(), val message: Int = R.string.discovery_search, val checked: Int = 0, val total: Int = 0)
data class LocalSubnet(val address: String, val prefix: Int)
data class Candidate(val id: String, val name: String, val urls: List<String>, val source: String)

/** Only LAN literals / .local names may be probed automatically; remote URLs stay manual. */
fun isLocalHost(raw: String): Boolean {
    val host = raw.removeSurrounding("[", "]").trimEnd('.').lowercase()
    if (host.endsWith(".local") && !host.contains('/') && !host.contains('@')) return true
    val v4 = parseIpv4(host)
    if (v4 != null) return (v4 ushr 24 == 10L) || (v4 ushr 20 == 0xac1L) || (v4 ushr 16 == 0xc0a8L)
    if (!host.contains(':') || host.contains('%') || !host.matches(Regex("[0-9a-f:]+"))) return false
    return runCatching { val ip = InetAddress.getByName(host); ip.address.size == 16 && (ip.address[0].toInt() and 0xfe) == 0xfc }.getOrDefault(false)
}
private fun localUrl(raw: String): String? = runCatching {
    val parsed = ServerAddress.parse(raw)
    require(isLocalHost(URI(parsed.base).host))
    parsed.base
}.getOrNull()

fun candidateFromAdvertisement(a: Advertisement): Candidate? {
    if (a.type.trimEnd('.').lowercase() != HA_SERVICE_TYPE.trimEnd('.')) return null
    if (a.port !in 1..65535) return null
    val internal = a.attributes["internal_url"].orEmpty()
    val explicitHttps = runCatching { URI(internal).scheme == "https" }.getOrDefault(false)
    val scheme = if (explicitHttps || a.port == 443) "https" else "http"
    val urls = buildList {
        localUrl(internal)?.let(::add)
        a.hosts.filter(::isLocalHost).sortedBy { it.contains(':') }.forEach { host ->
            runCatching { URI(scheme, null, host.removeSurrounding("[", "]"), a.port, null, null, null).toString() }.getOrNull()?.let { localUrl(it)?.let(::add) }
        }
    }.distinct().take(4)
    if (urls.isEmpty()) return null
    val uuid = a.attributes["uuid"]?.takeIf { it.matches(Regex("[a-fA-F0-9-]{32,36}")) }
    val name = (a.attributes["location_name"]?.takeIf { it.isNotBlank() } ?: a.name).filterNot(Char::isISOControl).take(80).ifBlank { "Home Assistant" }
    return Candidate(uuid?.lowercase() ?: a.key, name, urls, "mDNS")
}

private fun parseIpv4(host: String): Long? {
    val parts = host.split('.')
    if (parts.size != 4) return null
    return parts.fold(0L) { acc, s ->
        if (!s.matches(Regex("[0-9]{1,3}"))) return null
        val n = s.toIntOrNull() ?: return null
        if (n !in 0..255) return null
        (acc shl 8) or n.toLong()
    }
}
private fun ipv4(value: Long) = (3 downTo 0).joinToString(".") { ((value ushr (it*8)) and 255).toString() }

/** A single private on-link subnet; cap large LANs to the /24 containing this device. */
fun subnetTargets(subnet: LocalSubnet, maxHosts: Int = 254): List<String> {
    if (subnet.prefix !in 1..30 || !isLocalHost(subnet.address)) return emptyList()
    val own = parseIpv4(subnet.address) ?: return emptyList()
    val prefix = maxOf(subnet.prefix, 24)
    val mask = (0xffffffffL shl (32-prefix)) and 0xffffffffL
    val start = own and mask
    val end = start or (0xffffffffL xor mask)
    return ((start+1) until end).filter { it != own }.take(maxHosts.coerceIn(0,254)).map(::ipv4)
}

/** Convenience only at manual entry; explicit URLs retain their scheme/port/path. */
fun normalizeServerInput(raw: String): String {
    val input = raw.trim()
    require(input.isNotEmpty() && input.none(Char::isWhitespace)) { "Enter an IP, hostname or Home Assistant URL." }
    if (input.contains("://")) return ServerAddress.parse(input).base
    require(!input.contains('/') && !input.contains('?') && !input.contains('#') && !input.contains('@')) { "Enter a full URL for a custom path." }
    val authority = if (input.count { it == ':' } > 1 && !input.startsWith('[')) "[$input]" else input
    val uri = URI("http://$authority")
    require(!uri.host.isNullOrBlank()) { "Invalid address." }
    val port = if (uri.port == -1) 8123 else uri.port
    return ServerAddress.parse(URI("http", null, uri.host, port, null, null, null).toString()).base
}
