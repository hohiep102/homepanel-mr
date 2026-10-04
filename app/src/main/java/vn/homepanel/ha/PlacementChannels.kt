package vn.homepanel.ha

/** Individually placeable controls on one HA device, excluding setup/diagnostic entities. */
fun placementChannels(device: HaDevice): List<HaEntity> = device.entities.filter {
    it.isEveryday() && it.domain in setOf("switch", "input_boolean", "light", "fan", "climate", "cover")
}.distinctBy { it.id }

fun placementLabel(device: HaDevice, entityId: String): String =
    if (placementChannels(device).size > 1) device.entities.find { it.id == entityId }?.name ?: device.name
    else device.name
