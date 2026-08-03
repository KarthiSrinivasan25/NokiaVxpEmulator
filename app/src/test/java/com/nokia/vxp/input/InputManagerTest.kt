package com.nokia.vxp.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputManagerTest {

    @Test
    fun `keyDown fires an event and marks the key pressed`() {
        val events = mutableListOf<KeyEvent>()
        val manager = InputManager { events += it }

        manager.keyDown(NokiaKey.NUM1)

        assertEquals(1, events.size)
        assertTrue(events[0].down)
        assertEquals(NokiaKey.NUM1, events[0].key)
        assertTrue(manager.isPressed(NokiaKey.NUM1))
    }

    @Test
    fun `repeated keyDown without an intervening keyUp is deduplicated`() {
        val events = mutableListOf<KeyEvent>()
        val manager = InputManager { events += it }

        manager.keyDown(NokiaKey.NUM1)
        manager.keyDown(NokiaKey.NUM1)
        manager.keyDown(NokiaKey.NUM1)

        assertEquals(1, events.size) // only the first down should have fired
    }

    @Test
    fun `keyUp on a key that was never pressed does nothing`() {
        val events = mutableListOf<KeyEvent>()
        val manager = InputManager { events += it }

        manager.keyUp(NokiaKey.NUM1)

        assertTrue(events.isEmpty())
    }

    @Test
    fun `keyUp after keyDown fires and clears pressed state`() {
        val events = mutableListOf<KeyEvent>()
        val manager = InputManager { events += it }

        manager.keyDown(NokiaKey.SELECT)
        manager.keyUp(NokiaKey.SELECT)

        assertEquals(2, events.size)
        assertFalse(events[1].down)
        assertFalse(manager.isPressed(NokiaKey.SELECT))
    }

    @Test
    fun `releaseAll fires keyUp for every currently pressed key`() {
        val events = mutableListOf<KeyEvent>()
        val manager = InputManager { events += it }

        manager.keyDown(NokiaKey.UP)
        manager.keyDown(NokiaKey.LEFT)
        manager.keyDown(NokiaKey.NUM5)
        manager.releaseAll()

        assertEquals(0, manager.pressedCount())
        val upEvents = events.filter { !it.down }
        assertEquals(3, upEvents.size)
    }

    @Test
    fun `pressedKeys reflects the current held set`() {
        val manager = InputManager { }
        manager.keyDown(NokiaKey.NUM2)
        manager.keyDown(NokiaKey.NUM3)

        assertEquals(setOf(NokiaKey.NUM2, NokiaKey.NUM3), manager.pressedKeys())
    }
}
