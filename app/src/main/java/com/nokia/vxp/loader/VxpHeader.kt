package com.nokia.vxp.loader

/**
 * Parsed VXP file header. Field layout is defined in utils.Constants and
 * is best-effort pending verification against real sample files.
 */
data class VxpHeader(
    val magic: ByteArray,
    val versionMajor: Int,
    val versionMinor: Int,
    val flags: Int,
    val codeOffset: Long,
    val codeSize: Long,
    val dataOffset: Long,
    val dataSize: Long,
    val resourceTableOffset: Long,
    val resourceCount: Int
) {
    val version: String get() = "$versionMajor.$versionMinor"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VxpHeader) return false
        return magic.contentEquals(other.magic) &&
            versionMajor == other.versionMajor &&
            versionMinor == other.versionMinor &&
            flags == other.flags &&
            codeOffset == other.codeOffset &&
            codeSize == other.codeSize &&
            dataOffset == other.dataOffset &&
            dataSize == other.dataSize &&
            resourceTableOffset == other.resourceTableOffset &&
            resourceCount == other.resourceCount
    }

    override fun hashCode(): Int {
        var result = magic.contentHashCode()
        result = 31 * result + versionMajor
        result = 31 * result + versionMinor
        result = 31 * result + flags
        result = 31 * result + codeOffset.hashCode()
        result = 31 * result + codeSize.hashCode()
        result = 31 * result + dataOffset.hashCode()
        result = 31 * result + dataSize.hashCode()
        result = 31 * result + resourceTableOffset.hashCode()
        result = 31 * result + resourceCount
        return result
    }
}
