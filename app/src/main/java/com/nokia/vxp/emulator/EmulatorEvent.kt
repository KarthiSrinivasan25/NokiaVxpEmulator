package com.nokia.vxp.emulator

/**
 * Events fed into EmulatorLoop from the UI thread. Key events use a raw
 * keyCode Int rather than referencing input.NokiaKey directly, since the
 * input/ module doesn't exist yet - VirtualKeypadView will translate
 * to/from NokiaKey once that module is built, without this class needing
 * to change.
 */
sealed class EmulatorEvent {
    data class KeyDown(val keyCode: Int) : EmulatorEvent()
    data class KeyUp(val keyCode: Int) : EmulatorEvent()
    object Pause : EmulatorEvent()
    object Resume : EmulatorEvent()
    object Stop : EmulatorEvent()
}
