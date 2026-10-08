package vn.homepanel

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import vn.homepanel.discovery.*

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryTest {
    private fun advertisement(key: String = "one", host: String = "192.168.1.10", attributes: Map<String,String> = emptyMap()) = Advertisement(key,"My Home",HA_SERVICE_TYPE,listOf(host),8123,mapOf("uuid" to "0123456789abcdef0123456789abcdef") + attributes)
    @Test fun derivesLocalEndpointAndIgnoresRemoteAdvertisements() {
        val c = candidateFromAdvertisement(advertisement(attributes = mapOf("internal_url" to "http://homeassistant.local:8123", "external_url" to "https://outside.example")))!!
        assertEquals(listOf("http://homeassistant.local:8123","http://192.168.1.10:8123"),c.urls)
        assertNull(candidateFromAdvertisement(advertisement(host="8.8.8.8")))
        assertNull(candidateFromAdvertisement(advertisement().copy(type="_http._tcp.")))
        assertNull(candidateFromAdvertisement(advertisement().copy(port=0)))
    }
    @Test fun preservesHttpsAndSupportsPrivateIpv6() {
        val c = candidateFromAdvertisement(advertisement(host="fd00::10", attributes=mapOf("internal_url" to "https://homeassistant.local:8123")))!!
        assertEquals(listOf("https://homeassistant.local:8123","https://[fd00::10]:8123"),c.urls)
        assertFalse(isLocalHost("fe80::1%wlan0"))
        assertFalse(isLocalHost("2001:4860:4860::8888"))
        assertFalse(isLocalHost("127.0.0.1"))
        assertFalse(isLocalHost("192.168.999.1"))
    }
    @Test fun rejectsCredentialBearingOrMalformedAdvertisedUrls() {
        val c = candidateFromAdvertisement(advertisement(attributes=mapOf("internal_url" to "http://secret@homeassistant.local:8123")))!!
        assertEquals(listOf("http://192.168.1.10:8123"),c.urls)
    }
    @Test fun subnetScanIsBoundedPrivateAndExcludesSelfNetworkAndBroadcast() {
        val targets = subnetTargets(LocalSubnet("192.168.12.50",16))
        assertEquals(253,targets.size)
        assertTrue(targets.all { it.startsWith("192.168.12.") })
        assertFalse("192.168.12.0" in targets); assertFalse("192.168.12.255" in targets); assertFalse("192.168.12.50" in targets)
        assertEquals(listOf("10.1.2.2"),subnetTargets(LocalSubnet("10.1.2.1",30)))
        assertTrue(subnetTargets(LocalSubnet("8.8.8.8",24)).isEmpty())
        assertTrue(subnetTargets(LocalSubnet("192.168.1.1",32)).isEmpty())
    }
    @Test fun shortAddressEntryDoesNotAlterExplicitUrls() {
        assertEquals("http://192.168.1.2:8123",normalizeServerInput(" 192.168.1.2 "))
        assertEquals("http://homeassistant.local:8123",normalizeServerInput("homeassistant.local"))
        assertEquals("http://ha.local:9000",normalizeServerInput("ha.local:9000"))
        assertEquals("http://[fd00::1]:8123",normalizeServerInput("fd00::1"))
        assertEquals("https://example.com/ha",normalizeServerInput("https://example.com:443/ha/"))
        assertThrows(IllegalArgumentException::class.java) { normalizeServerInput("ha.local:99999") }
        assertThrows(IllegalArgumentException::class.java) { normalizeServerInput("secret@ha.local") }
    }
    private class Backend(private val subnet: LocalSubnet? = null) : DiscoveryBackend {
        lateinit var found: (Advertisement)->Unit; lateinit var lost: (String)->Unit
        var closed=0
        override fun start(onFound:(Advertisement)->Unit,onLost:(String)->Unit,onError:(Int)->Unit):AutoCloseable { found=onFound;lost=onLost;return AutoCloseable { closed++ } }
        override fun localSubnet():LocalSubnet? = subnet
    }
    @Test fun deduplicatesInterfacesAndRetainsReachableAddressWhenOneIsLost() = runTest {
        val b=Backend();val c=DiscoveryController(b,this,identify={"2026.9"})
        c.start();runCurrent()
        b.found(advertisement("wifi"));b.found(advertisement("ethernet","192.168.1.11"));runCurrent()
        assertEquals(1,c.state.value.servers.size)
        b.lost("wifi");runCurrent()
        assertEquals("http://192.168.1.11:8123",c.state.value.servers.single().address)
        b.lost("ethernet");runCurrent();assertTrue(c.state.value.servers.isEmpty())
        b.found(advertisement("ethernet","192.168.1.11"));runCurrent();assertEquals(1,c.state.value.servers.size)
        c.stop();assertEquals(1,b.closed)
    }
    @Test fun stoppedScanRejectsStaleCallbacksAndDoesNotAutoConnect() = runTest {
        val b=Backend();var probes=0;val c=DiscoveryController(b,this,identify={probes++;delay(100);"2026.9"})
        c.start();runCurrent();b.found(advertisement());runCurrent();c.stop()
        b.found(advertisement("late"));advanceUntilIdle()
        assertFalse(c.state.value.scanning);assertTrue(c.state.value.servers.isEmpty());assertEquals(1,probes)
    }
    @Test fun networklessScanRetainsActionableError() = runTest {
        val c=DiscoveryController(Backend(),this,identify={null})
        c.start(expanded=true);advanceUntilIdle()
        assertFalse(c.state.value.scanning);assertTrue(c.state.value.message == R.string.discovery_no_subnet);c.close()
    }
    @Test fun lanScanFindsOnlyVerifiedHaAndBoundsConcurrentProbes() = runTest {
        val checked=mutableListOf<String>();var active=0;var peak=0
        val c=DiscoveryController(Backend(LocalSubnet("192.168.1.50",24)),this,identify={ url ->
            checked.add(url);active++;peak=maxOf(peak,active)
            try { delay(100);if(url=="http://192.168.1.10:8123") "2026.9" else null } finally { active-- }
        })
        c.start(expanded=true);advanceUntilIdle()
        assertEquals(253,checked.size);assertEquals(253,checked.distinct().size)
        assertTrue(peak<=16);assertEquals(0,active)
        assertEquals("http://192.168.1.10:8123",c.state.value.servers.single().address)
        assertEquals(253,c.state.value.checked);assertFalse(c.state.value.scanning);c.close()
    }
    @Test fun silentAdvertisementSearchFallsBackToLanScan() = runTest {
        val checked=mutableListOf<String>()
        val c=DiscoveryController(Backend(LocalSubnet("192.168.1.50",24)),this,identify={ url ->
            checked.add(url);if(url=="http://192.168.1.10:8123") "2026.9" else null
        })
        c.start();advanceUntilIdle()
        assertTrue("http://homeassistant.local:8123" in checked);assertEquals(254,checked.size)
        assertEquals("http://192.168.1.10:8123",c.state.value.servers.single().address)
        assertEquals(R.string.discovery_found,c.state.value.message);assertFalse(c.state.value.scanning);c.close()
    }
    @Test fun advertisedServerSkipsLanScan() = runTest {
        val b=Backend(LocalSubnet("192.168.1.50",24));var probes=0
        val c=DiscoveryController(b,this,identify={probes++;"2026.9"})
        c.start();runCurrent();b.found(advertisement());advanceUntilIdle()
        assertEquals(1,probes);assertEquals(0,c.state.value.total);assertEquals(1,c.state.value.servers.size);c.close()
    }
    @Test fun lanDeadlineCancelsPendingSocketsAndFinishesScan() = runTest {
        var active=0
        val c=DiscoveryController(Backend(LocalSubnet("192.168.1.50",24)),this,identify={
            active++;try { delay(10_000);"2026.9" } finally { active-- }
        },lanMs=50)
        c.start(expanded=true);advanceUntilIdle()
        assertEquals(50,testScheduler.currentTime);assertEquals(0,active)
        assertFalse(c.state.value.scanning);assertTrue(c.state.value.servers.isEmpty());c.close()
    }
}
