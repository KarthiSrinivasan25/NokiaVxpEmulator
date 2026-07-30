package com.nokia.vxp.loader

import com.nokia.vxp.utils.Constants
import com.nokia.vxp.utils.Logger

private const val TAG = "VxpValidator"

sealed class ValidationResult {
    object Ok : ValidationResult()
    data class Failed(val reason: String) : ValidationResult()
}

/**
 * Structural validation, split into two passes:
 *  - validateRaw(): cheap checks on the untouched byte buffer (size limits,
 *    magic bytes) before we bother parsing anything.
 *  - validateHeader(): checks the parsed header's internal consistency
 *    (segment offsets/sizes actually fit inside the file, counts are sane).
 */
object VxpValidator {

    fun validateRaw(bytes: ByteArray): ValidationResult {
        if (bytes.size < Constants.VXP_HEADER_SIZE) {
            return ValidationResult.Failed(
                "File too small to contain a VXP header (${bytes.size} bytes, need at least ${Constants.VXP_HEADER_SIZE})"
            )
        }
        if (bytes.size.toLong() > Constants.VXP_MAX_REASONABLE_FILE_SIZE) {
            return ValidationResult.Failed(
                "File suspiciously large for a VXP module (${bytes.size} bytes) - refusing to load"
            )
        }

        val magic = bytes.copyOfRange(
            Constants.OFFSET_MAGIC,
            Constants.OFFSET_MAGIC + Constants.VXP_MAGIC.size
        )
        if (!magic.contentEquals(Constants.VXP_MAGIC)) {
            // Dump the first chunk of the real file so the actual header layout
            // is visible right in the rejection message, not just logcat. This is
            // the fastest way to reverse-engineer the true magic/offsets from a
            // real sample instead of guessing again.
            val dumpLen = minOf(bytes.size, 64)
            val hexDump = bytes.copyOfRange(0, dumpLen).toHex()
            val asciiDump = bytes.copyOfRange(0, dumpLen).toAscii()

            Logger.w(TAG, "Magic mismatch: expected ${Constants.VXP_MAGIC.toHex()}, got ${magic.toHex()}")
            Logger.w(TAG, "First $dumpLen bytes: $hexDump")

            return ValidationResult.Failed(
                "Not a recognized VXP file (magic bytes don't match).\n" +
                    "Expected: ${Constants.VXP_MAGIC.toHex()} (\"VXP1\")\n" +
                    "Got:      ${magic.toHex()}\n\n" +
                    "First $dumpLen bytes of this file:\n$hexDump\n$asciiDump\n\n" +
                    "Constants.VXP_MAGIC / the header offsets in Constants.kt are a " +
                    "placeholder guess and likely need updating to match this file's " +
                    "real format - update VXP_MAGIC (and re-check OFFSET_* if the " +
                    "layout differs) once you know the real values from the dump above."
            )
        }

        return ValidationResult.Ok
    }

    fun validateHeader(header: VxpHeader, totalFileSize: Long): ValidationResult {
        if (header.codeSize < 0 || header.dataSize < 0) {
            return ValidationResult.Failed("Negative segment size in header - corrupt or misparsed file")
        }
        if (header.codeOffset + header.codeSize > totalFileSize) {
            return ValidationResult.Failed(
                "Code segment (offset=${header.codeOffset}, size=${header.codeSize}) " +
                    "extends past end of file ($totalFileSize bytes)"
            )
        }
        if (header.dataOffset + header.dataSize > totalFileSize) {
            return ValidationResult.Failed(
                "Data segment (offset=${header.dataOffset}, size=${header.dataSize}) " +
                    "extends past end of file ($totalFileSize bytes)"
            )
        }
        if (header.resourceCount < 0 || header.resourceCount > 10_000) {
            return ValidationResult.Failed(
                "Implausible resource count in header: ${header.resourceCount}"
            )
        }
        if (header.resourceTableOffset > totalFileSize) {
            return ValidationResult.Failed(
                "Resource table offset (${header.resourceTableOffset}) is past end of file"
            )
        }
        return ValidationResult.Ok
    }

    private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }

    private fun ByteArray.toAscii(): String = joinToString("") { b ->
        val c = b.toInt().toChar()
        if (c.code in 32..126) c.toString() else "."
    }
}
