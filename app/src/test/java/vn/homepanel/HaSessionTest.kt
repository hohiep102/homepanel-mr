package vn.homepanel

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import vn.homepanel.ha.*
import java.util.concurrent.CopyOnWriteArrayList

class HaSessionTest {
    @Test fun authenticatesBuffersEventsAndReportsRejectedCommands() = runBlocking<Unit> {
        val server = MockWebServer()
        val commands = CopyOnWriteArrayList<String>()
        var serverSocket: WebSocket? = null
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) { serverSocket = ws; ws.send("""{"type":"auth_required"}""") }
            override fun onMessage(ws: WebSocket, text: String) {
                val request = JSONObject(text); val type = request.getString("type"); commands.add(type)
                if(type == "auth") { assertEquals("fixture-token",request.getString("access_token")); ws.send("""{"type":"auth_ok"}"""); return }
                val id = request.getInt("id")
                fun result(value: Any) { ws.send(JSONObject().put("id",id).put("type","result").put("success",true).put("result",value).toString()) }
                when(type) {
                    "subscribe_events" -> result(JSONObject.NULL)
                    "get_states" -> {
                        result(JSONArray("""[{"entity_id":"light.test","state":"off","attributes":{}}]"""))
                        ws.send("""{"type":"event","event":{"event_type":"state_changed","data":{"entity_id":"light.test","new_state":{"entity_id":"light.test","state":"on","attributes":{}}}}}""")
                    }
                    "get_services" -> result(JSONObject("""{"light":{"turn_on":{},"turn_off":{}}}"""))
                    "config/device_registry/list" -> ws.send("""{"id":$id,"type":"result","success":false,"error":{"code":"unauthorized"}}""")
                    "call_service" -> ws.send("""{"id":$id,"type":"result","success":false,"error":{"code":"not_found"}}""")
                    else -> result(JSONArray())
                }
            }
        }))
        server.start()
        val session = HaSession(ServerAddress.parse(server.url("/").toString()),"fixture-token")
        try {
            session.connect()
            assertEquals("on",session.catalog.value.entities.getValue("light.test").state)
            assertTrue(session.catalog.value.warnings.isNotEmpty())
            assertTrue(commands.indexOf("subscribe_events") < commands.indexOf("get_states"))
            try { session.command(ServiceCall("light","turn_on","light.test").toJson()); fail("must reject") } catch(e:HaFailure) { assertTrue(e.message!!.contains("not_found")) }
            serverSocket!!.send("""{"type":"event","event":{"event_type":"state_changed","data":{"entity_id":"light.test","new_state":{"entity_id":"light.test","state":"unavailable","attributes":{}}}}}""")
            val update = withTimeout(3000) { session.catalog.first { it.entities["light.test"]?.state == "unavailable" } }
            assertFalse(update.entities.getValue("light.test").available)
            serverSocket!!.send("""{"type":"event","event":{"event_type":"state_changed","data":{"entity_id":"light.test","new_state":null}}}""")
            withTimeout(3000) { session.catalog.first { "light.test" !in it.entities } }
        } finally { session.close(); server.shutdown() }
    }
    @Test fun oldBufferedEventCannotOverwriteNewerSnapshot() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) { ws.send("""{"type":"auth_required"}""") }
            override fun onMessage(ws: WebSocket, text: String) {
                val r = JSONObject(text); val type = r.getString("type")
                if(type == "auth") { ws.send("""{"type":"auth_ok"}"""); return }
                val result: Any = when(type) {
                    "get_states" -> JSONArray("""[{"entity_id":"light.a","state":"on","last_updated":"2026-09-27T14:00:02Z","attributes":{}}]""")
                    "get_services" -> JSONObject()
                    "get_config" -> JSONObject("""{"unit_system":{"temperature":"°F"}}""")
                    else -> JSONArray()
                }
                if(type == "get_states") ws.send("""{"type":"event","event":{"event_type":"state_changed","time_fired":"2026-09-27T14:00:01Z","data":{"entity_id":"light.a","new_state":{"entity_id":"light.a","state":"off","last_updated":"2026-09-27T14:00:01Z","attributes":{}}}}}""")
                ws.send(JSONObject().put("type","result").put("id",r.getInt("id")).put("success",true).put("result",result).toString())
            }
        }))
        server.start(); val session = HaSession(ServerAddress.parse(server.url("/").toString()),"fixture")
        try { session.connect(); assertEquals("on",session.catalog.value.entities["light.a"]?.state); assertEquals("°F",session.catalog.value.temperatureUnit) }
        finally { session.close(); server.shutdown() }
    }
    @Test fun authFailureIsNotTreatedAsConnected() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) { ws.send("""{"type":"auth_required"}""") }
            override fun onMessage(ws: WebSocket, text: String) { ws.send("""{"type":"auth_invalid","message":"invalid"}""") }
        }))
        server.start(); val session = HaSession(ServerAddress.parse(server.url("/").toString()),"fixture-token")
        try { try { session.connect(); fail("must reject") } catch (e:HaFailure) { assertTrue(e.authenticationRejected) }; assertTrue(session.catalog.value.entities.isEmpty()) }
        finally { session.close(); server.shutdown() }
    }
}
