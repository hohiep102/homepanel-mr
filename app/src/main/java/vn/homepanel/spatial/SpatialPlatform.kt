package vn.homepanel.spatial

import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3

fun supportsSpatialRoom(hasFeature: (String) -> Boolean): Boolean =
    hasFeature("oculus.hardware.standalone_vr") || hasFeature("android.hardware.vr.headtracking")

/** SDK quad panels face local -Z. A panel ahead of the head keeps its rotation. */
fun panelPose(head: Pose, offset: Vector3): Pose =
    Pose(head.t + head.q * offset, head.q.copy())
