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
 *
 * ON ELF RELOCATIONS: a real sample (gtrxAC/peanut.vxp, MIT licensed)
 * turned out to be ET_DYN (position-independent) with .rel.dyn/.rel.plt
 * sections present - normally that would mean relocations need
 * processing at load time (e.g. R_ARM_RELATIVE entries adding a load
 * bias to each patched address). This mapper deliberately loads every
 * segment at its exact file vaddr rather than rebasing anywhere else,
 * which makes the load bias always zero - so those relocations are
 * no-ops under this strategy specifically, and can be safely skipped.
 * This stops being true if this mapper is ever changed to load modules
 * at a different/dynamic base address (e.g. to run several VXP modules
 * side by side) - relocation processing would need to be added then.
 */
data class ModuleMemoryLayout(
    val entryPoint: Long,
    val isThumbEntry: Boolean,
    val regions: List<MappedRegion>,
    /**
     * Runtime base address for ARM RWPI (Read-Write Position Independence)
     * data - the value R9 must hold before the entry point runs, or null
     * if this module doesn't use RWPI (no ER_RW/ER_ZI sections found).
     *
     * Binaries built with the default ARM RVCT/armlink scatter-load
     * layout (section names ER_RO/ER_RW/ER_ZI) link their writable-data
     * section at virtual address 0 - it's not a real load address, it's
     * a placeholder meaning "R9-relative". The entry point's own
     * scatter-load startup code (a copy loop reading a small "region
     * table" right after the entry point) computes each RW/ZI
     * destination address as `offset + R9`, expecting the OS/loader to
     * have pointed R9 at a real, writable runtime data area first. If R9
     * is left at 0 (the CPU's power-on default), those destination
     * addresses collapse down near address 0 - an unmapped guard page in
     * our layout - and the very first scatter-load copy faults with an
     * unmapped write, before a single line of the guest's actual game
     * code has run. See emulator.Runtime.from(), which is what actually
     * applies this to R9 via cpu.CpuState.
     */
    val staticBaseAddress: Long?
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

        // ARM RWPI static-data region (ER_RW + ER_ZI), if this binary has
        // one - see the doc comment on ModuleMemoryLayout.staticBaseAddress
        // for the full "why". ER_RW's own file bytes are the *initial
        // content* for that data (copied by the guest's own scatter-load
        // routine at runtime, not by us - we only need to give it a real,
        // writable destination to copy into); ER_ZI is purely zero-filled
        // and needs no file bytes at all. Both are optional; a binary with
        // neither (no writable globals) has no need for RWPI, no ER_RW/
        // ER_ZI sections will exist, and staticBase stays null.
        val erRwSection = vxpFile.sectionHeaders.firstOrNull { it.name == "ER_RW" }
        val erZiSection = vxpFile.sectionHeaders.firstOrNull { it.name == "ER_ZI" }
        val staticDataSize = (erRwSection?.size ?: 0L) + (erZiSection?.size ?: 0L)

        val staticDataRegion = if (staticDataSize > 0L) {
            MappedRegion(
                name = "staticData",
                baseAddress = alignUp(highestSegmentEnd + PAGE_SIZE),
                size = alignUp(staticDataSize),
                readable = true,
                writable = true,
                executable = false,
                // Zero-filled: the guest's own __scatterload copy loop
                // fills in ER_RW's real initial values at runtime by
                // reading them from ROM (inside segment0, where they're
                // already correctly present) and writing them here -
                // pre-populating this region would just be overwritten.
                initialContent = null
            )
        } else {
            null
        }
        val afterStaticData = staticDataRegion?.let { it.baseAddress + it.size } ?: highestSegmentEnd

        val heapBase = alignUp(afterStaticData + PAGE_SIZE) // one-page gap as a guard against off-by-one overruns
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
            regions = segmentRegions + listOfNotNull(staticDataRegion) + heapRegion + stackRegion,
            staticBaseAddress = staticDataRegion?.baseAddress
        )
    }
}
