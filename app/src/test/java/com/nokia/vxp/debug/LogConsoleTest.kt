package com.nokia.vxp.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogConsoleTest {

    @Test
    fun `log appends entries in order`() {
        val console = LogConsole()
        console.log("A", "first")
        console.log("B", "second")

        val entries = console.entries()
        assertEquals(2, entries.size)
        assertEquals("first", entries[0].message)
        assertEquals("second", entries[1].message)
    }

    @Test
    fun `capacity evicts oldest entries first`() {
        val console = LogConsole(capacity = 3)
        console.log("T", "1")
        console.log("T", "2")
        console.log("T", "3")
        console.log("T", "4") // should evict "1"

        val messages = console.entries().map { it.message }
        assertEquals(listOf("2", "3", "4"), messages)
        assertEquals(3, console.size())
    }

    @Test
    fun `filteredBy returns only matching tag`() {
        val console = LogConsole()
        console.log("CPU", "fault")
        console.log("GFX", "frame")
        console.log("CPU", "reset")

        val cpuOnly = console.filteredBy("CPU")
        assertEquals(2, cpuOnly.size)
        assertTrue(cpuOnly.all { it.tag == "CPU" })
    }

    @Test
    fun `clear empties the console`() {
        val console = LogConsole()
        console.log("T", "msg")
        console.clear()
        assertEquals(0, console.size())
    }
}
