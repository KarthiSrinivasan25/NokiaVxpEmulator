package com.nokia.vxp.loader

import com.nokia.vxp.utils.Constants
import com.nokia.vxp.utils.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "VxpParser"

/** Resource table entry byte layout, TODO verify against real samples. */
private const val RESOURCE_ENTRY_SIZE = 16 // id:4, typeId:4, offset:4, size:4

sealed class ParseResult {
    data class Success(val file: VxpFile) : ParseResult()
    data class Failure(val reason: String) : ParseResult()
}

/**
 * Pure byte-buffer parser: turns raw file bytes into a VxpFile. Doesn't
 * touch storage/IO itself (VxpLoader does that) so this class stays easy
 * to unit test with byte arrays built by hand or captured from samples.
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
            Logger.e(TAG, "Exception while reading header", e)
            return ParseResult.Failure("Malformed header: ${e.message}")
        }

        val headerCheck = VxpValidator.validateHeader(header, bytes.size.toLong())
        if (headerCheck is ValidationResult.Failed) {
            return ParseResult.Failure(headerCheck.reason)
        }

        val code = bytes.copyOfRange(
            header.codeOffset.toInt(),
            (header.codeOffset + header.codeSize).toInt()
        )
        val data = bytes.copyOfRange(
            header.dataOffset.toInt(),
            (header.dataOffset + header.dataSize).toInt()
        )

        val resources = try {
            readResourceTable(buffer, header, bytes.size)
        } catch (e: Exception) {
            Logger.e(TAG, "Exception while reading resource table", e)
            return ParseResult.Failure("Malformed resource table: ${e.message}")
        }

        Logger.i(
            TAG,
            "Parsed VXP OK: version=${header.version} code=${code.size}B data=${data.size}B resources=${resources.size}"
        )

        return ParseResult.Success(
            VxpFile(
                sourceName = sourceName,
                header = header,
                code = code,
                data = data,
                resources = resources,
                rawSize = bytes.size.toLong()
            )
        )
    }

    private fun readHeader(buffer: ByteBuffer): VxpHeader {
        val magic = ByteArray(Constants.VXP_MAGIC.size)
        buffer.position(Constants.OFFSET_MAGIC)
        buffer.get(magic)

        buffer.position(Constants.OFFSET_VERSION)
        val versionRaw = buffer.short.toInt() and 0xFFFF
        val versionMajor = (versionRaw shr 8) and 0xFF
        val versionMinor = versionRaw and 0xFF

        buffer.position(Constants.OFFSET_FLAGS)
        val flags = buffer.short.toInt() and 0xFFFF

        buffer.position(Constants.OFFSET_CODE_OFFSET)
        val codeOffset = buffer.int.toLong() and 0xFFFFFFFFL

        buffer.position(Constants.OFFSET_CODE_SIZE)
        val codeSize = buffer.int.toLong() and 0xFFFFFFFFL

        buffer.position(Constants.OFFSET_DATA_OFFSET)
        val dataOffset = buffer.int.toLong() and 0xFFFFFFFFL

        buffer.position(Constants.OFFSET_DATA_SIZE)
        val dataSize = buffer.int.toLong() and 0xFFFFFFFFL

        buffer.position(Constants.OFFSET_RESOURCE_TABLE_OFFSET)
        val resourceTableOffset = buffer.int.toLong() and 0xFFFFFFFFL

        buffer.position(Constants.OFFSET_RESOURCE_COUNT)
        val resourceCount = buffer.int

        return VxpHeader(
            magic = magic,
            versionMajor = versionMajor,
            versionMinor = versionMinor,
            flags = flags,
            codeOffset = codeOffset,
            codeSize = codeSize,
            dataOffset = dataOffset,
            dataSize = dataSize,
            resourceTableOffset = resourceTableOffset,
            resourceCount = resourceCount
        )
    }

    private fun readResourceTable(
        buffer: ByteBuffer,
        header: VxpHeader,
        totalFileSize: Int
    ): List<VxpResourceEntry> {
        if (header.resourceCount == 0) return emptyList()

        val entries = ArrayList<VxpResourceEntry>(header.resourceCount)
        var pos = header.resourceTableOffset.toInt()

        repeat(header.resourceCount) { index ->
            val entryEnd = pos + RESOURCE_ENTRY_SIZE
            if (entryEnd > totalFileSize) {
                throw IllegalStateException(
                    "Resource entry #$index would read past end of file (offset=$pos)"
                )
            }
            buffer.position(pos)
            val id = buffer.int
            val typeId = buffer.int
            val offset = buffer.int.toLong() and 0xFFFFFFFFL
            val size = buffer.int.toLong() and 0xFFFFFFFFL

            entries += VxpResourceEntry(id, typeId, offset, size)
            pos = entryEnd
        }

        return entries
    }
}
