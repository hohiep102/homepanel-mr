package vn.homepanel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.homepanel.R
import vn.homepanel.ha.Catalog
import vn.homepanel.ha.summarizeRooms

@Composable fun RoomOverview(catalog: Catalog, connected: Boolean, placed: Set<String>, onRoom: (String) -> Unit) {
    val rooms = summarizeRooms(catalog)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.rooms_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!connected) Text(stringResource(R.string.rooms_stale), color = MaterialTheme.colorScheme.secondary)
        LazyVerticalGrid(columns = GridCells.Adaptive(260.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(rooms, key = { it.id }) { room ->
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().clickable { onRoom(room.id) }) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(room.name.ifBlank { stringResource(R.string.no_area) }, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.room_device_total, room.devices.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        Text(stringResource(R.string.room_lights_on, room.lightsOn), color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
                        if (room.unavailable > 0) Text(stringResource(R.string.room_unavailable, room.unavailable), color = MaterialTheme.colorScheme.secondary)
                        Text(stringResource(R.string.room_placed, room.devices.count { it.key in placed }), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.room_open), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
