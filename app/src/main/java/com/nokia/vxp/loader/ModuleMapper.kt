package com.nokia.vxp.loader

import com.nokia.vxp.utils.Constants
import com.nokia.vxp.utils.Logger

private const val TAG = "ModuleMapper"

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
 * ON ELF RELOCATIONS: this mapper loads every segment at its exact file
 * vaddr rather than rebasing anywhere else, which makes the LOAD BIAS
 * always zero - so R_ARM_RELATIVE-style relocations (which just add a
 * bias to an existing value) are genuinely no-ops under this strategy.
 * BUT relocation types that need an actual resolved SYMBOL VALUE
 * (R_ARM_ABS32, R_ARM_GLOB_DAT, R_ARM_JUMP_SLOT) are NOT no-ops - if
 * left unprocessed, the memory location they target simply stays
 * whatever was in the file (often zero), and guest code that
 * dereferences it as a pointer faults immediately. This was confirmed
 * as a real bug via an actual user session's logcat: a file with
 * symbols=0 (nothing found - a separate bug, since only .symtab was
 * being checked, not .dynsym too) faulted writing to guest address 0x0
 * only ~22 instructions after entry - the textbook signature of crt0's
 * .bss-zeroing loop running with an unresolved (still-zero) pointer.
 * applyRelocations() below now actually processes these.
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

        applyRelocations(segmentRegions, vxpFile.relocations, vxpFile.symbols)

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

    /**
     * Applies relocations directly into each segment's initialContent
     * byte array, in place, BEFORE mapping - so the correct resolved
     * values are already there on the very first uc_mem_write, rather
     * than needing a separate patch pass after mapping.
     *
     * Only the relocation types with well-defined, symbol-based
     * semantics are handled: R_ARM_ABS32/GLOB_DAT/JUMP_SLOT (target =
     * resolved symbol value + addend) and R_ARM_RELATIVE (target =
     * addend + load bias, and load bias is always 0 here - see this
     * file's top doc comment). Anything else is logged and skipped
     * rather than guessed at.
     */
    private fun applyRelocations(segments: List<MappedRegion>, relocations: List<ElfRelocation>, symbols: List<ElfSymbol>) {
        if (relocations.isEmpty()) return

        var applied = 0
        var skipped = 0

        for (reloc in relocations) {
            val value: Long? = when (reloc.type) {
                ElfRelocation.R_ARM_ABS32, ElfRelocation.R_ARM_GLOB_DAT, ElfRelocation.R_ARM_JUMP_SLOT -> {
                    val symbolValue = symbols.getOrNull(reloc.symbolIndex)?.value
                    if (symbolValue == null) {
                        Logger.w(TAG, "Relocation at 0x${reloc.offset.toString(16)} references symbol index ${reloc.symbolIndex}, which isn't in our (possibly incomplete) symbol list - skipping")
                        null
                    } else {
                        symbolValue + reloc.addend
                    }
                }
                ElfRelocation.R_ARM_RELATIVE -> reloc.addend // + load bias (always 0 under this mapper's strategy)
                else -> {
                    Logger.w(TAG, "Unhandled relocation type ${reloc.type} at 0x${reloc.offset.toString(16)} - skipping")
                    null
                }
            }

            if (value == null) {
                skipped++
                continue
            }

            val region = segments.firstOrNull { reloc.offset >= it.baseAddress && reloc.offset < it.baseAddress + it.size }
            val content = region?.initialContent
            if (region == null || content == null) {
                Logger.w(TAG, "Relocation target 0x${reloc.offset.toString(16)} isn't inside any mapped segment - skipping")
                skipped++
                continue
            }

            val localOffset = (reloc.offset - region.baseAddress).toInt()
            if (localOffset + 4 > content.size) {
                Logger.w(TAG, "Relocation target 0x${reloc.offset.toString(16)} is too close to the end of segment '${region.name}' - skipping")
                skipped++
                continue
            }

            content[localOffset] = (value and 0xFF).toByte()
            content[localOffset + 1] = ((value ushr 8) and 0xFF).toByte()
            content[localOffset + 2] = ((value ushr 16) and 0xFF).toByte()
            content[localOffset + 3] = ((value ushr 24) and 0xFF).toByte()
            applied++
        }

        Logger.i(TAG, "Relocations: applied $applied, skipped $skipped (of ${relocations.size} total)")
    }
}
