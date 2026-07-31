package com.nokia.vxp.loader

import com.nokia.vxp.utils.Constants
import com.nokia.vxp.utils.Logger

private const val TAG = "VxpValidator"

sealed class ValidationResult {
    object Ok : ValidationResult()
    data class Failed(val reason: String) : ValidationResult()
}

/**
 * Structural validation of the (already-unwrapped, i.e. post zlib/ZIP
 * decompression) ELF bytes, split into two passes:
 *  - validateRaw(): magic/size checks before we bother parsing the header.
 *  - validateHeader(): checks the parsed header's internal consistency
 *    (program/section header tables actually fit inside the file).
 */
object VxpValidator {

    fun validateRaw(bytes: ByteArray): ValidationResult {
        if (bytes.size < Constants.ELF_HEADER_SIZE) {
            return ValidationResult.Failed(
                "File too small to contain an ELF header (${bytes.size} bytes, need at least ${Constants.ELF_HEADER_SIZE})"
            )
        }
        if (bytes.size.toLong() > Constants.VXP_MAX_REASONABLE_FILE_SIZE) {
            return ValidationResult.Failed(
                "File suspiciously large for a VXP module (${bytes.size} bytes) - refusing to load"
            )
        }

        val magic = bytes.copyOfRange(0, Constants.ELF_MAGIC.size)
        if (!magic.contentEquals(Constants.ELF_MAGIC)) {
            Logger.w(TAG, "ELF magic mismatch: expected ${Constants.ELF_MAGIC.toHex()}, got ${magic.toHex()}")
            return ValidationResult.Failed(
                "Not a recognized VXP/ELF file (magic bytes are ${magic.toHex()}, expected " +
                    "${Constants.ELF_MAGIC.toHex()} = ELF). If this file is zlib- or ZIP-wrapped, " +
                    "VxpLoader should have already unwrapped it before reaching here - check " +
                    "VxpLoader's container detection if you're seeing this for a real .vxp file."
            )
        }

        val elfClass = bytes[Constants.EI_CLASS_OFFSET].toInt()
        if (elfClass != Constants.ELF_CLASS_32) {
            return ValidationResult.Failed(
                "Unsupported ELF class $elfClass - only 32-bit ELF (class 1) is supported, " +
                    "VXP/MRE binaries should always be 32-bit ARM"
            )
        }

        val elfData = bytes[Constants.EI_DATA_OFFSET].toInt()
        if (elfData != Constants.ELF_DATA_2LSB) {
            return ValidationResult.Failed(
                "Unsupported ELF data encoding $elfData - only little-endian (1) is supported"
            )
        }

        return ValidationResult.Ok
    }

    fun validateHeader(header: VxpHeader, totalFileSize: Long): ValidationResult {
        if (header.machine != Constants.EM_ARM) {
            return ValidationResult.Failed(
                "ELF e_machine=${header.machine} is not ARM (expected ${Constants.EM_ARM}) - " +
                    "this doesn't look like a VXP/MRE binary"
            )
        }

        val phTableEnd = header.programHeaderOffset + header.programHeaderCount.toLong() * header.programHeaderEntrySize
        if (header.programHeaderCount > 0 && phTableEnd > totalFileSize) {
            return ValidationResult.Failed(
                "Program header table (offset=${header.programHeaderOffset}, " +
                    "count=${header.programHeaderCount}) extends past end of file ($totalFileSize bytes)"
            )
        }

        val shTableEnd = header.sectionHeaderOffset + header.sectionHeaderCount.toLong() * header.sectionHeaderEntrySize
        if (header.sectionHeaderCount > 0 && shTableEnd > totalFileSize) {
            return ValidationResult.Failed(
                "Section header table (offset=${header.sectionHeaderOffset}, " +
                    "count=${header.sectionHeaderCount}) extends past end of file ($totalFileSize bytes)"
            )
        }

        return ValidationResult.Ok
    }

    fun validateProgramHeader(ph: ElfProgramHeader, index: Int, totalFileSize: Long): ValidationResult {
        if (ph.fileSize < 0 || ph.memSize < 0) {
            return ValidationResult.Failed("Program header #$index has a negative size - corrupt file")
        }
        if (ph.memSize < ph.fileSize) {
            return ValidationResult.Failed(
                "Program header #$index has memSize (${ph.memSize}) < fileSize (${ph.fileSize}), which is invalid"
            )
        }
        if (ph.offset + ph.fileSize > totalFileSize) {
            return ValidationResult.Failed(
                "Program header #$index (offset=${ph.offset}, fileSize=${ph.fileSize}) " +
                    "extends past end of file ($totalFileSize bytes)"
            )
        }
        return ValidationResult.Ok
    }

    private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
}
