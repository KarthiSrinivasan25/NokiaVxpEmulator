package com.nokia.vxp.mre

import com.nokia.vxp.memory.GuestMemoryReader

private const val MAX_GUEST_STRING_LENGTH = 4096
private const val READ_CHUNK_SIZE = 64

/**
 * Reads a null-terminated ASCII string from guest memory starting at
 * [address]. Returns "" for a null (0) pointer. Reads in chunks rather
 * than byte-by-byte to avoid a JNI round-trip per character; if a
 * string happens to end exactly at the edge of a mapped region such
 * that the next chunk read fails, the string is truncated there rather
 * than throwing - acceptable for the log/debug-string use cases this
 * exists for today.
 */
fun readGuestCString(memory: GuestMemoryReader, address: Long, maxLength: Int = MAX_GUEST_STRING_LENGTH): String {
    if (address == 0L) return ""

    val result = StringBuilder()
    var offset = 0

    while (offset < maxLength) {
        val remaining = maxLength - offset
        val chunkSize = minOf(READ_CHUNK_SIZE, remaining)
        val chunk = memory.read(address + offset, chunkSize) ?: break

        for (b in chunk) {
            if (b == 0.toByte()) return result.toString()
            result.append(b.toInt().toChar())
        }
        offset += chunk.size
        if (chunk.size < chunkSize) break // short read - treat as end of readable memory
    }

    return result.toString()
}
