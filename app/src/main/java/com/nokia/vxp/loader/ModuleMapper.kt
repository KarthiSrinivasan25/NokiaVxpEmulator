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
    /** Initial content to copy in at map time, or null for zero-filled (e.g. heap/stack). */
    val initialContent: ByteArray? = null
)

/**
 * Full memory layout for one loaded VXP module: where code/data/heap/stack
 * live in the emulated ARM address space. memory/MemoryManager (next
 * module) will consume this to actually set up Unicorn's mappings.
 *
 * Base addresses default to utils.Constants but are kept per-instance so
 * multiple modules could theoretically be mapped side by side later
 * (e.g. shared MRE system library + game module) without colliding.
 */
data class ModuleMemoryLayout(
    val entryPoint: Long,
    val regions: List<MappedRegion>
) {
    val codeRegion: MappedRegion get() = regions.first { it.name == "code" }
    val dataRegion: MappedRegion get() = regions.first { it.name == "data" }
    val heapRegion: MappedRegion get() = regions.first { it.name == "heap" }
    val stackRegion: MappedRegion get() = regions.first { it.name == "stack" }
}

object ModuleMapper {

    fun map(vxpFile: VxpFile): ModuleMemoryLayout {
        val codeBase = Constants.DEFAULT_CODE_BASE
        val dataBase = Constants.DEFAULT_DATA_BASE
        val heapBase = Constants.DEFAULT_HEAP_BASE
        val stackBase = Constants.DEFAULT_STACK_BASE

        val regions = listOf(
            MappedRegion(
                name = "code",
                baseAddress = codeBase,
                size = vxpFile.code.size.toLong(),
                readable = true,
                writable = false,
                executable = true,
                initialContent = vxpFile.code
            ),
            MappedRegion(
                name = "data",
                baseAddress = dataBase,
                size = vxpFile.data.size.toLong(),
                readable = true,
                writable = true,
                executable = false,
                initialContent = vxpFile.data
            ),
            MappedRegion(
                name = "heap",
                baseAddress = heapBase,
                size = Constants.DEFAULT_HEAP_SIZE,
                readable = true,
                writable = true,
                executable = false,
                initialContent = null
            ),
            MappedRegion(
                name = "stack",
                baseAddress = stackBase,
                size = Constants.DEFAULT_STACK_SIZE,
                readable = true,
                writable = true,
                executable = false,
                initialContent = null
            )
        )

        // TODO: once the real VXP entry-point field location is confirmed,
        // read it from the header instead of assuming code segment start.
        val entryPoint = codeBase

        return ModuleMemoryLayout(entryPoint = entryPoint, regions = regions)
    }
}
