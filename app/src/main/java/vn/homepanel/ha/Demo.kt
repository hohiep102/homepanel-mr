package vn.homepanel.ha

import org.json.JSONArray
import org.json.JSONObject

object Demo {
    const val KEY = "demo-local-only"
    fun catalog(vietnamese: Boolean = false): Catalog {
        val states = JSONArray("""[
          {"entity_id":"light.living","state":"on","attributes":{"friendly_name":"Sofa light","brightness":184,"supported_color_modes":["brightness"]}},
          {"entity_id":"climate.bedroom","state":"cool","attributes":{"friendly_name":"Air conditioner","current_temperature":28,"temperature":25,"min_temp":16,"max_temp":30,"target_temp_step":0.5,"temperature_unit":"°C","supported_features":1,"hvac_modes":["off","cool","dry","fan_only","auto"]}},
          {"entity_id":"fan.desk","state":"on","attributes":{"friendly_name":"Desk fan","percentage":50,"percentage_step":25,"supported_features":1}},
          {"entity_id":"cover.curtains","state":"open","attributes":{"friendly_name":"Curtains","current_position":80,"supported_features":15}},
          {"entity_id":"sensor.room_temperature","state":"27.5","attributes":{"friendly_name":"Room temperature","unit_of_measurement":"°C"}},
          {"entity_id":"switch.coffee","state":"unavailable","attributes":{"friendly_name":"Coffee maker"}}
        ]""")
        val devices = JSONArray("""[{"id":"lamp","name":"Floor lamp","area_id":"living"},{"id":"ac","name":"Bedroom AC","area_id":"bedroom"},{"id":"fan","name":"Desk fan","area_id":"living"},{"id":"curtain","name":"Curtains","area_id":"living"},{"id":"coffee","name":"Coffee maker","area_id":"kitchen"}]""")
        val registry = JSONArray("""[{"entity_id":"light.living","device_id":"lamp"},{"entity_id":"climate.bedroom","device_id":"ac"},{"entity_id":"fan.desk","device_id":"fan"},{"entity_id":"cover.curtains","device_id":"curtain"},{"entity_id":"sensor.room_temperature","device_id":"ac"},{"entity_id":"switch.coffee","device_id":"coffee"}]""")
        val areas = JSONArray("""[{"area_id":"living","name":"Living room"},{"area_id":"bedroom","name":"Bedroom"},{"area_id":"kitchen","name":"Kitchen"}]""")
        val services = JSONObject("""{"light":{"turn_on":{},"turn_off":{}},"switch":{"turn_on":{},"turn_off":{}},"climate":{"set_temperature":{},"set_hvac_mode":{}},"fan":{"turn_on":{},"turn_off":{},"set_percentage":{}},"cover":{"open_cover":{},"close_cover":{},"stop_cover":{},"set_cover_position":{}}}""")
        if (vietnamese) {
            val names = mapOf("Sofa light" to "Đèn sofa", "Air conditioner" to "Điều hòa", "Desk fan" to "Quạt bàn", "Curtains" to "Rèm cửa", "Room temperature" to "Nhiệt độ phòng", "Coffee maker" to "Máy pha cà phê", "Floor lamp" to "Đèn đứng", "Bedroom AC" to "Điều hòa phòng ngủ", "Living room" to "Phòng khách", "Bedroom" to "Phòng ngủ", "Kitchen" to "Bếp")
            states.objects().forEach { val a=it.getJSONObject("attributes"); a.put("friendly_name", names[a.optString("friendly_name")] ?: a.optString("friendly_name")) }
            (devices.objects()+areas.objects()).forEach { it.put("name", names[it.optString("name")] ?: it.optString("name")) }
        }
        return buildCatalog(states.objects().map(HaEntity::fromJson), devices, registry, areas, services)
    }
    fun apply(catalog: Catalog, call: ServiceCall): Catalog {
        val old = catalog.entities.getValue(call.entityId)
        val attrs = JSONObject(old.attributes.toString())
        val state = when(call.service) {
            "turn_on" -> { if (call.data.has("brightness_pct")) attrs.put("brightness", call.data.getInt("brightness_pct") * 255 / 100); "on" }
            "turn_off" -> "off"
            "set_hvac_mode" -> call.data.getString("hvac_mode")
            "set_temperature" -> { attrs.put("temperature", call.data.getDouble("temperature")); old.state }
            "set_percentage" -> { attrs.put("percentage", call.data.getInt("percentage")); if (call.data.getInt("percentage") == 0) "off" else "on" }
            "set_cover_position" -> { val pos = call.data.getInt("position"); attrs.put("current_position", pos); if (pos == 0) "closed" else "open" }
            "open_cover" -> { attrs.put("current_position", 100); "open" }
            "close_cover" -> { attrs.put("current_position", 0); "closed" }
            else -> old.state
        }
        val replacement = old.copy(state = state, attributes = attrs)
        return catalog.copy(entities = catalog.entities + (old.id to replacement), devices = catalog.devices.map { it.copy(entities = it.entities.map { e -> if (e.id == old.id) replacement else e }) })
    }
}
