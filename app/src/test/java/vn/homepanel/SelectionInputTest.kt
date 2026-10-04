package vn.homepanel

import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.toolkit.ControllerType
import org.junit.Assert.*
import org.junit.Test
import vn.homepanel.spatial.*

class SelectionInputTest {
    private fun hand(side: InputSide = InputSide.RIGHT, pressed: Boolean = false, raised: Boolean = true) = SelectionSample(
        side, ControllerType.HAND, true,
        if (!pressed) 0 else if (side == InputSide.LEFT) ButtonBits.ButtonX else ButtonBits.ButtonA, raised,
    )

    @Test fun eitherHandPinchWorksWithoutControllerTriggerBits() {
        for (side in InputSide.entries) {
            val input = SelectionInput()
            assertFalse(input.update(listOf(hand(side))))
            assertTrue(input.update(listOf(hand(side, pressed = true))))
            assertFalse(input.update(listOf(hand(side, pressed = true))))
        }
    }
    @Test fun controllerTriggerRemainsOptionalAndFaceButtonsAreNotHandPinches() {
        val input = SelectionInput()
        val controller = hand().copy(type = ControllerType.CONTROLLER)
        input.update(listOf(controller))
        assertFalse(input.update(listOf(controller.copy(buttons = ButtonBits.ButtonA))))
        assertTrue(input.update(listOf(controller.copy(buttons = ButtonBits.ButtonTriggerR))))
        assertFalse(selectionPressed(hand().copy(buttons = ButtonBits.ButtonTriggerR)))
        assertFalse(selectionPressed(hand(InputSide.LEFT).copy(buttons = ButtonBits.ButtonA)))
    }
    @Test fun trackingRecoveryAndModeChangesRequireReleaseBeforeAction() {
        val input = SelectionInput()
        assertFalse(input.update(listOf(hand(pressed = true))))
        input.update(listOf(hand()))
        assertTrue(input.update(listOf(hand(pressed = true))))
        input.update(emptyList())
        assertFalse(input.update(listOf(hand(pressed = true))))
        input.update(listOf(hand()))
        input.reset()
        assertFalse(input.update(listOf(hand(pressed = true))))
        input.update(listOf(hand()))
        assertTrue(input.update(listOf(hand(pressed = true))))
    }
    @Test fun eachHandCanActWhileTheOtherIsHeld() {
        val input = SelectionInput()
        input.update(listOf(hand(InputSide.LEFT), hand()))
        assertTrue(input.update(listOf(hand(InputSide.LEFT, true), hand())))
        assertTrue(input.update(listOf(hand(InputSide.LEFT, true), hand(pressed = true))))
        assertFalse(input.update(listOf(hand(InputSide.LEFT, true), hand(pressed = true))))
    }
    @Test fun inactiveOrLoweredHandDoesNotSelectAndRaisingHeldPinchDoesNotSelect() {
        val input = SelectionInput()
        input.update(listOf(hand()))
        assertFalse(input.update(listOf(hand(pressed = true, raised = false))))
        assertFalse(input.update(listOf(hand(pressed = true))))
        input.update(listOf(hand()))
        assertFalse(input.update(listOf(hand(pressed = true).copy(active = false))))
        assertFalse(input.update(listOf(hand(pressed = true))))
    }
    @Test fun panelInteractionCannotAlsoSelectSpatialTarget() {
        val input = SelectionInput()
        input.update(listOf(hand()))
        assertFalse(input.update(listOf(hand(pressed = true)), blocked = true))
        assertFalse(input.update(listOf(hand(pressed = true))))
        input.update(listOf(hand()))
        assertTrue(input.update(listOf(hand(pressed = true))))
    }
    @Test fun systemGestureAndSwitchingInputTypeRequireNewPinch() {
        val input = SelectionInput()
        input.update(listOf(hand()))
        assertFalse(input.update(listOf(hand(pressed = true).copy(buttons = ButtonBits.ButtonA or ButtonBits.ButtonSystem))))
        assertFalse(input.update(listOf(hand(pressed = true))))
        input.update(listOf(hand()))
        assertFalse(input.update(listOf(hand().copy(type = ControllerType.CONTROLLER, buttons = ButtonBits.ButtonTriggerR))))
        assertFalse(input.update(listOf(hand(pressed = true))))
    }
}
