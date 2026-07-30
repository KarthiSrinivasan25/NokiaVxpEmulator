package com.nokia.vxp.loader

/**
 * One resource table entry, pointing into the raw file's resource blob.
 * Kept generic (raw type id + offset/size) here; resource/ResourceType.kt
 * and resource/ResourceLoader.kt will interpret the payload later.
 */
data class VxpResourceEntry(
    val id: Int,
    val typeId: Int,
    val offset: Long,
    val size: Long
)

/**
 * Fully parsed, in-memory representation of a loaded .vxp module: the
 * header, the raw code/data segments as byte arrays, and the resource
 * table entries. This is what gets handed to memory/ModuleMapper next.
 */
data class VxpFile(
    val sourceName: String,
    val header: VxpHeader,
    val code: ByteArray,
    val data: ByteArray,
    val resources: List<VxpResourceEntry>,
    val rawSize: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VxpFile) return false
        return sourceName == other.sourceName &&
            header == other.header &&
            code.contentEquals(other.code) &&
            data.contentEquals(other.data) &&
            resources == other.resources &&
            rawSize == other.rawSize
    }

    override fun hashCode(): Int {
        var result = sourceName.hashCode()
        result = 31 * result + header.hashCode()
        result = 31 * result + code.contentHashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + resources.hashCode()
        result = 31 * result + rawSize.hashCode()
        return result
    }
}
