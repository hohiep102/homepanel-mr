package vn.homepanel.auth

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import vn.homepanel.ha.ServerAddress

/** Implements HA's public IndieAuth token API; passwords stay in the external browser. */
class HaAuthClient(private val http: OkHttpClient = defaultHttp, private val now: () -> Long = System::currentTimeMillis) {
    suspend fun exchange(server: ServerAddress, code: String, clientId: String): HaCredentials {
        val result=post(server,"/auth/token",FormBody.Builder().add("grant_type","authorization_code").add("code",code).add("client_id",clientId).build())
        return tokens(result,clientId,null)
    }
    suspend fun refresh(server: ServerAddress, saved: HaCredentials): HaCredentials {
        require(saved.renewable)
        val result=post(server,"/auth/token",FormBody.Builder().add("grant_type","refresh_token").add("refresh_token",saved.refreshToken!!).add("client_id",saved.clientId!!).build())
        return tokens(result,saved.clientId,saved.refreshToken)
    }
    suspend fun revoke(server: ServerAddress, saved: HaCredentials) {
        if(!saved.renewable) return
        // action=revoke is supported by both older and current HA versions.
        post(server,"/auth/token",FormBody.Builder().add("action","revoke").add("token",saved.refreshToken!!).build(),allowEmpty=true)
    }
    private fun tokens(j: JSONObject, clientId: String, previousRefresh: String?): HaCredentials {
        try {
            val access=j.getString("access_token")
            val refresh=j.optString("refresh_token").takeUnless { it.isBlank() || it=="null" } ?: previousRefresh
            val seconds=j.getLong("expires_in")
            require(access.isNotBlank() && access.length<=16384 && !refresh.isNullOrBlank() && refresh.length<=16384 && seconds in 1..604800 && j.getString("token_type").equals("Bearer",true))
            val ttl=seconds*1000
            return HaCredentials(access,refresh,clientId,now()+ttl-minOf(30_000,ttl/10))
        } catch(_: Exception) { throw AuthFailure(AuthError.BAD_RESPONSE) }
    }
    private suspend fun post(server: ServerAddress, path: String, body: RequestBody, allowEmpty: Boolean = false): JSONObject {
        val call=http.newCall(Request.Builder().url(server.base+path).post(body).build())
        val response=suspendCancellableCoroutine<Response> { c ->
            c.invokeOnCancellation { call.cancel() }
            call.enqueue(object: Callback {
                override fun onFailure(call: Call,e: IOException) { if(c.isActive) c.resumeWithException(AuthFailure(AuthError.NETWORK)) }
                override fun onResponse(call: Call,response: Response) {
                    if(c.isActive) c.resume(response) { _, value, _ -> value.close() } else response.close()
                }
            })
        }
        return response.use { withContext(Dispatchers.IO) {
            if(it.code in listOf(400,401,403)) throw AuthFailure(AuthError.EXPIRED)
            if(!it.isSuccessful) throw AuthFailure(AuthError.NETWORK)
            if(allowEmpty) return@withContext JSONObject()
            try {
                val source=it.body?.source() ?: throw IOException()
                if(source.request(65537)) throw IOException()
                JSONObject(source.readUtf8())
            } catch(_: Exception) { throw AuthFailure(AuthError.BAD_RESPONSE) }
        } }
    }
    companion object {
        private val defaultHttp=OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).callTimeout(20,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    }
}
