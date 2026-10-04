package vn.homepanel

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import vn.homepanel.storage.CredentialStore

@RunWith(AndroidJUnit4::class)
class BrowserAuthUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun browserButtonUsesOfficialAuthAndSurvivesActivityStopWithoutTokenEntry() {
        val fixture=HaFixture()
        val store=(compose.activity.application as HomePanelApp).store
        Intents.init()
        try {
            intending(hasAction(Intent.ACTION_VIEW)).respondWith(ActivityResult(Activity.RESULT_OK,null))
            compose.runOnUiThread { store.disconnect(true) }
            compose.onNodeWithText(compose.activity.getString(R.string.connection)).performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.manual_address)).performScrollTo().performClick()
            compose.onNodeWithTag("server-address").performScrollTo().performTextInput(fixture.localUrl)
            compose.onNodeWithTag("server-token").assertDoesNotExist()
            compose.onNodeWithTag("browser-login").performScrollTo().assertIsEnabled()
            val screenshot=compose.onRoot().captureToImage().asAndroidBitmap()
            File(compose.activity.getExternalFilesDir(null),"browser-login-preview.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) }
            compose.onNodeWithTag("browser-login").performClick()
            compose.waitUntil(5000) { Intents.getIntents().any { it.action==Intent.ACTION_VIEW } }
            val intent=Intents.getIntents().single { it.action==Intent.ACTION_VIEW }
            val authorize=intent.data.toString().toHttpUrl()
            assertEquals("/auth/authorize",authorize.encodedPath)
            assertEquals(fixture.server.port,authorize.port)
            assertNull(authorize.queryParameter("access_token"))
            val callback=authorize.queryParameter("redirect_uri")!!.toHttpUrl()
            assertEquals("127.0.0.1",callback.host)
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            val returnUrl=callback.newBuilder().addQueryParameter("state",authorize.queryParameter("state")).addQueryParameter("code","fixture-code").build()
            OkHttpClient().newCall(Request.Builder().url(returnUrl).build()).execute().use {
                assertEquals(200,it.code)
                assertTrue(it.body!!.string().contains("href=\"${BuildConfig.AUTH_RETURN_SCHEME}://open\""))
            }
            compose.waitUntil(10_000) { store.state.value.connected }
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(6,store.state.value.catalog.entities.size)
            val saved=CredentialStore(compose.activity).loadAuth()!!.second
            assertEquals(authorize.queryParameter("client_id"),saved.clientId)
            assertEquals("fixture-refresh",saved.refreshToken)
            assertEquals(listOf("authorization_code"),fixture.tokenGrants.toList())
            assertTrue(fixture.serviceCalls.isEmpty())
        } finally {
            compose.runOnUiThread { store.disconnect(true) }
            Intents.release();fixture.close()
        }
    }
}
