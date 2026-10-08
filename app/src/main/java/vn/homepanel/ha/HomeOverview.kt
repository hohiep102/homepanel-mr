package vn.homepanel.ha

/** What a glance at the home should answer: is it secure, is anything open or alarming, how warm is it. */
data class HomeOverview(
    val alarm: HaEntity?,
    val locks: Int,
    val unlocked: List<HaEntity>,
    val openings: Int,
    val open: List<HaEntity>,
    val alerts: List<HaEntity>,
    val motion: List<HaEntity>,
    val temperatures: List<Float>,
    val temperatureUnit: String,
    val humidity: Float?,
) {
    val secure get() = unlocked.isEmpty() && open.isEmpty() && alerts.isEmpty() && alarm?.state != "triggered"
    val hasSecurity get() = alarm != null || locks > 0 || openings > 0
}

private val openingClasses = setOf("door", "window", "opening", "garage_door")
private val alertClasses = setOf("smoke", "gas", "carbon_monoxide", "moisture", "safety", "tamper", "problem")
private val motionClasses = setOf("motion", "occupancy", "presence")

fun homeOverview(catalog: Catalog): HomeOverview {
    val entities = catalog.entities.values.filter { !it.hidden && !it.disabled && it.category == null }
    fun binary(classes: Set<String>) = entities.filter { it.domain == "binary_sensor" && it.attributes.nullString("device_class") in classes }
    val openings = binary(openingClasses) + entities.filter { it.domain == "cover" && it.attributes.nullString("device_class") in setOf("door", "garage", "gate") }
    val locks = entities.filter { it.domain == "lock" }
    // Room sensors first; thermostats only where no sensor reports, so one room is not counted twice.
    val sensorTemps = entities.filter { it.domain == "sensor" && it.attributes.nullString("device_class") == "temperature" && it.available }
    val sensorAreas = sensorTemps.mapNotNull { it.areaId }.toSet()
    val climateTemps = entities.filter { it.domain == "climate" && (it.areaId == null || it.areaId !in sensorAreas) }
        .mapNotNull { e -> e.attributes.opt("current_temperature")?.toString()?.toFloatOrNull() }
    val temps = sensorTemps.mapNotNull { it.state.toFloatOrNull() } + climateTemps
    val humidity = entities.filter { it.domain == "sensor" && it.attributes.nullString("device_class") == "humidity" && it.available }.mapNotNull { it.state.toFloatOrNull() }
    return HomeOverview(
        alarm = entities.firstOrNull { it.domain == "alarm_control_panel" },
        locks = locks.size,
        unlocked = locks.filter { it.state in setOf("unlocked", "open", "opening", "jammed") },
        openings = openings.size,
        open = openings.filter { it.state in setOf("on", "open", "opening") },
        alerts = binary(alertClasses).filter { it.state == "on" },
        motion = binary(motionClasses).filter { it.state == "on" },
        temperatures = temps.filter { it.isFinite() }.sorted(),
        temperatureUnit = sensorTemps.firstNotNullOfOrNull { it.attributes.nullString("unit_of_measurement") } ?: catalog.temperatureUnit.ifBlank { "°C" },
        humidity = humidity.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
    )
}
