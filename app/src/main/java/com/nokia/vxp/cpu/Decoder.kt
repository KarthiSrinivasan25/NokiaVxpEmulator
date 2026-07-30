package com.nokia.vxp.cpu

/**
 * Partial 32-bit ARM disassembler covering the common encodings you'd
 * actually see disassembling a debug view: data-processing (immediate
 * and register), branch/branch-with-link, branch-and-exchange, and
 * single load/store. This is NOT a full ISA decoder and is NOT used to
 * drive emulation - Unicorn executes the real instruction stream
 * regardless of whether this recognizes it. Anything unrecognized comes
 * back as Instruction.unknown() rather than guessing.
 *
 * Bit-layout reference: ARM Architecture Reference Manual, "ARM
 * instruction set encoding" chapter. Cond field (bits 31:28) is decoded
 * but folded into the mnemonic only for the non-AL (always) case, to
 * keep the common path readable.
 */
object Decoder {

    private val CONDS = arrayOf(
        "EQ", "NE", "CS", "CC", "MI", "PL", "VS", "VC",
        "HI", "LS", "GE", "LT", "GT", "LE", "", /*AL - no suffix*/ "NV"
    )

    private val DATA_PROC_MNEMONICS = arrayOf(
        "AND", "EOR", "SUB", "RSB", "ADD", "ADC", "SBC", "RSC",
        "TST", "TEQ", "CMP", "CMN", "ORR", "MOV", "BIC", "MVN"
    )

    fun decode(address: Long, word: Int): Instruction {
        val bytes = intToLeBytes(word)
        val cond = (word ushr 28) and 0xF
        val condSuffix = CONDS[cond]

        // Branch and Exchange: cond 0001 0010 1111 1111 1111 0001 Rm (0x012FFF1_)
        if ((word and 0x0FFFFFF0.toInt()) == 0x012FFF10 && cond != 0xF) {
            val rm = word and 0xF
            return Instruction(address, bytes, "BX$condSuffix", "R$rm", isThumb = false)
        }

        // Branch / Branch-with-Link: bits 27:25 = 101
        if (((word ushr 25) and 0b111) == 0b101) {
            val link = ((word ushr 24) and 1) == 1
            val imm24 = word and 0x00FFFFFF
            // Sign-extend 24-bit immediate, then it's a word-count offset (<<2), relative to PC+8.
            val signExtended = (imm24 shl 8) shr 8
            val target = address + 8 + (signExtended.toLong() shl 2)
            val mnemonic = if (link) "BL$condSuffix" else "B$condSuffix"
            return Instruction(address, bytes, mnemonic, "0x${target.toString(16)}", isThumb = false)
        }

        // Data-processing: bits 27:26 = 00
        if (((word ushr 26) and 0b11) == 0b00) {
            val immediate = ((word ushr 25) and 1) == 1
            val opcode = (word ushr 21) and 0xF
            val setFlags = ((word ushr 20) and 1) == 1
            val rn = (word ushr 16) and 0xF
            val rd = (word ushr 12) and 0xF
            val mnemonicBase = DATA_PROC_MNEMONICS[opcode]
            val sSuffix = if (setFlags && mnemonicBase !in setOf("TST", "TEQ", "CMP", "CMN")) "S" else ""
            val mnemonic = "$mnemonicBase$sSuffix$condSuffix"

            // MOV/MVN don't use Rn as a source operand; TST/TEQ/CMP/CMN don't write Rd.
            val noRnOps = setOf("MOV", "MVN")
            val noRdOps = setOf("TST", "TEQ", "CMP", "CMN")

            val operand2 = if (immediate) {
                val rotate = (word ushr 8) and 0xF
                val imm8 = word and 0xFF
                val rotated = java.lang.Integer.rotateRight(imm8, rotate * 2)
                "#$rotated"
            } else {
                val rm = word and 0xF
                "R$rm" // shifted-register operand forms aren't decoded here - display-only best effort
            }

            val operandParts = mutableListOf<String>()
            if (mnemonicBase !in noRdOps) operandParts += "R$rd"
            if (mnemonicBase !in noRnOps) operandParts += "R$rn"
            operandParts += operand2

            return Instruction(address, bytes, mnemonic, operandParts.joinToString(", "), isThumb = false)
        }

        // Single data transfer (LDR/STR immediate offset): bits 27:26 = 01
        if (((word ushr 26) and 0b11) == 0b01) {
            val isLoad = ((word ushr 20) and 1) == 1
            val isByte = ((word ushr 22) and 1) == 1
            val rn = (word ushr 16) and 0xF
            val rd = (word ushr 12) and 0xF
            val imm12 = word and 0xFFF
            val add = ((word ushr 23) and 1) == 1
            val sign = if (add) "+" else "-"
            val mnemonic = (if (isLoad) "LDR" else "STR") + (if (isByte) "B" else "") + condSuffix
            return Instruction(
                address, bytes, mnemonic,
                "R$rd, [R$rn, #$sign$imm12]", isThumb = false
            )
        }

        return Instruction.unknown(address, bytes, isThumb = false)
    }

    private fun intToLeBytes(word: Int): ByteArray = byteArrayOf(
        (word and 0xFF).toByte(),
        ((word ushr 8) and 0xFF).toByte(),
        ((word ushr 16) and 0xFF).toByte(),
        ((word ushr 24) and 0xFF).toByte()
    )
}
