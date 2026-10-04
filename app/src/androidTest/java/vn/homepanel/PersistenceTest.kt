package vn.homepanel

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import vn.homepanel.storage.*
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import android.util.Base64

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private fun main(block:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun until(predicate:()->Boolean) {
        val end=System.nanoTime()+10_000_000_000L
        while(!predicate() && System.nanoTime()<end) Thread.sleep(50)
        assertTrue("Condition did not complete",predicate())
    }
    @Test fun savedCredentialsAndBindingsSurviveTwoAppStoreRestarts() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val vault=CredentialStore(app)
        val fixture=HaFixture()
        val stores=mutableListOf<AppStore>()
        var id:String?=null
        try {
            vault.clear()
            lateinit var first:AppStore
            main { first=AppStore(app);stores.add(first);first.connect(fixture.localUrl,"fixture-only-token",true) }
            until { first.state.value.connected }
            assertEquals(fixture.localUrl to "fixture-only-token",vault.load())
            val encrypted=app.getSharedPreferences("connection",Context.MODE_PRIVATE).getString("secret","")!!
            assertFalse(encrypted.contains("fixture-only-token"))
            val binding=SpatialBinding(serverKey=first.state.value.serverKey,deviceKey="entity:light.living",entityId="light.living",roomId="qa-room",anchorId="qa-anchor",x=1f,y=2f,z=3f,label="QA lamp")
            id=binding.id
            main { first.saveBinding(binding);first.disconnect() }
            repeat(2) {
                lateinit var restored:AppStore
                main { restored=AppStore(app);stores.add(restored) }
                until { restored.state.value.connected }
                assertEquals(fixture.localUrl to "fixture-only-token",vault.load())
                assertEquals(binding,restored.state.value.bindings.single { it.id==binding.id })
                main { restored.disconnect() }
            }
        } finally {
            main { stores.forEach { it.disconnect(true) } }
            val storage=BindingStore(app);storage.save(storage.load().filterNot { it.id==id })
            vault.clear();fixture.close()
        }
    }
    @Test fun legacyVersion02EncryptedTokenStillLoads() {
        val app=ApplicationProvider.getApplicationContext<Application>();val vault=CredentialStore(app)
        try {
            vault.save("http://ha.local:8123","fixture-legacy-token")
            val key=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey("homepanel.ha.v1",null) as SecretKey
            val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key) }
            val secret=cipher.doFinal("fixture-legacy-token".toByteArray())
            app.getSharedPreferences("connection",Context.MODE_PRIVATE).edit().remove("format").putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).putString("secret",Base64.encodeToString(secret,Base64.NO_WRAP)).commit()
            val legacy=vault.loadAuth()!!
            assertEquals("fixture-legacy-token",legacy.second.accessToken);assertFalse(legacy.second.renewable)
            vault.saveAuth(legacy.first,legacy.second)
            assertEquals(legacy,vault.loadAuth())
            assertEquals(2,app.getSharedPreferences("connection",Context.MODE_PRIVATE).getInt("format",0))
        } finally { vault.clear() }
    }
}
