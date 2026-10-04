package vn.homepanel

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import vn.homepanel.discovery.HaProbe
import java.util.concurrent.atomic.AtomicInteger

class HaProbeTest {
    @Test fun recognizesGreetingWithoutSendingCredentialsOrCommands() = runBlocking<Unit> {
        val messages=AtomicInteger();val server=MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
            override fun onOpen(webSocket:WebSocket,response:Response) { webSocket.send("""{"type":"auth_required","ha_version":"2026.9.1"}""") }
            override fun onMessage(webSocket:WebSocket,text:String) { messages.incrementAndGet() }
            override fun onClosing(webSocket:WebSocket,code:Int,reason:String) { webSocket.close(code,null) }
        }))
        server.start()
        try { assertEquals("2026.9.1",HaProbe().identify(server.url("/").toString()));assertEquals(0,messages.get());assertEquals("/api/websocket",server.takeRequest().path) }
        finally { server.shutdown() }
    }
    @Test fun rejectsOtherServersAndDoesNotFollowRedirects() = runBlocking<Unit> {
        val outside=MockWebServer();outside.start();val server=MockWebServer()
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location",outside.url("/")))
        server.start()
        try { assertNull(HaProbe().identify(server.url("/").toString()));assertEquals(0,outside.requestCount) }
        finally { server.shutdown();outside.shutdown() }
    }
    @Test fun silentSocketTimesOutAndCloses() = runBlocking<Unit> {
        val server=MockWebServer();server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener(){}));server.start()
        try { assertNull(HaProbe(timeoutMs=150).identify(server.url("/").toString())) } finally { server.shutdown() }
    }
}
