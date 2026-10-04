package vn.homepanel

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeFalse
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.homepanel.spatial.supportsSpatialRoom

/** Run only on an emulator with the actual signed release. Never clears headset app data. */
@RunWith(AndroidJUnit4::class)
class OwnershipReleaseTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun actualReleaseDeniesUnsupportedDeviceAndKeepsControlsInaccessible() {
        val app=ApplicationProvider.getApplicationContext<HomePanelApp>()
        assumeFalse(supportsSpatialRoom(app.packageManager::hasSystemFeature))
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { app.entitlement.flow.value==Access.DENIED }
            compose.onNodeWithText("We could not verify your copy.").assertIsDisplayed()
            compose.onNodeWithText("Rooms",substring=false).assertDoesNotExist()
            compose.onNodeWithText("Explore demo",substring=false).assertDoesNotExist()
            compose.onNodeWithText("Try again").performClick()
            compose.waitUntil(10_000) { app.entitlement.flow.value==Access.DENIED }
            assertFalse(app.entitlement.allowed)
            assertEquals("DEVICE_UNSUPPORTED",app.entitlement.failureCode.value)
        }
    }
}
