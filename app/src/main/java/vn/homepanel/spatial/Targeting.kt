package vn.homepanel.spatial

import kotlin.math.*

data class Point3(val x: Float, val y: Float, val z: Float) {
    operator fun minus(o: Point3) = Point3(x-o.x,y-o.y,z-o.z)
    fun length() = sqrt(x*x+y*y+z*z)
    fun dot(o: Point3) = x*o.x+y*o.y+z*o.z
}
data class TargetPoint(val id: String, val position: Point3)

/** Cull distant/behind-head markers, without changing their saved anchor or target. */
fun markerVisible(origin: Point3, forward: Point3, position: Point3): Boolean {
    val delta = position - origin
    val distance = delta.length()
    val length = forward.length()
    return distance in .2f..8f && length > .001f && delta.dot(forward) / (distance * length) >= cos(75f * PI.toFloat() / 180f)
}
/** Angular targeting uses head direction, never claims eye tracking on Quest 3. */
fun chooseTarget(origin: Point3, forward: Point3, candidates: List<TargetPoint>, maxDegrees: Float = 8f, maxDistance: Float = 8f): String? {
    val directionLength = forward.length()
    if (directionLength < .001f) return null
    return candidates.mapNotNull { candidate ->
        val delta = candidate.position - origin; val distance = delta.length()
        if (distance < .2f || distance > maxDistance) null else {
            val cosAngle = (delta.dot(forward) / (distance * directionLength)).coerceIn(-1f,1f)
            val angle = acos(cosAngle) * 180f / PI.toFloat()
            if (angle > maxDegrees) null else candidate.id to (angle + distance * .02f)
        }
    }.minByOrNull { it.second }?.first
}
class IntentGate(private val dwellMs: Long = 350) {
    private var candidate: String? = null; private var since = 0L
    fun update(target: String?, selected: Boolean, now: Long): String? {
        if (candidate != target) { candidate = target; since = now }
        return candidate?.takeIf { selected && now - since >= dwellMs }
    }
    fun reset() { candidate = null; since = 0 }
}
