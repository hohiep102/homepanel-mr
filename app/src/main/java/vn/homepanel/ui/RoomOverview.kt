package vn.homepanel.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.homepanel.R
import vn.homepanel.ha.Catalog
import vn.homepanel.ha.primaryEntity
import vn.homepanel.ha.summarizeRooms

/** One card per HA area: what is on, the first few devices with their state, and a nudge to place one not yet placed. */
@Composable fun RoomOverview(catalog: Catalog, connected: Boolean, placed: Set<String>, onRoom: (String) -> Unit) {
    val rooms = summarizeRooms(catalog)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!connected) Text(stringResource(R.string.rooms_stale), color = Amber, fontSize = 14.sp)
        LazyVerticalGrid(columns = GridCells.Adaptive(280.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(rooms, key = { it.id }) { room ->
                Surface(color = Card, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth().clickable { onRoom(room.id) }) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row {
                            Text(room.name.ifBlank { stringResource(R.string.no_area) }, Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.room_lights_on, room.lightsOn), style = Mono, color = if (room.lightsOn > 0) Mint else Muted)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.room_device_total, room.devices.size), color = Muted, fontSize = 14.sp)
                            Text("· " + stringResource(R.string.room_placed, room.devices.count { it.key in placed }) + if (room.unavailable > 0) " · " + stringResource(R.string.room_unavailable, room.unavailable) else "", color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        room.devices.take(3).forEach { device ->
                            val entity = primaryEntity(device)
                            val on = entity?.state == "on"
                            Row(Modifier.fillMaxWidth().background(Raised, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
                                Text(device.name, Modifier.weight(1f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(entity?.let { displayState(it) } ?: "—", color = if (on) Mint else Muted, fontSize = 15.sp, maxLines = 1)
                            }
                        }
                        room.devices.firstOrNull { it.key !in placed }?.let { next ->
                            Text(stringResource(R.string.room_place_hint, next.name), Modifier.fillMaxWidth().border(1.5.dp, Outline, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 12.dp), color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                        Text(stringResource(R.string.room_open), color = Mint, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
