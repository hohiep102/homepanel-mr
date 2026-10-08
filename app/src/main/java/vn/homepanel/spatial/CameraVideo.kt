package vn.homepanel.spatial

import android.content.Context
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.*
import kotlinx.coroutines.*
import vn.homepanel.AppStore
import vn.homepanel.R

/**
 * HLS camera video drawn straight into Spatial SDK media panels. Compose panels cannot show a video
 * surface, so each camera screen gets one of these at the same pose, which replaces the Compose screen once
 * frames arrive. Until then, and for cameras without a stream, the Compose screen shows status, MJPEG or stills.
 */
@OptIn(UnstableApi::class)
class CameraVideo(private val context: Context, private val store: AppStore) {
    private class Slot(val id: Int) {
        var entity: Entity? = null; var surface: Surface? = null; var camera: String? = null
        var player: ExoPlayer? = null; var job: Job? = null; var playing = false
    }
    private val scope = MainScope()
    private val slots = listOf(R.id.camera_video_0, R.id.camera_video_1, R.id.camera_video_2, R.id.camera_video_3, R.id.camera_video_preview).map(::Slot)
    /** Cameras whose HLS failed recently are not retried every frame. */
    private val failedAt = mutableMapOf<String, Long>()
    /**
     * A player whose screen just went away stays alive briefly, so the preview screen handing over to the
     * saved one keeps the running stream instead of waiting for HA to start a new one (several seconds).
     */
    private class Parked(val camera: String, val player: ExoPlayer, val playing: Boolean, val at: Long)
    private val parked = mutableListOf<Parked>()
    val count get() = slots.size
    /**
     * Media panels are compositor layers drawn beneath the scene, so any panel in front of or just behind
     * them still covers the video. The Compose screen of a playing slot must be hidden.
     */
    fun playing(index: Int) = slots[index].playing

    fun registrations(): List<PanelRegistration> = slots.map { slot ->
        VideoSurfacePanelRegistration(slot.id, { _, surface -> scope.launch { slot.surface = surface; slot.player?.setVideoSurface(surface) } }, {
            MediaPanelSettings(shape = QuadShapeOptions(width = SCREEN_WIDTH, height = SCREEN_HEIGHT), display = PixelDisplayOptions(width = 1280, height = 720))
        })
    }

    fun create() {
        slots.forEach { stop(it); it.surface = null; it.entity = Entity.create(listOf(Panel(it.id), Transform(), Scale(Vector3(1f)), Visible(false))) }
    }

    /** Called every frame on the main thread: [camera] null hides the slot and ends its playback. */
    fun show(index: Int, camera: String?, pose: Pose?, scale: Float) {
        val slot = slots[index]
        if (slot.camera != camera) { park(slot); slot.camera = camera; if (camera != null && !adopt(slot, camera)) start(slot, camera) }
        expireParked()
        val entity = slot.entity ?: return
        if (pose == null || !slot.playing) { entity.setComponent(Visible(false)); return }
        entity.setComponent(Transform(pose))
        entity.setComponent(Scale(Vector3(scale, scale, 1f)))
        entity.setComponent(Visible(true))
    }

    private fun start(slot: Slot, camera: String) {
        val entity = store.state.value.catalog.entities[camera] ?: return
        if (!entity.supports(STREAM) || failedAt[camera]?.let { System.currentTimeMillis() - it < 30_000 } == true) return
        slot.job = scope.launch {
            val (url, client) = try { store.cameraHls(camera) } catch (e: CancellationException) { throw e } catch (e: Exception) { log(camera, "HLS unavailable: ${e.message}"); null } ?: return@launch
            log(camera, "Playing HLS in space")
            val started = System.currentTimeMillis()
            // A camera view needs the newest picture, not smooth buffering: start after half a second of video.
            val load = DefaultLoadControl.Builder().setBufferDurationsMs(1_000, 4_000, 500, 500).build()
            val live = MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(1_500).setMinPlaybackSpeed(1f).setMaxPlaybackSpeed(1.05f).build()
            slot.player = ExoPlayer.Builder(context).setLoadControl(load).setMediaSourceFactory(HlsMediaSource.Factory(OkHttpDataSource.Factory(client))).build().apply {
                volume = 0f
                slot.surface?.let(::setVideoSurface)
                addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        // The slot holding this player may have changed after a hand-over.
                        val owner = slots.find { it.player === this@apply }
                        if (owner != null && !owner.playing) { log(camera, "First video frame after ${System.currentTimeMillis() - started} ms"); owner.playing = true }
                        parked.replaceAll { if (it.player === this@apply) Parked(it.camera, it.player, true, it.at) else it }
                    }
                    override fun onPlayerError(e: PlaybackException) {
                        log(camera, "HLS playback failed: ${e.errorCodeName}"); failedAt[camera] = System.currentTimeMillis()
                        slots.find { it.player === this@apply }?.let(::stop) ?: run { parked.removeAll { it.player === this@apply }; release() }
                    }
                })
                setMediaItem(MediaItem.Builder().setUri(url).setLiveConfiguration(live).build()); playWhenReady = true; prepare()
            }
        }
    }

    private fun park(slot: Slot) {
        val player = slot.player
        val camera = slot.camera
        if (player != null && camera != null) {
            slot.job?.cancel(); slot.job = null
            player.clearVideoSurface()
            parked += Parked(camera, player, slot.playing, System.currentTimeMillis())
            slot.player = null; slot.playing = false; slot.entity?.setComponent(Visible(false))
        } else stop(slot)
    }

    private fun adopt(slot: Slot, camera: String): Boolean {
        val found = parked.firstOrNull { it.camera == camera } ?: return false
        parked.remove(found)
        slot.player = found.player; slot.playing = found.playing
        slot.surface?.let(found.player::setVideoSurface)
        log(camera, "Kept the running stream for the new screen")
        return true
    }

    private fun expireParked() {
        val now = System.currentTimeMillis()
        parked.filter { now - it.at > 5_000 }.forEach { parked.remove(it); it.player.release() }
    }

    private fun stop(slot: Slot) {
        slot.job?.cancel(); slot.job = null
        // ExoPlayer may only be touched on the thread that built it, the main thread.
        slot.player?.let { player -> if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) player.release() else scope.launch { player.release() } }
        slot.player = null; slot.playing = false
        slot.entity?.setComponent(Visible(false))
    }

    fun release() { slots.forEach { stop(it); it.camera = null; it.entity = null; it.surface = null }; parked.forEach { it.player.release() }; parked.clear() }

    private fun log(camera: String, message: String) = Log.i("HomePanelCamera", "$camera: $message")

    companion object { private const val STREAM = 2 }
}
