package com.nokia.vxp.cpu

/**
 * A decoded instruction, for display purposes only (a future disassembly
 * view, debug.HexDump annotations) - NOT used to drive actual execution.
 * Unicorn does the real decode+execute internally via Executor/uc_emu_start;
 * this is purely cosmetic so a human can see what's at a given address.
 */
data class Instruction(
    val address: Long,
    val rawBytes: ByteArray,
    val mnemonic: String,
    val operands: String,
    val isThumb: Boolean
) {
    val sizeBytes: Int get() = rawBytes.size

    fun display(): String =
        "0x${address.toString(16).padStart(8, '0')}: $mnemonic${if (operands.isNotEmpty()) " $operands" else ""}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Instruction) return false
        return address == other.address &&
            rawBytes.contentEquals(other.rawBytes) &&
            mnemonic == other.mnemonic &&
            operands == other.operands &&
            isThumb == other.isThumb
    }

    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + rawBytes.contentHashCode()
        result = 31 * result + mnemonic.hashCode()
        result = 31 * result + operands.hashCode()
        result = 31 * result + isThumb.hashCode()
        return result
    }

    companion object {
        /** Placeholder for bytes that don't match any pattern this decoder recognizes. */
        fun unknown(address: Long, rawBytes: ByteArray, isThumb: Boolean) =
            Instruction(address, rawBytes, "??", "", isThumb)
    }
}
