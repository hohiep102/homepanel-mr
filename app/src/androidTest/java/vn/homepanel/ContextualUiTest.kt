package vn.homepanel

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import vn.homepanel.ui.*

@RunWith(AndroidJUnit4::class)
class ContextualUiTest {
    @get:Rule val compose=createComposeRule()
    private val app get()=ApplicationProvider.getApplicationContext<HomePanelApp>()
    private fun capture(name:String) {
        val image=compose.onRoot().captureToImage().asAndroidBitmap()
        File(app.getExternalFilesDir(null),name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
    }

    @Test fun roomToDeviceFlowKeepsRoomFilterAndUpdatesSummaryFromState() {
        compose.runOnUiThread { app.store.changeLanguage("en");app.store.showDemo() }
        compose.setContent { LocalizedContent { HomeScreen(app.store,onEnterRoom={}) } }
        compose.onNodeWithText("Living room",substring=false).assertIsDisplayed()
        capture("rooms-overview.png")
        compose.onNodeWithText("Living room",substring=false).performTouchInput { click() }
        compose.onAllNodesWithText("Floor lamp",substring=false).onFirst().performClick()
        compose.onNodeWithText("Turn off").performScrollTo().performTouchInput { click() }
        compose.runOnIdle { assertEquals("off",app.store.state.value.catalog.entities.getValue("light.living").state) }
        compose.onNodeWithText("Quick controls").assertExists()
        compose.onNodeWithText("All functions & details").performScrollTo().performClick()
        compose.onNodeWithText("Brightness",substring=false).assertExists()
        compose.onNodeWithText("Rooms",substring=false).performClick()
        compose.onAllNodesWithText("Lights on: 0").assertCountEquals(3)
        compose.onNodeWithText("Bedroom",substring=false).performTouchInput { click() }
        compose.onNodeWithText("Bedroom ▾ · Devices: 1").assertExists()
        compose.onAllNodesWithText("Bedroom AC",substring=false).assertCountEquals(2)
        compose.onNodeWithText("Floor lamp",substring=false).assertDoesNotExist()
    }

    @Test fun mrQuickControlsFitWithoutScrollingAndDoNotControlUnavailableEntities() {
        compose.runOnUiThread { app.store.changeLanguage("en");app.store.showDemo();app.store.select("device:ac") }
        var details=0;var closed=0
        compose.setContent { LocalizedContent { HomeTheme {
            val state by app.store.state.collectAsState()
            Surface(Modifier.size(580.dp,900.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Column(Modifier.weight(1f)) {
                        QuickControls(app.store,state.catalog.devices.first { it.key==state.selectedDevice },{details++},{},"Place on object")
                    }
                    Button(onClick={closed++}) { Text(stringResource(R.string.close_controls)) }
                }
            }
        } } }
        compose.onNodeWithContentDescription("Increase Target temperature").assertIsDisplayed().performTouchInput { click() }
        compose.runOnIdle { assertEquals(25.5,app.store.state.value.catalog.entities.getValue("climate.bedroom").attributes.getDouble("temperature"),.001) }
        compose.onNodeWithText("All functions & details").assertIsDisplayed().performTouchInput { click() }
        compose.onNodeWithText("Done").assertIsDisplayed().performTouchInput { click() }
        compose.runOnIdle { assertEquals(1,details);assertEquals(1,closed) }
        capture("quick-ac-en.png")
        compose.runOnUiThread { app.store.changeLanguage("vi") }
        try {
            compose.onNodeWithContentDescription("Tăng Nhiệt độ mục tiêu").assertIsDisplayed().performTouchInput { click() }
            compose.runOnIdle { assertEquals(26.0,app.store.state.value.catalog.entities.getValue("climate.bedroom").attributes.getDouble("temperature"),.001) }
            compose.onNodeWithText("Mọi chức năng và chi tiết").assertIsDisplayed()
            capture("quick-ac-vi.png")
        } finally { compose.runOnUiThread { app.store.changeLanguage("en") } }
        compose.runOnUiThread { app.store.select("device:coffee") }
        compose.onNodeWithText("Turn on").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Turn off").assertIsNotEnabled()
        compose.runOnIdle { assertEquals("unavailable",app.store.state.value.catalog.entities.getValue("switch.coffee").state) }
    }
}
