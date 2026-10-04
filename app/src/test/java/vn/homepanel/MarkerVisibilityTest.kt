package vn.homepanel

import org.junit.Assert.*
import org.junit.Test
import vn.homepanel.spatial.*

class MarkerVisibilityTest {
    @Test fun onlyNearbyMarkersInFrontAreVisible() {
        val origin=Point3(0f,1.6f,0f);val forward=Point3(0f,0f,1f)
        assertTrue(markerVisible(origin,forward,Point3(0f,2.4f,2f)))
        assertFalse(markerVisible(origin,forward,Point3(0f,1.6f,-2f)))
        assertFalse(markerVisible(origin,forward,Point3(0f,1.6f,9f)))
        assertFalse(markerVisible(origin,forward,Point3(3f,1.6f,.1f)))
        assertTrue(markerVisible(origin,Point3(1f,0f,0f),Point3(3f,1.6f,.1f)))
    }
}
