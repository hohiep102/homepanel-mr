package vn.homepanel

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import vn.homepanel.auth.HaCredentials
import vn.homepanel.storage.CredentialStore

@RunWith(AndroidJUnit4::class)
class OAuthPersistenceTest {
    private fun main(block:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun until(predicate:()->Boolean) {
        val end=System.nanoTime()+10_000_000_000L
        while(!predicate() && System.nanoTime()<end) Thread.sleep(25)
        assertTrue("Condition did not complete",predicate())
    }
    @Test fun expiredAccessIsRefreshedFromEncryptedSessionAcrossRestart() {
        val app=ApplicationProvider.getApplicationContext<Application>();val vault=CredentialStore(app);val fixture=HaFixture();val stores=mutableListOf<AppStore>()
        try {
            vault.saveAuth(fixture.localUrl,HaCredentials("expired-access","fixture-refresh","http://127.0.0.1:43210/homepanel-mr",0))
            lateinit var first:AppStore
            main { first=AppStore(app);stores.add(first) }
            until { first.state.value.connected }
            val saved=vault.loadAuth()!!.second
            assertEquals("fixture-renewed-access",saved.accessToken);assertTrue(saved.refreshAt>System.currentTimeMillis())
            val encrypted=app.getSharedPreferences("connection",Context.MODE_PRIVATE).getString("secret","")!!
            assertFalse(encrypted.contains("fixture-refresh"));assertFalse(encrypted.contains("fixture-renewed-access"))
            main { first.disconnect() }
            lateinit var second:AppStore
            main { second=AppStore(app);stores.add(second) }
            until { second.state.value.connected }
            assertEquals(saved,vault.loadAuth()!!.second)
            assertEquals(listOf("refresh_token"),fixture.tokenGrants.toList())
            assertTrue(fixture.serviceCalls.isEmpty())
        } finally { main { stores.forEach { it.disconnect() } };vault.clear();fixture.close() }
    }
    @Test fun revokedRefreshStopsRetryingAndRequestsSignInAgain() {
        val app=ApplicationProvider.getApplicationContext<Application>();val vault=CredentialStore(app);val fixture=HaFixture();fixture.rejectRefresh=true
        var store:AppStore?=null
        try {
            vault.saveAuth(fixture.localUrl,HaCredentials("expired-access","fixture-refresh","http://127.0.0.1:43210/homepanel-mr",0))
            main { store=AppStore(app) }
            until { store!!.state.value.status==app.getString(R.string.auth_sign_in_again) }
            assertFalse(store!!.state.value.connected);assertNull(vault.loadAuth())
            assertEquals(listOf("refresh_token"),fixture.tokenGrants.toList());assertTrue(fixture.received.isEmpty())
        } finally { main { store?.disconnect() };vault.clear();fixture.close() }
    }
    @Test fun activeSessionRenewsBeforeExpiryWithoutUserInput() {
        val app=ApplicationProvider.getApplicationContext<Application>();val vault=CredentialStore(app);val fixture=HaFixture();var store:AppStore?=null
        try {
            vault.saveAuth(fixture.localUrl,HaCredentials("fixture-oauth-access","fixture-refresh","http://127.0.0.1:43210/homepanel-mr",System.currentTimeMillis()+1800))
            main { store=AppStore(app) }
            until { store!!.state.value.connected }
            until { fixture.tokenGrants.contains("refresh_token") && store!!.state.value.connected && vault.loadAuth()?.second?.accessToken=="fixture-renewed-access" }
            assertEquals(1,fixture.tokenGrants.count { it=="refresh_token" });assertTrue(fixture.serviceCalls.isEmpty())
        } finally { main { store?.disconnect() };vault.clear();fixture.close() }
    }
}
