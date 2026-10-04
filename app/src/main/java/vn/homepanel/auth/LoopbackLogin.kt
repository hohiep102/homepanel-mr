package vn.homepanel.auth

import kotlinx.coroutines.CompletableDeferred
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import vn.homepanel.ha.ServerAddress

/** Temporary callback accessible only from this headset, never from the LAN. */
class LoopbackLogin(private val page: String) : AutoCloseable {
    private val server=ServerSocket(0,8,InetAddress.getByName("127.0.0.1"))
    private val closed=AtomicBoolean(false)
    @Volatile private var activeSocket: Socket?=null
    private val state=Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
    val origin="http://127.0.0.1:${server.localPort}"
    val clientId="$origin/homepanel-mr"
    val redirectUri="$origin/oauth/callback"
    val code=CompletableDeferred<String>()
    private val worker=thread(name="HomePanel OAuth callback",isDaemon=true) {
        try {
            while(!closed.get() && !code.isCompleted) {
                val socket=server.accept();activeSocket=socket
                socket.use { connection ->
                    connection.soTimeout=2500
                    runCatching { receive(connection) }
                }
                activeSocket=null
            }
        } catch(_: Exception) { if(!closed.get() && !code.isCompleted) code.completeExceptionally(AuthFailure(AuthError.NETWORK)) }
        finally { runCatching { server.close() } }
    }
    fun authorizationUrl(server: ServerAddress): String = (server.base+"/auth/authorize").toHttpUrl().newBuilder().addQueryParameter("client_id",clientId).addQueryParameter("redirect_uri",redirectUri).addQueryParameter("response_type","code").addQueryParameter("state",state).build().toString()
    private fun receive(socket: Socket) {
        val input=socket.getInputStream().buffered()
        val header=StringBuilder()
        while(header.length<8192 && !header.endsWith("\r\n\r\n")) {
            val c=input.read();if(c<0) return;header.append(c.toChar())
        }
        if(!header.endsWith("\r\n\r\n")) { respond(socket,431,"Request too large");return }
        val lines=header.toString().split("\r\n")
        val request=lines.first().split(' ')
        val hosts=lines.drop(1).filter { it.startsWith("Host:",ignoreCase=true) }
        if(request.size!=3 || request[0]!="GET" || hosts.size!=1 || hosts.single().substringAfter(':').trim()!="127.0.0.1:${server.localPort}") { respond(socket,400,"Invalid request");return }
        if(!request[1].startsWith("/oauth/callback?")) { respond(socket,404,"Not found");return }
        val url=(origin+request[1]).toHttpUrl()
        val returnedState=url.queryParameterValues("state").singleOrNull()
        if(returnedState==null || !MessageDigest.isEqual(state.toByteArray(),returnedState.toByteArray())) { respond(socket,403,"Invalid session");return }
        if(code.isCompleted) { respond(socket,410,"Session closed");return }
        val authCode=url.queryParameterValues("code").singleOrNull()
        val error=url.queryParameterValues("error").singleOrNull()
        if(error!=null && authCode==null) {
            respond(socket,200,page)
            code.completeExceptionally(AuthFailure(AuthError.CANCELLED));return
        }
        if(authCode.isNullOrBlank() || authCode.length>2048 || authCode.any(Char::isISOControl) || error!=null) { respond(socket,400,"Invalid response");return }
        // Write the page before completing the deferred so coordinator cleanup cannot cut it off.
        respond(socket,200,page)
        code.complete(authCode)
    }
    private fun respond(socket: Socket,status: Int,body: String) {
        val bytes=body.toByteArray(Charsets.UTF_8)
        val reason=if(status==200) "OK" else "Rejected"
        val headers="HTTP/1.1 $status $reason\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply { write(headers.toByteArray(Charsets.US_ASCII));write(bytes);flush() }
    }
    override fun close() {
        if(closed.compareAndSet(false,true)) {
            runCatching { server.close() };runCatching { activeSocket?.close() }
            code.cancel()
        }
    }
}
