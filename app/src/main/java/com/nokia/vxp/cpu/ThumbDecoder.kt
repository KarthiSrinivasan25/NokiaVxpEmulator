package com.nokia.vxp.cpu

/**
 * Partial 16-bit Thumb disassembler covering a handful of the most
 * common formats: move/compare/add/subtract immediate (format 3),
 * add/subtract register-or-3-bit-immediate (format 2), and unconditional
 * branch (format 18). Like Decoder.kt (ARM), this is display-only and
 * NOT exhaustive - Unicorn handles real decode+execute regardless.
 *
 * Bit-layout reference: ARM Architecture Reference Manual, "Thumb
 * instruction set encoding" chapter (format numbers as commonly cited
 * from the original ARM7TDMI Thumb documentation).
 */
object ThumbDecoder {

    fun decode(address: Long, halfword: Int): Instruction {
        val bytes = byteArrayOf((halfword and 0xFF).toByte(), ((halfword ushr 8) and 0xFF).toByte())

        // Format 3: move/compare/add/subtract immediate - bits15:13 = 001
        if (((halfword ushr 13) and 0b111) == 0b001) {
            val op = (halfword ushr 11) and 0b11
            val rd = (halfword ushr 8) and 0b111
            val imm8 = halfword and 0xFF
            val mnemonic = when (op) {
                0b00 -> "MOVS"
                0b01 -> "CMP"
                0b10 -> "ADDS"
                else -> "SUBS"
            }
            return Instruction(address, bytes, mnemonic, "R$rd, #$imm8", isThumb = true)
        }

        // Format 2: add/subtract register or 3-bit immediate - bits15:11 = 00011
        if (((halfword ushr 11) and 0b11111) == 0b00011) {
            val immediateFlag = ((halfword ushr 10) and 1) == 1
            val isSub = ((halfword ushr 9) and 1) == 1
            val rnOrImm3 = (halfword ushr 6) and 0b111
            val rs = (halfword ushr 3) and 0b111
            val rd = halfword and 0b111
            val mnemonic = if (isSub) "SUBS" else "ADDS"
            val operand3 = if (immediateFlag) "#$rnOrImm3" else "R$rnOrImm3"
            return Instruction(address, bytes, mnemonic, "R$rd, R$rs, $operand3", isThumb = true)
        }

        // Format 18: unconditional branch - bits15:11 = 11100
        if (((halfword ushr 11) and 0b11111) == 0b11100) {
            val offset11 = halfword and 0x7FF
            // Sign-extend 11-bit offset, then it's a halfword-count offset (<<1), relative to PC+4.
            val signExtended = (offset11 shl 21) shr 21
            val target = address + 4 + (signExtended.toLong() shl 1)
            return Instruction(address, bytes, "B", "0x${target.toString(16)}", isThumb = true)
        }

        return Instruction.unknown(address, bytes, isThumb = true)
    }
}
