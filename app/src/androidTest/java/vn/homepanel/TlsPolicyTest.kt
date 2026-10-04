package vn.homepanel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import vn.homepanel.auth.HaAuthClient
import vn.homepanel.ha.HaSession
import vn.homepanel.ha.ServerAddress

/** Exercises the actual production clients against a local TLS server, with no real credentials. */
@RunWith(AndroidJUnit4::class)
class TlsPolicyTest {
    private fun productionClients(): List<OkHttpClient> {
        val instances = listOf(HaAuthClient(), HaSession(ServerAddress.parse("https://localhost"), "unused-fixture"))
        // Inspect the existing private clients without changing production networking for a test.
        return instances.map { instance ->
            (instance.javaClass.getDeclaredField("http").apply { isAccessible = true }.get(instance) as OkHttpClient)
                .newBuilder().callTimeout(3, TimeUnit.SECONDS).build()
        }
    }

    private fun tlsFixture(block: (MockWebServer, SSLContext, X509TrustManager) -> Unit) {
        val password = "fixture-only".toCharArray()
        val keys = KeyStore.getInstance("PKCS12")
        InstrumentationRegistry.getInstrumentation().context.assets.open("tls/localhost-test-only.p12").use { keys.load(it, password) }
        val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keys, password) }
        val trusted = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            setCertificateEntry("localhost-fixture", keys.getCertificate("localhost-fixture"))
        }
        val tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trusted) }
        val trust = tm.trustManagers.single() as X509TrustManager
        val tls = SSLContext.getInstance("TLS").apply { init(km.keyManagers, arrayOf(trust), null) }
        MockWebServer().use { server ->
            server.useHttps(tls.socketFactory, false)
            server.start()
            block(server, tls, trust)
        }
    }

    @Test fun productionClientsRejectUntrustedCertificates() = tlsFixture { server, _, _ ->
        for (client in productionClients()) {
            val url = server.url("/").newBuilder().host("localhost").build()
            assertThrows(IOException::class.java) { client.newCall(Request.Builder().url(url).build()).execute().close() }
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun productionHostnameValidationRejectsWrongHostEvenWhenCertificateIsTrusted() = tlsFixture { server, tls, trust ->
        for (client in productionClients()) {
            // Trust only this fixture certificate; keep the exact production hostname verifier.
            val fixtureTrustClient = client.newBuilder().sslSocketFactory(tls.socketFactory, trust).build()
            val wrongHost = server.url("/").newBuilder().host("127.0.0.1").build()
            assertThrows(SSLPeerUnverifiedException::class.java) {
                fixtureTrustClient.newCall(Request.Builder().url(wrongHost).build()).execute().close()
            }
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun matchingHostnameAndTrustedFixtureAreAccepted() = tlsFixture { server, tls, trust ->
        for (client in productionClients()) {
            server.enqueue(MockResponse().setBody("TLS fixture"))
            val fixtureTrustClient = client.newBuilder().sslSocketFactory(tls.socketFactory, trust).build()
            val correctHost = server.url("/").newBuilder().host("localhost").build()
            fixtureTrustClient.newCall(Request.Builder().url(correctHost).build()).execute().use { assertEquals(200, it.code) }
        }
        assertEquals(2, server.requestCount)
    }
}
