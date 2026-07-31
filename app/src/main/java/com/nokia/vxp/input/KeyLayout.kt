package com.nokia.vxp.input

import android.view.KeyEvent as AndroidKeyEvent

/**
 * Logical layout of the virtual keypad (which key occupies which grid
 * cell) plus a hardware-keyboard fallback mapping, so a device with a
 * physical dpad/keyboard can drive the same NokiaKey events as on-screen
 * taps.
 */
object KeyLayout {

    /** Row-major grid: null cells are empty space (keeps softkeys visually separated from the numpad, dpad arms separated from each other). */
    val grid: List<List<NokiaKey?>> = listOf(
        listOf(NokiaKey.SOFT_LEFT, null, NokiaKey.SOFT_RIGHT),
        listOf(NokiaKey.CALL, NokiaKey.SELECT, NokiaKey.END),
        listOf(null, NokiaKey.UP, null),
        listOf(NokiaKey.LEFT, null, NokiaKey.RIGHT),
        listOf(null, NokiaKey.DOWN, null),
        listOf(NokiaKey.NUM1, NokiaKey.NUM2, NokiaKey.NUM3),
        listOf(NokiaKey.NUM4, NokiaKey.NUM5, NokiaKey.NUM6),
        listOf(NokiaKey.NUM7, NokiaKey.NUM8, NokiaKey.NUM9),
        listOf(NokiaKey.STAR, NokiaKey.NUM0, NokiaKey.POUND)
    )

    val rows: Int get() = grid.size
    val columns: Int get() = grid.maxOf { it.size }

    /** Maps Android hardware key codes (dpad, digits, softkeys) to NokiaKey, for devices with a physical keyboard. */
    fun fromAndroidKeyCode(androidKeyCode: Int): NokiaKey? = when (androidKeyCode) {
        AndroidKeyEvent.KEYCODE_DPAD_UP -> NokiaKey.UP
        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> NokiaKey.DOWN
        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> NokiaKey.LEFT
        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> NokiaKey.RIGHT
        AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> NokiaKey.SELECT
        AndroidKeyEvent.KEYCODE_0 -> NokiaKey.NUM0
        AndroidKeyEvent.KEYCODE_1 -> NokiaKey.NUM1
        AndroidKeyEvent.KEYCODE_2 -> NokiaKey.NUM2
        AndroidKeyEvent.KEYCODE_3 -> NokiaKey.NUM3
        AndroidKeyEvent.KEYCODE_4 -> NokiaKey.NUM4
        AndroidKeyEvent.KEYCODE_5 -> NokiaKey.NUM5
        AndroidKeyEvent.KEYCODE_6 -> NokiaKey.NUM6
        AndroidKeyEvent.KEYCODE_7 -> NokiaKey.NUM7
        AndroidKeyEvent.KEYCODE_8 -> NokiaKey.NUM8
        AndroidKeyEvent.KEYCODE_9 -> NokiaKey.NUM9
        AndroidKeyEvent.KEYCODE_STAR -> NokiaKey.STAR
        AndroidKeyEvent.KEYCODE_POUND -> NokiaKey.POUND
        AndroidKeyEvent.KEYCODE_BACK -> NokiaKey.END
        AndroidKeyEvent.KEYCODE_CALL -> NokiaKey.CALL
        AndroidKeyEvent.KEYCODE_SOFT_LEFT -> NokiaKey.SOFT_LEFT
        AndroidKeyEvent.KEYCODE_SOFT_RIGHT -> NokiaKey.SOFT_RIGHT
        else -> null
    }
}
