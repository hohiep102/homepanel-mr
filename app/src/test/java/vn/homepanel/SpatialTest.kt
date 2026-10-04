package vn.homepanel

import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import vn.homepanel.spatial.*
import vn.homepanel.storage.SpatialBinding

class SpatialTest {
    @Test fun questStandaloneFeatureOpensSpatialRoomWithoutAndroidHeadtrackingFlag() {
        assertTrue(supportsSpatialRoom { it == "oculus.hardware.standalone_vr" })
        assertTrue(supportsSpatialRoom { it == "android.hardware.vr.headtracking" })
        assertFalse(supportsSpatialRoom { false })
    }
    @Test fun panelsFaceTheViewerAtDifferentHeadRotations() {
        for (yaw in listOf(0f, 45f, 180f, -60f)) {
            val head = Pose(Vector3(2f,1.7f,3f),Quaternion(12f,yaw,0f))
            val panel = panelPose(head,Vector3(.5f,-.12f,1f))
            val normal = panel.q * Vector3(0f,0f,-1f)
            val toViewer = head.t - panel.t
            assertTrue(normal.x*toViewer.x + normal.y*toViewer.y + normal.z*toViewer.z > 0)
        }
    }
    @Test fun frameSizeAndOrientationSurviveSaveAndAnchorRelocalization() {
        val anchor = Pose(Vector3(1f,0f,2f),Quaternion(0f,25f,0f))
        val frame = Pose(Vector3(2f,1.2f,4f),Quaternion(5f,35f,0f))
        val local = anchor.inverse()*frame
        val b = SpatialBinding(serverKey="server",deviceKey="lamp",entityId="light.lamp",roomId="room",anchorId="anchor",x=local.t.x,y=local.t.y,z=local.t.z,label="Lamp",width=.8f,height=1.2f,qw=local.q.w,qx=local.q.x,qy=local.q.y,qz=local.q.z)
        val saved = SpatialBinding.fromJson(JSONObject(b.toJson().toString()))
        assertEquals(b,saved)
        val delta = Pose(Vector3(5f,0f,-3f),Quaternion(0f,90f,0f))
        val expected = delta*frame
        val restored = (delta*anchor)*saved.localPose()
        assertTrue(restored.isApproximatelyEqual(expected,.001f,.001f))
        val legacy = b.toJson().apply { listOf("width","height","qw","qx","qy","qz").forEach(::remove) }
        assertEquals(.5f,SpatialBinding.fromJson(legacy).width)
        assertEquals(1f,SpatialBinding.fromJson(legacy).qw)
    }
    private val origin = Point3(0f,1.6f,0f)
    private val forward = Point3(0f,0f,1f)
    @Test fun selectsVisibleTargetAndRejectsBehindOrFar() {
        assertEquals("lamp", chooseTarget(origin,forward,listOf(TargetPoint("lamp",Point3(.02f,1.6f,2f)),TargetPoint("behind",Point3(0f,1.6f,-1f)))))
        assertNull(chooseTarget(origin,forward,listOf(TargetPoint("far",Point3(0f,1.6f,10f)))))
        assertNull(chooseTarget(origin,forward,listOf(TargetPoint("side",Point3(2f,1.6f,2f)))))
    }
    @Test fun requiresSettledTargetAndExplicitSelection() {
        val gate = IntentGate()
        assertNull(gate.update("lamp",false,0))
        assertNull(gate.update("lamp",true,100))
        assertNull(gate.update("lamp",false,500))
        assertEquals("lamp",gate.update("lamp",true,700))
        assertNull(gate.update("ac",true,800))
        assertNull(gate.update("ac",false,1300))
        assertEquals("ac",gate.update("ac",true,1400))
        gate.reset()
        assertNull(gate.update("ac",true,2000))
    }
    @Test fun anchorRelativePointSurvivesOriginTranslationAndRotation() {
        val oldAnchor = Pose(Vector3(2f,0f,1f),Quaternion(0f,30f,0f))
        val objectWorld = Vector3(3f,2f,4f)
        val local = oldAnchor.inverse() * objectWorld
        val frameChange = Pose(Vector3(5f,0f,-2f),Quaternion(0f,90f,0f))
        val relocatedAnchor = frameChange * oldAnchor
        val restored = relocatedAnchor * local
        val expected = frameChange * objectWorld
        assertEquals(expected.x,restored.x,.0001f);assertEquals(expected.y,restored.y,.0001f);assertEquals(expected.z,restored.z,.0001f)
    }
    @Test fun bindingRoundTripPreservesServerAndAnchorIdentity() {
        val b = SpatialBinding(serverKey="server1",deviceKey="device:1",entityId="light.one",roomId="room1",anchorId="anchor1",x=1f,y=2f,z=-3f,label="Đèn sofa")
        assertEquals(b,SpatialBinding.fromJson(JSONObject(b.toJson().toString())))
    }
}
