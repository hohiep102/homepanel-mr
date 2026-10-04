package vn.homepanel

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Vector3
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import vn.homepanel.ha.*
import vn.homepanel.spatial.*
import vn.homepanel.storage.SpatialBinding
import vn.homepanel.ui.*

@RunWith(AndroidJUnit4::class)
class GazeControlsUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun lookingShowsSmallIconsAndLookingAwayHidesWithoutDoneOrCommands() {
        val app=ApplicationProvider.getApplicationContext<HomePanelApp>()
        val devices=JSONArray("""[{"id":"kitchen","name":"Kitchen switch"}]""")
        val registry=JSONArray("""[{"entity_id":"switch.kitchen_1","device_id":"kitchen"},{"entity_id":"switch.kitchen_2","device_id":"kitchen"}]""")
        val entities=listOf("switch.kitchen_1","switch.kitchen_2").mapIndexed { index,id -> HaEntity(id,"off",JSONObject().put("friendly_name","Channel ${index+1}")) }
        val catalog=buildCatalog(entities,devices,registry,JSONArray(),Demo.catalog().services)
        HaFixture(catalog,devices,registry).use { fixture ->
            compose.runOnUiThread { app.store.changeLanguage("en");app.store.connect(fixture.localUrl,"fixture-only-token",false) }
            var binding: SpatialBinding?=null
            try {
                compose.waitUntil(10_000) { app.store.state.value.connected && app.store.state.value.catalog.devices.size==1 }
                var shown by mutableStateOf<String?>(null)
                var details=0
                val focus=ContextualActionsFocus()
                compose.runOnUiThread {
                    binding=SpatialBinding(serverKey=app.store.state.value.serverKey,deviceKey="device:kitchen",entityId="switch.kitchen_2",roomId="fixture-room",anchorId="fixture-anchor",x=0f,y=0f,z=2f,label="Kitchen switch")
                    assertTrue(app.store.saveBinding(binding!!))
                    app.store.notify(null)
                }
                compose.setContent { LocalizedContent { HomeTheme {
                    Box(Modifier.size(340.dp,140.dp).background(Color(0xFF52635D))) {
                        shown?.let { SpatialActionIcons(app.store,it,onDetails={details++}) }
                    }
                } } }
                compose.runOnUiThread {
                    val frame=TargetFrame(binding!!.id,Pose(Vector3(0f,1.6f,2f)),.5f,.5f)
                    val target=chooseFrameTarget(Pose(Vector3(0f,1.6f,0f)),listOf(frame))
                    assertNull(focus.update(target,false,0))
                    val ready=focus.update(target,false,450)
                    assertEquals(binding!!.id,ready)
                    shown=ready
                    // The strip targets the binding directly, without changing the app selection.
                    app.store.select("device:kitchen","switch.kitchen_1")
                }
                compose.onNodeWithText("Channel 2",substring=true).assertIsDisplayed()
                compose.onNodeWithContentDescription("Turn on").assertIsDisplayed().assertIsEnabled()
                compose.onNodeWithText("Done").assertDoesNotExist()
                compose.runOnIdle {
                    assertTrue(fixture.serviceCalls.isEmpty())
                    assertEquals("switch.kitchen_1",app.store.state.value.selectedEntity)
                }
                val image=compose.onRoot().captureToImage().asAndroidBitmap()
                File(app.getExternalFilesDir(null),"icons-switch-en.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
                compose.runOnUiThread { app.store.changeLanguage("vi") }
                compose.onNodeWithContentDescription("Bật").assertIsDisplayed()
                val vi=compose.onRoot().captureToImage().asAndroidBitmap()
                File(app.getExternalFilesDir(null),"icons-switch-vi.png").outputStream().use { vi.compress(Bitmap.CompressFormat.PNG,100,it) }
                compose.runOnUiThread { shown=focus.update(null,false,1150) }
                compose.onNodeWithContentDescription("Bật").assertDoesNotExist()
                compose.runOnIdle { assertTrue(fixture.serviceCalls.isEmpty());assertEquals(0,details) }
                compose.runOnUiThread { shown=focus.update(binding!!.id,false,1200);shown=focus.update(binding!!.id,false,1650) }
                compose.onNodeWithContentDescription("Bật").performTouchInput { click() }
                compose.waitUntil(5_000) { fixture.serviceCalls.size==1 }
                val call=fixture.serviceCalls.single()
                assertEquals("switch",call.getString("domain"))
                assertEquals("turn_on",call.getString("service"))
                assertEquals("switch.kitchen_2",call.getJSONObject("target").getString("entity_id"))
                compose.onNodeWithContentDescription("Mọi chức năng và chi tiết").performTouchInput { click() }
                compose.runOnIdle { assertEquals(1,details) }
                compose.runOnUiThread {
                    app.store.deleteBinding(binding!!.id)
                    assertFalse(app.store.selectBinding(binding!!.id))
                    assertEquals("switch.kitchen_1",app.store.state.value.selectedEntity)
                }
                assertEquals(1,fixture.serviceCalls.size)
            } finally {
                compose.runOnUiThread {
                    binding?.let { app.store.deleteBinding(it.id) }
                    app.store.disconnect(forget=true)
                    app.store.changeLanguage("en")
                }
            }
        }
    }

