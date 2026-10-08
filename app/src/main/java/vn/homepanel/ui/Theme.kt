package vn.homepanel.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Night console palette from the HomePanel MR redesign: dark ink ground, mint for live/primary, amber for attention.
internal val Ink = Color(0xFF0A1216)
internal val Card = Color(0xFF121E24)
internal val Raised = Color(0xFF1A2930)
internal val Line = Color(0xFF22343C)
internal val Outline = Color(0xFF2E434C)
internal val Mint = Color(0xFF8FE3C0)
internal val MintInk = Color(0xFF06281C)
internal val Muted = Color(0xFF9BB0B8)
internal val Cream = Color(0xFFEEF3EF)
internal val Amber = Color(0xFFEDBE7C)
internal val Danger = Color(0xFFF4A897)
internal val Corners = RoundedCornerShape(22.dp)
internal val Rounded = RoundedCornerShape(16.dp)
internal val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HomeTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 56.dp) {
        MaterialTheme(
            colorScheme = darkColorScheme(primary = Mint, onPrimary = MintInk, background = Ink, surface = Card, onSurface = Cream, surfaceVariant = Raised, onSurfaceVariant = Muted, secondary = Amber, outline = Outline, error = Danger),
            shapes = Shapes(small = RoundedCornerShape(12.dp), medium = Rounded, large = Corners),
            content = content,
        )
    }
}

/** House mark and wordmark. */
@Composable fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).background(Mint, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(22.dp)) {
                val w = size.width; val h = size.height; val s = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
                fun l(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(MintInk, Offset(w * x1, h * y1), Offset(w * x2, h * y2), s.width, StrokeCap.Round)
                l(.12f, .46f, .5f, .16f); l(.5f, .16f, .88f, .46f); l(.2f, .42f, .2f, .84f); l(.8f, .42f, .8f, .84f); l(.2f, .84f, .8f, .84f); l(.4f, .84f, .4f, .58f); l(.6f, .84f, .6f, .58f); l(.4f, .58f, .6f, .58f)
            }
        }
        Text("HomePanel", fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = .5.sp)
    }
}

/** Connection status; a pill that opens connection details when it has somewhere to go. */
@Composable fun StatusPill(text: String, live: Boolean, onClick: (() -> Unit)? = null, description: String? = null) {
    val color = if (live) Mint else Amber
    val shape = RoundedCornerShape(999.dp)
    val base = Modifier.clip(shape).background(color.copy(alpha = .10f))
    Row(
        (if (onClick != null) base.clickable(onClickLabel = description, role = Role.Button, onClick = onClick) else base).heightIn(min = 40.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(text, style = Mono, color = color)
    }
}

/** Segmented control used for the section tabs and two-way filters. */
@Composable fun <T> Segmented(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.background(Card, Rounded).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            val active = option == selected
            val text = label(option)
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(if (active) Line else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(option) }.semantics { this.selected = active }
                    .heightIn(min = 46.dp).padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text(text, color = if (active) Cream else Muted, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, fontSize = 15.sp) }
        }
    }
}

/** A card surface with the redesign's border and radius. */
@Composable fun Panel(modifier: Modifier = Modifier, padding: Dp = 22.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Card, shape = Corners, border = BorderStroke(1.dp, Line), modifier = modifier) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable fun Eyebrow(text: String, color: Color = Muted) = Text(text.uppercase(), style = Mono, color = color)

/** Find → Sign in → Your rooms. */
@Composable fun SetupSteps(step: Int, labels: List<String>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        labels.forEachIndexed { index, label ->
            val number = index + 1
            if (index > 0) Box(Modifier.width(28.dp).height(1.5.dp).background(Outline))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Surface(Modifier.size(26.dp), shape = CircleShape, color = when { number < step -> Mint.copy(alpha = .16f); number == step -> Mint; else -> Color.Transparent }, border = if (number > step) BorderStroke(1.5.dp, Outline) else null) {
                    Box(contentAlignment = Alignment.Center) { Text(if (number < step) "✓" else "$number", color = when { number < step -> Mint; number == step -> MintInk; else -> Muted }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }
                Text(label, color = if (number == step) Cream else Muted, fontSize = 14.sp)
            }
        }
    }
}

