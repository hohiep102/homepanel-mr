package vn.homepanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import vn.homepanel.R
import vn.homepanel.AppStore
import vn.homepanel.AppLanguage
import vn.homepanel.ha.*
import kotlin.math.roundToInt

@Composable fun HomeScreen(store: AppStore, onEnterRoom: () -> Unit, inRoom: Boolean = false, onPlace: (() -> Unit)? = null, roomStatus: String = "", onScan: (() -> Unit)? = null, onExit: (() -> Unit)? = null, onExplore: (() -> Unit)? = null, suggestions: List<HaDevice> = emptyList(), onSuggest: (HaDevice) -> Unit = {}) {
    val state by store.state.collectAsState()
    var page by remember { mutableStateOf(R.string.rooms) }
    var search by remember { mutableStateOf("") }
    var roomFilter by remember(state.serverKey) { mutableStateOf<String?>(null) }
    var showDetail by remember { mutableStateOf(false) }
    var showHandHelp by remember { mutableStateOf(false) }
    var showLegalInfo by remember { mutableStateOf(false) }
    var fullControls by remember(state.selectedDevice) { mutableStateOf(false) }
    var showAllDevices by remember(state.serverKey) { mutableStateOf(false) }
    val displayCatalog = remember(state.catalog, showAllDevices) { deviceCatalogForDisplay(state.catalog, showAllDevices) }
    val selected = displayCatalog.devices.find { it.key == state.selectedDevice }
    // Until a server or the demo provides a home, only setup is meaningful; Placed appears once something is placed.
    val hasHome = state.demo || state.connected || state.catalog.devices.isNotEmpty()
    val tabs = listOfNotNull(R.string.rooms, R.string.devices, R.string.placed.takeIf { state.bindings.isNotEmpty() })
    val current = when { !hasHome -> R.string.connection; page == R.string.connection || page in tabs -> page; else -> R.string.rooms }
    fun changeVisibility(showAll: Boolean) {
        showAllDevices = showAll; fullControls = false; showDetail = false
        val next = deviceCatalogForDisplay(state.catalog, showAll)
        if (roomFilter != null && next.devices.none { it.belongsToRoom(roomFilter!!) }) roomFilter = null
        val choices = next.devices.filter { roomFilter == null || it.belongsToRoom(roomFilter!!) }
        val device = choices.find { it.key == state.selectedDevice } ?: choices.firstOrNull()
        device?.let {
            val entity = it.entities.find { e -> e.id == state.selectedEntity && (roomFilter == null || e.areaId.orEmpty() == roomFilter) }
                ?: primaryEntity(it, roomFilter)
            store.select(it.key,entity?.id)
        }
    }
    if (showLegalInfo) {
        HomeTheme { LegalInfo { showLegalInfo = false } }
        return
    }
    HomeTheme {
        // Drawn inside the panel: Spatial SDK panels cannot host a separate Dialog window.
        Box(Modifier.fillMaxSize()) {
        Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 32.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    BrandMark()
                    if (hasHome && current != R.string.connection) Segmented(tabs, current, { title -> if (title == R.string.placed) "${stringResource(title)} · ${state.bindings.size}" else stringResource(title) }, { page = it }, Modifier.padding(start = 14.dp))
                    Spacer(Modifier.weight(1f))
                    StatusPill(
                        if (state.demo) "DEMO" else if (state.connected) "LIVE" else stringResource(if (hasHome) R.string.offline_pill else R.string.setup_pill),
                        live = state.connected && !state.demo,
                        onClick = if (hasHome) ({ page = R.string.connection }) else null, description = stringResource(R.string.connection),
                    )
                    if (!inRoom) {
                        var languages by remember { mutableStateOf(false) }
                        val language by AppLanguage.language.collectAsState()
                        Box {
                            TextButton(onClick = { languages = true }) { Text(if (language == "en") "English ▾" else "Tiếng Việt ▾", color = Cream) }
                            DropdownMenu(languages, { languages = false }) {
                                listOf("en" to "English", "vi" to "Tiếng Việt").forEach { (tag,label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = { store.changeLanguage(tag); languages = false })
                                }
                            }
                        }
                    }
                    TextButton(onClick = { showHandHelp = true }) { Text(stringResource(R.string.hands_title), color = Cream) }
                    if (!inRoom) TextButton(onClick = { showLegalInfo = true }) { Text(stringResource(R.string.about), color = Cream) }
                }
                // In the room the exits stay even after the connection is forgotten.
                if (inRoom && (!hasHome || current == R.string.connection)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onExplore?.let { FilledTonalButton(onClick = it) { Text(stringResource(R.string.explore)) } }; onExit?.let { TextButton(onClick = it) { Text(stringResource(R.string.return_window)) } }
                }
                if (hasHome && current != R.string.connection) Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        // Only Rooms carries a heading; on Devices and Placed the selected tab already names the page.
                        if (current == R.string.rooms) Text(stringResource(greeting()), fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.4).sp)
                        val rooms = summarizeRooms(displayCatalog)
                        Text(if (current == R.string.rooms) stringResource(R.string.home_summary, rooms.size, displayCatalog.devices.count { d -> d.entities.any { it.state == "on" } }, state.bindings.size)
                            else stringResource(R.string.visible_device_count, displayCatalog.devices.size, state.catalog.devices.size), color = Muted, fontSize = 15.sp)
                        if (current == R.string.rooms) Text(stringResource(R.string.visible_device_count, displayCatalog.devices.size, state.catalog.devices.size), color = Muted, fontSize = 12.sp)
                    }
                    if (current != R.string.placed && state.catalog.devices.isNotEmpty()) Segmented(listOf(false, true), showAllDevices, { all -> stringResource(if (all) R.string.all_devices else R.string.everyday_devices) }, { changeVisibility(it) })
                    if (!inRoom) Button(onClick = onEnterRoom, enabled = state.connected, shape = Rounded, contentPadding = PaddingValues(horizontal = 22.dp), modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.enter_room), fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
                    else { onExplore?.let { Button(onClick = it, shape = Rounded, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.explore)) } }; onExit?.let { OutlinedButton(onClick = it, shape = Rounded, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.return_window)) } } }
                }
                if (hasHome && current != R.string.connection && (state.demo || !state.connected)) {
                    Surface(color = Amber.copy(alpha = .08f), shape = Rounded) {
                        Row(Modifier.padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (state.demo) stringResource(R.string.demo_banner) else stringResource(R.string.stale_data, state.status), Modifier.weight(1f), color = Amber, fontSize = 14.sp)
                            TextButton(onClick = { page = R.string.connection }) { Text(stringResource(if (state.demo) R.string.connect_ha else R.string.reconnect), color = Amber) }
                        }
                    }
                }
                if (inRoom && hasHome) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(roomStatus, modifier = Modifier.weight(1f), color = Muted, fontSize = 13.sp)
                        onScan?.let { OutlinedButton(onClick = it, shape = Rounded) { Text(stringResource(R.string.scan_room)) } }
                    }
                    if (suggestions.isNotEmpty() && current != R.string.connection) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.suggest_place), color = Mint, fontSize = 13.sp)
                        suggestions.take(3).forEach { device -> OutlinedButton(onClick = { onSuggest(device) }, shape = Rounded) { Text(device.name, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                    }
                }
                state.message?.let { message ->
                    Surface(color = Raised, shape = Rounded) {
                        Row(Modifier.padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(message, Modifier.weight(1f), fontSize = 14.sp)
                            TextButton(onClick = { store.notify(null) }) { Text(stringResource(R.string.close)) }
                        }
                    }
                }
                when (current) {
                    R.string.connection -> if (hasHome) ManageConnection(store, onBack = { page = R.string.rooms }, onHands = { showHandHelp = true }, onAbout = { showLegalInfo = true }) else ConnectionScreen(store)
                    R.string.rooms -> {
                        if (state.catalog.devices.isEmpty()) {
                            EmptyCard(stringResource(R.string.welcome_title), stringResource(R.string.welcome_body))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = { page = R.string.connection }) { Text(stringResource(R.string.connect_ha)) }
                                OutlinedButton(onClick = { store.showDemo() }) { Text(stringResource(R.string.explore_demo)) }
                            }
                        } else if (displayCatalog.devices.isEmpty()) {
                            EmptyCard(stringResource(R.string.no_everyday_devices), stringResource(R.string.advanced_devices_help))
                        } else {
                            HomeOverviewStrip(remember(state.catalog) { homeOverview(state.catalog) })
                            RoomOverview(displayCatalog, state.connected, state.bindings.map { it.deviceKey }.toSet()) { name ->
                            roomFilter = name; search = ""; showDetail = false; fullControls = false; page = R.string.devices
                            displayCatalog.devices.firstOrNull { it.belongsToRoom(name) }?.let { store.select(it.key, primaryEntity(it,name)?.id) }
                        }
                        }
                    }
                    R.string.placed -> {
                        // Known only while a room is open: placements the current scan cannot find come first, ready to place again.
                        val resolvedIds by store.resolvedPlacements.collectAsState()
                        val ordered = state.bindings.sortedBy { resolvedIds?.contains(it.id) != false }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(ordered, key = { it.id }) { b ->
                                val missing = resolvedIds?.contains(b.id) == false
                                Surface(shape = RoundedCornerShape(18.dp), color = Card, border = androidx.compose.foundation.BorderStroke(1.dp, if (missing) Amber.copy(alpha = .5f) else Line)) {
                                    Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        GlyphTile(Glyph.PIN, 44.dp, if (missing) Amber else Mint)
                                        Column(Modifier.weight(1f)) { Text(b.label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp); state.catalog.entities[b.entityId]?.name?.takeIf { it != b.label }?.let { Text(it, color = Muted, fontSize = 13.sp) }; Text(stringResource(if (missing) R.string.placement_missing else R.string.room_saved), color = if (missing) Amber else Muted, fontSize = 13.sp) }
                                        TextButton(onClick = { store.select(b.deviceKey, b.entityId); (onPlace ?: onEnterRoom)() }, enabled = state.connected) { Text(stringResource(R.string.place_again)) }
                                        TextButton(onClick = {
                                            if (deviceCatalogForDisplay(state.catalog).devices.none { d -> d.key == b.deviceKey && d.entities.any { it.id == b.entityId } }) showAllDevices = true
                                            store.select(b.deviceKey, b.entityId); page = R.string.devices; showDetail = true; fullControls = false; roomFilter = null; search = ""
                                        }) { Text(stringResource(R.string.open)) }
                                        TextButton(onClick = { store.deleteBinding(b.id) }) { Text(stringResource(R.string.unplace)) }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        if (state.catalog.devices.isEmpty()) {
                            EmptyCard(stringResource(R.string.welcome_title), stringResource(R.string.welcome_body))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = { page = R.string.connection }) { Text(stringResource(R.string.connect_ha)) }
                                OutlinedButton(onClick = { store.showDemo() }) { Text(stringResource(R.string.explore_demo)) }
                            }
                        } else {
                            state.catalog.warnings.forEach { Text(stringResource(it), color = Amber, fontSize = 12.sp) }
                            BoxWithConstraints(Modifier.weight(1f)) {
                                val wide = maxWidth >= 720.dp
                                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    if (wide || !showDetail) {
                                        Column(Modifier.weight(if (wide) 0.48f else 1f)) {
                                            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.search_devices)) }, singleLine = true, shape = Rounded,
                                                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Card, focusedContainerColor = Card, unfocusedBorderColor = Line, focusedBorderColor = Mint))
                                            val filtered = displayCatalog.devices.filter { (roomFilter == null || it.belongsToRoom(roomFilter!!)) && ("${it.name} ${it.area} ${it.entities.joinToString { e -> e.name + e.id }}").contains(search, true) }
                                            val rooms = listOf<String?>(null) + summarizeRooms(displayCatalog).map { it.id }
                                            InlinePicker(stringResource(R.string.device_count, roomFilter?.let { state.catalog.areas[it] ?: it.ifBlank { stringResource(R.string.no_area) } } ?: stringResource(R.string.all_rooms), filtered.size),
                                                rooms, { name -> name?.let { state.catalog.areas[it] ?: it.ifBlank { stringResource(R.string.no_area) } } ?: stringResource(R.string.all_rooms) }, { name ->
                                                    roomFilter = name; fullControls = false; showDetail = false
                                                    displayCatalog.devices.firstOrNull { name == null || it.belongsToRoom(name) }?.let { store.select(it.key, primaryEntity(it,name)?.id) }
                                                }, Modifier.padding(vertical = 6.dp), selected = roomFilter)
                                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                items(filtered, key = { it.key }) { device ->
                                                    DeviceCard(device, state.selectedDevice == device.key, state.selectedEntity, state.bindings.filter { it.deviceKey == device.key }.map { it.entityId }.toSet(),
                                                        onClick = { store.select(device.key,primaryEntity(device,roomFilter)?.id); showDetail = true; fullControls = false },
                                                        onChannel = { entity -> store.select(device.key,entity.id); showDetail = true; fullControls = false })
                                                }
                                                if (filtered.isEmpty()) item { Text(stringResource(if(showAllDevices) R.string.no_devices else R.string.no_everyday_match), color = Muted, modifier = Modifier.padding(16.dp)) }
                                            }
                                        }
                                    }
                                    if (wide || showDetail) {
                                        Column(Modifier.weight(if (wide) 0.52f else 1f).verticalScroll(rememberScrollState())) {
                                            if (!wide) TextButton(onClick = { showDetail = false }) { Text(stringResource(R.string.back_devices)) }
                                            if (selected != null) {
                                                if (fullControls) {
                                                    TextButton(onClick = { fullControls = false }) { Text(stringResource(R.string.quick_controls)) }
                                                    DeviceDetail(store, state.catalog.devices.find { it.key == selected.key } ?: selected, onPlace = onPlace ?: onEnterRoom, placementLabel = if (inRoom) stringResource(R.string.place_object) else stringResource(R.string.place_room))
                                                } else Panel(Modifier.fillMaxWidth(), padding = 26.dp) { QuickControls(store, selected, onDetails = { fullControls = true }, onPlace = onPlace ?: onEnterRoom, placementLabel = if (inRoom) stringResource(R.string.place_object) else stringResource(R.string.place_room)) }
                                            } else EmptyCard(stringResource(R.string.select_device), stringResource(R.string.select_device_body))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showHandHelp) HandHelp { showHandHelp = false }
        }
    }
}

