package vn.homepanel.ha

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import kotlin.math.round

fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONObject.strings(key: String): List<String> = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
fun JSONObject.nullString(key: String): String? = optString(key).takeUnless { it.isBlank() || it == "null" }

data class HaEntity(val id: String, val state: String, val attributes: JSONObject = JSONObject(), val deviceId: String? = null, val areaId: String? = null, val disabled: Boolean = false, val updatedAt: String? = null, val hidden: Boolean = false, val category: String? = null) {
    val domain get() = id.substringBefore('.')
    val name get() = attributes.nullString("friendly_name") ?: id.substringAfter('.')
    val available get() = !disabled && state !in setOf("unavailable", "unknown", "missing")
    val features get() = attributes.optInt("supported_features", 0)
    fun supports(flag: Int) = features and flag != 0
    companion object {
        fun fromJson(o: JSONObject) = HaEntity(o.getString("entity_id"), o.optString("state", "unknown"), o.optJSONObject("attributes") ?: JSONObject(), updatedAt = o.nullString("last_updated") ?: o.nullString("last_changed"))
    }
}
data class HaDevice(val key: String, val name: String, val area: String, val entities: List<HaEntity>, val areaIds: Set<String> = emptySet())
data class Catalog(val entities: Map<String, HaEntity> = emptyMap(), val devices: List<HaDevice> = emptyList(), val services: JSONObject = JSONObject(), val warnings: List<Int> = emptyList(), val temperatureUnit: String = "", val areas: Map<String,String> = emptyMap()) {
    fun supports(domain: String, service: String) = services.optJSONObject(domain)?.has(service) == true
}

fun buildCatalog(states: List<HaEntity>, deviceRegistry: JSONArray, entityRegistry: JSONArray, areas: JSONArray, services: JSONObject, warnings: List<Int> = emptyList()): Catalog {
    val registrations = entityRegistry.objects().associateBy { it.optString("entity_id") }
    val devices = deviceRegistry.objects().associateBy { it.optString("id") }
    val roomNames = areas.objects().associate { it.optString("area_id") to it.optString("name") }
    val stateMap = states.associateBy { it.id }.toMutableMap()
    // Disabled entities may not appear in get_states. Keep them discoverable, never controllable.
    registrations.forEach { (id, registry) ->
        if (id.isNotBlank() && id !in stateMap) stateMap[id] = HaEntity(id, "missing", JSONObject().put("friendly_name", registry.nullString("name") ?: registry.nullString("original_name") ?: id))
    }
    val enriched = stateMap.values.map { e ->
        val registry = registrations[e.id]
        val deviceId = registry?.nullString("device_id")
        e.copy(deviceId = deviceId, areaId = registry?.nullString("area_id") ?: deviceId?.let { devices[it]?.nullString("area_id") }, disabled = registry?.nullString("disabled_by") != null, hidden = registry?.nullString("hidden_by") != null, category = registry?.nullString("entity_category"))
    }
    val groups = enriched.groupBy { it.deviceId?.let { id -> "device:$id" } ?: "entity:${it.id}" }.map { (key, entities) ->
        val device = entities.first().deviceId?.let { devices[it] }
        val names = entities.mapNotNull { e -> e.areaId?.let { roomNames[it] } }.distinct()
        HaDevice(key, device?.nullString("name_by_user") ?: device?.nullString("name") ?: entities.first().name, names.joinToString(" · ").ifBlank { "" }, entities.sortedBy { it.name }, entities.mapNotNull { it.areaId }.toSet())
    }.sortedWith(compareBy({ it.area }, { it.name }))
    // Devices with no entities remain listed, so import does not silently omit them.
    val populated = groups.map { it.key }.toSet()
    val emptyDevices = devices.filter { "device:${it.key}" !in populated }.map { (id, d) ->
        HaDevice("device:$id", d.nullString("name_by_user") ?: d.optString("name", id), d.nullString("area_id")?.let { roomNames[it] } ?: "", emptyList(), setOfNotNull(d.nullString("area_id")))
    }
    return Catalog(enriched.associateBy { it.id }, (groups + emptyDevices).sortedBy { it.name }, services, warnings, areas = roomNames)
}

