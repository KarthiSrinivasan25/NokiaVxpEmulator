
package com.nokia.vxp.memory

/**
 * Guest stack bookkeeping.
 *
 * ARM/AAPCS-style stacks grow downward. The initial SP is therefore
 * stackBase + stackSize.
 */
class Stack(
    private val stackBase: Long,
    private val stackSize: Long
) {

    /**
     * SP value to load before starting execution.
     */
    val initialStackPointer: Long
        get() = stackBase + stackSize

    fun isValidStackAddress(
        address: Long
    ): Boolean {
        return address in stackBase..(stackBase + stackSize)
    }

    /**
     * Returns the new SP after pushing [bytes], or null if the push
     * would underflow the stack region.
     */
    fun computeSpAfterPush(
        currentSp: Long,
        bytes: Long
    ): Long? {
        val newSp = currentSp - bytes

        return if (newSp < stackBase) {
            null
        } else {
            newSp
        }
    }

    /**
     * Returns the new SP after popping [bytes], or null if the pop
     * would move beyond the top of the stack.
     */
    fun computeSpAfterPop(
        currentSp: Long,
        bytes: Long
    ): Long? {
        val newSp = currentSp + bytes

        return if (newSp > stackBase + stackSize) {
            null
        } else {
            newSp
        }
    }

    fun remainingBytes(
        currentSp: Long
    ): Long {
        return (currentSp - stackBase).coerceAtLeast(0L)
    }
}
