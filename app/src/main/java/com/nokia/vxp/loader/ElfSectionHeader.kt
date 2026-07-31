package com.nokia.vxp.loader

/** One ELF32 section header entry (Elf32_Shdr), with its name already resolved from .shstrtab. */
data class ElfSectionHeader(
    val name: String,
    val type: Int,
    val flags: Long,
    val addr: Long,
    val offset: Long,
    val size: Long,
    val link: Int,
    val info: Int,
    val addrAlign: Long,
    val entSize: Long
)
