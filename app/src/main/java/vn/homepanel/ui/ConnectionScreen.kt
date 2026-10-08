package vn.homepanel.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import vn.homepanel.AppStore
import vn.homepanel.R
import vn.homepanel.auth.*
import vn.homepanel.discovery.*
import vn.homepanel.ha.ServerAddress

@Composable internal fun ConnectionScreen(store: AppStore) {
    val state by store.state.collectAsState()
    val login by store.login.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val finder = remember { DiscoveryController(AndroidDiscoveryBackend(context.applicationContext),scope) }
    val discovery by finder.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var url by remember { mutableStateOf(state.serverUrl) }
    var token by remember { mutableStateOf("") }
    var rememberConnection by remember { mutableStateOf(true) }
    var advanced by remember { mutableStateOf(state.serverUrl.isNotEmpty()) }
    var showDiscovery by remember { mutableStateOf(state.serverUrl.isEmpty()) }
    var manualToken by remember { mutableStateOf(false) }
    val normalized = remember(url) { runCatching { normalizeServerInput(url) }.getOrNull() }
    DisposableEffect(finder,lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if(event == Lifecycle.Event.ON_START && showDiscovery && store.state.value.serverUrl.isEmpty()) finder.start()
            if(event == Lifecycle.Event.ON_STOP) finder.stop()
        }
        lifecycle.addObserver(observer)
        // Only search when setup is really needed: a saved server is being restored, possibly from another network.
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && showDiscovery && store.state.value.serverUrl.isEmpty()) finder.start()
        onDispose { lifecycle.removeObserver(observer); finder.close() }
    }
    val step = if (advanced) 2 else 1
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 860.dp
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("connection-scroll"), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SetupSteps(step, listOf(stringResource(R.string.step_find), stringResource(R.string.step_sign_in), stringResource(R.string.step_rooms)))
        Column {
            Text(stringResource(if (step == 1) R.string.find_home else R.string.sign_in_title), fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp, letterSpacing = (-.5).sp)
            Text(stringResource(if (step == 1) R.string.auth_intro else R.string.auth_explanation), color = Muted, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 8.dp))
        }
        if(!showDiscovery && !advanced) TextButton(onClick={showDiscovery=true;finder.start()}) { Text(stringResource(R.string.auth_choose_server)) }
        if(showDiscovery) Panel(Modifier.fillMaxWidth(), padding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlyphTile(Glyph.WIFI, 36.dp)
                    Text(stringResource(R.string.ha_lan), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    if(discovery.scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Mint)
                }
                Text(stringResource(discovery.message), fontSize = 14.sp, color = Muted)
                if(discovery.total > 0) {
                    LinearProgressIndicator({ discovery.checked / discovery.total.toFloat() }, Modifier.fillMaxWidth().height(6.dp), color = Mint, trackColor = Line)
                    Text(stringResource(R.string.checked_addresses, discovery.checked, discovery.total), style = Mono, color = Muted)
                }
                discovery.servers.forEach { server ->
                    Row(Modifier.fillMaxWidth().background(Raised, Rounded).padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        GlyphTile(Glyph.SCREEN, 44.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(server.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text(server.address + " · " + stringResource(when(server.source) { "mDNS" -> R.string.source_mdns; "IP" -> R.string.source_ip; else -> R.string.source_hostname }) + " · HA ${server.version}", style = Mono, color = Muted)
                        }
                        Button(onClick = { store.login.cancel();url = server.address; token = ""; advanced = true;showDiscovery=false; finder.stop() }, shape = Rounded, modifier = Modifier.heightIn(min = 56.dp).testTag("server-${server.id}")) { Text(stringResource(R.string.use_server), fontWeight = FontWeight.SemiBold) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if(discovery.scanning) OutlinedButton(onClick = { finder.stop() }, shape = Rounded) { Text(stringResource(R.string.stop_search)) }
                    else OutlinedButton(onClick = { finder.start() }, shape = Rounded) { Text(stringResource(R.string.search_again)) }
                    OutlinedButton(onClick = { finder.start(expanded = true) }, enabled = !discovery.scanning, shape = Rounded) { Text(stringResource(R.string.scan_lan)) }
                }
                Text(stringResource(R.string.scan_lan_help),fontSize = 12.sp,color = Muted)
        }
        if(!advanced) TextButton(onClick = { advanced = true;showDiscovery=false; finder.stop() }) { Text(stringResource(R.string.manual_address), color = Mint) }
        if(advanced) {
            OutlinedTextField(url, { store.login.cancel();url = it; token = "" }, Modifier.fillMaxWidth().testTag("server-address"), label = { Text(stringResource(R.string.address_label)) }, placeholder = { Text(stringResource(R.string.address_example)) }, supportingText = { Text(if(normalized != null) stringResource(R.string.will_connect, normalized) else stringResource(R.string.address_help)) }, isError = url.isNotEmpty() && normalized == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true, shape = Rounded,
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Card, focusedContainerColor = Card, unfocusedBorderColor = Line, focusedBorderColor = Mint))
            if(normalized?.startsWith("http://") == true) Row(Modifier.fillMaxWidth().background(Amber.copy(alpha = .08f), Rounded).padding(horizontal = 18.dp, vertical = 14.dp)) { Text(stringResource(R.string.auth_http_notice),fontSize = 14.sp,color = Amber, lineHeight = 20.sp) }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(rememberConnection, { rememberConnection = it },enabled=!login.active); Text(stringResource(R.string.auth_remember),fontSize = 15.sp) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    finder.stop();token=""
                    val address=normalized?.let(ServerAddress::parse) ?: return@Button
                    val page=authReturnPage(context.getString(R.string.auth_callback_title),context.getString(R.string.auth_callback_body),context.getString(R.string.auth_callback_open),vn.homepanel.BuildConfig.AUTH_RETURN_SCHEME)
                    store.login.start(address,rememberConnection,page) { link -> context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                },enabled=normalized!=null && !login.active,shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 28.dp), modifier=Modifier.heightIn(min = 60.dp).testTag("browser-login")) { Text(stringResource(R.string.auth_login), fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                TextButton(onClick={manualToken=!manualToken;token="";store.login.cancel()},modifier=Modifier.testTag("manual-token")) { Text(stringResource(if(manualToken) R.string.auth_manual_hide else R.string.auth_manual), color = Muted) }
            }
            when(login.phase) {
                LoginPhase.EXCHANGING -> Text(stringResource(R.string.auth_exchanging))
                LoginPhase.COMPLETE -> if(!state.connected) Text(stringResource(R.string.auth_complete))
                LoginPhase.FAILED -> Text(stringResource(when(login.error) {
                    AuthError.CANCELLED -> R.string.auth_cancelled
                    AuthError.TIMEOUT -> R.string.auth_timeout
                    AuthError.BROWSER -> R.string.auth_browser_error
                    AuthError.EXPIRED -> R.string.auth_expired
                    AuthError.BAD_RESPONSE -> R.string.auth_bad_response
                    else -> R.string.auth_network_error
                }),color=MaterialTheme.colorScheme.error)
                else -> Unit
            }
            if(!wide && login.active) { Text(stringResource(R.string.auth_browser_waiting)); OutlinedButton(onClick={store.login.cancel()}, shape = Rounded) { Text(stringResource(R.string.auth_cancel)) } }
            if(manualToken) {
                OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth().testTag("server-token"), label = { Text("Access token") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, shape = Rounded)
                Text(stringResource(R.string.token_help),fontSize = 12.sp,color = Muted)
                OutlinedButton(onClick = { finder.stop(); store.connect(url,token,rememberConnection); token = "" },enabled = normalized != null && token.isNotBlank(), shape = Rounded, modifier = Modifier.testTag("connect-server")) { Text(stringResource(R.string.connect_ha)) }
            }
            TextButton(onClick = { store.login.cancel(); url = ""; advanced = false; showDiscovery = true; manualToken = false; finder.start() }) { Text(stringResource(R.string.change_server), color = Mint) }
        }
        // "Not connected" is already the pill; only progress such as connecting or retrying is worth repeating.
        if (state.status.isNotBlank() && state.status != stringResource(R.string.not_connected)) Text(state.status, color = Mint, fontSize = 14.sp)
        if (!wide) OutlinedButton(onClick = { finder.stop(); store.showDemo() }, shape = Rounded) { Text(stringResource(R.string.try_demo)) }
        if (state.serverUrl.isNotEmpty()) TextButton(onClick = { store.disconnect(forget = true); token = ""; url = ""; advanced = false;showDiscovery=true;manualToken=false;finder.start() }) { Text(stringResource(R.string.forget_connection), color = Muted) }
    }
            if (wide) Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (login.active) Panel(Modifier.fillMaxWidth(), padding = 20.dp) {
                    Eyebrow(stringResource(R.string.while_signing_in))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp, color = Mint, trackColor = Line)
                        Text(stringResource(R.string.auth_browser_waiting), fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    Text(stringResource(R.string.sign_in_steps), color = Color(0xFFC9D6DB), fontSize = 14.sp, lineHeight = 22.sp)
                    OutlinedButton(onClick={store.login.cancel()}, shape = Rounded, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.auth_cancel)) }
                } else Panel(Modifier.fillMaxWidth(), padding = 22.dp) {
                    Eyebrow(stringResource(R.string.just_looking), Amber)
                    Text(stringResource(R.string.demo_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
                    Text(stringResource(R.string.demo_pitch), color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
                    val demo = remember { vn.homepanel.ha.Demo.catalog(vn.homepanel.AppLanguage.language.value == "vi") }
                    demo.devices.take(4).chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { d -> val e = vn.homepanel.ha.primaryEntity(d); Column(Modifier.weight(1f).background(Raised, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) { Text(d.name, fontSize = 13.sp, maxLines = 1); Text(e?.let { displayState(it) } ?: "", fontSize = 13.sp, color = if (e?.state == "on") Mint else Muted, maxLines = 1) } }
                        }
                    }
                    OutlinedButton(onClick = { finder.stop(); store.showDemo() }, shape = Rounded, border = androidx.compose.foundation.BorderStroke(1.dp, Outline), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.try_demo), fontWeight = FontWeight.SemiBold, color = Cream) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
                    GlyphTile(Glyph.LOCK, 28.dp, Muted)
                    Text(stringResource(R.string.privacy_note), color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
    }
}
