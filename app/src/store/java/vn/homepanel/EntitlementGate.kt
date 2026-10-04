package vn.homepanel

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.meta.horizon.platform.ovr.Error as MetaError
import com.meta.horizon.platform.ovr.requests.Request
import com.meta.horizon.platform.ovr.Core
import com.meta.horizon.platform.ovr.enums.PlatformInitializeResult
import com.meta.horizon.platform.ovr.requests.Entitlements
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Every Store build requires a successful Meta entitlement response. */
class EntitlementGate(private val context: Context) {
    private val mutable = MutableStateFlow(Access.CHECKING)
    val flow = mutable.asStateFlow()
    private val failure = MutableStateFlow<String?>(null)
    val failureCode = failure.asStateFlow()
    val allowed get() = mutable.value == Access.GRANTED
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0

    fun check() {
        val requestGeneration = ++generation
        mutable.value = Access.CHECKING
        failure.value = null
        val started = SystemClock.elapsedRealtime()
        val deadline = started + 8000
        fun finish(granted: Boolean, code: String? = null) {
            handler.post {
                if (requestGeneration == generation && mutable.value == Access.CHECKING) {
                    val timely = SystemClock.elapsedRealtime() < deadline
                    failure.value = if (!timely) "TIMEOUT" else code
                    mutable.value = if (granted && timely) Access.GRANTED else Access.DENIED
                    // No account IDs, tokens, poses or raw SDK error messages in application logs.
                    Log.i("HomePanelOwnership", "result=${mutable.value} code=${failure.value ?: "OK"} elapsedMs=${SystemClock.elapsedRealtime()-started}")
                }
            }
        }
        // Quest 3 does not advertise android.hardware.vr.headtracking via PackageManager.
        // A manifest uses-feature entry is not evidence of that runtime capability.
        if (!vn.homepanel.spatial.supportsSpatialRoom(context.packageManager::hasSystemFeature)) {
            finish(false, "DEVICE_UNSUPPORTED")
            return
        }
        Log.i("HomePanelOwnership", "begin version=${BuildConfig.VERSION_NAME} edition=store debug=${BuildConfig.DEBUG} platformSupported=true")
        try {
            // SDK 77 owns the callback pump. It returns null from asyncInitialize once initialized;
            // retries must issue a fresh entitlement request without reinitializing the SDK.
            if (!Core.isInitialized()) {
                Core.asyncInitialize(BuildConfig.META_APP_ID, context)
                    ?.onSuccess { result ->
                        Log.i("HomePanelOwnership", "initialization=${result.result}")
                        if (result.result != PlatformInitializeResult.Success)
                            finish(false, "INIT_${result.result}")
                    }
                    ?.onError { error -> finish(false, "INIT_${error.code}") }
                    ?: run { finish(false, "INIT_UNAVAILABLE"); return }
            }
            // Meta queues this asynchronous request until initialization completes.
            Entitlements.getIsViewerEntitled()
                .onSuccess { finish(true) }
                .onError(Request.Handler<MetaError> { error -> finish(false, "ENTITLEMENT_${error.code}") })
        } catch (_: Exception) {
            finish(false, "SDK_ERROR")
        } catch (_: LinkageError) {
            finish(false, "SDK_UNAVAILABLE")
        }
        handler.postDelayed({ finish(false, "TIMEOUT") }, 8000)
    }
}
