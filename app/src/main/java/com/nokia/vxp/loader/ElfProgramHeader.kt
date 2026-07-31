package com.nokia.vxp.loader

import com.nokia.vxp.utils.Constants

/** One ELF32 program header entry (Elf32_Phdr) - describes one segment to load into memory. */
data class ElfProgramHeader(
    val type: Int,
    val offset: Long,
    val vaddr: Long,
    val paddr: Long,
    val fileSize: Long,
    val memSize: Long,
    val flags: Int,
    val align: Long
) {
    val isLoadable: Boolean get() = type == Constants.PT_LOAD
    val readable: Boolean get() = (flags and Constants.PF_READ) != 0
    val writable: Boolean get() = (flags and Constants.PF_WRITE) != 0
    val executable: Boolean get() = (flags and Constants.PF_EXEC) != 0
}
