package vn.homepanel.ha

import kotlin.math.roundToInt

/** The icon states describe an explicit press, never a command triggered by gaze. */
fun compactPowerAction(entity: HaEntity, catalog: Catalog): Control? {
    val on = entity.state == "off"
    val service = if (on) "turn_on" else "turn_off"
    if (entity.domain in setOf("light", "switch", "input_boolean", "fan") && catalog.supports(entity.domain,service)) return Control.Power(on)
    if (entity.domain != "climate") return null
    if (entity.supports(if (on) 256 else 128) && catalog.supports("climate",service)) return Control.Power(on)
    if (!catalog.supports("climate","set_hvac_mode")) return null
    val modes = entity.attributes.strings("hvac_modes")
    val mode = if (on) listOf("cool","auto","heat_cool","heat","fan_only","dry").firstOrNull { it in modes } else "off".takeIf { it in modes }
    return mode?.let(Control::Mode)
}

data class CompactAdjustment(val label: Int, val value: Float, val min: Float, val max: Float, val step: Float, val suffix: String, val domain: String) {
    fun action(increase: Boolean): Control {
        val next = (value + if (increase) step else -step).coerceIn(min,max)
        return when(domain) {
            "climate" -> Control.Temperature(next.toDouble())
            "light" -> Control.Brightness(next.roundToInt())
            else -> Control.FanSpeed(next.roundToInt())
        }
    }
}

fun compactAdjustment(entity: HaEntity, catalog: Catalog): CompactAdjustment? {
    val a=entity.attributes
    return when {
        entity.domain=="climate" && entity.supports(1) && catalog.supports("climate","set_temperature") -> {
            val value=a.optDouble("temperature",Double.NaN).toFloat()
            val min=a.optDouble("min_temp",7.0).toFloat();val max=a.optDouble("max_temp",35.0).toFloat()
            if (!value.isFinite() || !min.isFinite() || !max.isFinite() || min>=max || value !in min..max) null
            else CompactAdjustment(vn.homepanel.R.string.target_temperature,value,min,max,a.optDouble("target_temp_step",1.0).toFloat().takeIf { it.isFinite() && it>0 } ?: 1f,"°","climate")
        }
        entity.domain=="light" && a.strings("supported_color_modes").any { it !in setOf("onoff","unknown") } && catalog.supports("light","turn_on") ->
            CompactAdjustment(vn.homepanel.R.string.brightness,brightnessPercent(entity),0f,100f,10f,"%","light")
        entity.domain=="fan" && entity.supports(1) && catalog.supports("fan","set_percentage") ->
            CompactAdjustment(vn.homepanel.R.string.speed,a.optInt("percentage",0).toFloat().coerceIn(0f,100f),0f,100f,a.optDouble("percentage_step",10.0).toFloat().takeIf { it.isFinite() && it>0 } ?: 10f,"%","fan")
        else -> null
    }
}

/** HA reports brightness as null while a light is off; showing 100% there made "−" turn the light on at 99%. */
fun brightnessPercent(entity: HaEntity): Float {
    val a = entity.attributes
    if (entity.state != "on" || a.isNull("brightness")) return 0f
    return (a.optInt("brightness") / 255f * 100).coerceIn(1f, 100f)
}
