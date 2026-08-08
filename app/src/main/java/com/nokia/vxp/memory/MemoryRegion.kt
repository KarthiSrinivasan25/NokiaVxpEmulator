
package com.nokia.vxp.memory

/**
 * Runtime-side region descriptor.
 *
 * This is distinct from loader.MappedRegion, which describes the
 * loader's intended mapping. MemoryManager consumes MappedRegion and
 * produces MemoryRegion after mapping.
 */
data class MemoryRegion(
    val name: String,
    val baseAddress: Long,
    val size: Long,
    val readable: Boolean,
    val writable: Boolean,
    val executable: Boolean
) {

    val endAddress: Long
        get() = baseAddress + size

    fun contains(address: Long): Boolean {
        return address in baseAddress until endAddress
    }

    /**
     * Bitmask matching Unicorn's UC_PROT_READ/WRITE/EXEC values.
     */
    fun toUnicornPerms(): Int {
        var perms = 0

        if (readable) {
            perms = perms or PROT_READ
        }

        if (writable) {
            perms = perms or PROT_WRITE
        }

        if (executable) {
            perms = perms or PROT_EXEC
        }

        return perms
    }

    companion object {
        const val PROT_READ = 1
        const val PROT_WRITE = 2
        const val PROT_EXEC = 4
    }
}