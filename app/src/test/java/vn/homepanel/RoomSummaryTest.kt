package vn.homepanel

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import vn.homepanel.ha.*

class RoomSummaryTest {
    @Test fun roomsUseStableIdsAndAnEntityOverrideIsCountedOnlyInItsOwnRoom() {
        val catalog=buildCatalog(listOf(HaEntity("light.a","on"),HaEntity("light.b","on")),
            JSONArray("""[{"id":"shared","area_id":"a"}]"""),
            JSONArray("""[{"entity_id":"light.a","device_id":"shared"},{"entity_id":"light.b","device_id":"shared","area_id":"b"}]"""),
            JSONArray("""[{"area_id":"a","name":"Same name"},{"area_id":"b","name":"Same name"}]"""),JSONObject())
        val rooms=summarizeRooms(catalog)
        assertEquals(setOf("a","b"),rooms.map { it.id }.toSet())
        assertTrue(rooms.all { it.lightsOn==1 && it.devices.size==1 })
        assertEquals("light.b",primaryEntity(catalog.devices.single(),"b")!!.id)
    }
    @Test fun summaryTracksConfirmedStateAndKeepsUnavailableSeparate() {
        val catalog = Demo.catalog()
        assertEquals(1, summarizeRooms(catalog).first { it.name == "Living room" }.lightsOn)
        assertEquals(1, summarizeRooms(catalog).first { it.name == "Kitchen" }.unavailable)
        val changed = Demo.apply(catalog, ServiceCall("light","turn_off","light.living"))
        assertEquals(0, summarizeRooms(changed).first { it.name == "Living room" }.lightsOn)
        assertEquals(1, summarizeRooms(catalog).first { it.name == "Living room" }.lightsOn)
    }
    @Test fun hiddenAndDiagnosticFunctionsRemainAccessibleButAreNotDefaultsOrLightCounts() {
        val catalog = buildCatalog(listOf(HaEntity("light.diagnostic","on"),HaEntity("light.hidden","on"),HaEntity("fan.main","off")),
            JSONArray("""[{"id":"a","area_id":"room"}]"""),
            JSONArray("""[{"entity_id":"light.diagnostic","device_id":"a","entity_category":"diagnostic"},{"entity_id":"light.hidden","device_id":"a","hidden_by":"user"},{"entity_id":"fan.main","device_id":"a"}]"""),
            JSONArray("""[{"area_id":"room","name":"Bedroom"}]"""),JSONObject())
        assertEquals(3,catalog.entities.size)
        assertEquals("fan.main",primaryEntity(catalog.devices.single())!!.id)
        assertEquals(0,summarizeRooms(catalog).single().lightsOn)
    }
    @Test fun unknownLightIsNeitherOnNorAHealthyDeviceAndUnassignedDevicesRemainListed() {
        val unknown=HaEntity("light.a","unknown")
        val room=summarizeRooms(Catalog(devices=listOf(HaDevice("a","Lamp","",listOf(unknown)),HaDevice("empty","Empty","",emptyList())))).single()
        assertEquals("",room.name)
        assertEquals(2,room.devices.size)
        assertEquals(0,room.lightsOn)
        assertEquals(1,room.unavailable)
    }
}
