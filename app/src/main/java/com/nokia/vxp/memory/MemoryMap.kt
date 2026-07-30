package com.nokia.vxp.memory

/**
 * Bookkeeping for all regions mapped for one running emulator instance.
 * Purely a Kotlin-side index (the real bytes live in Unicorn); used for
 * things like "what region does this faulting address belong to" in the
 * debug module, and to catch overlapping region requests before they
 * ever reach native code.
 */
class MemoryMap {
    private val regions = mutableListOf<MemoryRegion>()

    fun add(region: MemoryRegion) {
        val overlap = regions.firstOrNull { existing ->
            region.baseAddress < existing.endAddress && existing.baseAddress < region.endAddress
        }
        require(overlap == null) {
            "Region '${region.name}' [0x${region.baseAddress.toString(16)}-0x${region.endAddress.toString(16)}) " +
                "overlaps existing region '${overlap?.name}' " +
                "[0x${overlap?.baseAddress?.toString(16)}-0x${overlap?.endAddress?.toString(16)})"
        }
        regions += region
    }

    fun all(): List<MemoryRegion> = regions.toList()

    fun regionAt(address: Long): MemoryRegion? = regions.firstOrNull { it.contains(address) }

    fun regionByName(name: String): MemoryRegion? = regions.firstOrNull { it.name == name }

    fun clear() = regions.clear()
}
