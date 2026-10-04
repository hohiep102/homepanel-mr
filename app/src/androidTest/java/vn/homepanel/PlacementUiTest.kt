package vn.homepanel

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.graphics.Bitmap
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.homepanel.ui.HomeScreen
import vn.homepanel.ui.HomeTheme
import vn.homepanel.ui.PlacementForm
import vn.homepanel.ui.PlacementFormState
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@RunWith(AndroidJUnit4::class)
class PlacementUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun orangeFrameStillAllowsAdjustmentAndShowsRecoveryThenConfirm() {
        val app = ApplicationProvider.getApplicationContext<HomePanelApp>()
        val permissions = app.packageManager.getPackageInfo(app.packageName,android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue("MRUK requires anchor API permission", "com.oculus.permission.USE_ANCHOR_API" in permissions)
        var form by mutableStateOf(PlacementFormState(device="Test lamp",status="No room map loaded. Tap Set up room to scan, or Reload room if you already finished setup."))
        var scans=0
        var reloads=0
        var saves=0
        compose.setContent {
            LocalizedContent { HomeTheme {
                Surface(Modifier.width(580.dp).fillMaxHeight()) {
                    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        PlacementForm(form,
                            onAdjust={form=it},
                            onScan={scans++;form=form.copy(loading=true,settingUp=true,status="Complete room setup in Quest, then return here.")},
                            onReload={reloads++;form=form.copy(loading=false,settingUp=false,ready=true,status="Room loaded")},
                            onFreeze={form=form.copy(confirming=true)},onCancelCountdown={},onSave={saves++},onAimAgain={form=form.copy(confirming=false)})
                    }
                }
            } }
        }
        compose.onNodeWithText("Freeze after 3 seconds").assertIsDisplayed().assertIsNotEnabled()
        val initial=compose.onRoot().captureToImage().asAndroidBitmap()
        File(app.getExternalFilesDir(null),"placement-adjust.png").outputStream().use { initial.compress(Bitmap.CompressFormat.PNG,100,it) }
        compose.onNodeWithContentDescription("Increase Frame width").assertIsDisplayed().performTouchInput { click() }
        compose.runOnIdle { assertEquals(.6f,form.width,.001f) }
        compose.onNodeWithText("Set up room").assertIsDisplayed().performTouchInput { click() }
        compose.onNodeWithText("Set up room").assertIsNotEnabled()
        compose.onNodeWithText("Reload room").assertIsDisplayed().assertIsEnabled().performTouchInput { click() }
        compose.onNodeWithText("Freeze after 3 seconds").assertIsDisplayed().assertIsEnabled().performTouchInput { click() }
        compose.onNodeWithText("Save placement").assertIsDisplayed().assertIsEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(1,scans);assertEquals(1,reloads);assertEquals(1,saves) }
        val shot=compose.onRoot().captureToImage().asAndroidBitmap()
        File(app.getExternalFilesDir(null),"placement-confirm.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun vietnamesePlacementButtonsFitAndReceiveTouchWithoutScrolling() {
        val app = ApplicationProvider.getApplicationContext<HomePanelApp>()
        var form by mutableStateOf(PlacementFormState(device="Máy lạnh phòng khách phía bên trái cửa sổ",status="Chưa có bản đồ phòng. Bấm Thiết lập phòng để quét, hoặc Tải lại phòng nếu đã quét xong."))
        var scans=0
        compose.runOnUiThread { app.store.changeLanguage("vi") }
        try {
            compose.setContent { LocalizedContent { HomeTheme { Surface(Modifier.width(580.dp).fillMaxHeight()) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    PlacementForm(form,onAdjust={form=it},onScan={scans++},onReload={},onFreeze={},onCancelCountdown={},onSave={},onAimAgain={})
                }
            } } } }
            compose.onNodeWithText("Thiết lập phòng").assertIsDisplayed().performTouchInput { click() }
            compose.onNodeWithContentDescription("Tăng Chiều cao khung").assertIsDisplayed().performTouchInput { click() }
            compose.runOnIdle { assertEquals(1,scans);assertEquals(.6f,form.height,.001f) }
            val shot=compose.onRoot().captureToImage().asAndroidBitmap()
            File(app.getExternalFilesDir(null),"placement-vietnamese.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG,100,it) }
        } finally { compose.runOnUiThread { app.store.changeLanguage("en") } }
    }
    @Test fun placeButtonUsesPlacementCallbackAndLanguageChangesKeepSelectionAndControls() {
        val app = ApplicationProvider.getApplicationContext<HomePanelApp>()
        val store = app.store
        var placed = 0
        var entered = 0
        compose.runOnUiThread { store.changeLanguage("en"); store.showDemo() }
        compose.setContent { LocalizedContent { HomeScreen(store,onEnterRoom={entered++},onPlace={placed++}) } }
        try {
            compose.onNodeWithText("Devices").performClick()
            compose.onNodeWithText("Place in room ↗").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(1,placed); assertEquals(0,entered) }
            compose.onNodeWithText("English ▾").performClick()
            compose.onNodeWithText("Tiếng Việt",substring=false).performClick()
            compose.onNodeWithText("Thiết bị",substring=false).assertExists()
            compose.onNodeWithText("Gán trong không gian ↗").performScrollTo().assertExists()
            compose.runOnIdle { assertEquals("device:lamp",store.state.value.selectedDevice) }
            compose.onNodeWithText("Tắt",substring=false).performScrollTo().performClick()
            compose.onNodeWithText("Đang tắt",substring=false).assertExists()
            val shot = compose.onRoot().captureToImage().asAndroidBitmap()
            File(app.getExternalFilesDir(null),"vietnamese-dashboard.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG,100,it) }
            compose.runOnIdle {
                AppLanguage.load(app)
                assertEquals("vi",AppLanguage.language.value)
            }
            compose.onNodeWithText("Tiếng Việt ▾").performClick()
            compose.onNodeWithText("English",substring=false).performClick()
            compose.onNodeWithText("Off",substring=false).assertExists()
            compose.onNodeWithText("Place in room ↗").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(2,placed); assertEquals(0,entered) }
        } finally { compose.runOnUiThread { store.changeLanguage("en"); store.disconnect() } }
    }
}