@Composable private fun HandHelp(onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xC704090B)).clickable(onClick = onDismiss).testTag("hand-help"), contentAlignment = Alignment.Center) {
        Surface(color = Card, shape = RoundedCornerShape(30.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Outline), modifier = Modifier.widthIn(max = 820.dp).padding(24.dp).clickable(enabled = false) {}) {
            Column(Modifier.padding(32.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Text(stringResource(R.string.hands_title), fontWeight = FontWeight.Bold, fontSize = 30.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    listOf(Triple(Glyph.HAND, R.string.hands_step_select_title, R.string.hands_step_select_body), Triple(Glyph.EYE, R.string.hands_step_look_title, R.string.hands_step_look_body), Triple(Glyph.PIN, R.string.hands_step_place_title, R.string.hands_step_place_body)).forEach { (glyph, title, body) ->
                        Column(Modifier.weight(1f).background(Raised, Corners).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlyphTile(glyph, 52.dp)
                            Text(stringResource(title), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                            Text(stringResource(body), color = Color(0xFFC9D6DB), fontSize = 14.sp, lineHeight = 21.sp)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.hands_footer), Modifier.weight(1f), color = Muted, fontSize = 14.sp)
                    Button(onClick = onDismiss, shape = Rounded, contentPadding = PaddingValues(horizontal = 30.dp), modifier = Modifier.heightIn(min = 60.dp)) { Text(stringResource(R.string.hands_understood), fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable private fun DeviceCard(device: HaDevice, selected: Boolean, selectedEntity: String?, placedEntities: Set<String>, onClick: () -> Unit, onChannel: (HaEntity) -> Unit) {
    val primary = primaryEntity(device)
    val on = primary?.state in setOf("on", "open", "cool", "heat", "auto", "heat_cool", "dry", "fan_only", "playing")
    val available = device.entities.any { it.available }
    Surface(shape = RoundedCornerShape(18.dp), color = if (selected) Color(0xFF16262C) else Card, modifier = Modifier.fillMaxWidth().border(if (selected) 1.5.dp else 1.dp, if (selected) Mint else Line, RoundedCornerShape(18.dp)).clickable(onClick = onClick)) {
        Column {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).background(if (on) Mint.copy(alpha = .14f) else Raised, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text(symbol(primary?.domain), fontSize = 22.sp, color = if (on) Mint else Muted) }
            Column(Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(device.area.ifBlank { stringResource(R.string.no_area) }, color = Muted, fontSize = 12.sp)
                Text(if (available) stringResource(R.string.function_count, device.entities.size) + if (placedEntities.isNotEmpty()) stringResource(R.string.bound_suffix) else "" else stringResource(R.string.unavailable), color = if (available) Muted else Amber, fontSize = 13.sp)
            }
            Text(if (!available) "—" else if (on) "ON" else "OFF", style = Mono, color = if (on) Mint else Muted)
        }
        val channels = placementChannels(device)
        if (channels.size > 1) channels.forEach { channel ->
            val active = selected && selectedEntity == channel.id
            Surface(color = if(active) Color(0xFF1F3A32) else Color.Transparent, modifier = Modifier.fillMaxWidth().testTag("device-channel:${channel.id}").clickable { onChannel(channel) }) {
                Row(Modifier.padding(horizontal=18.dp,vertical=12.dp).heightIn(min=32.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(if(active) "●" else "○",color=Mint)
                    Column(Modifier.weight(1f)) {
                        Text(channel.name,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text((if(channel.available) displayState(channel) else stringResource(R.string.unavailable)) + if(channel.id in placedEntities) stringResource(R.string.bound_suffix) else "",fontSize=12.sp,color=Muted)
                    }
                }
            }
        }
        }
    }
}
private fun symbol(domain: String?) = when(domain) { "light" -> "☀"; "climate" -> "❄"; "fan" -> "✣"; "cover" -> "▤"; "sensor" -> "◉"; "camera" -> "◎"; else -> "◈" }
private fun greeting(): Int = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) { in 5..11 -> R.string.greeting_morning; in 12..17 -> R.string.greeting_afternoon; else -> R.string.greeting_evening }
@Composable private fun EmptyCard(title: String, description: String) {
    Panel(Modifier.fillMaxWidth(), padding = 26.dp) { Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text(description, color = Muted, lineHeight = 23.sp, fontSize = 15.sp) }
}

@Composable fun DeviceDetail(store: AppStore, device: HaDevice, onPlace: (() -> Unit)? = null, placementLabel: String? = null) {
    val state by store.state.collectAsState()
    val entity = device.entities.find { it.id == state.selectedEntity } ?: device.entities.firstOrNull()
    Surface(color = Card, shape = Corners, border = androidx.compose.foundation.BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(device.area.ifBlank { stringResource(R.string.no_area) }.uppercase(), color = Mint, fontSize = 11.sp, letterSpacing = 2.sp); Text(device.name, fontSize = 25.sp, fontWeight = FontWeight.Bold) }
                Text(symbol(entity?.domain), color = Mint, fontSize = 40.sp)
            }
            if (device.entities.size > 1) {
                InlinePicker(entity?.name ?: stringResource(R.string.select_function), device.entities, { it.name }, { store.select(device.key, it.id) }, Modifier.fillMaxWidth(), selected = entity)
            }
            if (entity != null) {
                val enabled = state.connected && entity.available && entity.id !in state.busy
                Text(if (entity.disabled) stringResource(R.string.disabled) else if (!entity.available) stringResource(R.string.unavailable) else displayState(entity), color = if (entity.available) Cream else Amber, fontSize = 20.sp)
                if (entity.domain == "camera") CameraView(store, entity) else key(entity.id) { EntityControls(entity, state.catalog, enabled) { store.control(entity.id, it) } }
                if (entity.id in state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                HorizontalDivider(color = Line)
                if (onPlace != null) Button(onClick = onPlace, enabled = state.connected && !entity.disabled, modifier = Modifier.fillMaxWidth()) { Text(placementLabel ?: stringResource(R.string.place_object)) }
                Text(if (state.bindings.any { it.entityId == entity.id }) stringResource(R.string.replace_placement) else stringResource(R.string.placement_intro), color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            } else Text(stringResource(R.string.no_entities), color = Muted)
        }
    }
}
@Composable internal fun displayState(e: HaEntity): String = when(e.state) { "on" -> stringResource(R.string.state_on); "off" -> stringResource(R.string.state_off); "open" -> stringResource(R.string.state_open); "closed" -> stringResource(R.string.state_closed); "cool" -> stringResource(R.string.mode_cool); "heat" -> stringResource(R.string.mode_heat); "auto" -> stringResource(R.string.mode_auto); else -> e.state + (e.attributes.nullString("unit_of_measurement")?.let { " $it" } ?: "") }

@Composable internal fun EntityControls(e: HaEntity, catalog: Catalog, enabled: Boolean, compact: Boolean = false, heroShown: Boolean = false, send: (Control) -> Unit) {
    val a = e.attributes
    var anyControl = false
    fun has(service: String) = catalog.supports(e.domain, service) && (e.domain != "climate" || service !in setOf("turn_on", "turn_off") || e.supports(if(service == "turn_on") 256 else 128))
    if (e.domain in setOf("light", "switch", "input_boolean", "fan", "climate") && (has("turn_on") || has("turn_off"))) {
        anyControl = true
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (has("turn_on")) Button(onClick = { send(Control.Power(true)) }, enabled = enabled, shape = Rounded, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(stringResource(R.string.power_on), fontWeight = FontWeight.SemiBold) }
            if (has("turn_off")) OutlinedButton(onClick = { send(Control.Power(false)) }, enabled = enabled, shape = Rounded, border = androidx.compose.foundation.BorderStroke(1.dp, Outline), modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(stringResource(R.string.power_off), color = if (enabled) Cream else Muted) }
        }
    }
    if (!heroShown && e.domain == "light" && a.strings("supported_color_modes").any { it !in setOf("onoff", "unknown") } && has("turn_on")) {
        anyControl = true
        ValueControl(stringResource(R.string.brightness), brightnessPercent(e), 0f..100f, enabled, suffix = "%", step = if(compact) 10f else 1f, showSlider = !compact) { send(Control.Brightness(it.roundToInt())) }
    }
    if (e.domain == "climate") {
        if (a.has("current_temperature") && !a.isNull("current_temperature")) Text(stringResource(R.string.room_temperature, a.optDouble("current_temperature").toString(), a.nullString("temperature_unit") ?: catalog.temperatureUnit), color = Muted)
        if (!heroShown && e.supports(1) && has("set_temperature")) {
            anyControl = true
            val min = a.optDouble("min_temp", 7.0).toFloat(); val max = a.optDouble("max_temp", 35.0).toFloat()
            val value = a.optDouble("temperature", Double.NaN).toFloat()
            val step = a.optDouble("target_temp_step", 1.0).toFloat().takeIf { it.isFinite() && it > 0 } ?: 1f
            if (min.isFinite() && max.isFinite() && max > min) {
                if (!value.isFinite()) Text(stringResource(R.string.no_target_temperature), color = Muted, fontSize = 12.sp)
                ValueControl(stringResource(R.string.target_temperature), if (value.isFinite()) value.coerceIn(min,max) else min, min..max, enabled, suffix = (a.nullString("temperature_unit") ?: catalog.temperatureUnit), step = step, showSlider = !compact) { send(Control.Temperature(it.toDouble())) }
            }
        }
        if (has("set_hvac_mode") && a.strings("hvac_modes").isNotEmpty()) {
            anyControl = true
            if (compact) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("off", e.state, "cool", "heat", "auto").distinct().filter { it in a.strings("hvac_modes") }.take(3).forEach { mode ->
                    if (mode == e.state) Button(onClick = {}, enabled = enabled, shape = Rounded, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(displayState(e.copy(state = mode)), fontWeight = FontWeight.SemiBold) }
                    else OutlinedButton(onClick = { send(Control.Mode(mode)) }, enabled = enabled, shape = Rounded, border = androidx.compose.foundation.BorderStroke(1.dp, Outline), modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(displayState(e.copy(state = mode)), color = Cream) }
                }
            } else InlinePicker(stringResource(R.string.hvac_mode, displayState(e)), a.strings("hvac_modes"), { mode -> displayState(e.copy(state = mode)) }, { mode -> send(Control.Mode(mode)) }, enabled = enabled, selected = e.state)
        }
    }
    if (!heroShown && e.domain == "fan" && e.supports(1) && has("set_percentage")) {
        anyControl = true
        ValueControl(stringResource(R.string.speed), a.optInt("percentage", 0).toFloat().coerceIn(0f,100f), 0f..100f, enabled, suffix = "%", step = a.optDouble("percentage_step",1.0).toFloat().takeIf { it.isFinite() && it > 0 } ?: 1f, showSlider = !compact) { send(Control.FanSpeed(it.roundToInt())) }
    }
    if (e.domain == "cover") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple(stringResource(R.string.open), "open_cover", 1), Triple(stringResource(R.string.stop), "stop_cover", 8), Triple(stringResource(R.string.close), "close_cover", 2)).forEach { (label, command, flag) ->
                if (e.supports(flag) && has(command)) { anyControl = true; OutlinedButton(onClick = { send(Control.Cover(command)) }, enabled = enabled) { Text(label) } }
            }
        }
        if (e.supports(4) && has("set_cover_position")) { anyControl = true; ValueControl(stringResource(R.string.cover_position), a.optInt("current_position",0).toFloat().coerceIn(0f,100f),0f..100f, enabled,suffix = "%", step = if(compact) 10f else 1f, showSlider = !compact) { send(Control.CoverPosition(it.roundToInt())) } }
    }
    if (!anyControl) Text(stringResource(R.string.read_only), color = Muted, fontSize = 13.sp)
}
