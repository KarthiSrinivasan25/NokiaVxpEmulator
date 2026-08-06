package com.nokia.vxp.loader

import com.nokia.vxp.utils.Constants

/**
 * One contiguous region mapped into Unicorn memory.
 */
data class MappedRegion(
    val name: String,
    val baseAddress: Long,
    val size: Long,
    val readable: Boolean,
    val writable: Boolean,
    val executable: Boolean,
    val initialContent: ByteArray? = null
)


data class ModuleMemoryLayout(
    val entryPoint: Long,
    val isThumbEntry: Boolean,
    val regions: List<MappedRegion>
) {

    val heapRegion: MappedRegion
        get() = regions.first { it.name == "heap" }

    val stackRegion: MappedRegion
        get() = regions.first { it.name == "stack" }

    val segmentRegions: List<MappedRegion>
        get() = regions.filter {
            it.name.startsWith("segment")
        }
}


object ModuleMapper {

    private const val PAGE_SIZE = 0x1000L


    private fun alignUp(value: Long): Long {
        if (value <= 0) return PAGE_SIZE

        return (value + PAGE_SIZE - 1) and
                (PAGE_SIZE - 1).inv()
    }


    fun map(vxpFile: VxpFile): ModuleMemoryLayout {

        val loadableSegments =
            vxpFile.programHeaders.filter {
                it.isLoadable
            }


        val segmentRegions =
            loadableSegments.mapIndexed { index, ph ->


                val fileStart = ph.offset.toInt()

                val fileEnd =
                    (ph.offset + ph.fileSize).toInt()


                val fileBytes =
                    if (fileEnd <= vxpFile.elfBytes.size) {
                        vxpFile.elfBytes.copyOfRange(
                            fileStart,
                            fileEnd
                        )
                    } else {
                        ByteArray(0)
                    }



                /*
                 * PT_LOAD:
                 *
                 * memSize >= fileSize normally.
                 *
                 * BSS area is zero filled.
                 */
                val totalSize =
                    maxOf(
                        ph.memSize,
                        ph.fileSize
                    )


                val content =
                    if (totalSize > ph.fileSize) {

                        fileBytes.copyOf(
                            totalSize.toInt()
                        )

                    } else {

                        fileBytes
                    }



                MappedRegion(
                    name = "segment$index",

                    baseAddress = ph.vaddr,

                    size = alignUp(totalSize),

                    readable = ph.readable,

                    writable = ph.writable,

                    executable = ph.executable,

                    initialContent = content
                )
            }



        /*
         * DO NOT MERGE PT_LOAD SEGMENTS.
         *
         * .text  -> RX
         * .data  -> RW
         *
         * Merging destroys permissions.
         */
        val highestSegmentEnd =
            segmentRegions.maxOfOrNull {
                it.baseAddress + it.size
            } ?: 0L



        val heapBase =
            alignUp(
                highestSegmentEnd +
                        PAGE_SIZE
            )


        val heapSize =
            alignUp(
                Constants.DEFAULT_HEAP_SIZE
            )


        val stackBase =
            alignUp(
                heapBase +
                        heapSize +
                        PAGE_SIZE
            )


        val stackSize =
            alignUp(
                Constants.DEFAULT_STACK_SIZE
            )



        val heapRegion =
            MappedRegion(
                name = "heap",

                baseAddress = heapBase,

                size = heapSize,

                readable = true,

                writable = true,

                executable = false,

                initialContent = null
            )



        val stackRegion =
            MappedRegion(
                name = "stack",

                baseAddress = stackBase,

                size = stackSize,

                readable = true,

                writable = true,

                executable = false,

                initialContent = null
            )



        return ModuleMemoryLayout(

            entryPoint =
                vxpFile.header.realEntryAddress,

            isThumbEntry =
                vxpFile.header.isThumbEntry,


            /*
             * IMPORTANT:
             * keep original PT_LOAD regions.
             */
            regions =
                segmentRegions +
                heapRegion +
                stackRegion
        )
    }
}