package com.nokia.vxp.utils

/**
 * Shared constants.
 *
 * VXP/MRE files are ARM32 ELF executables (per community reverse-
 * engineering at lpcwiki.miraheze.org/wiki/MediaTek_MRE - Nokia/MediaTek
 * never published an official spec) with MediaTek-specific metadata
 * "tags" appended after the ELF data, and an ELF section named
 * ".vm_res" holding bundled resources. Some VXP files wrap that ELF in
 * zlib compression (detectable by the first two bytes, commonly 0x78
 * 0xDA) or a ZIP container (magic "PK\x03\x04") instead of storing it
 * raw - VxpLoader handles unwrapping both before the ELF parser ever
 * sees the bytes.
 *
 * The ELF32 field layout below is the standard, publicly documented ELF
 * specification (not VXP-specific), so unlike the old guessed custom
 * header this is not a "best effort" - it's the real format.
 */
object Constants {

    const val VXP_MAX_REASONABLE_FILE_SIZE = 32L * 1024 * 1024 // 32 MB sanity cap

    // --- ELF32 identification -------------------------------------------
    val ELF_MAGIC = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())
    const val ELF_CLASS_32 = 1
    const val ELF_DATA_2LSB = 1 // little-endian
    const val EM_ARM = 40 // e_machine value for ARM per the ELF spec
    const val EM_NONE = 0 // what real MediaTek VXP tooling actually writes - see loader.VxpValidator

    // --- ELF32 header (Elf32_Ehdr), all fields little-endian ------------
    const val ELF_HEADER_SIZE = 52
    const val EI_CLASS_OFFSET = 4
    const val EI_DATA_OFFSET = 5
    const val OFFSET_E_TYPE = 16
    const val OFFSET_E_MACHINE = 18
    const val OFFSET_E_VERSION = 20
    const val OFFSET_E_ENTRY = 24
    const val OFFSET_E_PHOFF = 28
    const val OFFSET_E_SHOFF = 32
    const val OFFSET_E_FLAGS = 36
    const val OFFSET_E_EHSIZE = 40
    const val OFFSET_E_PHENTSIZE = 42
    const val OFFSET_E_PHNUM = 44
    const val OFFSET_E_SHENTSIZE = 46
    const val OFFSET_E_SHNUM = 48
    const val OFFSET_E_SHSTRNDX = 50

    // --- ELF32 program header (Elf32_Phdr) -------------------------------
    const val PROGRAM_HEADER_SIZE = 32
    const val PT_LOAD = 1
    const val PF_EXEC = 1
    const val PF_WRITE = 2
    const val PF_READ = 4

    // --- ELF32 section header (Elf32_Shdr) -------------------------------
    const val SECTION_HEADER_SIZE = 40
    const val VM_RES_SECTION_NAME = ".vm_res"

    // --- Container wrapper detection --------------------------------------
    // zlib header's first byte (CMF) is 0x78 for the deflate/32K-window
    // method MRE tooling uses; the wiki source notes 0x78 0xDA specifically.
    const val ZLIB_MAGIC_BYTE: Byte = 0x78
    val ZIP_MAGIC = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 0x03, 0x04)

    // --- Emulated screen defaults (graphics/ module) ---------------------
    // 128x160 was a common Nokia S40-era color-LCD resolution for
    // MRE/VXP-capable devices; adjust per real target device once known.
    const val DEFAULT_SCREEN_WIDTH = 128
    const val DEFAULT_SCREEN_HEIGHT = 160

    // --- Emulated heap/stack sizing (memory/ module) ----------------------
    // Bases are now computed dynamically by loader.ModuleMapper, placed
    // right after the highest ELF PT_LOAD segment, so they can never
    // collide with the guest's actual code/data addresses. Only sizes
    // are fixed defaults here.
    const val DEFAULT_HEAP_SIZE = 2L * 1024 * 1024
    const val DEFAULT_STACK_SIZE = 256L * 1024
}
