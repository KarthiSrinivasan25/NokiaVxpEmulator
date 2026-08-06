package com.nokia.vxp.loader

/**
 * One ELF32 symbol table entry (Elf32_Sym). CONFIRMED VALUABLE against a
 * real sample (gtrxAC/peanut.vxp, MIT licensed): its .symtab contains
 * compiled vm_* API stub functions (in .text) alongside a separate set
 * of "_vm_*" (single leading underscore) symbols living in .bss - a
 * zero-initialized function-pointer jump table that the real MRE OS
 * loader patches with real API addresses before vm_main() runs. See
 * mre.VmSymbolBinder, which does the equivalent patching with our own
 * trap addresses instead.
 */
data class ElfSymbol(
    val name: String,
    val value: Long,
    val size: Long,
    val info: Int,
    val sectionIndex: Int
) {
    val bind: Int get() = info ushr 4
    val type: Int get() = info and 0xF

    companion object {
        const val STT_OBJECT = 1
        const val STT_FUNC = 2
    }
}
