package com.nokia.vxp.loader

/**
 * Fully parsed, in-memory representation of a loaded .vxp module: the
 * ELF header, its program headers (the actual loadable segments),
 * section headers (used to locate .vm_res), the symbol table (used by
 * mre.VmSymbolBinder to find and patch the guest's OS-API jump table),
 * the raw bytes of the .vm_res resource section if present, and any
 * MediaTek metadata tags appended after the ELF data. This is what gets
 * handed to loader.ModuleMapper next.
 */
data class VxpFile(
    val sourceName: String,
    val header: VxpHeader,
    val programHeaders: List<ElfProgramHeader>,
    val sectionHeaders: List<ElfSectionHeader>,
    val symbols: List<ElfSymbol>,
    val elfBytes: ByteArray,
    val resourceSectionData: ByteArray?,
    val tags: List<VxpTag>,
    val rawSize: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VxpFile) return false
        return sourceName == other.sourceName &&
            header == other.header &&
            programHeaders == other.programHeaders &&
            sectionHeaders == other.sectionHeaders &&
            symbols == other.symbols &&
            elfBytes.contentEquals(other.elfBytes) &&
            (resourceSectionData?.contentEquals(other.resourceSectionData ?: ByteArray(0)) ?: (other.resourceSectionData == null)) &&
            tags == other.tags &&
            rawSize == other.rawSize
    }

    override fun hashCode(): Int {
        var result = sourceName.hashCode()
        result = 31 * result + header.hashCode()
        result = 31 * result + programHeaders.hashCode()
        result = 31 * result + sectionHeaders.hashCode()
        result = 31 * result + symbols.hashCode()
        result = 31 * result + elfBytes.contentHashCode()
        result = 31 * result + (resourceSectionData?.contentHashCode() ?: 0)
        result = 31 * result + tags.hashCode()
        result = 31 * result + rawSize.hashCode()
        return result
    }
}
