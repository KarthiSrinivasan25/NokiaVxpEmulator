package com.nokia.vxp.loader

/**
 * One ELF32 relocation entry, normalized from either Elf32_Rel (implicit
 * addend - read from the existing bytes at the target offset) or
 * Elf32_Rela (explicit addend field) so callers don't need to care
 * which form a given section used.
 *
 * WHY THIS MATTERS: a real sample (gtrxAC/peanut.vxp) worked without
 * relocation processing because our loader happens to map every
 * segment at its exact file vaddr (load bias always zero - see
 * loader.ModuleMapper's doc comment). But a DIFFERENT real file
 * (observed via logcat from an actual user session: entry=0x8000,
 * symbols=0, a WRITE_UNMAPPED fault at guest address 0x0 only ~22
 * instructions after entry - textbook ".bss zeroing via an unresolved
 * pointer") shows that assumption doesn't hold universally. Some
 * relocation types (R_ARM_ABS32 etc.) need an actual symbol VALUE
 * filled in, not just a zero bias applied - if that symbol is never
 * resolved, the memory location stays zero, and code that dereferences
 * it as a pointer immediately faults exactly like this.
 */
data class ElfRelocation(
    val offset: Long,
    val type: Int,
    val symbolIndex: Int,
    val addend: Long
) {
    companion object {
        // Confirmed standard ARM ELF relocation type values (AAELF32 spec, not VXP-specific).
        const val R_ARM_ABS32 = 2
        const val R_ARM_REL32 = 3
        const val R_ARM_GLOB_DAT = 21
        const val R_ARM_JUMP_SLOT = 22
        const val R_ARM_RELATIVE = 23
    }
}
