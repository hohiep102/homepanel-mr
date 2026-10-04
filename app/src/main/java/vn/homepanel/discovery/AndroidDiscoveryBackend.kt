package vn.homepanel.discovery

import vn.homepanel.R

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.Inet4Address

class AndroidDiscoveryBackend(private val context: Context) : DiscoveryBackend {
    private val manager = context.getSystemService(NsdManager::class.java)
    override fun start(onFound: (Advertisement) -> Unit, onLost: (String) -> Unit, onError: (Int) -> Unit): AutoCloseable {
        var closed = false
        val callbacks = mutableMapOf<String,NsdManager.ServiceInfoCallback>()
        fun key(info: NsdServiceInfo) = "${info.network}:${info.serviceName}:${info.serviceType}"
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) { if(!closed) onError(R.string.discovery_error) }
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                if(closed || callbacks.size >= 32 || !info.serviceType.startsWith("_home-assistant._tcp")) return
                val id = key(info)
                if(id in callbacks) return
                val callback = object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(code: Int) { callbacks.remove(id) }
                    override fun onServiceInfoCallbackUnregistered() = Unit
                    override fun onServiceLost() { if(!closed) onLost(id) }
                    override fun onServiceUpdated(resolved: NsdServiceInfo) {
                        if(closed) return
                        val hosts = resolved.hostAddresses.mapNotNull { it.hostAddress }
                        val attributes = resolved.attributes.mapValues { (_, v) -> String(v,Charsets.UTF_8) }
                        onFound(Advertisement(id,resolved.serviceName,resolved.serviceType,hosts,resolved.port,attributes))
                    }
                }
                callbacks[id] = callback
                runCatching { manager.registerServiceInfoCallback(info,context.mainExecutor,callback) }.onFailure { callbacks.remove(id) }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                val id = key(info)
                callbacks.remove(id)?.let { runCatching { manager.unregisterServiceInfoCallback(it) } }
                if(!closed) onLost(id)
            }
        }
        val networkRequest = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build()
        try { manager.discoverServices(HA_SERVICE_TYPE,NsdManager.PROTOCOL_DNS_SD,networkRequest,context.mainExecutor,listener) }
        catch (_: Exception) { onError(R.string.discovery_error) }
        return AutoCloseable {
            if(!closed) {
                closed = true
                runCatching { manager.stopServiceDiscovery(listener) }
                callbacks.values.toList().forEach { runCatching { manager.unregisterServiceInfoCallback(it) } }; callbacks.clear()
            }
        }
    }
    override fun localSubnet(): LocalSubnet? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        return connectivity.allNetworks.sortedBy { it != connectivity.activeNetwork }.firstNotNullOfOrNull { network ->
            val cap = connectivity.getNetworkCapabilities(network) ?: return@firstNotNullOfOrNull null
            if(cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || (!cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) return@firstNotNullOfOrNull null
            connectivity.getLinkProperties(network)?.linkAddresses?.firstNotNullOfOrNull { link ->
                val host = link.address.hostAddress
                if(link.address is Inet4Address && host != null && isLocalHost(host)) LocalSubnet(host,link.prefixLength) else null
            }
        }
    }
}
