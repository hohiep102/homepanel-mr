package vn.homepanel.discovery

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import vn.homepanel.ha.ServerAddress

/** Identify a HA WebSocket greeting. Never sends authentication or follows redirects. */
class HaProbe(private val client: OkHttpClient = defaultClient, private val timeoutMs: Long = 1600) {
    suspend fun identify(url: String): String? = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { continuation ->
            val socket = client.newWebSocket(Request.Builder().url(ServerAddress.parse(url).websocket).build(), object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val version = if (text.length <= 4096) runCatching {
                        val greeting = JSONObject(text)
                        if (greeting.optString("type") == "auth_required") greeting.optString("ha_version").takeIf { it.isNotBlank() && it.length < 80 } else null
                    }.getOrNull() else null
                    if (continuation.isActive) continuation.resume(version)
                    webSocket.close(1000, null)
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { if (continuation.isActive) continuation.resume(null) }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { if (continuation.isActive) continuation.resume(null) }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code,null) }
            })
            continuation.invokeOnCancellation { socket.cancel() }
        }
    }
    companion object {
        private val defaultClient = OkHttpClient.Builder().connectTimeout(900,TimeUnit.MILLISECONDS).readTimeout(2,TimeUnit.SECONDS).callTimeout(3,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    }
}
