package com.nokia.vxp.input

/**
 * One key transition, timestamped for InputManager's dedup/debug logic.
 * Distinct from emulator.EmulatorEvent (which carries a raw Int keyCode)
 * - InputManager translates KeyEvent(NokiaKey) into that raw form right
 * before handing it to Emulator, so everything upstream of that point
 * can work with the richer NokiaKey type instead of magic numbers.
 */
data class KeyEvent(
    val key: NokiaKey,
    val down: Boolean,
    val timestampMillis: Long = System.currentTimeMillis()
)
