package vn.homepanel.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import vn.homepanel.auth.HaCredentials

/** Credentials stay on this headset. Android backup is disabled in the manifest. */
class CredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("connection", Context.MODE_PRIVATE)
    private val alias = "homepanel.ha.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(url: String, token: String) {
        saveAuth(url,HaCredentials(token))
    }
    fun saveAuth(url: String, auth: HaCredentials) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(auth.toJson().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putInt("format",2).putString("url", url).putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).putString("secret", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) { "Could not save credentials." }
    }
    fun load(): Pair<String, String>? = loadAuth()?.let { it.first to it.second.accessToken }
    fun loadAuth(): Pair<String, HaCredentials>? {
        val url = prefs.getString("url", null) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP))) }
        val plain=String(cipher.doFinal(Base64.decode(prefs.getString("secret", ""), Base64.NO_WRAP)), Charsets.UTF_8)
        return url to if(prefs.getInt("format",1)==2) HaCredentials.fromJson(plain) else HaCredentials(plain)
    }
    fun clear() { prefs.edit().clear().commit() }
}

data class SpatialBinding(val id: String = UUID.randomUUID().toString(), val serverKey: String, val deviceKey: String, val entityId: String, val roomId: String, val anchorId: String, val x: Float, val y: Float, val z: Float, val label: String, val width: Float = .5f, val height: Float = .5f, val qw: Float = 1f, val qx: Float = 0f, val qy: Float = 0f, val qz: Float = 0f) {
    fun localPose() = com.meta.spatial.core.Pose(com.meta.spatial.core.Vector3(x,y,z), com.meta.spatial.core.Quaternion(w=qw,x=qx,y=qy,z=qz).normalize())
    fun toJson() = JSONObject().put("id", id).put("server", serverKey).put("device", deviceKey).put("entity", entityId).put("room", roomId).put("anchor", anchorId).put("x", x).put("y", y).put("z", z).put("label", label).put("width",width).put("height",height).put("qw",qw).put("qx",qx).put("qy",qy).put("qz",qz)
    companion object {
        fun fromJson(o: JSONObject) = SpatialBinding(o.getString("id"), o.getString("server"), o.getString("device"), o.getString("entity"), o.getString("room"), o.getString("anchor"), o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.getDouble("z").toFloat(), o.getString("label"), o.optDouble("width",.5).toFloat(),o.optDouble("height",.5).toFloat(),o.optDouble("qw",1.0).toFloat(),o.optDouble("qx",0.0).toFloat(),o.optDouble("qy",0.0).toFloat(),o.optDouble("qz",0.0).toFloat()).also {
            require(listOf(it.x,it.y,it.z,it.qw,it.qx,it.qy,it.qz).all(Float::isFinite))
            require(it.width in .1f..3f && it.height in .1f..3f)
            require(it.qw*it.qw+it.qx*it.qx+it.qy*it.qy+it.qz*it.qz > .001f)
        }
    }
}
class BindingStore(context: Context) {
    private val prefs = context.getSharedPreferences("bindings", Context.MODE_PRIVATE)
    fun load(): List<SpatialBinding> {
        val raw = prefs.getString("v1", "[]")!!
        val array = JSONArray(raw)
        return (0 until array.length()).map { SpatialBinding.fromJson(array.getJSONObject(it)) }
    }
    fun save(bindings: List<SpatialBinding>) {
        check(prefs.edit().putString("v1", JSONArray(bindings.map { it.toJson() }).toString()).commit()) { "Could not save placement." }
    }
}
