package com.nokia.vxp.mre

import com.nokia.vxp.memory.GuestMemoryReader
import org.junit.Assert.assertEquals
import org.junit.Test

/** Simple in-memory fake so string-reading logic can be tested without a real Unicorn engine. */
private class FakeGuestMemory(private val backing: ByteArray) : GuestMemoryReader {
    override fun read(address: Long, length: Int): ByteArray? {
        val start = address.toInt()
        if (start < 0 || start >= backing.size) return null
        val end = minOf(start + length, backing.size)
        return backing.copyOfRange(start, end)
    }
}

class VmStringsTest {

    @Test
    fun `reads a simple short string`() {
        val bytes = "hello".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
        val memory = FakeGuestMemory(bytes)

        assertEquals("hello", readGuestCString(memory, 0))
    }

    @Test
    fun `null pointer returns empty string`() {
        val memory = FakeGuestMemory(byteArrayOf(1, 2, 3))
        assertEquals("", readGuestCString(memory, 0L))
    }

    @Test
    fun `reads a string starting at a nonzero offset`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte()) + "hi".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
        val memory = FakeGuestMemory(bytes)

        assertEquals("hi", readGuestCString(memory, 2))
    }

    @Test
    fun `string spanning multiple read chunks is assembled correctly`() {
        // Longer than the internal 64-byte chunk size, to exercise the multi-chunk loop.
        val longString = "x".repeat(100)
        val bytes = longString.toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
        val memory = FakeGuestMemory(bytes)

        assertEquals(longString, readGuestCString(memory, 0))
    }

    @Test
    fun `unterminated string is truncated at maxLength rather than reading forever`() {
        val bytes = ByteArray(200) { 'a'.code.toByte() } // no null terminator anywhere
        val memory = FakeGuestMemory(bytes)

        val result = readGuestCString(memory, 0, maxLength = 50)
        assertEquals(50, result.length)
    }

    @Test
    fun `read failure partway through truncates rather than throwing`() {
        // Backing array is shorter than the string claims to be - FakeGuestMemory
        // returns null once the address is past the end of backing.
        val bytes = "abc".toByteArray(Charsets.US_ASCII) // no null terminator, and nothing beyond this
        val memory = FakeGuestMemory(bytes)

        val result = readGuestCString(memory, 0)
        assertEquals("abc", result)
    }

    @Test
    fun `empty string (immediate null terminator) reads as empty`() {
        val memory = FakeGuestMemory(byteArrayOf(0))
        assertEquals("", readGuestCString(memory, 0))
    }
}
