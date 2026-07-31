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
            Logger.w(TAG, "Magic mismatch: expected ${Constants.VXP_MAGIC.toHex()}, got ${magic.toHex()}")
            return ValidationResult.Failed(
                "Not a recognized VXP file (magic bytes don't match). " +
                    "If you're sure this is a valid .vxp, the magic constant in " +
                    "Constants.kt likely needs updating for this VXP format version."
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
}
