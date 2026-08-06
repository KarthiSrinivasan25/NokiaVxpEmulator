package com.nokia.vxp.loader

/**
 * One MediaTek MRE metadata tag: identifier(4 bytes) + size(4 bytes) +
 * raw data, appended after the ELF payload (per
 * lpcwiki.miraheze.org/wiki/MediaTek_MRE/Tag_format).
 *
 * PARTIALLY CONFIRMED AGAINST A REAL SAMPLE: parsing gtrxAC/peanut.vxp
 * (MIT licensed) with this exact id+size+data framing produced entries
 * with small, plausible-looking integer values and one tag whose 8-byte
 * payload decoded cleanly as "App\0" in UTF-16LE - strong positive
 * evidence this framing is right, even though the specific meaning of
 * each numeric tag id (app name, version, required RAM, signing info,
 * etc.) still isn't decoded here. Contrast with loader.ResourceLoader's
 * .vm_res parsing, where this same flat-tag framing was tried and
 * disproven against real data - the two use different formats.
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
