package vn.homepanel

import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import org.junit.Assert.*
import org.junit.Test
import vn.homepanel.ha.Demo
import vn.homepanel.spatial.*
import vn.homepanel.storage.SpatialBinding

class RoomContextTest {
    @Test fun roomKindComesFromRoomSetupLabels() {
        assertEquals(RoomKind.BEDROOM, roomKindFromLabels(listOf("WALL_FACE", "BED", "TABLE")))
        assertEquals(RoomKind.LIVING, roomKindFromLabels(listOf("COUCH", "SCREEN")))
        assertEquals(RoomKind.UNKNOWN, roomKindFromLabels(listOf("TABLE", "STORAGE")))
    }

    @Test fun areaNamesAreClassifiedInBothLanguages() {
        assertEquals(RoomKind.BEDROOM, areaKind("Phòng ngủ chính"))
        assertEquals(RoomKind.LIVING, areaKind("Living room"))
        assertEquals(RoomKind.OTHER, areaKind("Bếp"))
        assertEquals(RoomKind.UNKNOWN, areaKind("Tầng 2"))
        assertTrue(areaMismatch(RoomKind.BEDROOM, RoomKind.OTHER))
        assertFalse(areaMismatch(RoomKind.UNKNOWN, RoomKind.OTHER))
        assertFalse(areaMismatch(RoomKind.LIVING, RoomKind.UNKNOWN))
    }

    @Test fun suggestionsAreUnplacedDevicesOfTheMatchingArea() {
        val catalog = Demo.catalog()
        val bedroom = suggestedForRoom(RoomKind.BEDROOM, catalog, emptyList())
        assertTrue(bedroom.isNotEmpty() && bedroom.all { areaKind(it.area) == RoomKind.BEDROOM })
        val placed = SpatialBinding(serverKey = Demo.KEY, deviceKey = bedroom.first().key, entityId = "x", roomId = "r", anchorId = "a", x = 0f, y = 0f, z = 0f, label = "x")
        assertFalse(suggestedForRoom(RoomKind.BEDROOM, catalog, listOf(placed)).any { it.key == placed.deviceKey })
        assertTrue(suggestedForRoom(RoomKind.UNKNOWN, catalog, emptyList()).isEmpty())
    }

    @Test fun overlapOnlyCountsFramesInTheSamePlane() {
        val a = Pose(Vector3(0f, 1f, 2f))
        assertEquals(1f, frameOverlap(a, .6f, .6f, Pose(Vector3(.05f, 1f, 2.02f)), .4f, .4f), .001f)
        assertEquals(0f, frameOverlap(a, .6f, .6f, Pose(Vector3(1f, 1f, 2f)), .4f, .4f), .001f)
        assertEquals(0f, frameOverlap(a, .6f, .6f, Pose(Vector3(0f, 1f, 2.6f)), .4f, .4f), .001f)
        assertEquals(0f, frameOverlap(a, .6f, .6f, Pose(Vector3(0f, 1f, 2f), Quaternion.lookRotation(Vector3(1f, 0f, 0f))), .4f, .4f), .001f)
    }
}
