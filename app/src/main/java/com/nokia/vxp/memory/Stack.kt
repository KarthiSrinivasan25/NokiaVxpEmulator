package com.nokia.vxp.memory

/**
 * Guest stack bookkeeping. ARM/AAPCS-style stacks grow downward - SP
 * starts at stackBase + stackSize and decrements on push. The actual SP
 * register lives inside Unicorn once cpu/CpuState wires up register
 * access; this class only tracks the *intended* initial value and
 * offers overflow/underflow sanity checks other modules can call before
 * trusting a computed address (e.g. the CPU interpreter before it
 * commits a push/pop to the real SP register).
 */
class Stack(private val stackBase: Long, private val stackSize: Long) {

    /** SP value to load before starting execution. */
    val initialStackPointer: Long get() = stackBase + stackSize

    fun isValidStackAddress(address: Long): Boolean =
        address in stackBase..(stackBase + stackSize)

    /** Returns the new SP after pushing [bytes], or null if that would underflow the stack region. */
    fun computeSpAfterPush(currentSp: Long, bytes: Long): Long? {
        val newSp = currentSp - bytes
        return if (newSp < stackBase) null else newSp
    }

    /** Returns the new SP after popping [bytes], or null if that would go past the top of the region. */
    fun computeSpAfterPop(currentSp: Long, bytes: Long): Long? {
        val newSp = currentSp + bytes
        return if (newSp > stackBase + stackSize) null else newSp
    }

    fun remainingBytes(currentSp: Long): Long = (currentSp - stackBase).coerceAtLeast(0)
}
