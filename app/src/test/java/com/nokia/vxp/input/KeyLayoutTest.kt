package com.nokia.vxp.input

import android.view.KeyEvent as AndroidKeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyLayoutTest {

    @Test
    fun `grid has a consistent column count across all rows`() {
        assertEquals(3, KeyLayout.columns)
        for (row in KeyLayout.grid) {
            assertTrue(row.size <= KeyLayout.columns)
        }
    }

    @Test
    fun `numeric keypad rows contain the expected digits`() {
        // Rows 5-7 (0-indexed) are the 1-9 numpad block.
        val digitsInGrid = KeyLayout.grid.flatten().filterNotNull().filter {
            it.name.startsWith("NUM")
        }
        val expectedDigits = (0..9).map { NokiaKey.valueOf("NUM$it") }
        assertEquals(expectedDigits.toSet(), digitsInGrid.toSet())
    }

    @Test
    fun `every NokiaKey value appears exactly once in the grid`() {
        val allInGrid = KeyLayout.grid.flatten().filterNotNull()
        assertEquals(NokiaKey.values().size, allInGrid.size)
        assertEquals(NokiaKey.values().toSet(), allInGrid.toSet())
    }

    @Test
    fun `hardware dpad keys map to navigation NokiaKeys`() {
        assertEquals(NokiaKey.UP, KeyLayout.fromAndroidKeyCode(AndroidKeyEvent.KEYCODE_DPAD_UP))
        assertEquals(NokiaKey.DOWN, KeyLayout.fromAndroidKeyCode(AndroidKeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(NokiaKey.LEFT, KeyLayout.fromAndroidKeyCode(AndroidKeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(NokiaKey.RIGHT, KeyLayout.fromAndroidKeyCode(AndroidKeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(NokiaKey.SELECT, KeyLayout.fromAndroidKeyCode(AndroidKeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test
    fun `hardware digit keys map to numeric NokiaKeys`() {
        assertEquals(NokiaKey.NUM7, KeyLayout.fromAndroidKeyCode(AndroidKeyEvent.KEYCODE_7))
    }

    @Test
    fun `unmapped hardware key codes return null`() {
        assertNull(KeyLayout.fromAndroidKeyCode(AndroidKeyEvent.KEYCODE_VOLUME_UP))
    }
}
