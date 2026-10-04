package vn.homepanel

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import vn.homepanel.ui.OwnershipScreen
import vn.homepanel.spatial.RoomActivity
import vn.homepanel.spatial.supportsSpatialRoom
import vn.homepanel.ui.HomeScreen

class MainActivity : ComponentActivity() {
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
    }
    private fun enterRoom(place: Boolean) {
        if (!(application as HomePanelApp).entitlement.allowed) return
        val store = (application as HomePanelApp).store
        if (supportsSpatialRoom(packageManager::hasSystemFeature)) {
            store.notify(AppLanguage.text(this,R.string.opening_room))
            startActivity(Intent(this, RoomActivity::class.java).setAction(Intent.ACTION_MAIN)
                .putExtra(RoomActivity.EXTRA_PLACE, place).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else store.notify(AppLanguage.text(this,R.string.quest_required))
    }
}
