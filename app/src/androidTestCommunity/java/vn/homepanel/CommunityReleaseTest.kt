package vn.homepanel

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.homepanel.spatial.supportsSpatialRoom
import java.io.File

/** Exercises the real entry point without a Meta account. Use a clean emulator only. */
@RunWith(AndroidJUnit4::class)
class CommunityReleaseTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun sideloadOpensAndDemoWorksWithoutStoreOwnership() {
        val app = ApplicationProvider.getApplicationContext<HomePanelApp>()
        assumeFalse(supportsSpatialRoom(app.packageManager::hasSystemFeature))
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.meta.horizon.platform.ovr.requests.Entitlements")
        }
        app.store.changeLanguage("en")
        app.store.disconnect()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("We could not verify your copy.").assertDoesNotExist()
            compose.onNodeWithText("Try demo", substring = false).performClick()
            compose.onNodeWithText("DEMO", substring = false).assertIsDisplayed()
            compose.onNodeWithText("Devices", substring = false).performClick()
            compose.onNodeWithText("Place in room ↗").assertExists()
            capture(app, "community-devices.png")
            compose.onNodeWithText("About", substring = false).performClick()
            compose.onNodeWithText("${app.getString(R.string.app_name)} ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
            capture(app, "community-about.png")
            app.entitlement.check()
            assertTrue(app.entitlement.allowed)
            assertNull(app.entitlement.failureCode.value)
        }
        app.store.disconnect()
    }

    private fun capture(app: HomePanelApp, name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(app.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
