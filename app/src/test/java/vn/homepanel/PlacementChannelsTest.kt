package vn.homepanel

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import vn.homepanel.ha.*

class PlacementChannelsTest {
    private fun channel(id:String,name:String)=HaEntity("switch.$id","off",JSONObject().put("friendly_name",name))
    @Test fun bothChannelsAreVisibleIncludingOfflineButSetupAndDiagnosticsStayHidden() {
        val play=channel("play","Giải trí")
        val work=channel("work","Làm việc").copy(state="unavailable")
        val device=HaDevice("device:dual","Công tắc phòng khách","",listOf(play,work,channel("led","LED config").copy(category="config"),play.copy(id="switch.hidden",hidden=true),play.copy(id="switch.disabled",disabled=true),HaEntity("sensor.rssi","-50")))
        assertEquals(listOf(play,work),placementChannels(device))
        assertEquals("Giải trí",placementLabel(device,play.id))
        assertEquals("Làm việc",placementLabel(device,work.id))
    }
    @Test fun singleControlKeepsTheDeviceLabelDespiteDiagnosticEntities() {
        val work=channel("work","Relay 1")
        val device=HaDevice("device:dual","Desk switch","",listOf(work,HaEntity("sensor.rssi","-50")))
        assertEquals("Desk switch",placementLabel(device,work.id))
    }
}
