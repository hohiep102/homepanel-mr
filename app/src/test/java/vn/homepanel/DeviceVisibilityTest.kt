package vn.homepanel

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import vn.homepanel.ha.*

class DeviceVisibilityTest {
    private fun entity(id: String, category: String? = null, hidden: Boolean = false, disabled: Boolean = false, deviceClass: String? = null, unit: String? = null, state: String = "on") =
        HaEntity(id,state,JSONObject().apply { deviceClass?.let { put("device_class",it) };unit?.let { put("unit_of_measurement",it) } },category=category,hidden=hidden,disabled=disabled)

    @Test fun respectsRegistryFlagsEvenForNormallyUsefulDomains() {
        assertTrue(entity("light.room").isEveryday())
        assertFalse(entity("light.calibration",category="config").isEveryday())
        assertFalse(entity("switch.check",category="diagnostic").isEveryday())
        assertFalse(entity("climate.hidden",hidden=true).isEveryday())
        assertFalse(entity("switch.disabled",disabled=true).isEveryday())
    }

    @Test fun technicalDomainsAndTelemetryStayInAllDevicesWithoutMetadata() {
        for (domain in listOf("automation","update","button","number","select","text","device_tracker")) assertFalse(domain,entity("$domain.example").isEveryday())
        for (deviceClass in listOf("battery","signal_strength","timestamp","duration","data_size")) assertFalse(deviceClass,entity("sensor.example",deviceClass=deviceClass,unit="%").isEveryday())
        assertFalse(entity("binary_sensor.network",deviceClass="connectivity").isEveryday())
    }

    @Test fun roomSensorsAndOfflineControlsRemainFindable() {
        assertTrue(entity("sensor.temperature",deviceClass="temperature",state="unavailable").isEveryday())
        assertTrue(entity("sensor.legacy_temperature",unit="°C").isEveryday())
        assertTrue(entity("binary_sensor.window",deviceClass="window").isEveryday())
        assertTrue(entity("binary_sensor.smoke",deviceClass="smoke").isEveryday())
        assertTrue(entity("switch.coffee",state="unavailable").isEveryday())
        assertTrue(entity("climate.ac",state="unknown").isEveryday())
        assertFalse(entity("sensor.firmware",state="2026.10").isEveryday())
    }

    @Test fun projectionHidesConfigOnlyDevicesAndTechnicalRoomsWithoutChangingSource() {
        val source=buildCatalog(listOf(entity("light.main"),entity("sensor.rssi",deviceClass="signal_strength"),entity("switch.setup")),
            JSONArray("""[{"id":"main","area_id":"living"},{"id":"tech","area_id":"server"},{"id":"empty","area_id":"server"}]"""),
            JSONArray("""[{"entity_id":"light.main","device_id":"main"},{"entity_id":"sensor.rssi","device_id":"main","area_id":"server","entity_category":"diagnostic"},{"entity_id":"switch.setup","device_id":"tech","entity_category":"config"}]"""),
            JSONArray("""[{"area_id":"living","name":"Living"},{"area_id":"server","name":"Server"}]"""),JSONObject())
        val display=deviceCatalogForDisplay(source)
        assertEquals(1,display.devices.size)
        assertEquals(listOf("light.main"),display.devices.single().entities.map { it.id })
        assertEquals(setOf("living"),display.devices.single().areaIds)
        assertEquals(listOf("Living"),summarizeRooms(display).map { it.name })
        assertEquals(3,source.devices.size)
        assertEquals(2,source.devices.first { it.key=="device:main" }.entities.size)
        assertSame(source,deviceCatalogForDisplay(source,showAll=true))
        assertSame(source.entities,display.entities)
    }

    @Test fun unavailableRegistriesStillAllowUsefulEntitiesAndAnExplicitAllFallback() {
        val source=buildCatalog(listOf(entity("light.a"),entity("update.core"),entity("sensor.temp",unit="°C")),JSONArray(),JSONArray(),JSONArray(),JSONObject(),warnings=listOf(123))
        assertEquals(setOf("entity:light.a","entity:sensor.temp"),deviceCatalogForDisplay(source).devices.map { it.key }.toSet())
        assertEquals(3,deviceCatalogForDisplay(source,true).devices.size)
        assertEquals(listOf(123),deviceCatalogForDisplay(source).warnings)
    }

    private fun named(id: String, name: String) = HaEntity(id, "off", JSONObject().put("friendly_name", name))

    @Test fun settingSwitchesWithoutRegistryFlagsAreHidden() {
        for ((id, name) in listOf("switch.cong_away_mode" to "Cổng Away mode", "input_boolean.vang_nha" to "Chế độ vắng nhà", "switch.cam_privacy" to "Camera privacy",
            "switch.cam_motion_detection" to "Camera phát hiện chuyển động", "switch.plug_led" to "Ổ cắm đèn báo", "switch.cam_record" to "Ghi hình thẻ nhớ"))
            assertFalse(name, named(id, name).isEveryday())
        for ((id, name) in listOf("switch.den_led_phong_ngu" to "Đèn LED phòng ngủ", "switch.o_cam_tivi" to "Ổ cắm tivi", "light.recorder_room" to "Đèn chế độ ngủ", "switch.binh_nong_lanh" to "Bình nóng lạnh"))
            assertTrue(name, named(id, name).isEveryday())
    }

    @Test fun wallSwitchesNamedAsLightsCountAsLights() {
        assertTrue(named("switch.cong_tac_phong_khach_1", "Đèn trần phòng khách").isLight())
        assertTrue(named("switch.living_lamp", "Living lamp").isLight())
        assertTrue(named("light.bed", "Bed").isLight())
        assertFalse(named("switch.o_cam_tivi", "Ổ cắm tivi").isLight())
        assertFalse(named("switch.plug_status", "Ổ cắm đèn báo").isLight())
    }
}
