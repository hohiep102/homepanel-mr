package vn.homepanel

import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import org.junit.Assert.*
import org.junit.Test
import vn.homepanel.spatial.PlacementPanelFollow

class PlacementPanelFollowTest {
    private val start = Pose(Vector3(0f, 1.6f, 0f), Quaternion())

    @Test fun gazeOpenedControlsStayInViewWhenLookingUpAtAnAirConditioner() {
        val follow = PlacementPanelFollow(Vector3(0f,-.15f,.85f))
        for (pitch in listOf(0f,-35f,-55f)) {
            val head = Pose(start.t,Quaternion(pitch,-90f,0f))
            val panel = follow.reset(head,0)
            val relative = head.inverse()*panel
            assertEquals(0f,relative.t.x,.001f)
            assertEquals(-.15f,relative.t.y,.001f)
            assertEquals(.85f,relative.t.z,.001f)
            assertTrue((panel.q*Vector3(0f,0f,-1f)).dot(head.t-panel.t)>0)
            assertEquals(panel,follow.update(Pose(start.t,Quaternion(0f,90f,0f)),true,16))
        }
    }

    @Test fun controlPanelStaysWithinReachWhenLookingUpAndPausesForTouch() {
        val follow = PlacementPanelFollow(Vector3(0f,-.15f,.85f),keepLevel=true)
        follow.reset(start,0)
        val up = Pose(start.t,Quaternion(-50f,-90f,0f))
        var panel=start
        for(time in 16L..2000L step 16) panel=follow.update(up,false,time)
        assertEquals(1.45f,panel.t.y,.01f)
        assertTrue(panel.t.distanceTo(up.t)<.9f)
        val turned=Pose(start.t,Quaternion(0f,90f,0f))
        assertEquals(panel,follow.update(turned,true,2016))
    }

    @Test fun editorComesAlongWhenTurningToAppliancesOnEitherSide() {
        for (yaw in listOf(-90f, 90f, 180f)) {
            val follow = PlacementPanelFollow()
            follow.reset(start, 0)
            val turned = Pose(start.t, Quaternion(0f, yaw, 0f))
            var panel = start
            for (time in 16L..2000L step 16) panel = follow.update(turned, false, time)
            assertTrue("yaw=$yaw", panel.isApproximatelyEqual(PlacementPanelFollow.target(turned), .01f, .01f))
            assertTrue((panel.q * Vector3(0f,0f,-1f)).dot(turned.t-panel.t) > 0)
        }
    }

    @Test fun editorStaysStillDuringTouchOrPointerUseThenFollowsAgain() {
        val follow = PlacementPanelFollow()
        val initial = follow.reset(start,0)
        val turned = Pose(Vector3(.4f,1.6f,0f), Quaternion(0f,-90f,0f))
        for (time in 16L..1000L step 16) assertEquals(initial,follow.update(turned,true,time))
        var resumed = initial
        for (time in 1016L..3000L step 16) resumed=follow.update(turned,false,time)
        assertTrue(resumed.isApproximatelyEqual(PlacementPanelFollow.target(turned),.01f,.01f))
    }

    @Test fun smallHeadMovementsDoNotMakeButtonsChaseTheUser() {
        val follow = PlacementPanelFollow()
        val initial = follow.reset(start,0)
        for (yaw in listOf(3f,-4f,5f,-2f)) {
            assertEquals(initial,follow.update(Pose(start.t,Quaternion(0f,yaw,0f)),false,100))
        }
    }

    @Test fun editorFollowsTranslationAndLookingUpAtAnAirConditioner() {
        val follow = PlacementPanelFollow()
        follow.reset(start,0)
        val moved = Pose(Vector3(1f,1.7f,.5f),Quaternion(-35f,-70f,0f))
        var panel = start
        for (time in 16L..2000L step 16) panel=follow.update(moved,false,time)
        assertTrue(panel.isApproximatelyEqual(PlacementPanelFollow.target(moved),.01f,.01f))
    }
}
