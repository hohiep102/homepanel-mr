package vn.homepanel.discovery

import vn.homepanel.R

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface DiscoveryBackend {
    fun start(onFound: (Advertisement) -> Unit, onLost: (String) -> Unit, onError: (Int) -> Unit): AutoCloseable
    fun localSubnet(): LocalSubnet?
}

/** Owns a bounded scan. A generation prevents stale callbacks from reviving stopped scans. */
class DiscoveryController(
    private val backend: DiscoveryBackend,
    private val scope: CoroutineScope,
    private val identify: suspend (String) -> String? = HaProbe()::identify,
    private val discoveryMs: Long = 12_000,
    private val lanMs: Long = 25_000,
) : AutoCloseable {
    private val mutableState = MutableStateFlow(DiscoveryState())
    val state = mutableState.asStateFlow()
    private val resultsBySource = linkedMapOf<String,DiscoveredServer>()
    private var generation = 0
    private var job: Job? = null
    private var lease: AutoCloseable? = null

    fun start(expanded: Boolean = false) {
        stop()
        resultsBySource.clear()
        val run = generation
        mutableState.value = DiscoveryState(scanning = true, message = if(expanded) R.string.discovery_scan else R.string.discovery_search)
        job = scope.launch {
            try {
                if (expanded) scanSubnet(run) else {
                    scanAdvertisements(run)
                    // HA in Docker bridge networking or on a multicast-filtering Wi-Fi never advertises; fall back to the LAN.
                    if (run == generation) { lease?.close(); lease = null }
                    if (run == generation && state.value.servers.isEmpty() && backend.localSubnet()?.let(::subnetTargets).orEmpty().isNotEmpty()) {
                        mutableState.update { it.copy(message = R.string.discovery_scan) }
                        scanSubnet(run)
                    }
                }
                if(run == generation) mutableState.update { if (!it.scanning) it else it.copy(scanning = false, message = if(it.servers.isEmpty()) R.string.discovery_empty else R.string.discovery_found) }
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { if(run == generation) mutableState.update { it.copy(scanning = false, message = R.string.discovery_error) } }
            finally { if(run == generation) { lease?.close(); lease = null } }
        }
    }
    private suspend fun scanAdvertisements(run: Int) = coroutineScope {
        val events = Channel<Advertisement>(Channel.BUFFERED)
        val active = mutableSetOf<String>()
        lease = backend.start(
            onFound = { ad -> if(run == generation) events.trySend(ad) },
            onLost = { key -> scope.launch { if(run == generation) { active.remove(key); resultsBySource.remove(key); publishResults() } } },
            onError = { message -> if(run == generation) mutableState.update { it.copy(message = message) } },
        )
        val seen = mutableMapOf<String,Advertisement>()
        val workers = List(3) { launch {
            for(ad in events) {
                if ((seen.size >= 32 && ad.key !in seen) || (seen[ad.key] == ad && ad.key in active)) continue
                seen[ad.key] = ad
                active.add(ad.key)
                val candidate = candidateFromAdvertisement(ad) ?: continue
                probe(candidate,run,ad.key) { ad.key in active && seen[ad.key] == ad }
            }
        } }
        try { delay(discoveryMs) } finally { events.close(); workers.forEach { it.cancel() } }
        if(run == generation && state.value.servers.isEmpty()) probe(Candidate("homeassistant.local", "Home Assistant", listOf("http://homeassistant.local:8123"), "Hostname"), run)
    }
    private suspend fun scanSubnet(run: Int) = coroutineScope {
        val subnet = backend.localSubnet()
        val hosts = subnet?.let(::subnetTargets).orEmpty()
        if(hosts.isEmpty()) {
            mutableState.update { it.copy(scanning = false, message = R.string.discovery_no_subnet) }
            return@coroutineScope
        }
        mutableState.update { it.copy(total = hosts.size) }
        val queue = Channel<String>(hosts.size)
        hosts.forEach { queue.trySend(it) }; queue.close()
        withTimeoutOrNull(lanMs) {
            List(16) { launch {
                for(host in queue) {
                    probe(Candidate("http://$host:8123", "Home Assistant · $host", listOf("http://$host:8123"), "IP"),run)
                    if(run == generation) mutableState.update { it.copy(checked = it.checked+1) }
                }
            } }.joinAll()
        }
    }
    private suspend fun probe(candidate: Candidate, run: Int, sourceKey: String = candidate.id, stillPresent: () -> Boolean = { true }) {
        for(url in candidate.urls) {
            val version = identify(url) ?: continue
            if(run != generation || !stillPresent()) return
            val result = DiscoveredServer(candidate.id,candidate.name,url,candidate.source,version)
            resultsBySource[sourceKey] = result
            publishResults()
            return
        }
    }
    private fun publishResults() {
        val servers = resultsBySource.values.distinctBy { it.id }.distinctBy { it.address }.sortedBy { it.name }
        mutableState.update { it.copy(servers = servers) }
    }
    fun stop() {
        generation++
        job?.cancel(); job = null
        lease?.close(); lease = null
        mutableState.update { it.copy(scanning = false, message = if(it.scanning) R.string.discovery_stopped else it.message) }
    }
    override fun close() = stop()
}
