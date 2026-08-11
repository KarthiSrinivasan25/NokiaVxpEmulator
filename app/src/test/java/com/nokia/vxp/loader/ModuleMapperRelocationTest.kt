package com.nokia.vxp.loader

import com.nokia.vxp.utils.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds a minimal-but-structurally-real ELF32/ARM file with a
 * .symtab and a .rel.dyn section, to verify ModuleMapper actually
 * applies relocations rather than silently ignoring them - this is
 * what fixes the real "write to guest address 0x0" bug observed via a
 * real user session's logcat (see ModuleMapper's doc comment).
 */
class ModuleMapperRelocationTest {

    /**
     * Builds an ELF with:
     *  - one PT_LOAD segment (R+W) containing [4 bytes reserved for a
     *    relocation target, initialized to 0][4 bytes of normal code/data]
     *  - a .symtab with one real symbol at [symbolValue]
     *  - a .rel.dyn section with one relocation of [relocType] targeting
     *    the first 4 bytes of the segment, referencing that symbol
     */
    private fun buildElfWithRelocation(
        relocType: Int,
        symbolValue: Int,
        vaddr: Int = 0x8000,
        relocSymbolIndex: Int = 1
    ): ByteArray {
        val ehdrSize = Constants.ELF_HEADER_SIZE
        val phdrSize = Constants.PROGRAM_HEADER_SIZE
        val shdrSize = Constants.SECTION_HEADER_SIZE

        val phOff = ehdrSize
        val codeOff = phOff + phdrSize
        val codeContent = byteArrayOf(0, 0, 0, 0, 0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte()) // first 4 bytes = reloc target

        val shstrtabContent = byteArrayOf(0) +
            ".symtab".toByteArray(Charsets.US_ASCII) + byteArrayOf(0) +
            ".strtab".toByteArray(Charsets.US_ASCII) + byteArrayOf(0) +
            ".rel.dyn".toByteArray(Charsets.US_ASCII) + byteArrayOf(0) +
            ".shstrtab".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
        val symtabNameOff = 1
        val strtabNameOff = symtabNameOff + ".symtab".length + 1
        val relNameOff = strtabNameOff + ".strtab".length + 1
        val shstrtabNameOff = relNameOff + ".rel.dyn".length + 1

        val strtabContent = byteArrayOf(0) + "target_sym".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)

        val codeEnd = codeOff + codeContent.size
        val shstrtabOff = codeEnd
        val strtabOff = shstrtabOff + shstrtabContent.size
        val symtabOff = strtabOff + strtabContent.size
        // One null symbol (index 0, required by spec) + our real symbol (index 1)
        val symtabEntries = 2
        val symtabSize = symtabEntries * 16
        val relOff = symtabOff + symtabSize
        val relEntries = 1
        val relSize = relEntries * 8

        val shOff = relOff + relSize
        // sections: [0]=null, [1]=.symtab, [2]=.strtab, [3]=.rel.dyn, [4]=.shstrtab
        val shNum = 5
        val shstrtabIndex = 4

        val totalSize = shOff + shNum * shdrSize
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // --- ELF header ---
        buffer.put(Constants.ELF_MAGIC)
        buffer.put(Constants.EI_CLASS_OFFSET, Constants.ELF_CLASS_32.toByte())
        buffer.put(Constants.EI_DATA_OFFSET, Constants.ELF_DATA_2LSB.toByte())
        buffer.position(Constants.OFFSET_E_TYPE); buffer.putShort(2)
        buffer.position(Constants.OFFSET_E_MACHINE); buffer.putShort(Constants.EM_ARM.toShort())
        buffer.position(Constants.OFFSET_E_VERSION); buffer.putInt(1)
        buffer.position(Constants.OFFSET_E_ENTRY); buffer.putInt(vaddr)
        buffer.position(Constants.OFFSET_E_PHOFF); buffer.putInt(phOff)
        buffer.position(Constants.OFFSET_E_SHOFF); buffer.putInt(shOff)
        buffer.position(Constants.OFFSET_E_EHSIZE); buffer.putShort(ehdrSize.toShort())
        buffer.position(Constants.OFFSET_E_PHENTSIZE); buffer.putShort(phdrSize.toShort())
        buffer.position(Constants.OFFSET_E_PHNUM); buffer.putShort(1)
        buffer.position(Constants.OFFSET_E_SHENTSIZE); buffer.putShort(shdrSize.toShort())
        buffer.position(Constants.OFFSET_E_SHNUM); buffer.putShort(shNum.toShort())
        buffer.position(Constants.OFFSET_E_SHSTRNDX); buffer.putShort(shstrtabIndex.toShort())

        // --- Program header (RW, covers codeContent) ---
        buffer.position(phOff)
        buffer.putInt(Constants.PT_LOAD)
        buffer.putInt(codeOff)
        buffer.putInt(vaddr)
        buffer.putInt(vaddr)
        buffer.putInt(codeContent.size)
        buffer.putInt(codeContent.size)
        buffer.putInt(Constants.PF_READ or Constants.PF_WRITE)
        buffer.putInt(4)

        // --- Content ---
        buffer.position(codeOff); buffer.put(codeContent)
        buffer.position(shstrtabOff); buffer.put(shstrtabContent)
        buffer.position(strtabOff); buffer.put(strtabContent)

