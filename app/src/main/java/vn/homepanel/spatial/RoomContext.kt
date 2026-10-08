package vn.homepanel.spatial

import com.meta.spatial.core.Pose
import com.meta.spatial.core.Vector3
import vn.homepanel.ha.Catalog
import vn.homepanel.ha.HaDevice
import vn.homepanel.ha.deviceCatalogForDisplay
import vn.homepanel.storage.SpatialBinding
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class RoomKind { BEDROOM, LIVING, OTHER, UNKNOWN }

/** Room Setup labels say what kind of room the headset is in; only the two kinds they identify reliably are used. */
fun roomKindFromLabels(labels: Collection<String>): RoomKind {
    val upper = labels.map { it.uppercase() }
    return when {
        upper.any { "BED" in it || "SLEEPING" in it } -> RoomKind.BEDROOM
        upper.any { "COUCH" in it || "SITTING" in it } -> RoomKind.LIVING
        else -> RoomKind.UNKNOWN
    }
}

private val bedroomWords = listOf("bedroom", "bed room", "phòng ngủ", "ngủ")
private val livingWords = listOf("living", "lounge", "family room", "phòng khách", "khách")
private val otherWords = listOf("kitchen", "bếp", "bath", "tắm", "toilet", "wc", "garage", "hall", "hành lang", "office", "làm việc", "study", "garden", "vườn", "balcony", "ban công", "laundry")

/** Classifies an HA area name in English or Vietnamese. */
fun areaKind(name: String): RoomKind {
    val lower = name.lowercase()
    return when {
        bedroomWords.any { it in lower } -> RoomKind.BEDROOM
        livingWords.any { it in lower } -> RoomKind.LIVING
        otherWords.any { it in lower } -> RoomKind.OTHER
        else -> RoomKind.UNKNOWN
    }
}

/** Warn only when both sides are known and clearly differ. */
fun areaMismatch(room: RoomKind, area: RoomKind) = room != RoomKind.UNKNOWN && area != RoomKind.UNKNOWN && room != area

/** Everyday devices whose HA area matches this room and that have nothing placed yet. */
fun suggestedForRoom(kind: RoomKind, catalog: Catalog, bindings: List<SpatialBinding>): List<HaDevice> {
    if (kind == RoomKind.UNKNOWN) return emptyList()
    val placed = bindings.map { it.deviceKey }.toSet()
    return deviceCatalogForDisplay(catalog).devices.filter { it.key !in placed && areaKind(it.area) == kind }
}

/**
 * Share of the smaller frame covered by the other, for frames roughly in the same plane and facing the same way.
 * Frames at different depths (a lamp in front of a painting) do not overlap.
 */
fun frameOverlap(a: Pose, aw: Float, ah: Float, b: Pose, bw: Float, bh: Float): Float {
    val local = a.inverse() * b
    if (abs(local.t.z) > .15f) return 0f
    val facing = (a.q * Vector3(0f, 0f, 1f)).dot(b.q * Vector3(0f, 0f, 1f))
    if (facing < .8f) return 0f
    val x = max(0f, min(aw / 2, local.t.x + bw / 2) - max(-aw / 2, local.t.x - bw / 2))
    val y = max(0f, min(ah / 2, local.t.y + bh / 2) - max(-ah / 2, local.t.y - bh / 2))
    return x * y / min(aw * ah, bw * bh).coerceAtLeast(1e-4f)
}
