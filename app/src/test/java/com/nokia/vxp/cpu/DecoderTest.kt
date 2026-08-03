package com.nokia.vxp.cpu

import org.junit.Assert.assertEquals
import org.junit.Test

class DecoderTest {

    @Test
    fun `decodes MOV r0, #1 (well-known encoding 0xE3A00001)`() {
        val instr = Decoder.decode(address = 0x1000, word = 0xE3A00001.toInt())
        assertEquals("MOV", instr.mnemonic)
        assertEquals("R0, #1", instr.operands)
    }

    @Test
    fun `decodes unconditional BL with zero offset (well-known encoding 0xEB000000)`() {
        val instr = Decoder.decode(address = 0x0, word = 0xEB000000.toInt())
        assertEquals("BL", instr.mnemonic)
        // target = address + 8 + (signExtended imm24 << 2) = 0 + 8 + 0
        assertEquals("0x8", instr.operands)
    }

    @Test
    fun `decodes BX Rm`() {
        // cond=AL(1110), fixed pattern 0001 0010 1111 1111 1111 0001, Rm=R3(0011)
        val word = (0b1110 shl 28) or 0x012FFF10 or 0x3
        val instr = Decoder.decode(0x2000, word)
        assertEquals("BX", instr.mnemonic)
        assertEquals("R3", instr.operands)
    }

    @Test
    fun `decodes ADD with register operand and S flag set`() {
        // cond=AL, 00 (data-processing), I=0 (register operand), opcode=0100 (ADD),
        // S=1, Rn=R1, Rd=R2, Rm=R5
        val cond = 0b1110
        val opcode = 0b0100 // ADD
        val word = (cond shl 28) or
            (0b00 shl 26) or
            (0 shl 25) or          // I = 0 (register operand)
            (opcode shl 21) or
            (1 shl 20) or           // S = 1
            (1 shl 16) or           // Rn = R1
            (2 shl 12) or           // Rd = R2
            5                        // Rm = R5

        val instr = Decoder.decode(0x3000, word)
        assertEquals("ADDS", instr.mnemonic)
        assertEquals("R2, R1, R5", instr.operands)
    }

    @Test
    fun `decodes LDR with immediate offset`() {
        val cond = 0b1110
        val rn = 1
        val rd = 0
        val imm12 = 4
        val word = (cond shl 28) or
            (0b01 shl 26) or   // single data transfer
            (1 shl 24) or       // P = pre-indexed (not used by decoder but set for a realistic encoding)
            (1 shl 23) or       // U = add offset
            (0 shl 22) or       // B = word transfer
            (1 shl 20) or       // L = load
            (rn shl 16) or
            (rd shl 12) or
            imm12

        val instr = Decoder.decode(0x4000, word)
        assertEquals("LDR", instr.mnemonic)
        assertEquals("R0, [R1, #+4]", instr.operands)
    }

    @Test
    fun `unrecognized pattern returns unknown instruction`() {
        // 27:26 = 11 doesn't match any branch taken in this decoder's subset.
        val word = (0b1110 shl 28) or (0b11 shl 26)
        val instr = Decoder.decode(0x5000, word)
        assertEquals("??", instr.mnemonic)
    }
}
