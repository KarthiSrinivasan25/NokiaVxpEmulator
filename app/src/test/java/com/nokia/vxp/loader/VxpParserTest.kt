package com.nokia.vxp.loader

import com.nokia.vxp.utils.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds a minimal-but-structurally-real ELF32/ARM file by hand (header +
 * one PT_LOAD segment + section header table with a .vm_res section +
 * appended metadata tags) and checks VxpParser reads it back correctly.
 * Unlike the old custom-format tests, this is checking against the real,
 * standard, publicly-documented ELF32 spec - not a guessed layout.
 */
class VxpParserTest {

    /** Builds a minimal valid little-endian ELF32/ARM file with one PT_LOAD segment, an optional .vm_res section, and optional appended tags. */
    private fun buildFakeElf(
        code: ByteArray = byteArrayOf(0x01, 0x02, 0x03, 0x04),
        entryPoint: Int = 0x8000,
        vaddr: Int = 0x8000,
        vmResData: ByteArray? = byteArrayOf(0xAA.toByte(), 0xBB.toByte()),
        tags: List<Pair<Int, ByteArray>> = listOf(1 to byteArrayOf(0x41, 0x42))
    ): ByteArray {
        val ehdrSize = Constants.ELF_HEADER_SIZE
        val phdrSize = Constants.PROGRAM_HEADER_SIZE
        val shdrSize = Constants.SECTION_HEADER_SIZE
        val hasVmRes = vmResData != null

        val phOff = ehdrSize
        val codeOff = phOff + phdrSize

        // Section string table content: "\0.vm_res\0" (index 0 is always the empty string).
        val shstrtabContent = byteArrayOf(0) + ".vm_res".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
        val shstrtabOff = codeOff + code.size
        val vmResOff = shstrtabOff + shstrtabContent.size
        val vmResSize = vmResData?.size ?: 0

        val shOff = vmResOff + vmResSize
        // [0]=null section, [1]=.vm_res (only if present), [last]=.shstrtab
        val shNum = if (hasVmRes) 3 else 2
        val shstrtabIndex = shNum - 1
        val elfEnd = shOff + shNum * shdrSize

        val tagsBytes = tags.flatMap { (id, data) ->
            val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(id).putInt(data.size).array().toList()
            header + data.toList()
        }

        val totalSize = elfEnd + tagsBytes.size
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // --- ELF header ---
        buffer.put(Constants.ELF_MAGIC)
        buffer.put(Constants.EI_CLASS_OFFSET, Constants.ELF_CLASS_32.toByte())
        buffer.put(Constants.EI_DATA_OFFSET, Constants.ELF_DATA_2LSB.toByte())
        buffer.position(Constants.OFFSET_E_TYPE); buffer.putShort(2) // ET_EXEC
        buffer.position(Constants.OFFSET_E_MACHINE); buffer.putShort(Constants.EM_ARM.toShort())
        buffer.position(Constants.OFFSET_E_VERSION); buffer.putInt(1)
        buffer.position(Constants.OFFSET_E_ENTRY); buffer.putInt(entryPoint)
        buffer.position(Constants.OFFSET_E_PHOFF); buffer.putInt(phOff)
        buffer.position(Constants.OFFSET_E_SHOFF); buffer.putInt(shOff)
        buffer.position(Constants.OFFSET_E_EHSIZE); buffer.putShort(ehdrSize.toShort())
        buffer.position(Constants.OFFSET_E_PHENTSIZE); buffer.putShort(phdrSize.toShort())
        buffer.position(Constants.OFFSET_E_PHNUM); buffer.putShort(1)
        buffer.position(Constants.OFFSET_E_SHENTSIZE); buffer.putShort(shdrSize.toShort())
        buffer.position(Constants.OFFSET_E_SHNUM); buffer.putShort(shNum.toShort())
        buffer.position(Constants.OFFSET_E_SHSTRNDX); buffer.putShort(shstrtabIndex.toShort())

        // --- Program header (one PT_LOAD, R+X) ---
        buffer.position(phOff)
        buffer.putInt(Constants.PT_LOAD)
        buffer.putInt(codeOff)
        buffer.putInt(vaddr)
        buffer.putInt(vaddr) // paddr, unused
        buffer.putInt(code.size)
        buffer.putInt(code.size) // memSize == fileSize, no .bss for this test
        buffer.putInt(Constants.PF_READ or Constants.PF_EXEC)
        buffer.putInt(4) // align

        // --- Code, shstrtab, vm_res payload ---
        buffer.position(codeOff)
        buffer.put(code)
        buffer.position(shstrtabOff)
        buffer.put(shstrtabContent)
        if (hasVmRes) {
            buffer.position(vmResOff)
            buffer.put(vmResData!!)
        }

        // --- Section headers ---
        // [0] null section - all zero, already zero-filled by ByteBuffer.allocate
        if (hasVmRes) {
            buffer.position(shOff + shdrSize) // [1] .vm_res
            buffer.putInt(1) // name offset within shstrtab (".vm_res" starts right after the leading \0)
            buffer.putInt(1) // sh_type = SHT_PROGBITS
            buffer.putInt(0); buffer.putInt(0)
            buffer.putInt(vmResOff)
            buffer.putInt(vmResSize)
            buffer.putInt(0); buffer.putInt(0); buffer.putInt(1); buffer.putInt(0)
        }
        // [shstrtabIndex] .shstrtab
        buffer.position(shOff + shstrtabIndex * shdrSize)
        buffer.putInt(0) // name unused in tests
        buffer.putInt(3) // sh_type = SHT_STRTAB
        buffer.putInt(0); buffer.putInt(0)
        buffer.putInt(shstrtabOff)
        buffer.putInt(shstrtabContent.size)
        buffer.putInt(0); buffer.putInt(0); buffer.putInt(1); buffer.putInt(0)

        // --- Appended tags ---
        buffer.position(elfEnd)
        for (b in tagsBytes) buffer.put(b)

        return buffer.array()
    }

