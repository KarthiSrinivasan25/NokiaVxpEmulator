package com.nokia.vxp.mre

import com.nokia.vxp.input.InputManager
import com.nokia.vxp.utils.Logger

private const val TAG = "VmInput"

/**
 * Implements input-related vm_* API surface. vm_kbd_set_mode and
 * vm_input_text2 are confirmed real function names (gtrxAC/peanut.vxp's
 * .symtab, MIT licensed) - these replace an earlier, unconfirmed
 * "vm_get_key_state" polling guess. Real key input for MRE apps appears
 * to be event-driven via vm_reg_keyboard_callback (see
 * mre.SysEventRegistry) rather than guest-side polling, which fits: no
 * polling-style key-state function turned up in the real sample either.
 */
object VmInput {
    fun registerHandlers(dispatcher: VmDispatcher, inputManager: InputManager) {
        dispatcher.registerHandler("vm_kbd_set_mode", VmApiTable.KBD_SET_MODE) { args ->
            // r0 = requested keyboard mode (exact enum values not confirmed)
            Logger.i(TAG, "vm_kbd_set_mode(mode=${args.r0}) - accepted, no distinct input modes modeled yet")
            0L
        }

        dispatcher.registerHandler("vm_input_text2", VmApiTable.INPUT_TEXT2) { args ->
            Logger.w(TAG, "vm_input_text2() - text input UI not implemented yet, returning 0")
            0L
        }
    }
}
