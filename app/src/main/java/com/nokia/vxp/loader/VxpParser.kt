package com.nokia.vxp.loader

import com.nokia.vxp.utils.Constants
import com.nokia.vxp.utils.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "VxpParser"
private const val TAG_HEADER_SIZE = 8 // id:4, size:4

sealed class ParseResult {
    data class Success(val file: VxpFile) : ParseResult()
    data class Failure(val reason: String) : ParseResult()
}

/**
 * Parses already-unwrapped ELF bytes (VxpLoader handles zlib/ZIP
 * unwrapping before calling this) into a VxpFile: ELF header, program
 * headers, section headers, symbols (from BOTH .symtab and .dynsym -
 * some real VXP files strip the former but keep the latter, since real
 * dynamic linking needs it), relocations (from .rel.dyn/.rela.dyn - see
 * loader.ElfRelocation for why this matters), the .vm_res section if
 * present, and any MediaTek metadata tags appended after the ELF data.
 */
object VxpParser {

    fun parse(sourceName: String, bytes: ByteArray): ParseResult {
        val rawCheck = VxpValidator.validateRaw(bytes)
        if (rawCheck is ValidationResult.Failed) {
            return ParseResult.Failure(rawCheck.reason)
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val header = try {
            readHeader(buffer)
        } catch (e: Exception) {
            Logger.e(TAG, "Exception while reading ELF header", e)
            return ParseResult.Failure("Malformed ELF header: ${e.message}")
        }

        val headerCheck = VxpValidator.validateHeader(header, bytes.size.toLong())
        if (headerCheck is ValidationResult.Failed) {
            return ParseResult.Failure(headerCheck.reason)
        }

        val programHeaders = try {
            readProgramHeaders(buffer, header, bytes.size.toLong())
        } catch (e: Exception) {
            Logger.e(TAG, "Exception while reading program headers", e)
            return ParseResult.Failure("Malformed program headers: ${e.message}")
        }
        for ((index, ph) in programHeaders.withIndex()) {
            val check = VxpValidator.validateProgramHeader(ph, index, bytes.size.toLong())
            if (check is ValidationResult.Failed) return ParseResult.Failure(check.reason)
        }

        val sectionHeaders = try {
            readSectionHeaders(buffer, bytes, header)
        } catch (e: Exception) {
            Logger.w(TAG, "Exception while reading section headers (continuing without them): ${e.message}")
            emptyList()
        }

        val symbols = try {
            readSymbols(buffer, bytes, sectionHeaders)
        } catch (e: Exception) {
            Logger.w(TAG, "Exception while reading symbol table (continuing without it): ${e.message}")
            emptyList()
        }

        val relocations = try {
            readRelocations(buffer, bytes, sectionHeaders, programHeaders)
        } catch (e: Exception) {
            Logger.w(TAG, "Exception while reading relocations (continuing without them): ${e.message}")
            emptyList()
        }

        val resourceSectionData = sectionHeaders
            .firstOrNull { it.name == Constants.VM_RES_SECTION_NAME }
            ?.let { section ->
                if (section.offset + section.size <= bytes.size) {
                    bytes.copyOfRange(section.offset.toInt(), (section.offset + section.size).toInt())
                } else {
                    Logger.w(TAG, "${Constants.VM_RES_SECTION_NAME} section extends past EOF - skipping")
                    null
                }
            }

        val elfEndOffset = computeElfEndOffset(header, programHeaders, sectionHeaders, bytes.size.toLong())
        val tags = readTags(bytes, elfEndOffset)

        Logger.i(
            TAG,
            "Parsed VXP OK: entry=0x${header.entryPoint.toString(16)} " +
                "segments=${programHeaders.count { it.isLoadable }} " +
                "vm_res=${resourceSectionData?.size ?: 0}B tags=${tags.size} " +
                "symbols=${symbols.size} relocations=${relocations.size}"
        )

        return ParseResult.Success(
            VxpFile(
                sourceName = sourceName,
                header = header,
                programHeaders = programHeaders,
                sectionHeaders = sectionHeaders,
                symbols = symbols,
                relocations = relocations,
                elfBytes = bytes,
                resourceSectionData = resourceSectionData,
                tags = tags,
                rawSize = bytes.size.toLong()
            )
        )
    }

    private fun readHeader(buffer: ByteBuffer): VxpHeader {
        buffer.position(Constants.OFFSET_E_TYPE)
        val elfType = buffer.short.toInt() and 0xFFFF

        buffer.position(Constants.OFFSET_E_MACHINE)
        val machine = buffer.short.toInt() and 0xFFFF

        buffer.position(Constants.OFFSET_E_VERSION)
        val version = buffer.int.toLong() and 0xFFFFFFFFL

        buffer.position(Constants.OFFSET_E_ENTRY)
        val entry = buffer.int.toLong() and 0xFFFFFFFFL

        buffer.position(Constants.OFFSET_E_PHOFF)
        val phOff = buffer.int.toLong() and 0xFFFFFFFFL

        buffer.position(Constants.OFFSET_E_SHOFF)
        val shOff = buffer.int.toLong() and 0xFFFFFFFFL

        buffer.position(Constants.OFFSET_E_PHENTSIZE)
        val phEntSize = buffer.short.toInt() and 0xFFFF

        buffer.position(Constants.OFFSET_E_PHNUM)
        val phNum = buffer.short.toInt() and 0xFFFF

        buffer.position(Constants.OFFSET_E_SHENTSIZE)
        val shEntSize = buffer.short.toInt() and 0xFFFF

        buffer.position(Constants.OFFSET_E_SHNUM)
        val shNum = buffer.short.toInt() and 0xFFFF

        buffer.position(Constants.OFFSET_E_SHSTRNDX)
        val shStrNdx = buffer.short.toInt() and 0xFFFF

        return VxpHeader(
            elfType = elfType,
            machine = machine,
            version = version,
            entryPoint = entry,
            programHeaderOffset = phOff,
            programHeaderEntrySize = phEntSize,
            programHeaderCount = phNum,
            sectionHeaderOffset = shOff,
            sectionHeaderEntrySize = shEntSize,
            sectionHeaderCount = shNum,
            sectionHeaderStringTableIndex = shStrNdx
        )
    }

    private fun readProgramHeaders(buffer: ByteBuffer, header: VxpHeader, totalSize: Long): List<ElfProgramHeader> {
        val result = ArrayList<ElfProgramHeader>(header.programHeaderCount)
        for (i in 0 until header.programHeaderCount) {
            val base = header.programHeaderOffset + i.toLong() * header.programHeaderEntrySize
            if (base + Constants.PROGRAM_HEADER_SIZE > totalSize) {
                throw IllegalStateException("Program header #$i at offset $base extends past EOF")
            }
            buffer.position(base.toInt())
            val type = buffer.int
            val offset = buffer.int.toLong() and 0xFFFFFFFFL
            val vaddr = buffer.int.toLong() and 0xFFFFFFFFL
            val paddr = buffer.int.toLong() and 0xFFFFFFFFL
            val fileSize = buffer.int.toLong() and 0xFFFFFFFFL
            val memSize = buffer.int.toLong() and 0xFFFFFFFFL
            val flags = buffer.int
            val align = buffer.int.toLong() and 0xFFFFFFFFL

            result += ElfProgramHeader(type, offset, vaddr, paddr, fileSize, memSize, flags, align)
        }
        return result
    }

    private fun readSectionHeaders(buffer: ByteBuffer, bytes: ByteArray, header: VxpHeader): List<ElfSectionHeader> {
        if (header.sectionHeaderCount == 0) return emptyList()

        data class RawSection(
            val nameOffset: Int, val type: Int, val flags: Long, val addr: Long,
            val offset: Long, val size: Long, val link: Int, val info: Int,
            val addrAlign: Long, val entSize: Long
        )

        val raw = ArrayList<RawSection>(header.sectionHeaderCount)
        for (i in 0 until header.sectionHeaderCount) {
            val base = header.sectionHeaderOffset + i.toLong() * header.sectionHeaderEntrySize
            buffer.position(base.toInt())
            val nameOffset = buffer.int
            val type = buffer.int
            val flags = buffer.int.toLong() and 0xFFFFFFFFL
            val addr = buffer.int.toLong() and 0xFFFFFFFFL
            val offset = buffer.int.toLong() and 0xFFFFFFFFL
            val size = buffer.int.toLong() and 0xFFFFFFFFL
            val link = buffer.int
            val info = buffer.int
            val addrAlign = buffer.int.toLong() and 0xFFFFFFFFL
            val entSize = buffer.int.toLong() and 0xFFFFFFFFL
            raw += RawSection(nameOffset, type, flags, addr, offset, size, link, info, addrAlign, entSize)
        }

        // Resolve names via the section header string table (.shstrtab),
        // itself just another section pointed to by e_shstrndx.
        val shstrtab = raw.getOrNull(header.sectionHeaderStringTableIndex)
        return raw.map { section ->
            val name = shstrtab?.let { readNullTerminatedString(bytes, (it.offset + section.nameOffset).toInt()) } ?: ""
            ElfSectionHeader(
                name = name,
                type = section.type,
                flags = section.flags,
                addr = section.addr,
                offset = section.offset,
                size = section.size,
                link = section.link,
                info = section.info,
                addrAlign = section.addrAlign,
                entSize = section.entSize
            )
        }
    }

    /**
     * Parses symbols from BOTH .symtab and .dynsym (if present) into
     * ElfSymbol entries, resolving each name via the matching string
     * table (found through sh_link, per the ELF spec - not a fixed
     * section name/index).
     *
     * IMPORTANT: checking only .symtab was a real bug - a real user
     * session's logcat showed a file with symbols=0 (nothing in
     * .symtab) that then faulted writing to guest address 0x0 almost
     * immediately after entry (classic "dereferencing an unresolved
     * pointer" signature). .dynsym (the *dynamic* symbol table) is
     * commonly kept even when .symtab is stripped, since real dynamic
     * linking needs it - checking only .symtab meant missing symbols
     * (and therefore missing mre.VmSymbolBinder jump-table patches, and
     * missing relocation symbol resolution) on exactly the files most
     * likely to need them. Fully stripped files (neither table present)
     * still just yield an empty list - not an error.
     */
    private fun readSymbols(buffer: ByteBuffer, bytes: ByteArray, sectionHeaders: List<ElfSectionHeader>): List<ElfSymbol> {
        return readSymbolTable(buffer, bytes, sectionHeaders, ".symtab") +
            readSymbolTable(buffer, bytes, sectionHeaders, ".dynsym")
    }

    private fun readSymbolTable(buffer: ByteBuffer, bytes: ByteArray, sectionHeaders: List<ElfSectionHeader>, tableName: String): List<ElfSymbol> {
        val symtab = sectionHeaders.firstOrNull { it.name == tableName } ?: return emptyList()
        val strtabIndex = symtab.link
        val strtab = sectionHeaders.getOrNull(strtabIndex) ?: return emptyList()

        val entrySize = if (symtab.entSize > 0) symtab.entSize.toInt() else ELF_SYM_SIZE
        val count = (symtab.size / entrySize).toInt()
        val symbols = ArrayList<ElfSymbol>(count)

        for (i in 0 until count) {
            val base = symtab.offset + i.toLong() * entrySize
            if (base + ELF_SYM_SIZE > bytes.size) break

            buffer.position(base.toInt())
            val nameOffset = buffer.int
            val value = buffer.int.toLong() and 0xFFFFFFFFL
            val size = buffer.int.toLong() and 0xFFFFFFFFL
            val info = buffer.get().toInt() and 0xFF
            buffer.get() // st_other, unused
            val sectionIndex = buffer.short.toInt() and 0xFFFF

            val name = if (nameOffset == 0) "" else readNullTerminatedString(bytes, (strtab.offset + nameOffset).toInt())
            symbols += ElfSymbol(name, value, size, info, sectionIndex)
        }

        return symbols
    }

    private const val ELF_SYM_SIZE = 16 // Elf32_Sym: name(4) value(4) size(4) info(1) other(1) shndx(2)

    // Standard ELF sh_type values (not VXP-specific).
    private const val SHT_REL = 9
    private const val SHT_RELA = 4

    /**
     * Parses .rel.dyn/.rel.plt-style sections (SHT_REL, implicit addend
     * read from the existing bytes at the target vaddr) and
     * .rela.dyn/.rela.plt-style sections (SHT_RELA, explicit addend
     * field) into normalized ElfRelocation entries. See
     * loader.ElfRelocation's doc comment for why this exists - some real
     * VXP files need actual relocation processing, not just the "load
     * bias is always zero so it's a no-op" case that happened to hold
     * for the one sample this was first validated against.
     */
    private fun readRelocations(
        buffer: ByteBuffer,
        bytes: ByteArray,
        sectionHeaders: List<ElfSectionHeader>,
        programHeaders: List<ElfProgramHeader>
    ): List<ElfRelocation> {
        val result = mutableListOf<ElfRelocation>()
        for (section in sectionHeaders) {
            when (section.type) {
                SHT_REL -> result += readRelEntries(buffer, bytes, section, programHeaders)
                SHT_RELA -> result += readRelaEntries(buffer, bytes, section)
            }
        }
        return result
    }

    /** Finds which PT_LOAD segment contains [vaddr] and returns the corresponding file offset, or null if none does. */
    private fun vaddrToFileOffset(programHeaders: List<ElfProgramHeader>, vaddr: Long): Long? {
        val ph = programHeaders.firstOrNull { it.isLoadable && vaddr >= it.vaddr && vaddr < it.vaddr + it.fileSize } ?: return null
        return ph.offset + (vaddr - ph.vaddr)
    }

    private fun readRelEntries(
        buffer: ByteBuffer,
        bytes: ByteArray,
        section: ElfSectionHeader,
        programHeaders: List<ElfProgramHeader>
    ): List<ElfRelocation> {
        val entrySize = if (section.entSize > 0) section.entSize.toInt() else 8 // Elf32_Rel: offset(4) + info(4)
        val count = (section.size / entrySize).toInt()
        val result = ArrayList<ElfRelocation>(count)

        for (i in 0 until count) {
            val base = section.offset + i.toLong() * entrySize
            if (base + 8 > bytes.size) break

            buffer.position(base.toInt())
            val offset = buffer.int.toLong() and 0xFFFFFFFFL
            val info = buffer.int
            val symIndex = (info ushr 8) and 0xFFFFFF
            val type = info and 0xFF

            // Elf32_Rel has no explicit addend field - it's implicit,
            // read from whatever's already stored at the target vaddr.
            val addend = vaddrToFileOffset(programHeaders, offset)?.let { fileOff ->
                if (fileOff + 4 <= bytes.size) {
                    ByteBuffer.wrap(bytes, fileOff.toInt(), 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                } else null
            } ?: 0L

            result += ElfRelocation(offset, type, symIndex, addend)
        }
        return result
    }

    private fun readRelaEntries(buffer: ByteBuffer, bytes: ByteArray, section: ElfSectionHeader): List<ElfRelocation> {
        val entrySize = if (section.entSize > 0) section.entSize.toInt() else 12 // Elf32_Rela: offset(4) + info(4) + addend(4)
        val count = (section.size / entrySize).toInt()
        val result = ArrayList<ElfRelocation>(count)

        for (i in 0 until count) {
            val base = section.offset + i.toLong() * entrySize
            if (base + 12 > bytes.size) break

            buffer.position(base.toInt())
            val offset = buffer.int.toLong() and 0xFFFFFFFFL
            val info = buffer.int
            val symIndex = (info ushr 8) and 0xFFFFFF
            val type = info and 0xFF
            val addend = buffer.int.toLong() and 0xFFFFFFFFL

            result += ElfRelocation(offset, type, symIndex, addend)
        }
        return result
    }

    private fun readNullTerminatedString(bytes: ByteArray, startOffset: Int): String {
        if (startOffset < 0 || startOffset >= bytes.size) return ""
        var end = startOffset
        while (end < bytes.size && bytes[end] != 0.toByte()) end++
        return String(bytes, startOffset, end - startOffset, Charsets.US_ASCII)
    }

    /** Where the ELF's own data (header + program/section header tables + section contents) ends, so tag-parsing knows where to start looking. */
    private fun computeElfEndOffset(
        header: VxpHeader,
        programHeaders: List<ElfProgramHeader>,
        sectionHeaders: List<ElfSectionHeader>,
        totalSize: Long
    ): Long {
        var end = Constants.ELF_HEADER_SIZE.toLong()
        end = maxOf(end, header.programHeaderOffset + header.programHeaderCount.toLong() * header.programHeaderEntrySize)
        end = maxOf(end, header.sectionHeaderOffset + header.sectionHeaderCount.toLong() * header.sectionHeaderEntrySize)
        for (ph in programHeaders) end = maxOf(end, ph.offset + ph.fileSize)
        for (sh in sectionHeaders) end = maxOf(end, sh.offset + sh.size)
        return end.coerceAtMost(totalSize)
    }

    /** Best-effort: reads id+size+data tags until EOF or a tag that doesn't fit; never throws, since tags are optional metadata. */
    private fun readTags(bytes: ByteArray, startOffset: Long): List<VxpTag> {
        val tags = mutableListOf<VxpTag>()
        var pos = startOffset

        while (pos + TAG_HEADER_SIZE <= bytes.size) {
            val buffer = ByteBuffer.wrap(bytes, pos.toInt(), TAG_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            val id = buffer.int
            val size = buffer.int.toLong() and 0xFFFFFFFFL

            val dataStart = pos + TAG_HEADER_SIZE
            if (size < 0 || dataStart + size > bytes.size) {
                Logger.w(TAG, "Stopping tag scan at offset $pos - tag claims size=$size, doesn't fit remaining ${bytes.size - dataStart} bytes")
                break
            }

            val data = bytes.copyOfRange(dataStart.toInt(), (dataStart + size).toInt())
            tags += VxpTag(id, data)
            pos = dataStart + size
        }

        return tags
    }
}