    @Test
    fun `valid ELF parses successfully with matching entry point and segment`() {
        val code = byteArrayOf(1, 2, 3, 4, 5)
        val bytes = buildFakeElf(code = code, entryPoint = 0x8000, vaddr = 0x8000)

        val result = VxpParser.parse("test.vxp", bytes)

        assertTrue(result is ParseResult.Success)
        result as ParseResult.Success
        assertEquals(0x8000L, result.file.header.entryPoint)
        assertEquals(1, result.file.programHeaders.count { it.isLoadable })
        val segment = result.file.programHeaders.first { it.isLoadable }
        assertEquals(0x8000L, segment.vaddr)
        assertEquals(code.size.toLong(), segment.fileSize)
    }

    @Test
    fun `vm_res section is located and extracted`() {
        val vmRes = byteArrayOf(0x11, 0x22, 0x33)
        val bytes = buildFakeElf(vmResData = vmRes)

        val result = VxpParser.parse("test.vxp", bytes) as ParseResult.Success

        assertNotNull(result.file.resourceSectionData)
        assertTrue(vmRes.contentEquals(result.file.resourceSectionData))
    }

    @Test
    fun `appended tags are read back correctly`() {
        val bytes = buildFakeElf(tags = listOf(1 to byteArrayOf(0x41, 0x42), 2 to byteArrayOf(0x99.toByte())))

        val result = VxpParser.parse("test.vxp", bytes) as ParseResult.Success

        assertEquals(2, result.file.tags.size)
        assertEquals(1, result.file.tags[0].id)
        assertTrue(byteArrayOf(0x41, 0x42).contentEquals(result.file.tags[0].data))
        assertEquals(2, result.file.tags[1].id)
    }

    @Test
    fun `bad magic bytes are rejected`() {
        val bytes = buildFakeElf()
        bytes[0] = 0x00 // corrupt the ELF magic

        val result = VxpParser.parse("bad.vxp", bytes)
        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun `wrong machine type is rejected`() {
        val bytes = buildFakeElf()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(Constants.OFFSET_E_MACHINE)
        buffer.putShort(3) // EM_386, not ARM

        val result = VxpParser.parse("wrong-arch.vxp", bytes)
        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun `truncated file is rejected before header read`() {
        val bytes = ByteArray(4) // way smaller than a full ELF header
        val result = VxpParser.parse("tiny.vxp", bytes)
        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun `segment overrunning file size is rejected`() {
        val bytes = buildFakeElf()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val phOff = Constants.ELF_HEADER_SIZE
        buffer.position(phOff + 16) // p_filesz field offset within Elf32_Phdr
        buffer.putInt(bytes.size + 1000) // claim a huge file size, past EOF

        val result = VxpParser.parse("overrun.vxp", bytes)
        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun `thumb entry bit is detected and masked off`() {
        val bytes = buildFakeElf(entryPoint = 0x8001, vaddr = 0x8000) // low bit set = Thumb

        val result = VxpParser.parse("thumb.vxp", bytes) as ParseResult.Success

        assertTrue(result.file.header.isThumbEntry)
        assertEquals(0x8000L, result.file.header.realEntryAddress)
    }

    @Test
    fun `module mapper produces segment plus heap and stack regions with no overlap`() {
        val bytes = buildFakeElf(entryPoint = 0x8000, vaddr = 0x8000)
        val parsed = VxpParser.parse("map.vxp", bytes) as ParseResult.Success

        val layout = ModuleMapper.map(parsed.file)

        assertEquals(1, layout.segmentRegions.size)
        assertTrue(layout.segmentRegions[0].executable)
        assertTrue(!layout.segmentRegions[0].writable)

        // Heap must start at or after the segment's end (no overlap).
        val segmentEnd = layout.segmentRegions[0].baseAddress + layout.segmentRegions[0].size
        assertTrue(layout.heapRegion.baseAddress >= segmentEnd)
        // Stack must start at or after the heap's end.
        val heapEnd = layout.heapRegion.baseAddress + layout.heapRegion.size
        assertTrue(layout.stackRegion.baseAddress >= heapEnd)

        assertEquals(0x8000L, layout.entryPoint)
    }

    @Test
    fun `missing vm_res section results in null resourceSectionData`() {
        val bytes = buildFakeElf(vmResData = null)
        val result = VxpParser.parse("no-res.vxp", bytes) as ParseResult.Success
        assertNull(result.file.resourceSectionData)
    }
}
