package com.nokia.vxp.debug

import com.nokia.vxp.memory.GuestMemoryReader
import com.nokia.vxp.memory.MemoryManager
import com.nokia.vxp.memory.MemoryRegion

/**
 * Hex-dumps guest memory and lists mapped regions, for a future debug
 * UI. dumpMemory() only needs read access (GuestMemoryReader), so it's
 * testable against a fake the same way mre/'s string reading is;
 * listRegions()/formatRegion() need the real MemoryManager since region
 * bookkeeping lives there - formatRegion() itself is still pure given a
 * MemoryRegion value, so it's tested directly with hand-built regions.
 */
object MemoryViewer {

    fun dumpMemory(memory: GuestMemoryReader, address: Long, length: Int): List<String> {
        val bytes = memory.read(address, length)
            ?: return listOf("<unreadable: 0x${address.toString(16)}, length=$length - likely unmapped or out of bounds>")
        return HexDump.format(bytes, address)
    }

    fun listRegions(memoryManager: MemoryManager): List<String> =
        memoryManager.regions().sortedBy { it.baseAddress }.map { formatRegion(it) }

    fun formatRegion(region: MemoryRegion): String {
        val perms = buildString {
            append(if (region.readable) 'R' else '-')
            append(if (region.writable) 'W' else '-')
            append(if (region.executable) 'X' else '-')
        }
        return "0x%08X-0x%08X  %-10s  %s  (%d bytes)".format(
            region.baseAddress, region.endAddress, region.name, perms, region.size
        )
    }
}