    @Test fun climateIconsFitAndProvideLocalizedPowerAndTemperatureActions() {
        val app=ApplicationProvider.getApplicationContext<HomePanelApp>()
        var binding: SpatialBinding?=null
        compose.runOnUiThread {
            app.store.changeLanguage("en");app.store.showDemo()
            binding=SpatialBinding(serverKey=Demo.KEY,deviceKey="device:ac",entityId="climate.bedroom",roomId="fixture-room",anchorId="fixture-anchor",x=0f,y=0f,z=2f,label="Bedroom AC")
            assertTrue(app.store.saveBinding(binding!!));app.store.notify(null)
        }
        try {
            compose.setContent { LocalizedContent { HomeTheme {
                Box(Modifier.size(340.dp,140.dp).background(Color(0xFF52635D))) { SpatialActionIcons(app.store,binding!!.id,{}) }
            } } }
            compose.onNodeWithContentDescription("Increase Target temperature").assertIsDisplayed().performTouchInput { click() }
            compose.runOnIdle { assertEquals(25.5,app.store.state.value.catalog.entities.getValue("climate.bedroom").attributes.getDouble("temperature"),.001) }
            compose.onNodeWithContentDescription("Turn off").assertIsDisplayed()
            compose.onNodeWithContentDescription("All functions & details").assertIsDisplayed()
            val en=compose.onRoot().captureToImage().asAndroidBitmap()
            File(app.getExternalFilesDir(null),"icons-ac-en.png").outputStream().use { en.compress(Bitmap.CompressFormat.PNG,100,it) }
            compose.runOnUiThread { app.store.changeLanguage("vi") }
            compose.onNodeWithContentDescription("Tăng Nhiệt độ mục tiêu").assertIsDisplayed()
            compose.onNodeWithContentDescription("Tắt").assertIsDisplayed()
            val vi=compose.onRoot().captureToImage().asAndroidBitmap()
            File(app.getExternalFilesDir(null),"icons-ac-vi.png").outputStream().use { vi.compress(Bitmap.CompressFormat.PNG,100,it) }
            compose.onNodeWithContentDescription("Tắt").performTouchInput { click() }
            compose.runOnIdle { assertEquals("off",app.store.state.value.catalog.entities.getValue("climate.bedroom").state) }
            compose.onNodeWithContentDescription("Bật").performTouchInput { click() }
            compose.runOnIdle { assertEquals("cool",app.store.state.value.catalog.entities.getValue("climate.bedroom").state) }
            compose.onNodeWithText("Xong").assertDoesNotExist()
        } finally { compose.runOnUiThread { binding?.let { app.store.deleteBinding(it.id) };app.store.changeLanguage("en") } }
    }
}
