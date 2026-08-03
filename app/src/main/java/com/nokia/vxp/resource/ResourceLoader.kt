package com.nokia.vxp.resource

import com.nokia.vxp.utils.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "ResourceLoader"
private const val ENTRY_HEADER_SIZE = 8 // id:2, typeId:2, size:4 - see doc comment below

/**
 * Parses the raw .vm_res section (extracted by loader.VxpParser) into
 * individual Resource entries.
 *
 * FORMAT CAVEAT: the internal layout of .vm_res is NOT confirmed - same
 * situation as the VXP metadata-tag format (loader.VxpTag) and mre/'s
 * API addresses. This parser assumes the same simple tag-style pattern
 * (a small header + size + data, repeated) that MediaTek's *confirmed*
 * metadata-tag format uses, since that's the only concretely-documented
 * MediaTek MRE container convention available to extrapolate from - but
 * it hasn't been verified against a real .vm_res section's actual
 * bytes. If parsing a real file produces entries whose sniffed
 * ResourceType is UNKNOWN across the board, that's a sign this framing
 * guess is wrong and the real layout needs reverse-engineering from an
 * actual sample.
 */
object ResourceLoader {

    fun parse(vmResData: ByteArray?): List<Resource> {
        if (vmResData == null || vmResData.isEmpty()) return emptyList()

        val resources = mutableListOf<Resource>()
        var pos = 0

        while (pos + ENTRY_HEADER_SIZE <= vmResData.size) {
            val buffer = ByteBuffer.wrap(vmResData, pos, ENTRY_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            val id = buffer.short.toInt() and 0xFFFF
            val typeId = buffer.short.toInt() and 0xFFFF
            val size = buffer.int.toLong() and 0xFFFFFFFFL

            val dataStart = pos + ENTRY_HEADER_SIZE
            if (size < 0 || dataStart + size > vmResData.size) {
                Logger.w(
                    TAG,
                    "Stopping resource scan at offset $pos - entry claims size=$size, " +
                        "doesn't fit remaining ${vmResData.size - dataStart} bytes"
                )
                break
            }

            val data = vmResData.copyOfRange(dataStart, (dataStart + size).toInt())
            val detectedType = ResourceType.sniff(data)
            resources += Resource(id, typeId, data, detectedType)

            pos = (dataStart + size).toInt()
        }

        Logger.i(
            TAG,
            "Parsed ${resources.size} resource entries " +
                "(${resources.count { it.detectedType != ResourceType.UNKNOWN }} recognized by content sniffing)"
        )
        return resources
    }
}