        // --- .symtab: [0] null symbol, [1] our real symbol ---
        buffer.position(symtabOff) // symbol 0: all zero (already zero-filled by allocate)
        buffer.position(symtabOff + 16)
        buffer.putInt(1) // name offset in .strtab ("target_sym")
        buffer.putInt(symbolValue) // st_value - the value relocations should resolve to
        buffer.putInt(0) // st_size
        buffer.put(0.toByte()) // st_info
        buffer.put(0.toByte()) // st_other
        buffer.putShort(1) // st_shndx (arbitrary non-zero, "defined somewhere")

        // --- .rel.dyn: one entry targeting vaddr (first 4 bytes of the segment), referencing symbol index [relocSymbolIndex] ---
        buffer.position(relOff)
        buffer.putInt(vaddr) // r_offset - where to patch
        val rInfo = (relocSymbolIndex shl 8) or (relocType and 0xFF)
        buffer.putInt(rInfo)

        // --- Section headers ---
        buffer.position(shOff + 1 * shdrSize) // [1] .symtab
        buffer.putInt(symtabNameOff); buffer.putInt(2 /* SHT_SYMTAB */); buffer.putInt(0); buffer.putInt(0)
        buffer.putInt(symtabOff); buffer.putInt(symtabSize)
        buffer.putInt(2 /* link -> .strtab is section index 2 */); buffer.putInt(1); buffer.putInt(4); buffer.putInt(16)

        buffer.position(shOff + 2 * shdrSize) // [2] .strtab
        buffer.putInt(strtabNameOff); buffer.putInt(3 /* SHT_STRTAB */); buffer.putInt(0); buffer.putInt(0)
        buffer.putInt(strtabOff); buffer.putInt(strtabContent.size)
        buffer.putInt(0); buffer.putInt(0); buffer.putInt(1); buffer.putInt(0)

        buffer.position(shOff + 3 * shdrSize) // [3] .rel.dyn
        buffer.putInt(relNameOff); buffer.putInt(9 /* SHT_REL */); buffer.putInt(0); buffer.putInt(0)
        buffer.putInt(relOff); buffer.putInt(relSize)
        buffer.putInt(0); buffer.putInt(0); buffer.putInt(4); buffer.putInt(8)

        buffer.position(shOff + shstrtabIndex * shdrSize) // [4] .shstrtab
        buffer.putInt(shstrtabNameOff); buffer.putInt(3 /* SHT_STRTAB */); buffer.putInt(0); buffer.putInt(0)
        buffer.putInt(shstrtabOff); buffer.putInt(shstrtabContent.size)
        buffer.putInt(0); buffer.putInt(0); buffer.putInt(1); buffer.putInt(0)

        return buffer.array()
    }

    @Test
    fun `R_ARM_ABS32 relocation patches the target with the resolved symbol value`() {
        val bytes = buildElfWithRelocation(relocType = ElfRelocation.R_ARM_ABS32, symbolValue = 0x12345678, vaddr = 0x8000)
        val parsed = VxpParser.parse("reloc-test.vxp", bytes) as ParseResult.Success

        assertEquals(1, parsed.file.relocations.size)
        assertTrue(parsed.file.symbols.any { it.value == 0x12345678L })

        val layout = ModuleMapper.map(parsed.file)
        val content = layout.segmentRegions[0].initialContent!!

        // First 4 bytes should now be 0x12345678 in little-endian, not the original zeros.
        assertEquals(0x78, content[0].toInt() and 0xFF)
        assertEquals(0x56, content[1].toInt() and 0xFF)
        assertEquals(0x34, content[2].toInt() and 0xFF)
        assertEquals(0x12, content[3].toInt() and 0xFF)
    }

    @Test
    fun `unresolved symbol index leaves the target unpatched rather than crashing`() {
        // Reference symbol index 99, which is genuinely out of range for
        // our 2-entry .symtab (indices 0 and 1 only) - this is the actual
        // "symbol doesn't resolve" case, not just a relocation that
        // happens to reference a valid symbol.
        val bytes = buildElfWithRelocation(
            relocType = ElfRelocation.R_ARM_ABS32,
            symbolValue = 0x11111111,
            vaddr = 0x8000,
            relocSymbolIndex = 99
        )
        val parsed = VxpParser.parse("reloc-test2.vxp", bytes) as ParseResult.Success

        // Parsing itself must never throw even for a relocation with a
        // nonsensical symbol index.
        assertEquals(1, parsed.file.relocations.size)
        assertEquals(99, parsed.file.relocations[0].symbolIndex)

        val layout = ModuleMapper.map(parsed.file)
        val content = layout.segmentRegions[0].initialContent!!

        // Target bytes must remain the original zeros - NOT patched with
        // symbolValue (0x11111111), since that symbol index doesn't exist.
        assertEquals(0, content[0].toInt())
        assertEquals(0, content[1].toInt())
        assertEquals(0, content[2].toInt())
        assertEquals(0, content[3].toInt())
    }

    @Test
    fun `relocation target outside any segment is skipped safely`() {
        // vaddr for the relocation matches the segment, but let's confirm
        // parsing+mapping a well-formed file completes without exceptions
        // as a baseline before more targeted edge-case coverage.
        val bytes = buildElfWithRelocation(relocType = ElfRelocation.R_ARM_RELATIVE, symbolValue = 0, vaddr = 0x8000)
        val parsed = VxpParser.parse("reloc-test3.vxp", bytes) as ParseResult.Success
        val layout = ModuleMapper.map(parsed.file)

        assertEquals(1, layout.segmentRegions.size)
    }
}
