package vn.homepanel

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import vn.homepanel.ha.*

class HomeOverviewTest {
    private fun e(id: String, state: String, deviceClass: String? = null, area: String? = null, extra: JSONObject.() -> Unit = {}) =
        HaEntity(id, state, JSONObject().apply { deviceClass?.let { put("device_class", it) }; extra() }, areaId = area)
    private fun catalog(vararg list: HaEntity) = Catalog(entities = list.associateBy { it.id })

    @Test fun openDoorsUnlockedLocksAndAlertsMakeTheHomeInsecure() {
        val o = homeOverview(catalog(
            e("binary_sensor.front_door", "on", "door"), e("binary_sensor.window", "off", "window"),
            e("lock.gate", "unlocked"), e("lock.back", "locked"), e("binary_sensor.smoke", "off", "smoke"),
            e("alarm_control_panel.home", "armed_away"), e("binary_sensor.hall_motion", "on", "motion"),
        ))
        assertEquals(2, o.openings); assertEquals(listOf("binary_sensor.front_door"), o.open.map { it.id })
        assertEquals(2, o.locks); assertEquals(listOf("lock.gate"), o.unlocked.map { it.id })
        assertTrue(o.alerts.isEmpty()); assertEquals(1, o.motion.size)
        assertFalse(o.secure)
        assertTrue(homeOverview(catalog(e("binary_sensor.door", "off", "door"), e("lock.back", "locked"))).secure)
    }

    @Test fun temperaturesComeFromSensorsAndThermostatsOnlyFillGaps() {
        val o = homeOverview(catalog(
            e("sensor.bed_temp", "27.5", "temperature", "bed") { put("unit_of_measurement", "°C") },
            e("climate.bed_ac", "cool", area = "bed") { put("current_temperature", 30) },
            e("climate.living_ac", "cool", area = "living") { put("current_temperature", 29) },
            e("sensor.humidity", "60", "humidity"), e("sensor.broken", "unavailable", "temperature"),
        ))
        assertEquals(listOf(27.5f, 29f), o.temperatures)
        assertEquals("°C", o.temperatureUnit)
        assertEquals(60f, o.humidity!!, .01f)
    }
}
