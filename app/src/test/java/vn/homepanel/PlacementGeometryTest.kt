package vn.homepanel

import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import org.junit.Assert.*
import org.junit.Test
import vn.homepanel.spatial.*

class PlacementGeometryTest {
    private fun forward(p: Pose) = p.q * Vector3(0f, 0f, 1f)
    private val tiltedHead = Pose(Vector3(0f, 1.6f, 0f), Quaternion.lookRotation(Vector3(0f, -.4f, 1f)))

    @Test fun wallFrameLiesFlatAndJustProudOfTheSurface() {
        val pose = placementPose(tiltedHead, 2f, Vector3(0f, 1.2f, 2f), Vector3(0f, 0f, -1f), .5f)
        assertEquals(1.99f, pose.t.z, .001f)
        assertEquals(1f, forward(pose).z, .001f)
        assertEquals(0f, forward(pose).y, .001f)
    }

    @Test fun tableFrameStandsUprightOnTopAndIgnoresHeadPitch() {
        val pose = placementPose(tiltedHead, 2f, Vector3(0f, .75f, 1.5f), Vector3(0f, 1f, 0f), .4f)
        assertEquals(.95f, pose.t.y, .001f)
        assertEquals(0f, forward(pose).y, .001f)
    }

    @Test fun floatingFrameStaysLevelAtManualDistance() {
        val pose = placementPose(tiltedHead, 2f, null, null, .5f)
        assertEquals(0f, forward(pose).y, .001f)
        assertTrue(pose.t.y < 1.6f)
    }

    @Test fun anchorIsTheSurfaceHitElseTheNearestAnchor() {
        val anchors = listOf("floor" to Vector3(0f, 0f, 0f), "wall" to Vector3(0f, 1.3f, 2f), "desk" to Vector3(1f, .7f, 1f))
        assertEquals("desk", placementAnchor("desk", Vector3(0f, 1.3f, 2f), anchors))
        assertEquals("wall", placementAnchor(null, Vector3(.2f, 1.2f, 1.9f), anchors))
        assertEquals("wall", placementAnchor("unknown", Vector3(.2f, 1.2f, 1.9f), anchors))
        assertNull(placementAnchor(null, Vector3(0f, 0f, 0f), emptyList()))
    }
}
