package vn.homepanel

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import vn.homepanel.ui.HomeScreen
import vn.homepanel.ui.OwnershipScreen

@RunWith(AndroidJUnit4::class)
class StoreReleaseUiTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<HomePanelApp>()
    private fun capture(name:String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(app.getExternalFilesDir(null),name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun demoAndLegalNoticesAreAvailableInBothLanguages() {
        assumeTrue(BuildConfig.DEBUG)
        compose.runOnUiThread { app.store.changeLanguage("en"); app.store.showDemo() }
        try {
            compose.setContent { LocalizedContent { HomeScreen(app.store,onEnterRoom={},onPlace={}) } }
            compose.onNodeWithText("DEMO").assertIsDisplayed()
            capture("store-rooms-en.png")
            compose.onNodeWithText("Devices",substring=false).performClick()
            compose.onNodeWithText("Place in room ↗").assertExists()
            capture("store-devices-en.png")
            compose.onNodeWithText("About",substring=false).performClick()
            compose.onNodeWithText("Privacy policy",substring=false).assertIsDisplayed()
            compose.onNodeWithText("Third-party licenses",substring=false).performClick()
            compose.onNodeWithText("Resolved release dependencies:",substring=true).assertExists()
            compose.onNodeWithText("Close",substring=false).performClick()
            compose.runOnUiThread { app.store.changeLanguage("vi") }
            compose.onNodeWithText("Thông tin",substring=false).performClick()
            compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("Chính sách quyền riêng tư",substring=false).assertIsDisplayed() }.isSuccess }
            val legalImage=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(app.getExternalFilesDir(null),"legal-vi.png").outputStream().use { legalImage.compress(Bitmap.CompressFormat.PNG,100,it) }
            compose.onNodeWithText("Chính sách quyền riêng tư",substring=false).assertIsDisplayed()
            compose.onNodeWithText("Hỗ trợ",substring=false).performScrollTo().assertIsDisplayed()
        } finally { compose.runOnUiThread { app.store.disconnect();app.store.changeLanguage("en") } }
    }
    @Test fun deniedOwnershipShowsRetryAndExitWithoutDeviceControls() {
        var retry=0;var exit=0
        compose.setContent { LocalizedContent { OwnershipScreen(Access.DENIED,{retry++},{exit++}) } }
        compose.onNodeWithText("We could not verify your copy.").assertIsDisplayed()
        compose.onNodeWithText("Devices",substring=false).assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Close app").performClick()
        compose.runOnIdle { assertEquals(1,retry);assertEquals(1,exit) }
        capture("ownership-denied-en.png")
    }
}
