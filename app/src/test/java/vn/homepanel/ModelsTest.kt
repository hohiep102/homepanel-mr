package vn.homepanel

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import vn.homepanel.ha.*

class ModelsTest {
    @Test fun groupsDeviceFunctionsAndPreservesDisabledAndEmptyDevices() {
        val states = listOf(HaEntity("light.a", "on"), HaEntity("sensor.a", "23"))
        val catalog = buildCatalog(states, JSONArray("""[{"id":"a","name":"Combo","area_id":"room"},{"id":"empty","name":"Empty"}]"""), JSONArray("""[{"entity_id":"light.a","device_id":"a"},{"entity_id":"sensor.a","device_id":"a","area_id":"override"},{"entity_id":"switch.disabled","device_id":"a","disabled_by":"user"}]"""), JSONArray("""[{"area_id":"room","name":"Living"},{"area_id":"override","name":"Kitchen"}]"""), JSONObject())
        assertEquals(2, catalog.devices.size)
        assertEquals(3,catalog.devices.first { it.key == "device:a" }.entities.size)
        assertTrue(catalog.entities.getValue("switch.disabled").disabled)
        assertFalse(catalog.entities.getValue("switch.disabled").available)
        assertEquals("override",catalog.entities.getValue("sensor.a").areaId)
        assertEquals("room",catalog.entities.getValue("light.a").areaId)
        assertTrue(catalog.devices.first { it.key == "device:empty" }.entities.isEmpty())
    }
    @Test fun sendsEntityTargetAndCapabilitySpecificBrightness() {
        val catalog = Demo.catalog()
        val call = buildServiceCall(catalog.entities.getValue("light.living"),Control.Brightness(42),catalog)
        assertEquals("light.living",call.toJson().getJSONObject("target").getString("entity_id"))
        assertEquals(42,call.data.getInt("brightness_pct"))
        assertEquals("turn_on",call.service)
    }
    @Test fun offLightStartsFromZeroAndZeroTurnsItOff() {
        val catalog = Demo.catalog()
        val on = catalog.entities.getValue("light.living")
        val off = on.copy(state = "off", attributes = JSONObject(on.attributes.toString()).put("brightness", JSONObject.NULL))
        assertEquals(0f, brightnessPercent(off), 0f)
        assertEquals(184 / 255f * 100, brightnessPercent(on), .01f)
        assertEquals("turn_off", buildServiceCall(on, Control.Brightness(0), catalog).service)
        assertEquals(10, buildServiceCall(off, compactAdjustment(off, catalog)!!.action(true), catalog).data.getInt("brightness_pct"))
    }
    @Test fun rejectsUnavailableAndUnsupportedControls() {
        val c = Demo.catalog()
        assertThrows(IllegalArgumentException::class.java) { buildServiceCall(c.entities.getValue("switch.coffee"),Control.Power(true),c) }
        assertThrows(IllegalArgumentException::class.java) { buildServiceCall(c.entities.getValue("sensor.room_temperature"),Control.Power(true),c) }
        assertThrows(IllegalArgumentException::class.java) { buildServiceCall(c.entities.getValue("climate.bedroom"),Control.Mode("heat"),c) }
        assertThrows(IllegalArgumentException::class.java) { buildServiceCall(c.entities.getValue("climate.bedroom"),Control.Temperature(45.0),c) }
        assertThrows(IllegalArgumentException::class.java) { buildServiceCall(c.entities.getValue("light.living"),Control.Power(true),c.copy(services = JSONObject())) }
    }
    @Test fun thermostatRespectsStepAndRange() {
        val c = Demo.catalog(); val ac = c.entities.getValue("climate.bedroom")
        assertEquals(25.5, buildServiceCall(ac,Control.Temperature(25.4),c).data.getDouble("temperature"),.001)
        assertThrows(IllegalArgumentException::class.java) { buildServiceCall(ac,Control.Temperature(Double.NaN),c) }
        assertThrows(IllegalArgumentException::class.java) { buildServiceCall(ac.copy(attributes = JSONObject().put("supported_features",2)),Control.Temperature(25.0),c) }
    }
    @Test fun validatesCoverFeaturesAndFanStep() {
        val c = Demo.catalog()
        assertEquals(75,buildServiceCall(c.entities.getValue("fan.desk"),Control.FanSpeed(68),c).data.getInt("percentage"))
        val cover = c.entities.getValue("cover.curtains").copy(attributes = JSONObject().put("supported_features",1))
        assertThrows(IllegalArgumentException::class.java) { buildServiceCall(cover,Control.Cover("close_cover"),c) }
        assertThrows(IllegalArgumentException::class.java) { buildServiceCall(cover,Control.CoverPosition(40),c) }
    }
    @Test fun canonicalAddressSeparatesServersWithoutSecrets() {
        assertEquals(ServerAddress.parse("https://HA.EXAMPLE:443/").key,ServerAddress.parse("https://ha.example").key)
        assertEquals("wss://ha.example/api/websocket",ServerAddress.parse("https://ha.example/").websocket)
        assertNotEquals(ServerAddress.parse("http://ha.local:8123").key,ServerAddress.parse("https://ha.local:8123").key)
        assertThrows(IllegalArgumentException::class.java) { ServerAddress.parse("https://token@example.com") }
        assertThrows(IllegalArgumentException::class.java) { ServerAddress.parse("https://example.com?token=secret") }
    }
}
