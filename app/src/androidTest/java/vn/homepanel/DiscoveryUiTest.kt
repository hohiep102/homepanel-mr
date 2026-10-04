package vn.homepanel

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.homepanel.discovery.HA_SERVICE_TYPE
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DiscoveryUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun discoversSelectsAndConnectsToAdvertisedHaWithoutTypingUrl() {
        val fixture=HaFixture()
        val manager=compose.activity.getSystemService(NsdManager::class.java)
        val registered=CountDownLatch(1)
        var registrationError:Int?=null
        val listener=object:NsdManager.RegistrationListener {
            override fun onServiceRegistered(info:NsdServiceInfo) { registered.countDown() }
            override fun onRegistrationFailed(info:NsdServiceInfo,code:Int) { registrationError=code;registered.countDown() }
            override fun onServiceUnregistered(info:NsdServiceInfo)=Unit
            override fun onUnregistrationFailed(info:NsdServiceInfo,code:Int)=Unit
        }
        try {
            compose.runOnUiThread { (compose.activity.application as HomePanelApp).store.disconnect(true) }
            manager.registerService(NsdServiceInfo().apply {
                serviceName="HomePanel QA";serviceType=HA_SERVICE_TYPE;port=fixture.server.port
                setAttribute("location_name","Nhà thử nghiệm HomePanel")
                setAttribute("uuid","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
            },NsdManager.PROTOCOL_DNS_SD,listener)
            assertTrue(registered.await(10,TimeUnit.SECONDS));assertNull(registrationError)
            compose.onNodeWithText(compose.activity.getString(R.string.connection)).performClick()
            compose.waitUntil(20_000) { compose.onAllNodesWithTag("server-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa").fetchSemanticsNodes().isNotEmpty() }
            assertTrue("Discovery must not authenticate",fixture.received.isEmpty())
            val screenshot=compose.onRoot().captureToImage().asAndroidBitmap()
            File(compose.activity.getExternalFilesDir(null),"discovery-servers.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) }
            compose.onNodeWithTag("server-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa").performScrollTo().performClick()
            compose.onNodeWithTag("server-address").performScrollTo().assert(hasText(":${fixture.server.port}",substring=true))
            compose.onNodeWithTag("server-token").assertDoesNotExist()
            compose.onNodeWithTag("browser-login").performScrollTo().assertIsEnabled()
            compose.onNodeWithTag("manual-token").performScrollTo().performClick()
            compose.onNodeWithTag("server-token").performScrollTo().performTextInput("fixture-only-token")
            compose.onNodeWithTag("connect-server").performScrollTo().performClick()
            compose.waitUntil(10_000) { (compose.activity.application as HomePanelApp).store.state.value.connected }
            assertEquals(6,(compose.activity.application as HomePanelApp).store.state.value.catalog.entities.size)
            assertTrue(fixture.received.contains("auth"));assertTrue(fixture.serviceCalls.isEmpty())
        } finally {
            compose.runOnUiThread { (compose.activity.application as HomePanelApp).store.disconnect(true) }
            runCatching { manager.unregisterService(listener) }
            fixture.close()
        }
    }
}
