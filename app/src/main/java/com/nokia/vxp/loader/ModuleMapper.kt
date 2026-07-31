package com.nokia.vxp.loader

import com.nokia.vxp.utils.Constants

/** One contiguous region to be mapped into the emulated address space. */
data class MappedRegion(
    val name: String,
    val baseAddress: Long,
    val size: Long,
    val readable: Boolean,
    val writable: Boolean,
    val executable: Boolean,
    /** Initial content to copy in at map time, or null for zero-filled (e.g. heap/stack, or a segment's .bss tail). */
    val initialContent: ByteArray? = null
)

/**
 * Full memory layout for one loaded VXP module: the ELF's own PT_LOAD
 * segments (mapped at their real ELF virtual addresses) plus a heap and
 * stack region placed safely after them. memory.MemoryManager consumes
 * this to actually set up Unicorn's mappings.
 */
data class ModuleMemoryLayout(
    val entryPoint: Long,
    val isThumbEntry: Boolean,
    val regions: List<MappedRegion>
) {
    val heapRegion: MappedRegion get() = regions.first { it.name == "heap" }
    val stackRegion: MappedRegion get() = regions.first { it.name == "stack" }

    /** The ELF-derived loadable segments, in the order they appeared in the program header table. */
    val segmentRegions: List<MappedRegion> get() = regions.filter { it.name.startsWith("segment") }
}

object ModuleMapper {

    // Small page-align helper kept local to this file rather than importing
    // memory.Page, to avoid loader/ taking a dependency on memory/ (memory/
    // already depends on loader/ - adding the reverse would create a cycle).
    private const val PAGE_SIZE = 0x1000L
    private fun alignUp(value: Long): Long {
        if (value <= 0) return PAGE_SIZE
        return (value + PAGE_SIZE - 1) and (PAGE_SIZE - 1).inv()
    }

    fun map(vxpFile: VxpFile): ModuleMemoryLayout {
        val loadableSegments = vxpFile.programHeaders.filter { it.isLoadable }

        val segmentRegions = loadableSegments.mapIndexed { index, ph ->
            val fileBytes = vxpFile.elfBytes.copyOfRange(ph.offset.toInt(), (ph.offset + ph.fileSize).toInt())
            // memSize can exceed fileSize (the difference is .bss - zero-initialized
            // data not stored in the file); pad with zeros up to memSize so the
            // mapped region is the full size the ELF says it needs.
            val content = if (ph.memSize > ph.fileSize) {
                fileBytes.copyOf(ph.memSize.toInt()) // copyOf zero-pads the extra space
            } else {
                fileBytes
            }

            MappedRegion(
                name = "segment$index",
                baseAddress = ph.vaddr,
                size = ph.memSize,
                readable = ph.readable,
                writable = ph.writable,
                executable = ph.executable,
                initialContent = content
            )
        }

        // Place heap/stack safely after the highest ELF segment, rather
        // than at fixed addresses that could collide with wherever the
        // real ELF's vaddrs actually land.
        val highestSegmentEnd = segmentRegions.maxOfOrNull { it.baseAddress + it.size } ?: 0L
        val heapBase = alignUp(highestSegmentEnd + PAGE_SIZE) // one-page gap as a guard against off-by-one overruns
        val heapSize = alignUp(Constants.DEFAULT_HEAP_SIZE)
        val stackBase = alignUp(heapBase + heapSize + PAGE_SIZE)
        val stackSize = alignUp(Constants.DEFAULT_STACK_SIZE)

        val heapRegion = MappedRegion(
            name = "heap",
            baseAddress = heapBase,
            size = heapSize,
            readable = true,
            writable = true,
            executable = false,
            initialContent = null
        )
        val stackRegion = MappedRegion(
            name = "stack",
            baseAddress = stackBase,
            size = stackSize,
            readable = true,
            writable = true,
            executable = false,
            initialContent = null
        )

        return ModuleMemoryLayout(
            entryPoint = vxpFile.header.realEntryAddress,
            isThumbEntry = vxpFile.header.isThumbEntry,
            regions = segmentRegions + heapRegion + stackRegion
        )
    }
}
