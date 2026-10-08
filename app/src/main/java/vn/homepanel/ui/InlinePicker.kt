package vn.homepanel.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Expands its choices in place. Spatial SDK panels cannot host the separate window a DropdownMenu
 * or Dialog creates, so every picker reachable from a room panel uses this instead.
 */
@Composable fun <T> InlinePicker(
    label: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: T? = null,
    buttonTag: String? = null,
    optionTag: ((T) -> String)? = null,
) {
    var open by remember(options) { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = { open = !open }, enabled = enabled, modifier = Modifier.fillMaxWidth().let { m -> buttonTag?.let { m.testTag(it) } ?: m }) {
            Text("$label ${if (open) "▴" else "▾"}", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (open) Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(4.dp)) {
                options.forEach { option ->
                    val current = option == selected
                    TextButton(
                        onClick = { open = false; onPick(option) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).let { m -> optionTag?.let { m.testTag(it(option)) } ?: m },
                    ) { Text((if (current) "✓ " else "") + optionLabel(option), Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}
