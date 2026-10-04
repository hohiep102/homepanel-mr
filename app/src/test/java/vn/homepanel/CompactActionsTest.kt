package vn.homepanel

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import vn.homepanel.ha.*

class CompactActionsTest {
    private val catalog=Demo.catalog()
    @Test fun switchPowerUsesExplicitOnOrOffFromCurrentState() {
        val off=HaEntity("switch.kitchen_2","off")
        assertEquals(Control.Power(true),compactPowerAction(off,catalog))
        assertEquals(Control.Power(false),compactPowerAction(off.copy(state="on"),catalog))
        assertNull(compactPowerAction(off,catalog.copy(services=JSONObject())))
    }
    @Test fun climatePowerUsesSupportedModeWhenIntegrationHasNoTurnOnFeature() {
        val ac=HaEntity("climate.ac","off",JSONObject("""{"supported_features":1,"hvac_modes":["off","cool"]}"""))
        val on=compactPowerAction(ac,catalog)!!
        assertEquals(Control.Mode("cool"),on)
        assertEquals("set_hvac_mode",buildServiceCall(ac,on,catalog).service)
        assertEquals(Control.Mode("off"),compactPowerAction(ac.copy(state="cool"),catalog))
        assertNull(compactPowerAction(ac.copy(attributes=JSONObject("""{"hvac_modes":["off"]}""")),catalog))
    }
    @Test fun climatePowerUsesNativePowerWhenFeaturesAndServicesAllowIt() {
        val services=JSONObject("""{"climate":{"turn_on":{},"turn_off":{}}}""")
        val ac=HaEntity("climate.ac","off",JSONObject().put("supported_features",384))
        assertEquals(Control.Power(true),compactPowerAction(ac,catalog.copy(services=services)))
        assertEquals(Control.Power(false),compactPowerAction(ac.copy(state="cool"),catalog.copy(services=services)))
    }
    @Test fun temperatureButtonsRespectDeviceStepLimitsAndMissingTarget() {
        val ac=HaEntity("climate.ac","cool",JSONObject("""{"supported_features":1,"temperature":29.5,"min_temp":16,"max_temp":30,"target_temp_step":0.5}"""))
        val adjustment=compactAdjustment(ac,catalog)!!
        assertEquals(Control.Temperature(30.0),adjustment.action(true))
        assertEquals(Control.Temperature(29.0),adjustment.action(false))
        assertEquals(Control.Temperature(30.0),adjustment.copy(value=30f).action(true))
        assertEquals(Control.Temperature(16.0),adjustment.copy(value=16f).action(false))
        assertNull(compactAdjustment(ac.copy(attributes=JSONObject().put("supported_features",1)),catalog))
    }
}
