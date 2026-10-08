package vn.homepanel.spatial

import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import kotlin.math.abs

/**
 * Where an aimed frame sits. On a wall it lies flat, just proud of the surface; on a table or floor it
 * stands upright on top; in mid-air it stays level. Head pitch and roll never tilt the saved outline.
 */
fun placementPose(aim: Pose, distance: Float, hitPosition: Vector3?, hitNormal: Vector3?, height: Float): Pose {
    val forward = aim.q * Vector3(0f, 0f, 1f)
    val level = Vector3(forward.x, 0f, forward.z).let { if (it.length() < .001f) Vector3(0f, 0f, 1f) else it.normalize() }
    if (hitPosition == null || hitNormal == null || hitNormal.length() < .001f) return Pose(aim.t + forward * distance, Quaternion.lookRotation(level))
    val normal = hitNormal.normalize()
    return if (abs(normal.y) < .7f) Pose(hitPosition + normal * .01f, Quaternion.lookRotation(-normal))
    else Pose(hitPosition + Vector3(0f, if (normal.y > 0) height / 2 else -height / 2, 0f), Quaternion.lookRotation(level))
}

/** The scene anchor a placement is stored against: the surface hit, else the closest anchor, so small anchor errors are not multiplied by a long lever arm. */
fun placementAnchor(hitAnchor: String?, target: Vector3, anchors: List<Pair<String, Vector3>>): String? =
    hitAnchor?.takeIf { id -> anchors.any { it.first == id } } ?: anchors.minByOrNull { (it.second - target).length() }?.first

data class FittedFrame(val pose: Pose, val width: Float, val height: Float)

/**
 * The upright face of a scanned object that the aim hit, so the frame matches a TV, lamp or wall art
 * instead of a manual size. [min]/[max] bound the object in its anchor's space; a plane has no depth.
 * Tops, bottoms, slivers and wall-sized faces are not fitted.
 */
fun fittedFrame(anchor: Pose, min: Vector3, max: Vector3, hitNormal: Vector3, maxSize: Float = 2.5f): FittedFrame? {
    if (hitNormal.length() < .001f) return null
    val local = anchor.inverse().q * hitNormal
    val axis = (0..2).maxBy { abs(local[it]) }
    val sign = if (local[axis] >= 0) 1f else -1f
    val normal = (anchor.q * unit(axis) * sign).normalize()
    if (abs(normal.y) >= .7f) return null
    val center = (min + max) * .5f
    val face = Vector3(center.x, center.y, center.z).also { it[axis] = if (sign > 0) max[axis] else min[axis] }
    val (heightAxis, widthAxis) = (0..2).filter { it != axis }.sortedByDescending { abs((anchor.q * unit(it)).y) }
    val width = max[widthAxis] - min[widthAxis]; val height = max[heightAxis] - min[heightAxis]
    if (width !in .05f..maxSize || height !in .05f..maxSize) return null
    return FittedFrame(Pose((anchor * Pose(face)).t + normal * .01f, Quaternion.lookRotation(-normal)), width, height)
}

private fun unit(axis: Int) = Vector3(0f, 0f, 0f).also { it[axis] = 1f }
private operator fun Vector3.get(axis: Int) = when (axis) { 0 -> x; 1 -> y; else -> z }
private operator fun Vector3.set(axis: Int, value: Float) { when (axis) { 0 -> x = value; 1 -> y = value; else -> z = value } }
