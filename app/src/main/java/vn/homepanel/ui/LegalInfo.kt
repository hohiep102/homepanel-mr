package vn.homepanel.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import vn.homepanel.BuildConfig
import vn.homepanel.R

@Composable fun LegalInfo(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    var notices by remember { mutableStateOf(false) }
    val licenses = remember { context.assets.open("legal/THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() } }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (notices) stringResource(R.string.third_party_notices) else "${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME}",
                    Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                FilledTonalButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (notices) Text(licenses)
                else {
                    TextButton(onClick = { uri.openUri("https://homepanel-mr.pages.dev/privacy/") }) { Text(stringResource(R.string.privacy_policy)) }
                    TextButton(onClick = { uri.openUri("https://homepanel-mr.pages.dev/support/") }) { Text(stringResource(R.string.support)) }
                    TextButton(onClick = { notices = true }) { Text(stringResource(R.string.third_party_notices)) }
                    Text(stringResource(R.string.about_compatibility))
                }
            }
        }
    }
}
