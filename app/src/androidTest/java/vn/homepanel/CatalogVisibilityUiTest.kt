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
import vn.homepanel.ui.HomeScreen

@RunWith(AndroidJUnit4::class)
class CatalogVisibilityUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun noisyServerDefaultsToEverydayAndTechnicalItemsAreRecoverableWithoutCommands() {
        val app=ApplicationProvider.getApplicationContext<HomePanelApp>()
        val devices=JSONArray("""[
            {"id":"lamp","name":"Floor lamp","area_id":"living"},
            {"id":"ac","name":"Bedroom AC","area_id":"bedroom"},
            {"id":"temp","name":"Room thermometer","area_id":"living"},
            {"id":"coffee","name":"Coffee maker","area_id":"kitchen"},
            {"id":"tech","name":"Router maintenance","area_id":"server"},
            {"id":"empty","name":"Registry-only device","area_id":"server"}
        ]""")
        val states=JSONArray("""[
            {"entity_id":"light.lamp","state":"on","attributes":{"friendly_name":"Sofa light"}},
            {"entity_id":"sensor.lamp_rssi","state":"-65","attributes":{"friendly_name":"Lamp RSSI","device_class":"signal_strength","unit_of_measurement":"dBm"}},
            {"entity_id":"climate.ac","state":"cool","attributes":{"friendly_name":"Air conditioner","supported_features":1,"temperature":25,"current_temperature":28,"min_temp":16,"max_temp":30,"hvac_modes":["off","cool"]}},
            {"entity_id":"sensor.temperature","state":"24","attributes":{"friendly_name":"Room temperature","device_class":"temperature","unit_of_measurement":"°C"}},
            {"entity_id":"switch.coffee","state":"unavailable","attributes":{"friendly_name":"Coffee maker"}},
            {"entity_id":"switch.router_restart","state":"off","attributes":{"friendly_name":"Router restart"}},
            {"entity_id":"sensor.router_rssi","state":"-70","attributes":{"friendly_name":"Router RSSI","device_class":"signal_strength","unit_of_measurement":"dBm"}},
            {"entity_id":"automation.restart","state":"on","attributes":{"friendly_name":"Restart automation"}},
            {"entity_id":"update.core","state":"off","attributes":{"friendly_name":"HA Core update"}}
        ]""")
        val registry=JSONArray("""[
            {"entity_id":"light.lamp","device_id":"lamp"},
            {"entity_id":"sensor.lamp_rssi","device_id":"lamp","entity_category":"diagnostic"},
            {"entity_id":"climate.ac","device_id":"ac"},
            {"entity_id":"sensor.temperature","device_id":"temp"},
            {"entity_id":"switch.coffee","device_id":"coffee"},
            {"entity_id":"switch.router_restart","device_id":"tech","entity_category":"config"},
            {"entity_id":"sensor.router_rssi","device_id":"tech","entity_category":"diagnostic"},
            {"entity_id":"light.hidden_debug","device_id":"tech","disabled_by":"user"}
        ]""")
        val areas=JSONArray("""[{"area_id":"living","name":"Living room"},{"area_id":"bedroom","name":"Bedroom"},{"area_id":"kitchen","name":"Kitchen"},{"area_id":"server","name":"Server closet"}]""")
        val catalog=buildCatalog(states.objects().map(HaEntity::fromJson),devices,registry,areas,Demo.catalog().services)
        HaFixture(catalog,devices,registry,areas).use { fixture ->
            var placedEntity: String? = null
            compose.runOnUiThread { app.store.changeLanguage("en");app.store.connect(fixture.localUrl,"fixture-only-token",false) }
            try {
                compose.waitUntil(10_000) { app.store.state.value.connected && app.store.state.value.catalog.devices.size==8 }
                compose.setContent { LocalizedContent { HomeScreen(app.store,onEnterRoom={},onPlace={ placedEntity=app.store.state.value.selectedEntity }) } }
                compose.onNodeWithText("4 / 8 devices").assertIsDisplayed()
                compose.onNodeWithText("Server closet",substring=false).assertDoesNotExist()
                compose.onNodeWithText("Devices: 2").assertExists()
                compose.onNodeWithText("Devices",substring=false).performTouchInput { click() }
                compose.onNodeWithText("Router maintenance",substring=false).assertDoesNotExist()
                compose.runOnUiThread { app.store.select("device:lamp","sensor.lamp_rssi") }
                compose.onNodeWithText("Place in room ↗").performScrollTo().performClick()
                compose.runOnIdle { assertEquals("light.lamp",placedEntity) }
                compose.onNode(hasSetTextAction()).performTextInput("RSSI")
                compose.onNodeWithText("No everyday devices match. Try All devices for configuration and diagnostics.").assertExists()
                compose.onNodeWithText("All devices",substring=false).performTouchInput { click() }
                compose.onNodeWithText("8 / 8 devices").assertExists()
                compose.onNodeWithText("Router maintenance",substring=false).assertExists()
                compose.onNode(hasSetTextAction()).performTextClearance()
                compose.onNodeWithText("Rooms",substring=false).performClick()
                compose.onNodeWithText("Server closet",substring=false).assertExists()
                compose.onNodeWithText("Everyday",substring=false).performTouchInput { click() }
                compose.onNodeWithText("Server closet",substring=false).assertDoesNotExist()
                compose.onNodeWithText("4 / 8 devices").assertExists()
                val image=compose.onRoot().captureToImage().asAndroidBitmap()
                File(app.getExternalFilesDir(null),"catalog-everyday-en.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
                compose.onNodeWithText("Devices",substring=false).performClick()
                val list=compose.onRoot().captureToImage().asAndroidBitmap()
                File(app.getExternalFilesDir(null),"catalog-devices-en.png").outputStream().use { list.compress(Bitmap.CompressFormat.PNG,100,it) }
                compose.onNodeWithText("Rooms",substring=false).performClick()
                compose.runOnUiThread { app.store.changeLanguage("vi") }
                compose.onNodeWithText("Dùng hằng ngày",substring=false).assertExists()
                compose.onNodeWithText("Tất cả thiết bị",substring=false).performClick()
                compose.onNodeWithText("8 / 8 thiết bị").assertExists()
                compose.onNodeWithText("Dùng hằng ngày",substring=false).performClick()
                compose.onNodeWithText("4 / 8 thiết bị").assertExists()
                val vi=compose.onRoot().captureToImage().asAndroidBitmap()
                File(app.getExternalFilesDir(null),"catalog-everyday-vi.png").outputStream().use { vi.compress(Bitmap.CompressFormat.PNG,100,it) }
                compose.onNodeWithText("Thiết bị",substring=false).performClick()
                val viList=compose.onRoot().captureToImage().asAndroidBitmap()
                File(app.getExternalFilesDir(null),"catalog-devices-vi.png").outputStream().use { viList.compress(Bitmap.CompressFormat.PNG,100,it) }
                compose.runOnIdle {
                    assertEquals(8,app.store.state.value.catalog.devices.size)
                    assertTrue(app.store.state.value.catalog.entities.getValue("sensor.lamp_rssi").category=="diagnostic")
                    assertTrue(fixture.serviceCalls.isEmpty())
                }
            } finally { compose.runOnUiThread { app.store.changeLanguage("en");app.store.disconnect() } }
        }
    }
}
