package vn.homepanel.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vn.homepanel.Access
import vn.homepanel.R

@Composable fun OwnershipScreen(access: Access, onRetry: () -> Unit, onExit: () -> Unit) {
    HomeTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(48.dp), verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("HomePanel MR", style = MaterialTheme.typography.headlineLarge)
                if (access == Access.CHECKING) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.ownership_checking))
                } else {
                    Text(stringResource(R.string.ownership_failed), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.ownership_help))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = onRetry) { Text(stringResource(R.string.ownership_retry)) }
                        OutlinedButton(onClick = onExit) { Text(stringResource(R.string.ownership_exit)) }
                    }
                }
            }
        }
    }
}
