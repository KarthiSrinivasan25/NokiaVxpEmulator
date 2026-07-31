package com.nokia.vxp.loader

/**
 * One MediaTek MRE metadata tag: identifier(4 bytes) + size(4 bytes) +
 * raw data, appended after the ELF payload (per
 * lpcwiki.miraheze.org/wiki/MediaTek_MRE/Tag_format). The specific
 * meaning of each tag identifier (app name, version, required RAM,
 * signing info, etc.) isn't decoded here yet - this just captures the
 * raw structure so it's available once that mapping is needed (e.g. to
 * show the app's real name instead of just its file name).
 */
data class VxpTag(
    val id: Int,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VxpTag) return false
        return id == other.id && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + data.contentHashCode()
        return result
    }
}
