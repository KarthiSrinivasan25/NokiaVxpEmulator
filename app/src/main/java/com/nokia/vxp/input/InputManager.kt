package com.nokia.vxp.input

import com.nokia.vxp.utils.Logger

private const val TAG = "InputManager"

/**
 * Central point all input sources (VirtualKeypadView taps, hardware
 * dpad/keyboard events) funnel through before reaching the emulator.
 * Tracks currently-held keys (handy for debug overlays / combo input
 * later) and de-duplicates redundant down/up calls so a stray repeated
 * ACTION_DOWN doesn't spam the guest with duplicate key-down events.
 */
class InputManager(private val onKeyEvent: (KeyEvent) -> Unit) {

    private val pressedKeys = mutableSetOf<NokiaKey>()

    fun keyDown(key: NokiaKey) {
        if (!pressedKeys.add(key)) return // already down - ignore repeat down without an up in between
        Logger.d(TAG, "keyDown: ${key.label}")
        onKeyEvent(KeyEvent(key, down = true))
    }

    fun keyUp(key: NokiaKey) {
        if (!pressedKeys.remove(key)) return // wasn't down - nothing to release
        Logger.d(TAG, "keyUp: ${key.label}")
        onKeyEvent(KeyEvent(key, down = false))
    }

    /** Releases every currently-held key - call this from e.g. Activity.onPause so nothing gets "stuck" pressed. */
    fun releaseAll() {
        val toRelease = pressedKeys.toList()
        for (key in toRelease) keyUp(key)
    }

    fun isPressed(key: NokiaKey): Boolean = key in pressedKeys
    fun pressedCount(): Int = pressedKeys.size
    fun pressedKeys(): Set<NokiaKey> = pressedKeys.toSet()
}
