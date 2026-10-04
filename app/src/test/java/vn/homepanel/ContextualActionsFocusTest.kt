package vn.homepanel

import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import org.junit.Assert.*
import org.junit.Test
import vn.homepanel.spatial.*

class ContextualActionsFocusTest {
    @Test fun iconsAppearAfterSteadyLookAndHideWithoutDone() {
        val focus=ContextualActionsFocus()
        assertNull(focus.update("switch",false,0))
        assertNull(focus.update("switch",false,449))
        assertEquals("switch",focus.update("switch",false,450))
        assertEquals("switch",focus.update(null,false,1000))
        assertNull(focus.update(null,false,1150))
        assertNull(focus.update("switch",false,1200))
        assertEquals("switch",focus.update("switch",false,1650))
    }
    @Test fun iconsDoNotDisappearOrSwitchDevicesWhileTheirButtonsAreTargeted() {
        val focus=ContextualActionsFocus()
        focus.show("switch",0)
        assertEquals("switch",focus.update(null,true,1000))
        assertEquals("switch",focus.update("ac",true,5000))
        assertEquals("switch",focus.update("ac",false,5100))
        assertEquals("ac",focus.update("ac",false,5550))
    }
    @Test fun shortGlanceAwayDoesNotFlickerAndChangingCandidateRestartsDwell() {
        val focus=ContextualActionsFocus()
        focus.show("switch",0)
        assertEquals("switch",focus.update(null,false,300))
        assertEquals("switch",focus.update("switch",false,500))
        assertEquals("switch",focus.update("ac",false,600))
        assertEquals("switch",focus.update("fan",false,1000))
        assertNull(focus.update("fan",false,1200))
        assertEquals("fan",focus.update("fan",false,1450))
    }
    @Test fun trackingLossResetHidesImmediatelyAndRequiresNewLook() {
        val focus=ContextualActionsFocus()
        focus.show("ac",0)
        focus.reset()
        assertNull(focus.target)
        assertNull(focus.update("ac",false,1000))
    }
    @Test fun iconsAreReachableBelowTargetAndFaceViewerAtMultipleHeadAngles() {
        for(yaw in listOf(0f,90f,180f)) for(pitch in listOf(0f,-45f)) {
            val head=Pose(Vector3(2f,1.6f,3f),Quaternion(pitch,yaw,0f))
            val target=head.t+head.q*Vector3(0f,0f,3f)
            val panel=contextualActionsPose(head,target)
            assertTrue(panel.t.distanceTo(head.t) in .5f..1.2f)
            assertTrue((panel.q*Vector3(0f,0f,-1f)).dot(head.t-panel.t)>0)
            val atPanel=Pose(head.t,Quaternion.lookRotation(panel.t-head.t))
            assertTrue(lookingAtPanel(atPanel,panel,.34f,.14f))
            assertFalse(lookingAtPanel(Pose(head.t,Quaternion.lookRotation(head.t-panel.t)),panel,.34f,.14f))
        }
    }
}
