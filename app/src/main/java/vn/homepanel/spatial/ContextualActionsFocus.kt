package vn.homepanel.spatial

import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import kotlin.math.abs

/** Small actions appear after steady head aim and disappear without a Done button. */
class ContextualActionsFocus(private val dwellMs: Long = 450, private val hideMs: Long = 700) {
    var target: String? = null
        private set
    private var candidate: String? = null
    private var since = 0L
    private var lastEngaged = 0L

    fun update(lookedTarget: String?, interacting: Boolean, now: Long): String? {
        if (target != null && (interacting || lookedTarget == target)) {
            lastEngaged = now; candidate = null
            return target
        }
        if (candidate != lookedTarget) { candidate = lookedTarget; since = now }
        if (lookedTarget != null && now - since >= dwellMs) show(lookedTarget,now)
        else if (target != null && now - lastEngaged >= hideMs) target = null
        return target
    }
    fun show(id: String, now: Long) { target=id; lastEngaged=now; candidate=null }
    fun reset() { target=null; candidate=null; since=0; lastEngaged=0 }
}

/** An upright, reachable strip below the object in the viewer's field of view. */
fun contextualActionsPose(head: Pose, target: Vector3): Pose {
    val delta=target-head.t
    val direction=if(delta.length()>.001f) delta.normalize() else head.q*Vector3(0f,0f,1f)
    val position=head.t+direction*delta.length().coerceIn(.55f,1.05f)+Vector3(0f,-.14f,0f)
    return Pose(position,Quaternion.lookRotation(position-head.t))
}

fun lookingAtPanel(head: Pose, panel: Pose, width: Float, height: Float, margin: Float = .035f): Boolean {
    val local=panel.inverse()*head
    val direction=local.q*Vector3(0f,0f,1f)
    if(abs(direction.z)<.001f) return false
    val distance=-local.t.z/direction.z
    val hit=local.t+direction*distance
    return distance>0 && abs(hit.x)<=width/2+margin && abs(hit.y)<=height/2+margin
}
