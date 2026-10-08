package vn.homepanel

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import vn.homepanel.ui.OwnershipScreen
import vn.homepanel.spatial.RoomActivity
import vn.homepanel.spatial.supportsSpatialRoom
import vn.homepanel.ui.HomeScreen

/** Only a launcher open goes straight to the room; Return to window and the sign-in callback stay in the window. */
internal fun opensRoomOnLaunch(action: String?, categories: Set<String>?) =
    action == Intent.ACTION_MAIN && categories.orEmpty().contains(Intent.CATEGORY_LAUNCHER)

class MainActivity : ComponentActivity() {
    private var autoRoom: Job? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as HomePanelApp
        setContent {
            val access by app.entitlement.flow.collectAsState()
            LocalizedContent {
                if (access == Access.GRANTED) HomeScreen(app.store, onEnterRoom = { enterRoom(false) }, onPlace = { enterRoom(true) })
                else OwnershipScreen(access, onRetry = { app.entitlement.check() }, onExit = { finishAndRemoveTask() })
            }
        }
        if (savedInstanceState == null) maybeOpenRoom(intent)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeOpenRoom(intent)
    }
    /** A saved session reconnects in the background; once it is live, the window hands over to the room. */
    private fun maybeOpenRoom(intent: Intent?) {
        autoRoom?.cancel()
        if (!opensRoomOnLaunch(intent?.action, intent?.categories) || !supportsSpatialRoom(packageManager::hasSystemFeature)) return
        val app = application as HomePanelApp
        autoRoom = lifecycleScope.launch {
            val live = withTimeoutOrNull(AUTO_ROOM_WAIT_MS) {
                combine(app.entitlement.flow, app.store.state) { access, state -> access == Access.GRANTED && state.connected && !state.demo }.first { it }
            }
            if (live == true && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) enterRoom(false)
        }
    }
    private fun enterRoom(place: Boolean) {
        autoRoom?.cancel()
        if (!(application as HomePanelApp).entitlement.allowed) return
        val store = (application as HomePanelApp).store
        if (supportsSpatialRoom(packageManager::hasSystemFeature)) {
            store.notify(AppLanguage.text(this,R.string.opening_room))
            startActivity(Intent(this, RoomActivity::class.java).setAction(Intent.ACTION_MAIN)
                .putExtra(RoomActivity.EXTRA_PLACE, place).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else store.notify(AppLanguage.text(this,R.string.quest_required))
    }
    private companion object { const val AUTO_ROOM_WAIT_MS = 8_000L }
}