data class ServerAddress(val base: String, val websocket: String, val key: String, val insecure: Boolean) {
    companion object {
        fun parse(raw: String): ServerAddress {
            val uri = URI(raw.trim().trimEnd('/'))
            require(uri.scheme in listOf("http", "https")) { "Address must start with http:// or https://" }
            require(uri.port == -1 || uri.port in 1..65535) { "Port must be within 1-65535." }
            require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) { "Invalid HA address; do not place tokens in URLs." }
            val path = uri.path.orEmpty().trimEnd('/')
            val canonical = URI(uri.scheme.lowercase(), null, uri.host.lowercase(), if ((uri.scheme == "https" && uri.port == 443) || (uri.scheme == "http" && uri.port == 80)) -1 else uri.port, path, null, null).toString()
            val ws = canonical.replaceFirst("http", "ws") + "/api/websocket"
            val key = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }
            return ServerAddress(canonical, ws, key, uri.scheme == "http")
        }
    }
}

data class ServiceCall(val domain: String, val service: String, val entityId: String, val data: JSONObject = JSONObject()) {
    fun toJson() = JSONObject().put("type", "call_service").put("domain", domain).put("service", service).put("target", JSONObject().put("entity_id", entityId)).put("service_data", data)
}
sealed interface Control {
    data class Power(val on: Boolean) : Control
    data class Brightness(val percent: Int) : Control
    data class Temperature(val value: Double) : Control
    data class Mode(val value: String) : Control
    data class FanSpeed(val percent: Int) : Control
    data class Cover(val command: String) : Control
    data class CoverPosition(val percent: Int) : Control
}

fun buildServiceCall(entity: HaEntity, control: Control, catalog: Catalog): ServiceCall {
    require(entity.available) { "Device unavailable." }
    val d = entity.domain
    val a = entity.attributes
    val (service, data) = when (control) {
        is Control.Power -> {
            require(d in setOf("light", "switch", "input_boolean", "fan", "climate")) { "Power control unsupported." }
            require(d != "climate" || entity.supports(if (control.on) 256 else 128)) { "Select HVAC mode to control this device." }
            (if (control.on) "turn_on" else "turn_off") to JSONObject()
        }
        is Control.Brightness -> {
            require(d == "light" && a.strings("supported_color_modes").any { it !in setOf("onoff", "unknown") }) { "Brightness unsupported." }
            require(control.percent in 0..100) { "Brightness must be within 0-100%." }
            if (control.percent == 0) "turn_off" to JSONObject() else "turn_on" to JSONObject().put("brightness_pct", control.percent)
        }
        is Control.Temperature -> {
            require(d == "climate" && entity.supports(1)) { "Single target temperature unsupported." }
            val min = a.optDouble("min_temp", 7.0); val max = a.optDouble("max_temp", 35.0)
            val step = a.optDouble("target_temp_step", 1.0).takeIf { it > 0 && it.isFinite() } ?: 1.0
            require(control.value.isFinite() && control.value in min..max) { "Temperature outside device limits." }
            val value = (min + round((control.value - min) / step) * step).coerceIn(min, max)
            "set_temperature" to JSONObject().put("temperature", value)
        }
        is Control.Mode -> {
            require(d == "climate" && control.value in a.strings("hvac_modes")) { "Mode unsupported." }
            "set_hvac_mode" to JSONObject().put("hvac_mode", control.value)
        }
        is Control.FanSpeed -> {
            require(d == "fan" && entity.supports(1) && control.percent in 0..100) { "Speed unsupported." }
            val step = a.optDouble("percentage_step", 1.0).takeIf { it > 0 && it.isFinite() } ?: 1.0
            "set_percentage" to JSONObject().put("percentage", (round(control.percent / step) * step).coerceIn(0.0, 100.0).toInt())
        }
        is Control.Cover -> {
            val flag = mapOf("open_cover" to 1, "close_cover" to 2, "stop_cover" to 8)[control.command]
            require(d == "cover" && flag != null && entity.supports(flag)) { "Cover command unsupported." }
            control.command to JSONObject()
        }
        is Control.CoverPosition -> {
            require(d == "cover" && entity.supports(4) && control.percent in 0..100) { "Cover position unsupported." }
            "set_cover_position" to JSONObject().put("position", control.percent)
        }
    }
    require(catalog.supports(d, service)) { "Home Assistant does not provide $d.$service." }
    return ServiceCall(d, service, entity.id, data)
}
