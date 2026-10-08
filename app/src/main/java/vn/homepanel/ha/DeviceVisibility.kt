package vn.homepanel.ha

/** A UI projection only. The complete HA catalog and saved bindings are never deleted. */
private val everydayDomains = setOf(
    "light", "climate", "fan", "cover", "switch", "input_boolean", "media_player",
    "lock", "vacuum", "water_heater", "humidifier", "alarm_control_panel", "camera", "valve",
)
private val roomSensorClasses = setOf(
    "temperature", "humidity", "carbon_dioxide", "carbon_monoxide", "aqi", "pm1", "pm25", "pm10",
    "volatile_organic_compounds", "volatile_organic_compounds_parts", "illuminance", "power", "energy",
    "gas", "water", "volume", "volume_storage", "volume_flow_rate", "pressure", "atmospheric_pressure", "moisture",
)
private val roomBinaryClasses = setOf(
    "door", "window", "opening", "garage_door", "motion", "occupancy", "presence",
    "smoke", "gas", "carbon_monoxide", "moisture", "safety", "tamper", "lock",
)
private val roomSensorUnits = setOf("°C", "°F", "%", "W", "kW", "Wh", "kWh", "MWh", "ppm", "lx", "µg/m³", "μg/m³", "m³", "m3", "L", "l")

/**
 * Integrations (cameras especially) often expose settings as plain switches without marking them as
 * configuration: away or privacy mode, motion detection, notifications, status lights. Matched as whole
 * words in English and Vietnamese; ids use underscores, so they are split too. Android's ICU regex has no
 * (?U) flag, so word edges are spelled out with letter classes and the text is lowercased instead.
 */
private val settingSwitch = Regex(
    "(?<![\\p{L}\\p{N}])(away|vắng nhà|privacy|riêng tư|detection|detect|phát hiện|notifications?|thông báo|alerts?|cảnh báo|indicator|status light|đèn báo|đèn trạng thái|" +
        "child lock|khoá trẻ em|khóa trẻ em|do not disturb|không làm phiền|night vision|hồng ngoại|tầm nhìn đêm|flip|lật hình|record(ing)?|ghi hình|" +
        "auto update|tự động cập nhật|sensitivity|độ nhạy|buzzer|beep|còi|tracking|theo dõi|mode|chế độ)(?![\\p{L}\\p{N}])"
)

fun HaEntity.isSetting() = (domain == "switch" || domain == "input_boolean") &&
    settingSwitch.containsMatchIn((name + " " + id.substringAfter('.').replace('_', ' ')).lowercase())

private val lightWords = Regex("(?<![\\p{L}\\p{N}])(đèn|bóng đèn|light|lights|lamp|chandelier|downlight|spotlight)(?![\\p{L}\\p{N}])")

/** Smart wall switches wired to lights are `switch` entities in HA; they count as lights when named as one. */
fun HaEntity.isLight() = domain == "light" ||
    (domain == "switch" && !isSetting() && lightWords.containsMatchIn((name + " " + id.substringAfter('.').replace('_', ' ')).lowercase()))

fun HaEntity.isEveryday(): Boolean {
    if (hidden || disabled || category != null) return false
    if (isSetting()) return false
    if (domain in everydayDomains) return true
    val deviceClass = attributes.nullString("device_class")
    return when (domain) {
        "sensor" -> if (deviceClass != null) deviceClass in roomSensorClasses else attributes.nullString("unit_of_measurement") in roomSensorUnits
        "binary_sensor" -> deviceClass in roomBinaryClasses
        else -> false
    }
}

fun deviceCatalogForDisplay(catalog: Catalog, showAll: Boolean = false): Catalog {
    if (showAll) return catalog
    val devices = catalog.devices.mapNotNull { device ->
        val entities = device.entities.filter { it.isEveryday() }
        if (entities.isEmpty()) null else {
            val areaIds = entities.mapNotNull { it.areaId }.toSet()
            device.copy(entities = entities, areaIds = areaIds, area = areaIds.map { catalog.areas[it] ?: it }.distinct().joinToString(" · "))
        }
    }
    return catalog.copy(devices = devices)
}
