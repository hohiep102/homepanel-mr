package vn.homepanel.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.homepanel.R
import vn.homepanel.ha.HomeOverview

/** Security, openings, alerts and climate at a glance; a tile only appears when the home has that kind of device. */
@Composable fun HomeOverviewStrip(o: HomeOverview, modifier: Modifier = Modifier) {
    val tiles = buildList<@Composable RowScope.() -> Unit> {
        if (o.hasSecurity) add {
            val alarm = o.alarm?.state
            val value = when {
                alarm == "triggered" -> stringResource(R.string.overview_alarm_triggered)
                alarm != null && alarm.startsWith("armed") -> stringResource(R.string.overview_armed)
                alarm in setOf("arming", "pending") -> stringResource(R.string.overview_arming)
                alarm == "disarmed" -> stringResource(R.string.overview_disarmed)
                o.secure -> stringResource(R.string.overview_secure)
                else -> stringResource(R.string.overview_attention)
            }
            val detail = if (o.locks > 0) stringResource(R.string.overview_locks, o.locks, o.unlocked.size) else stringResource(R.string.overview_no_locks)
            Tile(stringResource(R.string.overview_security), value, detail, if (alarm == "triggered") Danger else if (o.secure) Mint else Amber)
        }
        if (o.openings > 0) add {
            Tile(stringResource(R.string.overview_doors), if (o.open.isEmpty()) stringResource(R.string.overview_all_closed) else stringResource(R.string.overview_open_count, o.open.size),
                o.open.joinToString(", ") { it.name }.ifBlank { stringResource(R.string.overview_openings, o.openings) }, if (o.open.isEmpty()) Mint else Amber)
        }
        add {
            Tile(stringResource(R.string.overview_alerts), if (o.alerts.isEmpty()) stringResource(R.string.overview_no_alerts) else o.alerts.joinToString(", ") { it.name },
                stringResource(R.string.overview_motion, o.motion.size), if (o.alerts.isEmpty()) Mint else Danger)
        }
        if (o.temperatures.isNotEmpty()) add {
            val low = o.temperatures.first(); val high = o.temperatures.last()
            val value = if (high - low < .5f) "%.1f%s".format((low + high) / 2, o.temperatureUnit) else "%.0f–%.0f%s".format(low, high, o.temperatureUnit)
            Tile(stringResource(R.string.overview_climate), value, o.humidity?.let { stringResource(R.string.overview_humidity, it.toInt()) } ?: stringResource(R.string.overview_sensors, o.temperatures.size), Cream)
        }
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { tiles.forEach { it() } }
}

@Composable private fun RowScope.Tile(title: String, value: String, detail: String, tone: Color) {
    Surface(color = Card, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, if (tone == Mint || tone == Cream) Line else tone.copy(alpha = .55f)), modifier = Modifier.weight(1f)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title.uppercase(), color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
            Text(value, color = tone, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
