package vn.homepanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.homepanel.R

/** UI remains adjustable even when room data is unavailable; persistence stays gated. */
data class PlacementFormState(
    val device: String = "", val status: String = "", val confirming: Boolean = false,
    val ready: Boolean = false, val loading: Boolean = false, val settingUp: Boolean = false, val saving: Boolean = false,
    val countdown: Int = 0, val distance: Float = 2f, val width: Float = .5f,
    val height: Float = .5f, val surface: Boolean = false, val hint: Int = R.string.frame_aim, val warning: String? = null,
    /** A camera is placed as a 16:9 screen: one size control, the height follows. */
    val screen: Boolean = false,
)

@Composable fun PlacementForm(
    state: PlacementFormState,
    onAdjust: (PlacementFormState) -> Unit,
    onScan: () -> Unit,
    onReload: () -> Unit,
    onFreeze: () -> Unit,
    onCancelCountdown: () -> Unit,
    onSave: () -> Unit,
    onAimAgain: () -> Unit,
) {
    Text(stringResource(if (state.confirming) R.string.confirm_title else R.string.place_title), fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text(state.device, color = Mint, maxLines=1, overflow=TextOverflow.Ellipsis, fontSize = 16.sp)
    state.warning?.let { Text(it, color = Amber, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.fillMaxWidth().background(Amber.copy(alpha = .08f), Rounded).padding(horizontal = 14.dp, vertical = 10.dp)) }
    // The primary action is before instructions/settings, so it never requires scrolling.
    if (state.confirming) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave, enabled = state.ready && !state.saving, shape = Rounded, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(if (state.saving) R.string.saving_placement else R.string.save_placement), fontWeight = FontWeight.SemiBold) }
            OutlinedButton(onClick = onAimAgain, enabled = !state.saving, shape = Rounded, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.retry_placement)) }
        }
        Text(stringResource(R.string.confirm_body), fontSize = 14.sp)
        // The frozen position stays; only its size can still change before saving.
        SizeControls(state, !state.saving, onAdjust)
    } else if (state.countdown > 0) {
        Text(stringResource(R.string.placement_countdown,state.countdown))
        OutlinedButton(onClick = onCancelCountdown) { Text(stringResource(R.string.placement_cancel_countdown)) }
    } else Button(onClick = onFreeze, enabled = state.ready && !state.loading, shape = Rounded, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text(stringResource(R.string.placement_freeze_later), fontWeight = FontWeight.SemiBold) }
    Text(state.status, fontSize = 13.sp, maxLines=3, overflow=TextOverflow.Ellipsis)
    if (state.loading && !state.settingUp) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (!state.ready) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick=onScan, enabled=!state.loading, modifier=Modifier.weight(1f).heightIn(min=56.dp)) { Text(stringResource(R.string.scan_room)) }
            OutlinedButton(onClick=onReload, enabled=!state.loading || state.settingUp, modifier=Modifier.weight(1f).heightIn(min=56.dp)) { Text(stringResource(R.string.reload_room)) }
        }
    }
    if (!state.confirming) {
        Text(stringResource(state.hint), fontSize = 13.sp)
        ValueControl(stringResource(R.string.placement_distance), state.distance, .3f..6f, state.countdown == 0, "m", step=.1f, showSlider=false) { onAdjust(state.copy(distance=it,surface=false)) }
        SizeControls(state, state.countdown == 0, onAdjust)
        Row { Checkbox(state.surface, { onAdjust(state.copy(surface=it)) }, enabled=state.countdown==0); Text(stringResource(R.string.snap_surface)) }
    }
}

@Composable private fun SizeControls(state: PlacementFormState, enabled: Boolean, onAdjust: (PlacementFormState) -> Unit) {
    if (state.screen) ValueControl(stringResource(R.string.screen_size), state.width, .3f..3f, enabled, "m", step=.1f, showSlider=false) { onAdjust(state.copy(width=it, height=vn.homepanel.spatial.screenHeight(it))) }
    else {
        ValueControl(stringResource(R.string.frame_width), state.width, .1f..3f, enabled, "m", step=.1f, showSlider=false) { onAdjust(state.copy(width=it)) }
        ValueControl(stringResource(R.string.frame_height), state.height, .1f..3f, enabled, "m", step=.1f, showSlider=false) { onAdjust(state.copy(height=it)) }
    }
}
