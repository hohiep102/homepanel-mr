package vn.homepanel.ha

import vn.homepanel.R

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HaFailure(val textId: Int, val authenticationRejected: Boolean = false, code: String = "") : Exception(if (authenticationRejected) "Authentication rejected" else "Home Assistant failure: $code")

/** One authenticated socket. Events are buffered until the initial snapshot is installed. */
class HaSession(private val address: ServerAddress, private val token: String, private val http: OkHttpClient = sharedHttp) : AutoCloseable {
    private val auth = CompletableDeferred<Unit>()
    val ended = CompletableDeferred<Unit>()
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()
    private val sequence = AtomicInteger(1)
    private val lock = Any()
    private var socket: WebSocket? = null
    private var closed = false
    private var ready = false
    private var buffered = mutableListOf<JSONObject>()
    private var states = mutableMapOf<String, HaEntity>()
    private var devices = JSONArray(); private var registry = JSONArray(); private var areas = JSONArray()
    private var temperatureUnit = ""
    private val versions = mutableMapOf<String, Instant>()
    private var services = JSONObject(); private var warnings = mutableListOf<Int>()
    private val mutableCatalog = MutableStateFlow(Catalog())
    val catalog = mutableCatalog.asStateFlow()

    suspend fun connect() {
        require(token.isNotBlank()) { "Enter an access token." }
        socket = http.newWebSocket(Request.Builder().url(address.websocket).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = JSONObject(text)
                    when (message.optString("type")) {
                        "auth_required" -> webSocket.send(JSONObject().put("type", "auth").put("access_token", token).toString())
                        "auth_ok" -> auth.complete(Unit)
                        "auth_invalid" -> fail(HaFailure(R.string.auth_expired, authenticationRejected = true))
                        "result" -> pending.remove(message.optInt("id"))?.let { deferred ->
                            if (message.optBoolean("success")) deferred.complete(message)
                            else deferred.completeExceptionally(HaFailure(R.string.ha_request_failed, code = message.optJSONObject("error")?.optString("code") ?: "unknown"))
                        }
                        "event" -> {
                            val event = message.optJSONObject("event") ?: return
                            if (event.optString("event_type") == "state_changed") synchronized(lock) {
                                val data = event.optJSONObject("data") ?: return
                                data.put("__event_time", event.optString("time_fired"))
                                if (!ready) buffered.add(data) else { applyEvent(data); publish() }
                            }
                        }
                    }
                } catch (_: org.json.JSONException) { fail(HaFailure(R.string.ha_invalid_response)) }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { fail(HaFailure(R.string.connection_failed)) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { fail(HaFailure(R.string.not_connected)) }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
        })
        withTimeout(15_000) { auth.await() }
        command(JSONObject().put("type", "subscribe_events").put("event_type", "state_changed"))
        val initial = command(JSONObject().put("type", "get_states")).getJSONArray("result").objects().map(HaEntity::fromJson)
        services = command(JSONObject().put("type", "get_services")).optJSONObject("result") ?: JSONObject()
        temperatureUnit = try { command(JSONObject().put("type", "get_config")).optJSONObject("result")?.optJSONObject("unit_system")?.optString("temperature").orEmpty() } catch (_: HaFailure) { "" }
        devices = optionalRegistry("config/device_registry/list", R.string.registry_device_warning)
        registry = optionalRegistry("config/entity_registry/list", R.string.registry_entity_warning)
        areas = optionalRegistry("config/area_registry/list", R.string.registry_area_warning)
        synchronized(lock) {
            check(!closed) { "Connection closed." }
            states = initial.associateBy { it.id }.toMutableMap()
            initial.forEach { e -> timestamp(e.updatedAt)?.let { versions[e.id] = it } }
            buffered.forEach(::applyEvent); buffered.clear(); ready = true; publish()
        }
    }
    private suspend fun optionalRegistry(type: String, warning: Int): JSONArray = try {
        command(JSONObject().put("type", type)).optJSONArray("result") ?: JSONArray()
    } catch (_: HaFailure) { warnings.add(warning); JSONArray() }
    private fun applyEvent(data: JSONObject) {
        val id = data.optString("entity_id")
        val state = data.optJSONObject("new_state")
        val version = timestamp(state?.nullString("last_updated") ?: data.nullString("__event_time"))
        if (version != null && versions[id]?.let { version.isBefore(it) } == true) return
        if (version != null) versions[id] = version
        if (state == null) states.remove(id) else states[id] = HaEntity.fromJson(state)
    }
    private fun timestamp(raw: String?): Instant? = raw?.let { runCatching { Instant.parse(it) }.getOrNull() }
    private fun publish() { mutableCatalog.value = buildCatalog(states.values.toList(), devices, registry, areas, services, warnings.toList()).copy(temperatureUnit = temperatureUnit) }
    suspend fun command(command: JSONObject): JSONObject {
        val id = sequence.getAndIncrement()
        val reply = CompletableDeferred<JSONObject>()
        synchronized(lock) {
            check(!closed) { "Connection closed." }
            pending[id] = reply
            if (socket?.send(command.put("id", id).toString()) != true) {
                pending.remove(id); throw HaFailure(R.string.not_connected)
            }
        }
        return try { withTimeout(12_000) { reply.await() } } finally { pending.remove(id) }
    }
    private fun fail(error: Exception) = synchronized(lock) {
        if (!closed) {
            closed = true
            auth.completeExceptionally(error)
            pending.values.forEach { it.completeExceptionally(error) }; pending.clear()
            ended.completeExceptionally(error)
            socket?.cancel()
        }
    }
    override fun close() { fail(HaFailure(R.string.not_connected)) }
    companion object {
        private val sharedHttp = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).connectTimeout(12, TimeUnit.SECONDS).build()
    }
}
