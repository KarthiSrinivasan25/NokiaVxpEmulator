package com.nokia.vxp.cpu

import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbDecoderTest {

    @Test
    fun `decodes MOVS Rd, imm8 (format 3)`() {
        // bits15:13 = 001, op=00 (MOV), Rd=R2, imm8=0x2A
        val halfword = (0b001 shl 13) or (0b00 shl 11) or (2 shl 8) or 0x2A
        val instr = ThumbDecoder.decode(0x1000, halfword)
        assertEquals("MOVS", instr.mnemonic)
        assertEquals("R2, #42", instr.operands)
        assertEquals(true, instr.isThumb)
    }

    @Test
    fun `decodes CMP Rd, imm8 (format 3)`() {
        val halfword = (0b001 shl 13) or (0b01 shl 11) or (5 shl 8) or 0x10
        val instr = ThumbDecoder.decode(0x1002, halfword)
        assertEquals("CMP", instr.mnemonic)
        assertEquals("R5, #16", instr.operands)
    }

    @Test
    fun `decodes ADDS Rd, Rs, Rn register form (format 2)`() {
        // bits15:11 = 00011, I=0 (register), Op=0 (add), Rn/Rm=R4, Rs=R1, Rd=R0
        val halfword = (0b00011 shl 11) or (0 shl 10) or (0 shl 9) or (4 shl 6) or (1 shl 3) or 0
        val instr = ThumbDecoder.decode(0x1004, halfword)
        assertEquals("ADDS", instr.mnemonic)
        assertEquals("R0, R1, R4", instr.operands)
    }

    @Test
    fun `decodes SUBS Rd, Rs, imm3 form (format 2)`() {
        // I=1 (immediate), Op=1 (sub), imm3=3, Rs=R2, Rd=R1
        val halfword = (0b00011 shl 11) or (1 shl 10) or (1 shl 9) or (3 shl 6) or (2 shl 3) or 1
        val instr = ThumbDecoder.decode(0x1006, halfword)
        assertEquals("SUBS", instr.mnemonic)
        assertEquals("R1, R2, #3", instr.operands)
    }

    @Test
    fun `decodes unconditional branch (format 18) with positive offset`() {
        // bits15:11 = 11100, offset11 = 4 -> target = addr + 4 + (4 << 1) = addr + 12
        val halfword = (0b11100 shl 11) or 4
        val instr = ThumbDecoder.decode(0x2000, halfword)
        assertEquals("B", instr.mnemonic)
        assertEquals("0x${(0x2000 + 4 + 8).toString(16)}", instr.operands)
    }

    @Test
    fun `decodes unconditional branch with negative offset`() {
        // offset11 = -4 as 11-bit two's complement = 0x7FC
        val halfword = (0b11100 shl 11) or 0x7FC
        val instr = ThumbDecoder.decode(0x2000, halfword)
        assertEquals("B", instr.mnemonic)
        // target = 0x2000 + 4 + (-4 << 1) = 0x2000 + 4 - 8 = 0x1FFC
        assertEquals("0x1ffc", instr.operands)
    }

    @Test
    fun `unrecognized halfword returns unknown instruction`() {
        // bits15:13 = 111 doesn't match any format handled here.
        val halfword = (0b111 shl 13)
        val instr = ThumbDecoder.decode(0x3000, halfword)
        assertEquals("??", instr.mnemonic)
    }
}
