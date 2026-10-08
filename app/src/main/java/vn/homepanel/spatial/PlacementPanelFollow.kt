package vn.homepanel.spatial

import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import kotlin.math.acos
import kotlin.math.exp

/** Keep the editor beside the aim direction, but still while the user targets its buttons. */
class PlacementPanelFollow(private val offset: Vector3 = Vector3(.46f, -.12f, .78f), private val keepLevel: Boolean = false) {
    private var pose: Pose? = null
    private var following = false
    private var lastTime = 0L

    fun reset(head: Pose, now: Long): Pose {
        lastTime = now
        following = false
        return desired(head).also { pose = it }
    }

    fun update(head: Pose, interacting: Boolean, now: Long): Pose {
        val current = pose ?: return reset(head, now)
        val dt = ((now - lastTime).coerceIn(0, 100) / 1000f)
        lastTime = now
        if (interacting) {
            following = false
            return current
        }
        val desired = desired(head)
        // The panel stays put in the room while the user turns to aim; it only comes back once it has
        // clearly left its place in view, or the user walked away from it.
        if (!following && (angleDegrees(current.t - head.t, desired.t - head.t) > 40f || current.t.distanceTo(desired.t) > .6f)) following = true
        if (!following) return current
        val next = current.lerp(desired, 1f - exp(-dt / .12f))
        if (next.isApproximatelyEqual(desired, .008f, .008f)) following = false
        pose = next
        return next
    }

    private fun desired(head: Pose): Pose {
        val forward = head.q * Vector3(0f, 0f, 1f)
        val direction = if (keepLevel) Vector3(forward.x, 0f, forward.z).let { if (it.length() < .001f) Vector3(0f,0f,1f) else it } else forward
        return panelPose(Pose(head.t, Quaternion.lookRotation(direction)), offset)
    }

    companion object {
        fun target(head: Pose): Pose {
            val upright = Quaternion.lookRotation(head.q * Vector3(0f, 0f, 1f))
            return panelPose(Pose(head.t, upright), Vector3(.46f, -.12f, .78f))
        }
    }
}

private fun angleDegrees(a: Vector3, b: Vector3): Float {
    val lengths = a.length() * b.length()
    return if (lengths < 1e-6f) 0f else Math.toDegrees(acos((a.dot(b) / lengths).coerceIn(-1f, 1f)).toDouble()).toFloat()
}
