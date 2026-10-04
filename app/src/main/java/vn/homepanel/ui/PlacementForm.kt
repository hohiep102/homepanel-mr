package vn.homepanel.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.homepanel.R

/** UI remains adjustable even when room data is unavailable; persistence stays gated. */
data class PlacementFormState(
    val device: String = "", val status: String = "", val confirming: Boolean = false,
    val ready: Boolean = false, val loading: Boolean = false, val settingUp: Boolean = false, val saving: Boolean = false,
    val countdown: Int = 0, val distance: Float = 2f, val width: Float = .5f,
    val height: Float = .5f, val surface: Boolean = false, val hint: Int = R.string.frame_aim,
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
    Text(stringResource(if (state.confirming) R.string.confirm_title else R.string.place_title), fontSize = 25.sp)
    Text(state.device, color = MaterialTheme.colorScheme.primary, maxLines=1, overflow=TextOverflow.Ellipsis)
    // The primary action is before instructions/settings, so it never requires scrolling.
    if (state.confirming) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave, enabled = state.ready && !state.saving) { Text(stringResource(if (state.saving) R.string.saving_placement else R.string.save_placement)) }
            OutlinedButton(onClick = onAimAgain, enabled = !state.saving) { Text(stringResource(R.string.retry_placement)) }
        }
        Text(stringResource(R.string.confirm_body), fontSize = 14.sp)
    } else if (state.countdown > 0) {
        Text(stringResource(R.string.placement_countdown,state.countdown))
        OutlinedButton(onClick = onCancelCountdown) { Text(stringResource(R.string.placement_cancel_countdown)) }
    } else Button(onClick = onFreeze, enabled = state.ready && !state.loading, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.placement_freeze_later)) }
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
        ValueControl(stringResource(R.string.frame_width), state.width, .1f..3f, state.countdown == 0, "m", step=.1f, showSlider=false) { onAdjust(state.copy(width=it)) }
        ValueControl(stringResource(R.string.frame_height), state.height, .1f..3f, state.countdown == 0, "m", step=.1f, showSlider=false) { onAdjust(state.copy(height=it)) }
        Row { Checkbox(state.surface, { onAdjust(state.copy(surface=it)) }, enabled=state.countdown==0); Text(stringResource(R.string.snap_surface)) }
    }
}
