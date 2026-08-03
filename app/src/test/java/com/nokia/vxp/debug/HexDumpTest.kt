package com.nokia.vxp.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HexDumpTest {

    @Test
    fun `empty input produces no lines`() {
        assertTrue(HexDump.format(ByteArray(0)).isEmpty())
    }

    @Test
    fun `single full line contains address hex and ascii`() {
        val bytes = "Hello, World!!!!".toByteArray(Charsets.US_ASCII) // exactly 16 bytes
        val lines = HexDump.format(bytes, baseAddress = 0x1000)

        assertEquals(1, lines.size)
        val line = lines[0]
        assertTrue(line.startsWith("00001000"))
        assertTrue(line.contains("48 65 6C 6C 6F")) // "Hello" in hex
        assertTrue(line.contains("|Hello, World!!!!|"))
    }

    @Test
    fun `non-printable bytes show as dots in the ascii column`() {
        val bytes = byteArrayOf(0x00, 0x01, 0x41, 0xFF.toByte()) // NUL, SOH, 'A', 0xFF
        val lines = HexDump.format(bytes)

        assertTrue(lines[0].contains("|..A.|"))
    }

    @Test
    fun `multiple lines split every 16 bytes`() {
        val bytes = ByteArray(20) { it.toByte() }
        val lines = HexDump.format(bytes)

        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("00000000"))
        assertTrue(lines[1].startsWith("00000010")) // second line starts at offset 16 = 0x10
    }

    @Test
    fun `short final line is still well formed`() {
        val bytes = byteArrayOf(1, 2, 3) // fewer than 16 bytes
        val lines = HexDump.format(bytes)

        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("01 02 03"))
        assertTrue(lines[0].contains("|...|"))
    }

    @Test
    fun `base address offsets subsequent lines correctly`() {
        val bytes = ByteArray(32) { 0 }
        val lines = HexDump.format(bytes, baseAddress = 0x8000)

        assertTrue(lines[0].startsWith("00008000"))
        assertTrue(lines[1].startsWith("00008010"))
    }
}
