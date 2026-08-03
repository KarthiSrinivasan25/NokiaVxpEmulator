package com.nokia.vxp.debug

import com.nokia.vxp.memory.GuestMemoryReader
import com.nokia.vxp.memory.MemoryRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeGuestMemory(private val backing: ByteArray) : GuestMemoryReader {
    override fun read(address: Long, length: Int): ByteArray? {
        val start = address.toInt()
        if (start < 0 || start >= backing.size) return null
        val end = minOf(start + length, backing.size)
        return backing.copyOfRange(start, end)
    }
}

class MemoryViewerTest {

    @Test
    fun `dumpMemory delegates to HexDump for readable memory`() {
        val bytes = "test data!".toByteArray(Charsets.US_ASCII)
        val memory = FakeGuestMemory(bytes)

        val lines = MemoryViewer.dumpMemory(memory, address = 0, length = bytes.size)

        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("|test data!|"))
    }

    @Test
    fun `dumpMemory reports unreadable memory clearly instead of crashing`() {
        val memory = FakeGuestMemory(ByteArray(4))
        val lines = MemoryViewer.dumpMemory(memory, address = 0xDEAD0000L, length = 16)

        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("unreadable"))
        assertTrue(lines[0].contains("dead0000"))
    }

    @Test
    fun `formatRegion shows address range permissions and size`() {
        val region = MemoryRegion(
            name = "segment0",
            baseAddress = 0x8000,
            size = 0x1000,
            readable = true,
            writable = false,
            executable = true
        )

        val line = MemoryViewer.formatRegion(region)

        assertTrue(line.contains("00008000"))
        assertTrue(line.contains("00009000")) // baseAddress + size
        assertTrue(line.contains("segment0"))
        assertTrue(line.contains("R-X"))
        assertTrue(line.contains("4096 bytes"))
    }

    @Test
    fun `formatRegion shows all permission combinations correctly`() {
        val rwRegion = MemoryRegion("heap", 0x1000, 0x100, readable = true, writable = true, executable = false)
        assertTrue(MemoryViewer.formatRegion(rwRegion).contains("RW-"))

        val noPermsRegion = MemoryRegion("guard", 0x2000, 0x100, readable = false, writable = false, executable = false)
        assertTrue(MemoryViewer.formatRegion(noPermsRegion).contains("---"))
    }
}
