package com.nokia.vxp.cpu

/**
 * CPSR (Current Program Status Register) flag bit positions and helpers.
 * Standard ARM CPSR layout: N(31) Z(30) C(29) V(28) Q(27) ... T(5) mode(4:0).
 */
object Flags {
    const val BIT_N = 31 // Negative
    const val BIT_Z = 30 // Zero
    const val BIT_C = 29 // Carry
    const val BIT_V = 28 // Overflow
    const val BIT_Q = 27 // Sticky saturation (DSP extensions)
    const val BIT_T = 5  // Thumb execution state

    fun isSet(cpsr: Long, bit: Int): Boolean = ((cpsr shr bit) and 1L) == 1L

    fun withBit(cpsr: Long, bit: Int, value: Boolean): Long =
        if (value) cpsr or (1L shl bit) else cpsr and (1L shl bit).inv()

    fun negative(cpsr: Long): Boolean = isSet(cpsr, BIT_N)
    fun zero(cpsr: Long): Boolean = isSet(cpsr, BIT_Z)
    fun carry(cpsr: Long): Boolean = isSet(cpsr, BIT_C)
    fun overflow(cpsr: Long): Boolean = isSet(cpsr, BIT_V)
    fun thumbMode(cpsr: Long): Boolean = isSet(cpsr, BIT_T)

    /** Compact "NZCV MODE" style summary for debug.RegisterViewer. */
    fun describe(cpsr: Long): String {
        val sb = StringBuilder()
        sb.append(if (negative(cpsr)) 'N' else 'n')
        sb.append(if (zero(cpsr)) 'Z' else 'z')
        sb.append(if (carry(cpsr)) 'C' else 'c')
        sb.append(if (overflow(cpsr)) 'V' else 'v')
        sb.append(' ')
        sb.append(if (thumbMode(cpsr)) "THUMB" else "ARM")
        return sb.toString()
    }
}
