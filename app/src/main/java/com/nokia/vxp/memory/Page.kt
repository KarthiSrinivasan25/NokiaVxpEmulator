package com.nokia.vxp.memory

/**
 * Unicorn (via its embedded QEMU) requires mapped regions to be aligned
 * to 4KB pages in both base address and size. The native side
 * (memory.cpp) does its own aligning defensively, but Kotlin-side code
 * that wants to reason about region boundaries ahead of time (e.g. when
 * deciding default base addresses in ModuleMapper) can use these too.
 */
object Page {
    const val SIZE = 0x1000L

    fun alignDown(address: Long): Long = address and (SIZE - 1).inv()

    fun alignUpSize(size: Long): Long {
        if (size <= 0) return SIZE
        return (size + SIZE - 1) and (SIZE - 1).inv()
    }

    fun isAligned(address: Long): Boolean = (address and (SIZE - 1)) == 0L
}
