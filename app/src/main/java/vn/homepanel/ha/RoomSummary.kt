package vn.homepanel.ha

/** Presentation only: never infers device state or sends commands. */
data class RoomSummary(val id: String, val name: String, val devices: List<HaDevice>, val lightsOn: Int, val unavailable: Int)

fun HaDevice.belongsToRoom(id: String) = if (id.isBlank()) areaIds.isEmpty() || entities.any { it.areaId == null } else id in areaIds

fun primaryEntity(device: HaDevice, roomId: String? = null): HaEntity? = device.entities.filter { roomId == null || it.areaId.orEmpty() == roomId }.sortedBy {
    val rank = listOf("camera", "climate", "light", "fan", "cover", "switch", "input_boolean", "sensor").indexOf(it.domain).let { n -> if (n < 0) 20 else n }
    rank + if (!it.isEveryday()) 100 else 0
}.firstOrNull()

fun summarizeRooms(catalog: Catalog): List<RoomSummary> = (catalog.devices.flatMap { it.areaIds }.toSet() +
    if(catalog.devices.any { it.belongsToRoom("") }) setOf("") else emptySet()).map { id ->
    val devices = catalog.devices.filter { it.belongsToRoom(id) }
    val entities = devices.flatMap { it.entities }.distinctBy { it.id }.filter { it.areaId.orEmpty() == id && !it.hidden && it.category == null && !it.disabled }
    RoomSummary(id, catalog.areas[id] ?: id, devices, entities.count { it.isLight() && it.state == "on" },
        devices.count { d -> d.entities.filter { it.areaId.orEmpty() == id }.let { it.isNotEmpty() && it.none { e -> e.available } } })
}.sortedWith(compareBy<RoomSummary> { it.name.isBlank() }.thenBy { it.name })
