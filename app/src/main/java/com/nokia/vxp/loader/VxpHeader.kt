package com.nokia.vxp.loader

/**
 * Parsed ELF32 header (Elf32_Ehdr) fields relevant to loading a VXP/MRE
 * module. Field layout is the standard, publicly documented ELF spec -
 * see utils.Constants for the byte offsets.
 */
data class VxpHeader(
    val elfType: Int,
    val machine: Int,
    val version: Long,
    val entryPoint: Long,
    val programHeaderOffset: Long,
    val programHeaderEntrySize: Int,
    val programHeaderCount: Int,
    val sectionHeaderOffset: Long,
    val sectionHeaderEntrySize: Int,
    val sectionHeaderCount: Int,
    val sectionHeaderStringTableIndex: Int
) {
    /** Whether the entry point's low bit marks Thumb-mode execution (standard ARM interworking convention). */
    val isThumbEntry: Boolean get() = (entryPoint and 1L) == 1L

    /** entryPoint with the Thumb marker bit masked off - the actual address to set PC to. */
    val realEntryAddress: Long get() = entryPoint and 1L.inv()

    val displayVersion: String get() = "ELF32/ARM"
}
