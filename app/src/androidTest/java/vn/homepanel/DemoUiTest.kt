package vn.homepanel

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun demoControlsAndUnavailableDevice() {
        compose.runOnUiThread { (compose.activity.application as HomePanelApp).store.showDemo() }
        compose.onNodeWithText("DEMO").assertExists()
        compose.onNodeWithText("Devices",substring=false).performClick()
        compose.onAllNodesWithText("Floor lamp").onFirst().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.power_off), useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.state_off)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.power_on), useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.state_on)).assertExists()
        val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.getExternalFilesDir(null), "demo-dashboard.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.runOnUiThread { (compose.activity.application as HomePanelApp).store.select("device:coffee") }
        compose.onNodeWithText(compose.activity.getString(R.string.power_on)).assertIsNotEnabled()
    }
    @Test fun temperatureAndFanCanBeAdjustedWithSingleTaps() {
        compose.runOnUiThread {
            val store = (compose.activity.application as HomePanelApp).store
            store.showDemo(); store.select("device:ac")
        }
        compose.onNodeWithText("Devices",substring=false).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.hands_title)).performTouchInput { click() }
        compose.onNodeWithText(compose.activity.getString(R.string.hands_understood)).performClick()
        val increase = compose.activity.getString(R.string.value_increase, compose.activity.getString(R.string.target_temperature))
        val decrease = compose.activity.getString(R.string.value_decrease, compose.activity.getString(R.string.target_temperature))
        compose.onNodeWithContentDescription(increase).performScrollTo().performTouchInput { click() }
        compose.runOnIdle {
            val entity = (compose.activity.application as HomePanelApp).store.state.value.catalog.entities.getValue("climate.bedroom")
            org.junit.Assert.assertEquals(25.5, entity.attributes.getDouble("temperature"), .001)
        }
        val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.getExternalFilesDir(null), "hands-controls.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithContentDescription(decrease).performScrollTo().performTouchInput { click() }
        compose.runOnIdle {
            val store = (compose.activity.application as HomePanelApp).store
            org.junit.Assert.assertEquals(25.0, store.state.value.catalog.entities.getValue("climate.bedroom").attributes.getDouble("temperature"), .001)
            store.select("device:fan")
        }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.value_increase, compose.activity.getString(R.string.speed))).performScrollTo().performTouchInput { click() }
        compose.runOnIdle {
            org.junit.Assert.assertEquals(75, (compose.activity.application as HomePanelApp).store.state.value.catalog.entities.getValue("fan.desk").attributes.getInt("percentage"))
        }
    }
}
