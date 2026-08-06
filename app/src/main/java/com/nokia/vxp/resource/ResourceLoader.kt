package com.nokia.vxp.resource

import com.nokia.vxp.utils.Logger

private const val TAG = "ResourceLoader"

/**
 * Parses the raw .vm_res section (extracted by loader.VxpParser) into
 * individual Resource entries.
 *
 * CHECKED AGAINST A REAL SAMPLE: unlike most of this codebase's
 * unverified extrapolations, this one has actually been tested against
 * a real .vxp file (gtrxAC/peanut.vxp, MIT licensed, fetched from its
 * GitHub releases). Two things were confirmed:
 *
 *  1. .vm_res does NOT use the simple flat "id+size+data" tag format
 *     the top-level VXP metadata tags use (see loader.VxpTag, where
 *     that framing WAS confirmed correct) - .vm_res instead looks like
 *     a named-entry directory (filename strings like "AppLogo.img",
 *     "VRE"-prefixed markers, a "mre-2.0" format-version string) that
 *     hasn't been fully reverse-engineered here.
 *  2. The actual resource DATA embedded within it is exactly what
 *     ResourceType.sniff() expects - real PNG bytes were located,
 *     extracted by walking genuine PNG chunks through to a real IEND,
 *     and verified to decode as a correct 45x45 image.
 *
 * Given that, this scans the whole blob for recognized magic-byte
 * signatures at any offset ("carving") rather than trusting an
 * un-cracked directory format to tell us where each resource starts -
 * less structured than a real directory-based parse would be, but
 * demonstrably correct on real data. PNG end-of-stream is found
 * precisely by walking real chunks to IEND; other formats fall back to
 * "up to the next recognized signature, or end of blob" as an
 * approximation, since their exact end markers weren't verified here.
 */
object ResourceLoader {

    fun parse(vmResData: ByteArray?): List<Resource> {
        if (vmResData == null || vmResData.isEmpty()) return emptyList()

        val found = mutableListOf<Pair<Int, ByteArray>>() // (startOffset, bytes)
        var searchFrom = 0

        while (searchFrom < vmResData.size) {
            val (offset, type) = findNextSignature(vmResData, searchFrom) ?: break

            val preciseEnd = when (type) {
                ResourceType.IMAGE_PNG -> findPngEnd(vmResData, offset)
                ResourceType.IMAGE_JPEG -> findJpegEnd(vmResData, offset)
                else -> null
            }
            val end = preciseEnd ?: (findNextSignature(vmResData, offset + 4)?.first ?: vmResData.size)

            found += offset to vmResData.copyOfRange(offset, end)
            searchFrom = end
        }

        val resources = found.mapIndexed { index, (offset, bytes) ->
            // rawTypeId is repurposed here to record the byte offset the
            // resource was carved from, since carving (unlike a real
            // directory parse) has no actual type-id field to report.
            Resource(id = index, rawTypeId = offset, data = bytes, detectedType = ResourceType.sniff(bytes))
        }

        Logger.i(
            TAG,
            "Carved ${resources.size} resource(s) from .vm_res by content signature: " +
                resources.groupingBy { it.detectedType }.eachCount()
        )
        return resources
    }

    private fun findNextSignature(data: ByteArray, from: Int): Pair<Int, ResourceType>? {
        var i = from.coerceAtLeast(0)
        while (i < data.size) {
            val remaining = data.size - i
            if (remaining >= PNG_MAGIC.size && matchesAt(data, i, PNG_MAGIC)) return i to ResourceType.IMAGE_PNG
            if (remaining >= JPEG_MAGIC.size && matchesAt(data, i, JPEG_MAGIC)) return i to ResourceType.IMAGE_JPEG
            if (remaining >= MIDI_MAGIC.size && matchesAt(data, i, MIDI_MAGIC)) return i to ResourceType.AUDIO_MIDI
            if (remaining >= WAV_MAGIC.size && matchesAt(data, i, WAV_MAGIC)) return i to ResourceType.AUDIO_WAV
            i++
        }
        return null
    }

    private fun matchesAt(data: ByteArray, offset: Int, signature: ByteArray): Boolean {
        for (i in signature.indices) {
            if (data[offset + i] != signature[i]) return false
        }
        return true
    }

    /** Walks real PNG chunks (length + type + data + crc) from [start] until IEND. Returns the offset just past it, or null if malformed/truncated. */
    private fun findPngEnd(data: ByteArray, start: Int): Int? {
        var pos = start + PNG_MAGIC.size
        while (pos + 8 <= data.size) {
            val length = readBigEndianInt(data, pos)
            if (length < 0) return null
            val typeStart = pos + 4
            val chunkEnd = pos + 8 + length + 4 // length field(4) + type(4) + data(length) + crc(4)
            if (chunkEnd > data.size) return null
            if (matchesAt(data, typeStart, IEND_TYPE)) return chunkEnd
            pos = chunkEnd
        }
        return null
    }

    /** Scans for the JPEG End-Of-Image marker (0xFFD9) from [start]. Null if not found before end of blob. */
    private fun findJpegEnd(data: ByteArray, start: Int): Int? {
        var i = start + JPEG_MAGIC.size
        while (i + 1 < data.size) {
            if ((data[i].toInt() and 0xFF) == 0xFF && (data[i + 1].toInt() and 0xFF) == 0xD9) {
                return i + 2
            }
            i++
        }
        return null
    }

    private fun readBigEndianInt(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)

    private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
    private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val MIDI_MAGIC = "MThd".toByteArray(Charsets.US_ASCII)
    private val WAV_MAGIC = "RIFF".toByteArray(Charsets.US_ASCII)
    private val IEND_TYPE = "IEND".toByteArray(Charsets.US_ASCII)
}
