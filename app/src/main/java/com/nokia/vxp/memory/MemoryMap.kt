
package com.nokia.vxp.memory

/**
 * Kotlin-side bookkeeping for all regions mapped for one emulator
 * instance.
 *
 * The actual bytes live in Unicorn. This class only tracks the regions
 * so other parts of the emulator can determine which mapped region
 * contains an address.
 */
class MemoryMap {

    private val regions = mutableListOf<MemoryRegion>()

    fun add(region: MemoryRegion) {
        val overlap = regions.firstOrNull { existing ->
            region.baseAddress < existing.endAddress &&
                existing.baseAddress < region.endAddress
        }

        require(overlap == null) {
            "Region '${region.name}' " +
                "[0x${region.baseAddress.toString(16)}-" +
                "0x${region.endAddress.toString(16)}) overlaps " +
                "existing region '${overlap?.name}' " +
                "[0x${overlap?.baseAddress?.toString(16)}-" +
                "0x${overlap?.endAddress?.toString(16)})"
        }

        regions += region
    }

    fun all(): List<MemoryRegion> {
        return regions.toList()
    }

    fun regionAt(address: Long): MemoryRegion? {
        return regions.firstOrNull {
            it.contains(address)
        }
    }

    fun regionByName(name: String): MemoryRegion? {
        return regions.firstOrNull {
            it.name == name
        }
    }

    fun clear() {
        regions.clear()
    }
}
