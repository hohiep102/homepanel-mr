package vn.homepanel.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.homepanel.AppLanguage
import vn.homepanel.AppStore
import vn.homepanel.BuildConfig
import vn.homepanel.R

/** Opened from the status pill once a home exists: what the app is connected to, and the few settings it has. */
@Composable fun ManageConnection(store: AppStore, onBack: () -> Unit, onHands: () -> Unit, onAbout: () -> Unit) {
    val state by store.state.collectAsState()
    val language by AppLanguage.language.collectAsState()
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back_home), color = Cream) }
        Text(stringResource(R.string.manage_title), fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Panel(Modifier.weight(1f), padding = 24.dp) {
                Eyebrow("Home Assistant")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    GlyphTile(Glyph.SCREEN, 52.dp)
                    Column {
                        Text(if (state.demo) stringResource(R.string.demo_title) else stringResource(R.string.ha_server), fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                        if (!state.demo) Text(state.serverUrl, style = Mono, color = Muted)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Detail(stringResource(R.string.detail_status), state.status, if (state.connected && !state.demo) Mint else Amber)
                    if (!state.demo) {
                        Detail(stringResource(R.string.detail_session), stringResource(R.string.detail_session_value))
                        Detail(stringResource(R.string.detail_storage), stringResource(R.string.detail_storage_value))
                    }
                    Detail(stringResource(R.string.devices), stringResource(R.string.visible_device_count, vn.homepanel.ha.deviceCatalogForDisplay(state.catalog).devices.size, state.catalog.devices.size))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { store.disconnect() }, shape = Rounded, colors = ButtonDefaults.buttonColors(containerColor = Line, contentColor = Cream), modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Text(stringResource(if (state.demo) R.string.connect_ha else R.string.switch_server), fontWeight = FontWeight.SemiBold)
                    }
                    if (!state.demo) OutlinedButton(onClick = { store.disconnect(forget = true) }, shape = Rounded, border = BorderStroke(1.dp, Danger.copy(alpha = .45f)), modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.forget_connection), color = Danger) }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Panel(Modifier.fillMaxWidth(), padding = 24.dp) {
                    Eyebrow(stringResource(R.string.language_label))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("en" to "English", "vi" to "Tiếng Việt").forEach { (tag, label) ->
                            if (language == tag) Button(onClick = {}, shape = Rounded, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(label, fontWeight = FontWeight.SemiBold) }
                            else OutlinedButton(onClick = { store.changeLanguage(tag) }, shape = Rounded, border = BorderStroke(1.dp, Outline), modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(label, color = Cream) }
                        }
                    }
                }
                Panel(Modifier.fillMaxWidth(), padding = 8.dp) {
                    Link(stringResource(R.string.hands_title), onHands)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Line)
                    Link(stringResource(R.string.about_links), onAbout)
                }
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), color = Muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}

@Composable private fun Detail(label: String, value: String, color: androidx.compose.ui.graphics.Color = Cream) {
    Row { Text(label, Modifier.width(150.dp), color = Muted, fontSize = 15.sp); Text(value, color = color, fontSize = 15.sp) }
}

@Composable private fun Link(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 60.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp); Text("›", color = Muted, fontSize = 20.sp)
    }
}
