package vn.homepanel

import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import org.junit.Assert.*
import org.junit.Test
import vn.homepanel.spatial.*

class GazeTargetingTest {
    private val head = Pose(Vector3(0f, 1.6f, 0f))
    private fun frame(id: String, x: Float = 0f, z: Float = 2f, width: Float = .5f) = TargetFrame(id, Pose(Vector3(x, 1.6f, z)), width, .5f)

    @Test fun lookingAtEdgeOfWideApplianceHitsSavedRectangleOutsideOldCenterCone() {
        val ac = frame("ac", x = 1f, width = 2.2f)
        assertNull(chooseTarget(Point3(0f,1.6f,0f), Point3(0f,0f,1f), listOf(TargetPoint("ac",Point3(1f,1.6f,2f)))))
        assertEquals("ac", chooseFrameTarget(head,listOf(ac)))
        assertNull(chooseFrameTarget(head,listOf(ac.copy(width=.5f))))
    }

    @Test fun targetSurvivesRotatedRoomOriginAndHeadElevation() {
        val transform = Pose(Vector3(3f, .2f, -2f),Quaternion(-25f,110f,0f))
        val ac = frame("ac",x=.25f,width=.7f)
        assertEquals("ac",chooseFrameTarget(transform*head,listOf(ac.copy(pose=transform*ac.pose))))
        assertNull(chooseFrameTarget(head,listOf(frame("behind",z=-2f),frame("far",z=9f))))
    }

    @Test fun exactFrameHitWinsOverPreviouslyFocusedNearbyCenter() {
        val current = frame("nearby",x=.3f,width=.1f)
        val exact = frame("exact",x=-.3f,width=.8f)
        assertEquals("exact",chooseFrameTarget(head,listOf(current,exact),previous="nearby"))
    }

    @Test fun nestedSmallerFrameStaysSelectableInsideFocusedLargeFrame() {
        val tv = TargetFrame("tv", Pose(Vector3(0f, 1.6f, 2f)), 1.4f, .8f)
        val soundbar = TargetFrame("soundbar", Pose(Vector3(0f, 1.6f, 1.98f)), .6f, .12f)
        assertEquals("soundbar", chooseFrameTarget(head, listOf(tv, soundbar), previous = "tv"))
        val lookingAtTvCorner = Pose(head.t, Quaternion.lookRotation(Vector3(.5f, .3f, 2f)))
        assertEquals("tv", chooseFrameTarget(lookingAtTvCorner, listOf(tv, soundbar), previous = "soundbar"))
    }

    @Test fun smallTargetsAllowHeadAimToleranceButRejectSideTargets() {
        assertEquals("small",chooseFrameTarget(head,listOf(frame("small",x=.2f,width=.1f))))
        assertNull(chooseFrameTarget(head,listOf(frame("side",x=1f,width=.1f))))
    }

    @Test fun lookingSteadilyOpensOnceWithoutHandSelection() {
        val gate=GazeOpenGate()
        assertNull(gate.update("lamp",false,0))
        assertNull(gate.update("lamp",false,649))
        assertEquals("lamp",gate.update("lamp",false,650))
        assertNull(gate.update("lamp",false,2000))
    }

    @Test fun changingOrLosingTargetRestartsDwellAndExplicitSelectionCanOpen() {
        val gate=GazeOpenGate()
        assertNull(gate.update("lamp",false,0))
        assertNull(gate.update("ac",false,600))
        assertNull(gate.update("ac",false,1000))
        assertNull(gate.update(null,false,1200))
        assertNull(gate.update("ac",false,1300))
        assertEquals("ac",gate.update("ac",true,1301))
        gate.reset()
        assertNull(gate.update("ac",false,2000))
    }

    @Test fun doneStaysClosedThroughTinyTrackingGapsUntilLookingAwayAndBack() {
        val gate=GazeOpenGate()
        gate.dismiss("lamp")
        assertNull(gate.update("lamp",false,0))
        assertNull(gate.update("lamp",false,1500))
        assertNull(gate.update(null,false,1600))
        assertNull(gate.update("lamp",false,1700))
        assertNull(gate.update("lamp",false,3000))
        assertNull(gate.update(null,false,3100))
        assertNull(gate.update(null,false,3400))
        assertNull(gate.update("lamp",false,3500))
        assertEquals("lamp",gate.update("lamp",false,4150))
    }

    @Test fun doneDoesNotBlockOtherDevicesOrExplicitReopen() {
        val gate=GazeOpenGate()
        gate.dismiss("lamp")
        assertNull(gate.update("ac",false,0))
        assertEquals("ac",gate.update("ac",false,650))
        gate.dismiss("lamp")
        assertEquals("lamp",gate.update("lamp",true,1000))
    }
}
