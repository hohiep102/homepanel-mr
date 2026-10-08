package vn.homepanel

import org.junit.Assert.*
import org.junit.Test
import vn.homepanel.spatial.assignScreens
import vn.homepanel.spatial.screenScale

class CameraScreensTest {
    @Test fun nearestCamerasGetScreensAndKeepThem() {
        val first = assignScreens(listOf(null, null), listOf("door", "yard", "garage"))
        assertEquals(listOf("door", "yard"), first)
        // The garage comes closer than the yard: the door keeps its screen, the yard's goes to the garage.
        assertEquals(listOf("door", "garage"), assignScreens(first, listOf("garage", "door", "yard")))
        assertEquals(listOf(null, null), assignScreens(first, emptyList()))
    }

    @Test fun pictureFitsInsideTheFrame() {
        assertEquals(.5f, screenScale(.8f, .45f), .001f)
        assertEquals(.25f, screenScale(.4f, 1f), .001f)
    }

    @Test fun cameraScreenStays16By9InsideATvFace() {
        val face = vn.homepanel.spatial.FittedFrame(com.meta.spatial.core.Pose(), 1.2f, .8f)
        val screen = vn.homepanel.spatial.screenInFace(face)
        assertEquals(1.2f, screen.width, .001f); assertEquals(.675f, screen.height, .001f)
        val tall = vn.homepanel.spatial.screenInFace(face.copy(width = 2f, height = .45f))
        assertEquals(.8f, tall.width, .001f); assertEquals(.45f, tall.height, .001f)
        assertEquals(.45f, vn.homepanel.spatial.screenHeight(.8f), .001f)
    }
}
