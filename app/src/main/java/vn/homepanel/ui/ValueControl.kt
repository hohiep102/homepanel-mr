package vn.homepanel.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.homepanel.R
import kotlin.math.roundToInt

/** Single taps support precise adjustments without requiring a sustained hand drag. */
@Composable fun ValueControl(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    suffix: String,
    step: Float = 1f,
    showSlider: Boolean = true,
    onValue: (Float) -> Unit,
) {
    var draft by remember(value, range.start, range.endInclusive) { mutableFloatStateOf(value.coerceIn(range.start, range.endInclusive)) }
    fun snapped(raw: Float) = (range.start + ((raw - range.start) / step).roundToInt() * step).coerceIn(range.start, range.endInclusive)
    val locale = LocalConfiguration.current.locales[0]
    val decrease = stringResource(R.string.value_decrease, label)
    val increase = stringResource(R.string.value_increase, label)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showSlider) Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!showSlider) Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
                onClick = { draft = snapped(draft - step); onValue(draft) },
                enabled = enabled && draft > range.start,
                modifier = Modifier.sizeIn(minWidth = 64.dp, minHeight = 56.dp).semantics { contentDescription = decrease },
            ) { Text("−", fontSize = 24.sp) }
            Text("${if (draft % 1f == 0f) draft.toInt().toString() else "%.1f".format(locale,draft)} $suffix", if(showSlider) Modifier.weight(1f) else Modifier.width(72.dp), color = MaterialTheme.colorScheme.primary)
            OutlinedButton(
                onClick = { draft = snapped(draft + step); onValue(draft) },
                enabled = enabled && draft < range.endInclusive,
                modifier = Modifier.sizeIn(minWidth = 64.dp, minHeight = 56.dp).semantics { contentDescription = increase },
            ) { Text("+", fontSize = 24.sp) }
        }
        if (showSlider) Slider(
            value = draft, onValueChange = { draft = snapped(it) }, valueRange = range,
            enabled = enabled, onValueChangeFinished = { onValue(draft) },
            modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = label },
        )
    }
}
