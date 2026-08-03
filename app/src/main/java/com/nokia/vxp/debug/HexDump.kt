package com.nokia.vxp.debug

/**
 * Classic hex dump formatting: 16 bytes per line, address + hex bytes +
 * ASCII sidebar. Pure formatting logic with no dependency on memory/cpu
 * modules, so it's independently testable and reusable for anything
 * byte-shaped (guest RAM, a .vm_res resource blob, a raw VXP file, etc).
 */
object HexDump {
    private const val BYTES_PER_LINE = 16

    fun format(bytes: ByteArray, baseAddress: Long = 0): List<String> {
        val lines = mutableListOf<String>()
        var offset = 0
        while (offset < bytes.size) {
            val lineBytes = bytes.copyOfRange(offset, minOf(offset + BYTES_PER_LINE, bytes.size))
            lines += formatLine(baseAddress + offset, lineBytes)
            offset += BYTES_PER_LINE
        }
        return lines
    }

    private fun formatLine(address: Long, lineBytes: ByteArray): String {
        val hexPart = StringBuilder()
        val asciiPart = StringBuilder()

        for (i in 0 until BYTES_PER_LINE) {
            if (i < lineBytes.size) {
                val b = lineBytes[i]
                hexPart.append("%02X ".format(b))
                val c = b.toInt() and 0xFF
                asciiPart.append(if (c in 32..126) c.toChar() else '.')
            } else {
                hexPart.append("   ") // padding to keep the ASCII column aligned on a short final line
            }
            if (i == 7) hexPart.append(' ') // extra gap at the halfway point, classic hex-editor style
        }

        return "%08X  %s|%s|".format(address, hexPart.toString(), asciiPart.toString())
    }
}
