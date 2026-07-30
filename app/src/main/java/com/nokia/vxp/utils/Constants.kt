package com.nokia.vxp.utils

/**
 * Shared constants. The VXP_* format constants are best-effort based on
 * community reverse-engineering of the Nokia MRE/VXP format - Nokia never
 * published the spec. VERIFY these against a real .vxp file in a hex
 * editor before relying on them; different VXP tool versions (VXP 1.x vs
 * 2.x SDKs) are known to shift header layout.
 */
object Constants {

    // --- VXP header -----------------------------------------------------
    // 4-byte magic expected at offset 0. Placeholder pending confirmation
    // against real sample files - some VXP builds prefix with a different
    // signature or an unencrypted "MAUI" / vendor tag before this.
    val VXP_MAGIC = byteArrayOf(0x56, 0x58, 0x50, 0x31) // "VXP1"

    const val VXP_HEADER_SIZE = 32 // bytes, TODO verify
    const val VXP_MAX_REASONABLE_FILE_SIZE = 8L * 1024 * 1024 // 8 MB sanity cap

    // Header field byte offsets (all little-endian), TODO verify against samples
    const val OFFSET_MAGIC = 0
    const val OFFSET_VERSION = 4
    const val OFFSET_FLAGS = 6
    const val OFFSET_CODE_OFFSET = 8
    const val OFFSET_CODE_SIZE = 12
    const val OFFSET_DATA_OFFSET = 16
    const val OFFSET_DATA_SIZE = 20
    const val OFFSET_RESOURCE_TABLE_OFFSET = 24
    const val OFFSET_RESOURCE_COUNT = 28

    // --- Emulated memory map defaults (memory/ module will consume this) --
    const val DEFAULT_CODE_BASE = 0x00010000L
    const val DEFAULT_DATA_BASE = 0x00400000L
    const val DEFAULT_HEAP_BASE = 0x00800000L
    const val DEFAULT_HEAP_SIZE = 2L * 1024 * 1024
    const val DEFAULT_STACK_BASE = 0x00F00000L
    const val DEFAULT_STACK_SIZE = 256L * 1024
}
