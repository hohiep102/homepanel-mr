package vn.homepanel.auth

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.homepanel.ha.ServerAddress

enum class LoginPhase { IDLE, WAITING, EXCHANGING, COMPLETE, FAILED }
data class BrowserLoginState(val phase: LoginPhase = LoginPhase.IDLE, val error: AuthError? = null) {
    val active get() = phase==LoginPhase.WAITING || phase==LoginPhase.EXCHANGING
}

/** Application-owned so opening the external browser does not cancel the pending login. */
class BrowserLogin(private val scope: CoroutineScope, private val auth: HaAuthClient, private val completed: (ServerAddress,HaCredentials,Boolean)->Unit, private val timeoutMs: Long=300_000) {
    private val mutableState=MutableStateFlow(BrowserLoginState())
    val state=mutableState.asStateFlow()
    private var job: Job?=null
    private var generation=0
    fun start(address: ServerAddress,remember: Boolean,page: String,openBrowser: (String)->Unit) {
        cancel();val run=generation
        mutableState.value=BrowserLoginState(LoginPhase.WAITING)
        job=scope.launch {
            var receiver: LoopbackLogin?=null
            try {
                withTimeout(timeoutMs) {
                    withContext(Dispatchers.IO) { receiver=LoopbackLogin(page) }
                    val callback=receiver!!
                    try { openBrowser(callback.authorizationUrl(address)) } catch(_: Exception) { throw AuthFailure(AuthError.BROWSER) }
                    val code=callback.code.await()
                    mutableState.value=BrowserLoginState(LoginPhase.EXCHANGING)
                    val tokens=auth.exchange(address,code,callback.clientId)
                    if(run==generation) {
                        mutableState.value=BrowserLoginState(LoginPhase.COMPLETE)
                        completed(address,tokens,remember)
                    }
                }
            } catch(_: TimeoutCancellationException) { receiver?.close();if(run==generation) mutableState.value=BrowserLoginState(LoginPhase.FAILED,AuthError.TIMEOUT) }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { if(run==generation) mutableState.value=BrowserLoginState(LoginPhase.FAILED,(e as? AuthFailure)?.reason ?: AuthError.NETWORK) }
            finally { receiver?.close() }
        }
    }
    fun cancel() { generation++;job?.cancel();job=null;mutableState.value=BrowserLoginState() }
}
