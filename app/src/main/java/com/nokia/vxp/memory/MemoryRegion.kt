package com.nokia.vxp.memory

/**
 * Runtime-side region descriptor - what MemoryManager actually mapped.
 * (Distinct from loader.MappedRegion, which is the loader's *intent*
 * before mapping; ModuleMapper produces that, MemoryManager consumes it
 * and produces these.)
 */
data class MemoryRegion(
    val name: String,
    val baseAddress: Long,
    val size: Long,
    val readable: Boolean,
    val writable: Boolean,
    val executable: Boolean
) {
    val endAddress: Long get() = baseAddress + size

    fun contains(address: Long): Boolean = address in baseAddress until endAddress

    /** Bitmask matching Unicorn's UC_PROT_READ/WRITE/EXEC exactly. */
    fun toUnicornPerms(): Int {
        var perms = 0
        if (readable) perms = perms or PROT_READ
        if (writable) perms = perms or PROT_WRITE
        if (executable) perms = perms or PROT_EXEC
        return perms
    }

    companion object {
        const val PROT_READ = 1
        const val PROT_WRITE = 2
        const val PROT_EXEC = 4
    }
}
