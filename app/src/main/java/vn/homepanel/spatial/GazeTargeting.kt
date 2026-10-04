package vn.homepanel.spatial

import com.meta.spatial.core.Pose
import com.meta.spatial.core.Vector3
import kotlin.math.*

data class TargetFrame(val id: String, val pose: Pose, val width: Float, val height: Float)

/** Hit the saved rectangle, with a small angular fallback for tiny or oblique targets. */
fun chooseFrameTarget(head: Pose, frames: List<TargetFrame>, previous: String? = null): String? {
    val forward = head.q * Vector3(0f, 0f, 1f)
    data class Hit(val id: String, val rectangle: Boolean, val score: Float)
    val hits = frames.mapNotNull { frame ->
        val delta = frame.pose.t - head.t
        val distance = delta.length()
        if (distance !in .2f..8f) return@mapNotNull null
        val angle = acos(((delta.x * forward.x + delta.y * forward.y + delta.z * forward.z) / distance).coerceIn(-1f, 1f)) * 180f / PI.toFloat()
        val localHead = frame.pose.inverse() * head
        val ray = localHead.q * Vector3(0f, 0f, 1f)
        val along = if (abs(ray.z) > .001f) -localHead.t.z / ray.z else -1f
        val point = localHead.t + ray * along
        val margin = if (frame.id == previous) .10f else .06f
        val rectangle = along in .2f..8f && abs(point.x) <= frame.width / 2 + margin && abs(point.y) <= frame.height / 2 + margin
        if (!rectangle && angle > if (frame.id == previous) 10f else 8f) null
        else Hit(frame.id, rectangle, angle + distance * .02f)
    }
    val rectangles = hits.filter { it.rectangle }
    val eligible = rectangles.ifEmpty { hits }
    // Prefer an existing hit only within the best hit class, never over another exact rectangle.
    return eligible.find { it.id == previous }?.id ?: eligible.minByOrNull { it.score }?.id
}

/** Opens a UI only. Looking never issues a device command. */
class GazeOpenGate(private val dwellMs: Long = 650, private val awayMs: Long = 300) {
    private var candidate: String? = null
    private var since = 0L
    private var emitted = false
    private var dismissed: String? = null
    private var awaySince: Long? = null

    fun update(target: String?, selected: Boolean, now: Long): String? {
        if (dismissed != null) {
            if (target == dismissed) awaySince = null
            else {
                val away = awaySince ?: now.also { awaySince = it }
                if (now - away >= awayMs) dismissed = null
            }
        }
        if (candidate != target) { candidate = target; since = now; emitted = false }
        if (target == null || emitted || (target == dismissed && !selected)) return null
        if (!selected && now - since < dwellMs) return null
        emitted = true
        return target
    }

    /** Done must stay closed until the user looks away and back, or explicitly selects. */
    fun dismiss(id: String?) { dismissed = id; awaySince = null; reset() }
    fun reset() { candidate = null; since = 0L; emitted = false }
    fun clear() { dismissed = null; awaySince = null; reset() }
}
