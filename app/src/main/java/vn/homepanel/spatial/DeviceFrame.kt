package vn.homepanel.spatial

import android.net.Uri
import com.meta.spatial.core.*
import com.meta.spatial.core.Color4
import com.meta.spatial.toolkit.*

enum class MarkerTone { ACTIVE, IDLE, UNAVAILABLE }

/** Four thin bars leave passthrough visible inside a manually placed outline. */
class DeviceFrame {
    private val bars = List(4) {
        Entity.create(listOf(
            Mesh(Uri.parse("mesh://box"), hittable = MeshCollision.NoCollision),
            Box(Vector3(-.5f, -.5f, -.5f), Vector3(.5f, .5f, .5f)),
            Material().apply { baseColor = Color4(.57f, .90f, .76f, 1f); unlit = true },
            Transform(), Scale(Vector3(1f)), Visible(false),
        ))
    }
    private var lastSize: Pair<Float, Float>? = null
    private var lastTone: MarkerTone? = null
    fun show(pose: Pose, width: Float, height: Float, ready: Boolean = true, tone: MarkerTone = if(ready) MarkerTone.ACTIVE else MarkerTone.UNAVAILABLE) {
        val thickness = .009f
        val sizes = listOf(Vector3(width, thickness, thickness), Vector3(width, thickness, thickness), Vector3(thickness, height, thickness), Vector3(thickness, height, thickness))
        val centers = listOf(Vector3(0f, height/2, 0f), Vector3(0f, -height/2, 0f), Vector3(-width/2, 0f, 0f), Vector3(width/2, 0f, 0f))
        bars.forEachIndexed { i, bar ->
            if (lastSize != width to height) bar.setComponent(Scale(sizes[i]))
            if (lastTone != tone) bar.setComponent(Material().apply { baseColor = when(tone) { MarkerTone.ACTIVE -> Color4(.57f,.90f,.76f,1f); MarkerTone.IDLE -> Color4(.35f,.48f,.55f,1f); MarkerTone.UNAVAILABLE -> Color4(1f,.68f,.25f,1f) }; unlit = true })
            bar.setComponent(Transform(pose * Pose(centers[i])))
            bar.setComponent(Visible(true))
        }
        lastSize = width to height; lastTone = tone
    }
    fun hide() { bars.forEach { it.setComponent(Visible(false)) } }
    fun destroy() { bars.forEach { it.destroy() } }
}
