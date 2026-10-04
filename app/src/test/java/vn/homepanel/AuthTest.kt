package vn.homepanel

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import vn.homepanel.auth.*
import vn.homepanel.ha.ServerAddress
import java.io.IOException
import java.util.concurrent.TimeUnit

class AuthTest {
    private val client=OkHttpClient.Builder().callTimeout(2,TimeUnit.SECONDS).build()
    private val tokenJson="""{"access_token":"fixture-access","refresh_token":"fixture-refresh","token_type":"Bearer","expires_in":1800}"""
    private fun callback(login: LoopbackLogin, authorize: String, state: String? = null, code: String="fixture-code"): String = login.redirectUri.toHttpUrl().newBuilder().addQueryParameter("state",state ?: authorize.toHttpUrl().queryParameter("state")!!).addQueryParameter("code",code).build().toString()
    private fun get(url: String, host: String?=null): Response = client.newCall(Request.Builder().url(url).apply { if(host!=null) header("Host",host) }.build()).execute()
    @Test fun exchangeAndRefreshUseFormDataExactClientAndPreserveRefreshToken() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(tokenJson))
            server.enqueue(MockResponse().setBody("""{"access_token":"new-access","token_type":"Bearer","expires_in":1800}"""))
            val address=ServerAddress.parse(server.url("/").toString())
            val auth=HaAuthClient(now={1000})
            val saved=auth.exchange(address,"code & special","http://127.0.0.1:4411/homepanel-mr")
            assertEquals("fixture-access",saved.accessToken);assertEquals(1_771_000,saved.refreshAt)
            val request=server.takeRequest();assertEquals("POST",request.method);assertEquals("/auth/token",request.path)
            val form=("http://local/?"+request.body.readUtf8()).toHttpUrl()
            assertEquals("authorization_code",form.queryParameter("grant_type"));assertEquals("code & special",form.queryParameter("code"));assertEquals(saved.clientId,form.queryParameter("client_id"))
            val renewed=auth.refresh(address,saved)
            assertEquals("new-access",renewed.accessToken);assertEquals(saved.refreshToken,renewed.refreshToken)
            val refresh=("http://local/?"+server.takeRequest().body.readUtf8()).toHttpUrl()
            assertEquals("refresh_token",refresh.queryParameter("grant_type"));assertEquals(saved.refreshToken,refresh.queryParameter("refresh_token"));assertEquals(saved.clientId,refresh.queryParameter("client_id"))
        }
    }
    @Test fun tokensNeverFollowRedirectsOrExposeServerErrorBodies() = runBlocking<Unit> {
        MockWebServer().use { target -> MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location",target.url("/capture")))
            server.enqueue(MockResponse().setResponseCode(400).setBody("secret-server-body"))
            val auth=HaAuthClient();val address=ServerAddress.parse(server.url("/").toString())
            try { auth.exchange(address,"fixture-code","http://127.0.0.1:1/app");fail() } catch(e:AuthFailure) { assertEquals(AuthError.NETWORK,e.reason) }
            assertEquals(0,target.requestCount)
            try { auth.exchange(address,"fixture-code","http://127.0.0.1:1/app");fail() } catch(e:AuthFailure) { assertEquals(AuthError.EXPIRED,e.reason);assertFalse(e.toString().contains("secret-server-body")) }
        } }
    }
    @Test fun malformedTokenPayloadIsRejected() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"access_token":"a","token_type":"Bearer","expires_in":1800}"""))
            try { HaAuthClient().exchange(ServerAddress.parse(server.url("/").toString()),"code","http://127.0.0.1:1/app");fail() } catch(e:AuthFailure) { assertEquals(AuthError.BAD_RESPONSE,e.reason) }
        }
    }
    @Test fun revokeSendsRefreshTokenOnlyToOriginalServer() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200))
            HaAuthClient().revoke(ServerAddress.parse(server.url("/").toString()),HaCredentials("access","refresh","http://127.0.0.1:1/app",100))
            val form=("http://local/?"+server.takeRequest().body.readUtf8()).toHttpUrl()
            assertEquals("revoke",form.queryParameter("action"));assertEquals("refresh",form.queryParameter("token"));assertNull(form.queryParameter("access_token"))
        }
    }
    @Test fun loopbackChecksStateHostAndDuplicateParametersBeforeAcceptingCode() = runBlocking<Unit> {
        LoopbackLogin("return to app").use { receiver ->
            val authorize=receiver.authorizationUrl(ServerAddress.parse("http://192.168.1.5:8123"))
            assertEquals("127.0.0.1",receiver.redirectUri.toHttpUrl().host)
            assertEquals(receiver.clientId,authorize.toHttpUrl().queryParameter("client_id"))
            assertEquals(receiver.redirectUri,authorize.toHttpUrl().queryParameter("redirect_uri"))
            get(callback(receiver,authorize,state="wrong")).use { assertEquals(403,it.code) }
            get(callback(receiver,authorize),host="attacker.example").use { assertEquals(400,it.code) }
            get(callback(receiver,authorize)+"&state=second").use { assertEquals(403,it.code) }
            get(callback(receiver,authorize)+"&code=second").use { assertEquals(400,it.code) }
            assertFalse(receiver.code.isCompleted)
            get(callback(receiver,authorize)).use { assertEquals(200,it.code);assertEquals("no-store",it.header("Cache-Control"));assertFalse(it.body!!.string().contains("fixture-code")) }
            assertEquals("fixture-code",withTimeout(1000){receiver.code.await()})
        }
    }
    @Test fun closeInvalidatesCallbackAndStopsListening() {
        val receiver=LoopbackLogin("done");val url=callback(receiver,receiver.authorizationUrl(ServerAddress.parse("http://ha.local:8123")))
        receiver.close()
        assertTrue(receiver.code.isCancelled)
        assertThrows(IOException::class.java) { get(url).close() }
    }
    @Test fun deniedAuthorizationCompletesWithoutAnAccessToken() = runBlocking<Unit> {
        LoopbackLogin("return").use { receiver ->
            val auth=receiver.authorizationUrl(ServerAddress.parse("http://ha.local:8123")).toHttpUrl()
            val url=receiver.redirectUri.toHttpUrl().newBuilder().addQueryParameter("state",auth.queryParameter("state")).addQueryParameter("error","access_denied").build()
            get(url.toString()).close()
            try { receiver.code.await();fail() } catch(e:AuthFailure) { assertEquals(AuthError.CANCELLED,e.reason) }
        }
    }
    @Test fun browserCoordinatorExchangesCodeAndCompletesOnce() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(tokenJson))
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
            val opened=CompletableDeferred<String>();val done=CompletableDeferred<HaCredentials>()
            val login=BrowserLogin(scope,HaAuthClient(),{ _,tokens,remember -> assertTrue(remember);done.complete(tokens) })
            try {
                login.start(ServerAddress.parse(server.url("/").toString()),true,"return",{opened.complete(it)})
                val authorize=withTimeout(2000){opened.await()}.toHttpUrl()
                val url=authorize.queryParameter("redirect_uri")!!.toHttpUrl().newBuilder().addQueryParameter("state",authorize.queryParameter("state")).addQueryParameter("code","fixture-code").build()
                get(url.toString()).close()
                assertEquals("fixture-access",withTimeout(2000){done.await()}.accessToken)
                assertEquals(LoginPhase.COMPLETE,login.state.value.phase);assertEquals(1,server.requestCount)
            } finally { login.cancel();scope.cancel() }
        }
    }
    @Test fun timedOutLoginCannotConnectLater() = runBlocking<Unit> {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val opened=CompletableDeferred<String>();var connections=0
        val login=BrowserLogin(scope,HaAuthClient(),{ _,_,_ -> connections++ },timeoutMs=150)
        try {
            login.start(ServerAddress.parse("http://ha.local:8123"),true,"return",{opened.complete(it)})
            val url=withTimeout(2000){opened.await()}.toHttpUrl().queryParameter("redirect_uri")!!
            withTimeout(2000) { while(login.state.value.phase!=LoginPhase.FAILED) delay(10) }
            assertEquals(AuthError.TIMEOUT,login.state.value.error);assertEquals(0,connections)
            assertThrows(IOException::class.java) { get(url).close() }
        } finally { login.cancel();scope.cancel() }
    }
    @Test fun cancellingDuringTokenExchangePreventsLateConnection() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(tokenJson).setBodyDelay(300,TimeUnit.MILLISECONDS))
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default);val opened=CompletableDeferred<String>();var connected=false
            val login=BrowserLogin(scope,HaAuthClient(),{ _,_,_ -> connected=true })
            try {
                login.start(ServerAddress.parse(server.url("/").toString()),true,"return",{opened.complete(it)})
                val authorize=withTimeout(2000){opened.await()}.toHttpUrl()
                val callback=authorize.queryParameter("redirect_uri")!!.toHttpUrl().newBuilder().addQueryParameter("state",authorize.queryParameter("state")).addQueryParameter("code","fixture-code").build()
                get(callback.toString()).close()
                withTimeout(2000) { while(server.requestCount==0) delay(10) }
                login.cancel();delay(400)
                assertFalse(connected);assertEquals(LoginPhase.IDLE,login.state.value.phase)
            } finally { login.cancel();scope.cancel() }
        }
    }
    @Test fun credentialSerializationPreservesAuthWithoutPrintingSecrets() {
        val saved=HaCredentials("private-access","private-refresh","http://127.0.0.1:99/app",42)
        assertEquals(saved,HaCredentials.fromJson(saved.toJson()));assertTrue(saved.needsRefresh(43))
        assertFalse(saved.toString().contains("private-"));assertFalse(HaCredentials("manual-token").needsRefresh())
        assertEquals(HaCredentials("manual-token"),HaCredentials.fromJson(HaCredentials("manual-token").toJson()))
    }
}
