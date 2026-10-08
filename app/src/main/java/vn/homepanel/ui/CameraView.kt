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
import android.util.Log
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import okhttp3.OkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import vn.homepanel.AppStore
import vn.homepanel.R
import vn.homepanel.ha.HaEntity

private enum class Feed { STILLS, MJPEG, HLS }

/** False inside Spatial SDK panels, which cannot show a video surface; the room plays HLS on media panels instead. */
val LocalInlineVideo = androidx.compose.runtime.staticCompositionLocalOf { true }

/**
 * Live camera from Home Assistant. Cameras HA streams as video play as HLS; others use the MJPEG proxy.
 * Whichever fails falls through to the next (HLS, MJPEG, refreshing stills), and everything stops as soon as
 * the view leaves the screen.
 */
@Composable fun CameraView(store: AppStore, entity: HaEntity, modifier: Modifier = Modifier, fill: Boolean = false) {
    val state by store.state.collectAsState()
    var image by remember(entity.id) { mutableStateOf<ImageBitmap?>(null) }
    var updated by remember(entity.id) { mutableLongStateOf(0L) }
    var failed by remember(entity.id) { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var hls by remember(entity.id) { mutableStateOf<Pair<String, OkHttpClient>?>(null) }
    var hlsPlaying by remember(entity.id) { mutableStateOf(false) }
    var hlsFailed by remember(entity.id) { mutableStateOf(false) }
    val live = state.connected && !state.demo && entity.available
    val inlineVideo = LocalInlineVideo.current
    LaunchedEffect(entity.id, live, hlsFailed) {
        if (!live) return@LaunchedEffect
        fun log(message: String) = Log.i("HomePanelCamera", "${entity.id}: $message")
        val order = if (hlsFailed || !inlineVideo) listOf(Feed.MJPEG, Feed.STILLS)
            else if (entity.attributes.optString("frontend_stream_type") == "hls") listOf(Feed.HLS, Feed.MJPEG, Feed.STILLS) else listOf(Feed.MJPEG, Feed.HLS, Feed.STILLS)
        for (feed in order) when (feed) {
            Feed.HLS -> {
                val playlist = try { store.cameraHls(entity.id) } catch (e: CancellationException) { throw e } catch (e: Exception) { log("HLS unavailable: ${e.message}"); null }
                if (playlist != null) { log("Playing HLS"); hls = playlist; awaitCancellation() }
            }
            Feed.MJPEG -> {
                var frames = 0
                try {
                    store.cameraStream(entity.id).collect { bytes ->
                        val bitmap = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                        if (bitmap != null) { if (frames++ == 0) log("MJPEG streaming"); image = bitmap; updated = SystemClock.elapsedRealtime(); now = updated; failed = false }
                    }
                    log("MJPEG ended after $frames frames")
                } catch (e: CancellationException) { throw e } catch (e: Exception) { log("MJPEG failed after $frames frames: ${e.message}") }
            }
            Feed.STILLS -> while (isActive) {
                val bitmap = try {
                    store.cameraSnapshot(entity.id)?.let { bytes -> withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() } }
                } catch (e: CancellationException) { throw e } catch (e: Exception) { if (!failed) log("Snapshot failed: ${e.message}"); null }
                if (bitmap != null) { image = bitmap; updated = SystemClock.elapsedRealtime(); failed = false } else failed = true
                now = SystemClock.elapsedRealtime()
                delay(if (entity.state == "streaming") 1_000 else 2_000)
            }
        }
    }
    Box(modifier.fillMaxWidth().then(if (fill) Modifier.fillMaxHeight() else Modifier.aspectRatio(16f / 9f)).clip(RoundedCornerShape(20.dp)).background(Raised), contentAlignment = Alignment.Center) {
        val video = hls?.takeIf { live && !hlsFailed }
        if (video != null) HlsVideo(video.first, video.second, onPlaying = { hlsPlaying = true }, onError = { reason ->
            Log.w("HomePanelCamera", "${entity.id}: HLS playback failed: $reason"); hls = null; hlsPlaying = false; hlsFailed = true
        })
        else image?.let { Image(it, contentDescription = entity.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        val showing = (video != null && hlsPlaying) || (video == null && image != null)
        // In the room, a streaming camera's video plays on a media panel in front of this one.
        val videoPending = !inlineVideo && live && entity.supports(2)
        if (!showing) Text(stringResource(when { state.demo -> R.string.camera_demo; videoPending -> R.string.camera_connecting_video; !live || (failed && video == null) -> R.string.camera_unavailable; else -> R.string.camera_loading }), color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
        if (showing) Row(Modifier.align(Alignment.TopStart).padding(12.dp).background(Color(0xB30A1216), RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val stale = video == null && failed
            Box(Modifier.size(8.dp).background(if (stale) Amber else Mint, RoundedCornerShape(999.dp)))
            Text(when { stale -> stringResource(R.string.camera_stale); video != null -> "LIVE"; else -> stringResource(R.string.camera_live, ((now - updated) / 1000).coerceAtLeast(0)) }, style = Mono, color = if (stale) Amber else Mint)
        }
    }
}

/** Muted HLS playback into a TextureView, which renders inside Spatial SDK panels where a SurfaceView may not. */
@OptIn(UnstableApi::class)
@Composable private fun HlsVideo(url: String, client: OkHttpClient, onPlaying: () -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).setMediaSourceFactory(HlsMediaSource.Factory(OkHttpDataSource.Factory(client))).build().apply {
            volume = 0f; setMediaItem(MediaItem.fromUri(url)); playWhenReady = true; prepare()
        }
    }
    val playing by rememberUpdatedState(onPlaying); val error by rememberUpdatedState(onError)
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { playing() }
            override fun onPlayerError(e: PlaybackException) { error(e.errorCodeName) }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    AndroidView({ TextureView(it).also(player::setVideoTextureView) }, Modifier.fillMaxSize())
}
