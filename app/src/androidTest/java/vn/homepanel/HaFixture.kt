package vn.homepanel

import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.mockwebserver.Dispatcher
import org.json.JSONArray
import org.json.JSONObject
import vn.homepanel.ha.Demo
import vn.homepanel.ha.Catalog
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Test-only HA server: no production credentials, no outbound device commands. */
class HaFixture(private val catalog: Catalog = Demo.catalog(), private val devices: JSONArray = JSONArray(), private val entities: JSONArray = JSONArray(), private val areas: JSONArray = JSONArray()) : AutoCloseable {
    val received = CopyOnWriteArrayList<String>()
    val serviceCalls = CopyOnWriteArrayList<JSONObject>()
    val sockets = CopyOnWriteArrayList<WebSocket>()
    val tokenGrants=CopyOnWriteArrayList<String>()
    @Volatile var rejectRefresh=false
    val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest):MockResponse {
                if(request.path=="/auth/token") {
                    val form=("http://fixture/?"+request.body.readUtf8()).toHttpUrl()
                    if(form.queryParameter("action")=="revoke") { tokenGrants.add("revoke");return MockResponse().setResponseCode(200) }
                    val grant=form.queryParameter("grant_type").orEmpty();tokenGrants.add(grant)
                    if(grant=="refresh_token" && rejectRefresh) return MockResponse().setResponseCode(400).setBody("{\"error\":\"invalid_grant\"}")
                    val valid=when(grant) {
                        "authorization_code" -> form.queryParameter("code")=="fixture-code"
                        "refresh_token" -> form.queryParameter("refresh_token")=="fixture-refresh"
                        else -> false
                    }
                    if(!valid || form.queryParameter("client_id").isNullOrBlank()) return MockResponse().setResponseCode(400)
                    val access=if(grant=="refresh_token") "fixture-renewed-access" else "fixture-oauth-access"
                    return MockResponse().setHeader("Content-Type","application/json").setBody(JSONObject().put("access_token",access).put("refresh_token","fixture-refresh").put("expires_in",1800).put("token_type","Bearer").toString())
                }
                return MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { sockets.add(webSocket); webSocket.send("""{"type":"auth_required","ha_version":"2026.9.fixture"}""") }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val r=JSONObject(text);val type=r.getString("type");received.add(type)
                    if(type=="auth") {
                        webSocket.send(if(r.optString("access_token") in setOf("fixture-only-token","fixture-oauth-access","fixture-renewed-access")) """{"type":"auth_ok"}""" else """{"type":"auth_invalid"}""")
                        return
                    }
                    val result:Any = when(type) {
                        "get_states" -> JSONArray(catalog.entities.values.filter { it.state != "missing" }.map { JSONObject().put("entity_id",it.id).put("state",it.state).put("attributes",it.attributes) })
                        "get_services" -> catalog.services
                        "config/device_registry/list" -> devices
                        "config/entity_registry/list" -> entities
                        "config/area_registry/list" -> areas
                        "get_config" -> JSONObject("""{"unit_system":{"temperature":"°C"}}""")
                        "call_service" -> { serviceCalls.add(r);JSONObject.NULL }
                        "subscribe_events" -> JSONObject.NULL
                        else -> JSONArray()
                    }
                    webSocket.send(JSONObject().put("type","result").put("id",r.getInt("id")).put("success",true).put("result",result).toString())
                }
                override fun onClosing(webSocket:WebSocket,code:Int,reason:String) { webSocket.close(code,null) }
                })
            }
        }
        start(InetAddress.getByName("0.0.0.0"),0)
    }
    val localUrl get() = "http://127.0.0.1:${server.port}"
    override fun close() { sockets.forEach { it.close(1000,null) }; server.shutdown() }
}
