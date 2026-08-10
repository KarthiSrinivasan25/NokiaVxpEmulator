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
    /** Initial content to copy in at map time, or null for zero-filled. */
    val initialContent: ByteArray? = null
)

data class ModuleMemoryLayout(
    val entryPoint: Long,
    val isThumbEntry: Boolean,
    val regions: List<MappedRegion>,
    /** ARM/ADS static-base value. For these MRE images this is ER_ZI base. */
    val staticBaseAddress: Long = 0L
) {
    val heapRegion: MappedRegion get() = regions.first { it.name == "heap" }
    val stackRegion: MappedRegion get() = regions.first { it.name == "stack" }
    val segmentRegions: List<MappedRegion> get() = regions.filter { it.name.startsWith("segment") }
}

object ModuleMapper {
    private const val PAGE_SIZE = 0x1000L
    private const val SHF_WRITE = 0x1L
    private const val SHF_ALLOC = 0x2L
    private const val SHT_PROGBITS = 1
    private const val SHT_NOBITS = 8

    private fun alignDown(value: Long): Long = value and (PAGE_SIZE - 1).inv()
    private fun alignUp(value: Long): Long =
        if (value <= 0) PAGE_SIZE else (value + PAGE_SIZE - 1) and (PAGE_SIZE - 1).inv()

    fun map(vxpFile: VxpFile): ModuleMemoryLayout {
        val loadableSegments = vxpFile.programHeaders.filter { it.isLoadable }

        val segmentRegions = loadableSegments.mapIndexed { index, ph ->
            val fileStart = ph.offset.toInt()
            val fileEnd = (ph.offset + ph.fileSize).toInt()
            val fileBytes = vxpFile.elfBytes.copyOfRange(fileStart, fileEnd)
            val content = if (ph.memSize > ph.fileSize) fileBytes.copyOf(ph.memSize.toInt()) else fileBytes

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

        /*
         * MediaTek/ADS VXP files commonly use a custom scatter layout:
         * the ELF PT_LOAD may contain only ER_RO, while ER_RW/ER_ZI are
         * described by section headers and live at low guest addresses.
         * The uploaded Gamebox has exactly this layout:
         *   ER_RW @ 0x00000000, size 0x10c
         *   ER_ZI @ 0x0000010c, size 0x970
         *
         * If these sections are not mapped, the CRT/startup code reaches
         * stores such as [r9] and immediately produces WRITE_UNMAPPED.
         */
        val writableAllocSections = vxpFile.sectionHeaders.filter { section ->
            section.type == SHT_PROGBITS || section.type == SHT_NOBITS
        }.filter { section ->
            (section.flags and SHF_ALLOC) != 0L && (section.flags and SHF_WRITE) != 0L && section.size > 0
        }

        val runtimeDataRegion = if (writableAllocSections.isNotEmpty()) {
            val start = writableAllocSections.minOf { it.addr }
            val end = writableAllocSections.maxOf { it.addr + it.size }
            val base = alignDown(start)
            val endAligned = alignUp(end)
            val bytes = ByteArray((endAligned - base).toInt())

            for (section in writableAllocSections) {
                val dst = (section.addr - base).toInt()
                if (section.type == SHT_PROGBITS) {
                    val srcStart = section.offset.toInt()
                    val srcEnd = (section.offset + section.size).toInt()
                    if (srcStart >= 0 && srcEnd <= vxpFile.elfBytes.size && dst >= 0 && dst + (srcEnd - srcStart) <= bytes.size) {
                        vxpFile.elfBytes.copyInto(bytes, dst, srcStart, srcEnd)
                    }
                }
                // SHT_NOBITS (ER_ZI) intentionally remains zero-filled.
            }

            MappedRegion(
                name = "runtimeData",
                baseAddress = base,
                size = endAligned - base,
                readable = true,
                writable = true,
                executable = false,
                initialContent = bytes
            )
        } else null

        // ADS/MRE startup uses R9 as the static base. ER_ZI is the static
        // base in the Gamebox image (0x10c). Fall back to runtimeData base.
        val ziBase = vxpFile.sectionHeaders.firstOrNull { it.name == "ER_ZI" }?.addr
        val staticBase = ziBase ?: runtimeDataRegion?.baseAddress ?: 0L

        val highestSegmentEnd = segmentRegions.maxOfOrNull { it.baseAddress + it.size } ?: 0L
        val highestRuntimeEnd = runtimeDataRegion?.let { it.baseAddress + it.size } ?: 0L
        val highestLoadedEnd = maxOf(highestSegmentEnd, highestRuntimeEnd)
        val heapBase = alignUp(highestLoadedEnd + PAGE_SIZE)
        val heapSize = alignUp(Constants.DEFAULT_HEAP_SIZE)
        val stackBase = alignUp(heapBase + heapSize + PAGE_SIZE)
        val stackSize = alignUp(Constants.DEFAULT_STACK_SIZE)

        val heapRegion = MappedRegion(
            name = "heap", baseAddress = heapBase, size = heapSize,
            readable = true, writable = true, executable = false
        )
        val stackRegion = MappedRegion(
            name = "stack", baseAddress = stackBase, size = stackSize,
            readable = true, writable = true, executable = false
        )

        val regions = buildList {
            if (runtimeDataRegion != null) add(runtimeDataRegion)
            addAll(segmentRegions)
            add(heapRegion)
            add(stackRegion)
        }

        return ModuleMemoryLayout(
            entryPoint = vxpFile.header.realEntryAddress,
            isThumbEntry = vxpFile.header.isThumbEntry,
            regions = regions,
            staticBaseAddress = staticBase
        )
    }
}
