package vn.homepanel.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("connection-scroll"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.find_home), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.auth_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if(!showDiscovery) TextButton(onClick={showDiscovery=true;finder.start()}) { Text(stringResource(R.string.auth_choose_server)) }
        if(showDiscovery) Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ha_lan), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    if(discovery.scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                Text(stringResource(discovery.message), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if(discovery.total > 0) Text(stringResource(R.string.checked_addresses, discovery.checked, discovery.total),fontSize = 12.sp)
                discovery.servers.forEach { server ->
                    OutlinedButton(onClick = { store.login.cancel();url = server.address; token = ""; advanced = true;showDiscovery=false; finder.stop() }, modifier = Modifier.fillMaxWidth().testTag("server-${server.id}"), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp),verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(server.name,fontWeight = FontWeight.Bold)
                            Text(server.address,fontSize = 12.sp)
                            Text(stringResource(when(server.source) { "mDNS" -> R.string.source_mdns; "IP" -> R.string.source_ip; else -> R.string.source_hostname }) + " · HA ${server.version}",fontSize = 11.sp)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if(discovery.scanning) OutlinedButton(onClick = { finder.stop() }) { Text(stringResource(R.string.stop_search)) }
                    else Button(onClick = { finder.start() }) { Text(stringResource(R.string.search_again)) }
                    OutlinedButton(onClick = { finder.start(expanded = true) }, enabled = !discovery.scanning) { Text(stringResource(R.string.scan_lan)) }
                }
                Text(stringResource(R.string.scan_lan_help),fontSize = 12.sp,color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if(!advanced) TextButton(onClick = { advanced = true;showDiscovery=false; finder.stop() }) { Text(stringResource(R.string.manual_address)) }
        if(advanced) {
            OutlinedTextField(url, { store.login.cancel();url = it; token = "" }, Modifier.fillMaxWidth().testTag("server-address"), label = { Text(stringResource(R.string.address_label)) }, placeholder = { Text(stringResource(R.string.address_example)) }, supportingText = { Text(if(normalized != null) stringResource(R.string.will_connect, normalized) else stringResource(R.string.address_help)) }, isError = url.isNotEmpty() && normalized == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true)
            Text(stringResource(R.string.auth_explanation),color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(normalized?.startsWith("http://") == true) Text(stringResource(R.string.auth_http_notice),fontSize = 12.sp,color = MaterialTheme.colorScheme.secondary)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(rememberConnection, { rememberConnection = it },enabled=!login.active); Text(stringResource(R.string.auth_remember),fontSize = 13.sp) }
            Button(onClick = {
                finder.stop();token=""
                val address=normalized?.let(ServerAddress::parse) ?: return@Button
                val page=authReturnPage(context.getString(R.string.auth_callback_title),context.getString(R.string.auth_callback_body),context.getString(R.string.auth_callback_open),vn.homepanel.BuildConfig.AUTH_RETURN_SCHEME)
                store.login.start(address,rememberConnection,page) { link -> context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },enabled=normalized!=null && !login.active,modifier=Modifier.testTag("browser-login")) { Text(stringResource(R.string.auth_login)) }
            when(login.phase) {
                LoginPhase.WAITING -> Text(stringResource(R.string.auth_browser_waiting))
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
            if(login.active) OutlinedButton(onClick={store.login.cancel()}) { Text(stringResource(R.string.auth_cancel)) }
            TextButton(onClick={manualToken=!manualToken;token="";store.login.cancel()},modifier=Modifier.testTag("manual-token")) { Text(stringResource(if(manualToken) R.string.auth_manual_hide else R.string.auth_manual)) }
            if(manualToken) {
                OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth().testTag("server-token"), label = { Text("Access token") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
                Text(stringResource(R.string.token_help),fontSize = 12.sp,color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { finder.stop(); store.connect(url,token,rememberConnection); token = "" },enabled = normalized != null && token.isNotBlank(),modifier = Modifier.testTag("connect-server")) { Text(stringResource(R.string.connect_ha)) }
            }
        }
        Text(state.status, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { finder.stop(); store.showDemo() }) { Text(stringResource(R.string.try_demo)) }
            TextButton(onClick = { store.disconnect(forget = true); token = ""; url = ""; advanced = false;showDiscovery=true;manualToken=false;finder.start() }) { Text(stringResource(R.string.forget_connection)) }
        }
    }
}
