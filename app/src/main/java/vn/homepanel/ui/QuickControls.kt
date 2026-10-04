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

/** Main actions stay in a fixed panel; all entities remain accessible via details. */
@Composable fun QuickControls(store: AppStore, device: HaDevice, onDetails: () -> Unit, onPlace: () -> Unit, placementLabel: String) {
    val state by store.state.collectAsState()
    val entity = device.entities.find { it.id == state.selectedEntity } ?: primaryEntity(device)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(device.area.ifBlank { stringResource(R.string.no_area) }, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
        Text(device.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(stringResource(R.string.quick_controls), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (entity != null) {
            val choices = device.entities.filter { it.isEveryday() || it.id == entity.id }
            if (choices.size > 1) {
                var choosing by remember(device.key) { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth().testTag("quick-function-picker")) {
                        Text(entity.name + " ▾", maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(choosing, { choosing = false }) {
                        choices.forEach { option ->
                            DropdownMenuItem(text = { Text(option.name) }, onClick = { store.select(device.key,option.id); choosing = false }, modifier = Modifier.testTag("quick-function:${option.id}"))
                        }
                    }
                }
            }
            Text(if (!entity.available) stringResource(R.string.unavailable) else displayState(entity), fontSize = 21.sp, color = if (entity.available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
            if (!state.connected) Text(stringResource(R.string.rooms_stale), color = MaterialTheme.colorScheme.secondary)
            key(entity.id) {
                EntityControls(entity, state.catalog, state.connected && entity.available && entity.id !in state.busy, compact = true) { store.control(entity.id, it) }
            }
            if (entity.id in state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        } else Text(stringResource(R.string.no_entities))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Button(onClick = { entity?.let { store.select(device.key,it.id) }; onDetails() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.all_controls)) }
        OutlinedButton(onClick = { entity?.let { store.select(device.key,it.id) }; onPlace() }, enabled = state.connected && entity != null && !entity.disabled, modifier = Modifier.fillMaxWidth()) { Text(placementLabel) }
    }
}
