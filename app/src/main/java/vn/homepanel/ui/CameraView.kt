package vn.homepanel.ui

import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import vn.homepanel.AppStore
import vn.homepanel.R
import vn.homepanel.ha.HaEntity

/** Refreshing camera still from Home Assistant; polling stops as soon as the view leaves the screen. */
@Composable fun CameraView(store: AppStore, entity: HaEntity, modifier: Modifier = Modifier) {
    val state by store.state.collectAsState()
    var image by remember(entity.id) { mutableStateOf<ImageBitmap?>(null) }
    var updated by remember(entity.id) { mutableLongStateOf(0L) }
    var failed by remember(entity.id) { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val live = state.connected && !state.demo && entity.available
    LaunchedEffect(entity.id, live) {
        if (!live) return@LaunchedEffect
        while (isActive) {
            val bitmap = try {
                store.cameraSnapshot(entity.id)?.let { bytes -> withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() } }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            if (bitmap != null) { image = bitmap; updated = SystemClock.elapsedRealtime(); failed = false } else failed = true
            now = SystemClock.elapsedRealtime()
            delay(if (entity.state == "streaming") 1_000 else 2_000)
        }
    }
    Box(modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp)).background(Raised), contentAlignment = Alignment.Center) {
        image?.let { Image(it, contentDescription = entity.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (image == null) Text(stringResource(when { state.demo -> R.string.camera_demo; !live || failed -> R.string.camera_unavailable; else -> R.string.camera_loading }), color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
        if (image != null) Row(Modifier.align(Alignment.TopStart).padding(12.dp).background(Color(0xB30A1216), RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).background(if (failed) Amber else Mint, RoundedCornerShape(999.dp)))
            Text(if (failed) stringResource(R.string.camera_stale) else stringResource(R.string.camera_live, ((now - updated) / 1000).coerceAtLeast(0)), style = Mono, color = if (failed) Amber else Mint)
        }
    }
}
