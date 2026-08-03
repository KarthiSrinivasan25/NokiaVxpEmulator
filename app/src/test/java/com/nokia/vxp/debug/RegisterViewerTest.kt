package com.nokia.vxp.debug

import com.nokia.vxp.cpu.Registers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisterViewerTest {

    @Test
    fun `formats general purpose registers in rows of four`() {
        val snapshot = Registers.values().associateWith { it.id.toLong() }
        val lines = RegisterViewer.formatSnapshot(snapshot)

        // 13 general-purpose registers -> ceil(13/4) = 4 rows, plus SP/LR/PC row, plus CPSR row.
        val gpRows = lines.dropLast(2)
        assertEquals(4, gpRows.size)
        assertTrue(gpRows[0].contains("R0  0x00000000"))
    }

    @Test
    fun `sp lr pc appear on their own summary line`() {
        val snapshot = mapOf(
            Registers.SP to 0x1000L,
            Registers.LR to 0x2000L,
            Registers.PC to 0x3000L
        )
        val lines = RegisterViewer.formatSnapshot(snapshot)
        val summaryLine = lines.first { it.startsWith("SP") }

        assertTrue(summaryLine.contains("0x00001000"))
        assertTrue(summaryLine.contains("0x00002000"))
        assertTrue(summaryLine.contains("0x00003000"))
    }

    @Test
    fun `cpsr line shows flags description`() {
        // N and Z bits set (bits 31, 30): 0xC0000000
        val snapshot = mapOf(Registers.CPSR to 0xC0000000L)
        val lines = RegisterViewer.formatSnapshot(snapshot)
        val cpsrLine = lines.last()

        assertTrue(cpsrLine.startsWith("CPSR"))
        assertTrue(cpsrLine.contains("NZ"))
    }

    @Test
    fun `missing registers in the snapshot default to zero rather than crashing`() {
        val lines = RegisterViewer.formatSnapshot(emptyMap())
        assertTrue(lines.isNotEmpty())
        assertTrue(lines[0].contains("0x00000000"))
    }
}
