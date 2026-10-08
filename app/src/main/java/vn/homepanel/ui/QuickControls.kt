package vn.homepanel.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.homepanel.AppStore
import vn.homepanel.R
import vn.homepanel.ha.*

/** Main actions stay in a fixed panel; all entities remain accessible via details. The main adjustment is a large stepper. */
@Composable fun QuickControls(store: AppStore, device: HaDevice, onDetails: () -> Unit, onPlace: () -> Unit, placementLabel: String, large: Boolean = false) {
    val state by store.state.collectAsState()
    val entity = device.entities.find { it.id == state.selectedEntity } ?: primaryEntity(device)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(device.area.ifBlank { stringResource(R.string.no_area) }, color = Mint, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(device.name, fontSize = if (large) 34.sp else 30.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.quick_controls), color = Muted, fontSize = 13.sp)
        }
        if (entity != null) {
            val choices = device.entities.filter { it.isEveryday() || it.id == entity.id }
            if (choices.size > 1) {
                InlinePicker(entity.name, choices, { it.name }, { store.select(device.key,it.id) }, Modifier.fillMaxWidth(), selected = entity,
                    buttonTag = "quick-function-picker", optionTag = { "quick-function:${it.id}" })
            }
            Text(if (!entity.available) stringResource(R.string.unavailable) else displayState(entity), fontSize = 20.sp, color = if (entity.available) Mint else Amber)
            if (!state.connected) Text(stringResource(R.string.rooms_stale), color = Amber, fontSize = 14.sp)
            val enabled = state.connected && entity.available && entity.id !in state.busy
            val hero = compactAdjustment(entity, state.catalog)
            if (entity.domain == "camera") CameraView(store, entity)
            else key(entity.id) {
                hero?.let { adj ->
                    val label = stringResource(adj.label)
                    val value = if (adj.value % 1f == 0f) adj.value.toInt().toString() else "%.1f".format(adj.value)
                    BigStepper("$value${if (adj.suffix == "%") "%" else adj.suffix}", label, stringResource(R.string.value_decrease, label), stringResource(R.string.value_increase, label),
                        enabled && adj.value > adj.min, enabled && adj.value < adj.max, { store.control(entity.id, adj.action(false)) }, { store.control(entity.id, adj.action(true)) },
                        buttonSize = if (large) 88.dp else 72.dp, valueSize = if (large) 88 else 64)
                }
                EntityControls(entity, state.catalog, enabled, compact = true, heroShown = hero != null) { store.control(entity.id, it) }
            }
            if (entity.id in state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        } else Text(stringResource(R.string.no_entities))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { entity?.let { store.select(device.key,it.id) }; onPlace() }, enabled = state.connected && entity != null && !entity.disabled, shape = Rounded,
                colors = ButtonDefaults.buttonColors(containerColor = Line, contentColor = Cream), modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(placementLabel, fontWeight = FontWeight.SemiBold) }
            OutlinedButton(onClick = { entity?.let { store.select(device.key,it.id) }; onDetails() }, shape = Rounded, border = androidx.compose.foundation.BorderStroke(1.dp, Outline), modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.all_controls), color = Cream) }
        }
    }
}
