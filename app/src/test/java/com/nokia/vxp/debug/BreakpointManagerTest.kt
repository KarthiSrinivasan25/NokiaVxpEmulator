package com.nokia.vxp.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakpointManagerTest {

    @Test
    fun `add registers a breakpoint that is enabled by default`() {
        val manager = BreakpointManager()
        manager.add(0x1000)
        assertTrue(manager.isBreakpoint(0x1000))
    }

    @Test
    fun `remove clears a breakpoint`() {
        val manager = BreakpointManager()
        manager.add(0x1000)
        manager.remove(0x1000)
        assertFalse(manager.isBreakpoint(0x1000))
    }

    @Test
    fun `toggle flips enabled state without removing it`() {
        val manager = BreakpointManager()
        manager.add(0x1000)
        manager.toggle(0x1000)
        assertFalse(manager.isBreakpoint(0x1000))
        assertEquals(1, manager.count()) // still present, just disabled

        manager.toggle(0x1000)
        assertTrue(manager.isBreakpoint(0x1000))
    }

    @Test
    fun `nextBreakpointAtOrAfter finds the nearest enabled breakpoint`() {
        val manager = BreakpointManager()
        manager.add(0x3000)
        manager.add(0x1000)
        manager.add(0x2000)

        assertEquals(0x1000L, manager.nextBreakpointAtOrAfter(0x0))
        assertEquals(0x2000L, manager.nextBreakpointAtOrAfter(0x1001))
        assertEquals(0x3000L, manager.nextBreakpointAtOrAfter(0x2500))
    }

    @Test
    fun `nextBreakpointAtOrAfter skips disabled breakpoints`() {
        val manager = BreakpointManager()
        manager.add(0x1000)
        manager.add(0x2000)
        manager.toggle(0x1000) // disable the nearer one

        assertEquals(0x2000L, manager.nextBreakpointAtOrAfter(0x0))
    }

    @Test
    fun `nextBreakpointAtOrAfter returns null when nothing is ahead`() {
        val manager = BreakpointManager()
        manager.add(0x1000)
        assertNull(manager.nextBreakpointAtOrAfter(0x2000))
    }

    @Test
    fun `clear removes all breakpoints`() {
        val manager = BreakpointManager()
        manager.add(0x1000)
        manager.add(0x2000)
        manager.clear()
        assertEquals(0, manager.count())
    }

    @Test
    fun `all returns breakpoints sorted by address`() {
        val manager = BreakpointManager()
        manager.add(0x3000)
        manager.add(0x1000)
        manager.add(0x2000)

        val addresses = manager.all().map { it.address }
        assertEquals(listOf(0x1000L, 0x2000L, 0x3000L), addresses)
    }
}