/** Large stepper: the main adjustment of a device, sized for pinching at arm's length. */
@Composable fun BigStepper(value: String, caption: String?, decrease: String, increase: String, canDecrease: Boolean, canIncrease: Boolean, onDecrease: () -> Unit, onIncrease: () -> Unit, buttonSize: Dp = 72.dp, valueSize: Int = 64) {
    Row(Modifier.fillMaxWidth().background(Raised, RoundedCornerShape(22.dp)).padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        OutlinedButton(onClick = onDecrease, enabled = canDecrease, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Outline), contentPadding = PaddingValues(0.dp), modifier = Modifier.size(buttonSize).semantics { contentDescription = decrease }) { Text("−", fontSize = 32.sp, color = Cream) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = valueSize.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp, lineHeight = valueSize.sp)
            caption?.let { Text(it, color = Muted, fontSize = 14.sp) }
        }
        OutlinedButton(onClick = onIncrease, enabled = canIncrease, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Outline), contentPadding = PaddingValues(0.dp), modifier = Modifier.size(buttonSize).semantics { contentDescription = increase }) { Text("+", fontSize = 32.sp, color = Cream) }
    }
}

/** Simple stroke glyphs for places where the design uses icons; no emoji. */
enum class Glyph { HAND, EYE, PIN, WIFI, LOCK, SCREEN }
@Composable fun GlyphTile(glyph: Glyph, size: Dp = 44.dp, tint: Color = Mint) {
    Box(Modifier.size(size).background(tint.copy(alpha = .12f), RoundedCornerShape(size * .3f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size * .5f)) {
            val w = size.toPx() * .5f; val st = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round)
            fun l(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, Offset(w * x1, w * y1), Offset(w * x2, w * y2), st.width, StrokeCap.Round)
            when (glyph) {
                Glyph.HAND -> { drawCircle(tint, w * .14f, Offset(w * .36f, w * .36f), style = st); drawCircle(tint, w * .14f, Offset(w * .64f, w * .36f), style = st); l(.5f, .5f, .5f, .86f) }
                Glyph.EYE -> { drawOval(tint, Offset(w * .06f, w * .26f), androidx.compose.ui.geometry.Size(w * .88f, w * .48f), style = st); drawCircle(tint, w * .12f, Offset(w * .5f, w * .5f), style = st) }
                Glyph.PIN -> { drawCircle(tint, w * .22f, Offset(w * .5f, w * .38f), style = st); l(.34f, .54f, .5f, .9f); l(.66f, .54f, .5f, .9f) }
                Glyph.WIFI -> { drawArc(tint, 225f, 90f, false, Offset(w * .05f, w * .2f), androidx.compose.ui.geometry.Size(w * .9f, w * .9f), style = st); drawArc(tint, 225f, 90f, false, Offset(w * .25f, w * .42f), androidx.compose.ui.geometry.Size(w * .5f, w * .5f), style = st); drawCircle(tint, w * .05f, Offset(w * .5f, w * .82f)) }
                Glyph.LOCK -> { drawRoundRect(tint, Offset(w * .2f, w * .46f), androidx.compose.ui.geometry.Size(w * .6f, w * .44f), androidx.compose.ui.geometry.CornerRadius(w * .08f), style = st); drawArc(tint, 180f, 180f, false, Offset(w * .32f, w * .14f), androidx.compose.ui.geometry.Size(w * .36f, w * .5f), style = st) }
                Glyph.SCREEN -> { drawRoundRect(tint, Offset(w * .1f, w * .16f), androidx.compose.ui.geometry.Size(w * .8f, w * .52f), androidx.compose.ui.geometry.CornerRadius(w * .08f), style = st); l(.34f, .86f, .66f, .86f); l(.5f, .68f, .5f, .86f) }
            }
        }
    }
}
