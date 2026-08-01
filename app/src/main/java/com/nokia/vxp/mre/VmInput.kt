package com.nokia.vxp.mre

import com.nokia.vxp.input.InputManager
import com.nokia.vxp.input.NokiaKey

/**
 * Implements an input-polling vm_* API surface. Function name/semantics
 * here are a reasonable guess (get_key_state-style polling, a common
 * pattern across MRE-like embedded APIs) rather than confirmed against
 * a real SDK header - no real vm_key_*/vm_input_* names surfaced during
 * research for this project.
 */
object VmInput {
    fun registerHandlers(dispatcher: VmDispatcher, inputManager: InputManager) {
        dispatcher.registerHandler("vm_get_key_state", VmApiTable.INPUT_GET_KEY_STATE) { args ->
            // r0 = guest key code - see input.NokiaKey.guestCode
            val key = NokiaKey.fromGuestCode(args.r0.toInt())
            val pressed = key != null && inputManager.isPressed(key)
            if (pressed) 1L else 0L
        }
    }
}
