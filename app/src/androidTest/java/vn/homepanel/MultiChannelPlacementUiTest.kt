package vn.homepanel

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import vn.homepanel.ha.*
import vn.homepanel.storage.SpatialBinding
import vn.homepanel.ui.HomeScreen

@RunWith(AndroidJUnit4::class)
class MultiChannelPlacementUiTest {
    @get:Rule val compose=createComposeRule()
    private val app get()=ApplicationProvider.getApplicationContext<HomePanelApp>()
    private fun capture(name:String) {
        val image=compose.onRoot().captureToImage().asAndroidBitmap()
        File(app.getExternalFilesDir(null),name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun bothChannelsCanBeSelectedPlacedAndRestoredIndependentlyWithoutCommands() {
        val devices=JSONArray("""[{"id":"dual","name":"Công tắc phòng khách"}]""")
        val registry=JSONArray("""[{"entity_id":"switch.entertainment","device_id":"dual"},{"entity_id":"switch.work","device_id":"dual"},{"entity_id":"switch.indicator","device_id":"dual","entity_category":"config"}]""")
        val entities=JSONArray("""[{"entity_id":"switch.entertainment","state":"off","attributes":{"friendly_name":"Giải trí"}},{"entity_id":"switch.work","state":"on","attributes":{"friendly_name":"Làm việc"}},{"entity_id":"switch.indicator","state":"off","attributes":{"friendly_name":"Indicator config"}}]""").objects().map(HaEntity::fromJson)
        val catalog=buildCatalog(entities,devices,registry,JSONArray(),Demo.catalog().services)
        HaFixture(catalog,devices,registry).use { fixture ->
            var restored: AppStore?=null
            compose.runOnUiThread { app.store.changeLanguage("vi");app.store.connect(fixture.localUrl,"fixture-only-token",false) }
            try {
                compose.waitUntil(10_000) { app.store.state.value.connected && app.store.state.value.catalog.devices.size==1 }
                val saved=mutableListOf<SpatialBinding>()
                compose.setContent { LocalizedContent { HomeScreen(app.store,onEnterRoom={},onPlace={
                    val state=app.store.state.value
                    val device=state.catalog.devices.single()
                    val id=state.selectedEntity!!
                    val binding=SpatialBinding(serverKey=state.serverKey,deviceKey=device.key,entityId=id,roomId="fixture-room",anchorId="fixture-anchor",x=saved.size.toFloat(),y=1f,z=2f,label=placementLabel(device,id))
                    assertTrue(app.store.saveBinding(binding));saved.add(binding);app.store.notify(null)
                }) } }
                compose.onNodeWithText("Thiết bị",substring=false).performClick()
                compose.onNodeWithTag("device-channel:switch.entertainment").assertIsDisplayed()
                compose.onNodeWithTag("device-channel:switch.work").assertIsDisplayed()
                compose.onNodeWithText("Indicator config",substring=false).assertDoesNotExist()
                compose.onNodeWithTag("device-channel:switch.entertainment").performTouchInput { click() }
                compose.onNodeWithText("Gán trong không gian ↗").performScrollTo().performTouchInput { click() }
                compose.runOnIdle { assertEquals("switch.entertainment",saved.single().entityId) }
                compose.onNode(hasSetTextAction()).performTextInput("Làm việc")
                compose.onNodeWithTag("device-channel:switch.work").assertIsDisplayed().performTouchInput { click() }
                compose.runOnIdle { assertEquals("switch.work",app.store.state.value.selectedEntity) }
                capture("channels-before-placement-vi.png")
                compose.onNodeWithText("Gán trong không gian ↗").performScrollTo().performTouchInput { click() }
                compose.runOnIdle {
                    assertEquals(setOf("switch.entertainment","switch.work"),app.store.state.value.bindings.map { it.entityId }.toSet())
                    assertEquals(listOf("Giải trí","Làm việc"),saved.map { it.label })
                    assertTrue(fixture.serviceCalls.isEmpty())
                }
                // Place the second channel again: the first channel and its pose survive.
                compose.onNodeWithText("Gán trong không gian ↗").performClick()
                compose.runOnIdle {
                    assertEquals(2,app.store.state.value.bindings.size)
                    assertEquals(saved.first(),app.store.state.value.bindings.single { it.entityId=="switch.entertainment" })
                    assertEquals(saved.last(),app.store.state.value.bindings.single { it.entityId=="switch.work" })
                }
                compose.onNodeWithTag("quick-function-picker").performScrollTo().performTouchInput { click() }
                compose.onNodeWithTag("quick-function:switch.entertainment").performClick()
                compose.runOnIdle { assertEquals("switch.entertainment",app.store.state.value.selectedEntity) }
                compose.runOnUiThread { app.store.changeLanguage("en") }
                compose.onNodeWithTag("device-channel:switch.work").performClick()
                compose.onNodeWithText("Place in room ↗").assertExists()
                capture("channels-before-placement-en.png")
                compose.runOnUiThread { restored=AppStore(app);restored!!.connect(fixture.localUrl,"fixture-only-token",false) }
                compose.waitUntil(10_000) { restored!!.state.value.connected }
                compose.runOnUiThread {
                    assertEquals(app.store.state.value.bindings,restored!!.state.value.bindings)
                    val work=restored!!.state.value.bindings.single { it.entityId=="switch.work" }
                    assertTrue(restored!!.selectBinding(work.id));assertEquals("switch.work",restored!!.state.value.selectedEntity)
                    assertTrue(fixture.serviceCalls.isEmpty())
                }
            } finally {
                compose.runOnUiThread {
                    restored?.disconnect()
                    app.store.state.value.bindings.toList().forEach { app.store.deleteBinding(it.id) }
                    app.store.disconnect();app.store.changeLanguage("en")
                }
            }
        }
    }
}
