package vn.homepanel.spatial

import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.toolkit.ControllerType

enum class InputSide { LEFT, RIGHT }

data class SelectionSample(
    val side: InputSide,
    val type: ControllerType,
    val active: Boolean,
    val buttons: Int,
    val raised: Boolean,
)

/** Raw hand pinches use X/A; controller triggers are a separate input path. */
fun selectionPressed(sample: SelectionSample): Boolean {
    if (!sample.active) return false
    val mask = when (sample.type) {
        ControllerType.HAND -> if (sample.side == InputSide.LEFT) ButtonBits.ButtonX else ButtonBits.ButtonA
        ControllerType.CONTROLLER -> if (sample.side == InputSide.LEFT) ButtonBits.ButtonTriggerL else ButtonBits.ButtonTriggerR
        else -> return false
    }
    return sample.buttons and mask != 0
}

/** Requires a release after tracking loss, mode changes, UI interaction or system gestures. */
class SelectionInput {
    private data class State(val type: ControllerType, val armed: Boolean)
    private val states = mutableMapOf<InputSide, State>()

    fun update(samples: List<SelectionSample>, blocked: Boolean = false): Boolean {
        states.keys.retainAll(samples.filter { it.active }.map { it.side }.toSet())
        var selected = false
        for (sample in samples) {
            if (!sample.active) continue
            val pressed = selectionPressed(sample)
            val systemGesture = sample.buttons and (ButtonBits.ButtonMenu or ButtonBits.ButtonSystem) != 0
            val previous = states[sample.side]?.takeIf { it.type == sample.type }
            if (!blocked && !systemGesture && pressed && sample.raised && previous?.armed == true) selected = true
            states[sample.side] = State(sample.type, !pressed && !blocked && !systemGesture)
        }
        return selected
    }

    fun reset() = states.clear()
}
