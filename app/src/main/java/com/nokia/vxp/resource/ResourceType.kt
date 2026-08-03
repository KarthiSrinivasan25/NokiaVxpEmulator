package com.nokia.vxp.resource

/**
 * Resource content category. Real VXP resource entries don't have a
 * confirmed type-id scheme (see ResourceLoader's doc comment for why),
 * so ResourceType is primarily determined by sniffing the actual byte
 * content against well-known, genuinely confirmed file-format magic
 * numbers - not by trusting whatever numeric "type" field a resource
 * entry might carry, which we can't verify the meaning of.
 */
enum class ResourceType {
    IMAGE_PNG,
    IMAGE_JPEG,
    IMAGE_BMP,
    AUDIO_MIDI,
    AUDIO_WAV,
    AUDIO_MP3,
    AUDIO_AMR,
    TEXT,
    UNKNOWN;

    val isImage: Boolean get() = this == IMAGE_PNG || this == IMAGE_JPEG || this == IMAGE_BMP
    val isAudio: Boolean get() = this == AUDIO_MIDI || this == AUDIO_WAV || this == AUDIO_MP3 || this == AUDIO_AMR

    companion object {
        private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        private val BMP_MAGIC = byteArrayOf('B'.code.toByte(), 'M'.code.toByte())
        private val MIDI_MAGIC = "MThd".toByteArray(Charsets.US_ASCII)
        private val WAV_MAGIC = "RIFF".toByteArray(Charsets.US_ASCII)
        private val ID3_MAGIC = "ID3".toByteArray(Charsets.US_ASCII)
        private val MP3_FRAME_SYNC = byteArrayOf(0xFF.toByte(), 0xFB.toByte())
        private val AMR_MAGIC = "#!AMR".toByteArray(Charsets.US_ASCII)

        /** Sniffs [data]'s actual format from well-known magic bytes. */
        fun sniff(data: ByteArray): ResourceType {
            if (startsWith(data, PNG_MAGIC)) return IMAGE_PNG
            if (startsWith(data, JPEG_MAGIC)) return IMAGE_JPEG
            if (startsWith(data, BMP_MAGIC)) return IMAGE_BMP
            if (startsWith(data, MIDI_MAGIC)) return AUDIO_MIDI
            if (startsWith(data, WAV_MAGIC)) return AUDIO_WAV
            if (startsWith(data, ID3_MAGIC) || startsWith(data, MP3_FRAME_SYNC)) return AUDIO_MP3
            if (startsWith(data, AMR_MAGIC)) return AUDIO_AMR
            if (looksLikeText(data)) return TEXT
            return UNKNOWN
        }

        private fun startsWith(data: ByteArray, prefix: ByteArray): Boolean {
            if (data.size < prefix.size) return false
            for (i in prefix.indices) if (data[i] != prefix[i]) return false
            return true
        }

        private fun looksLikeText(data: ByteArray): Boolean {
            if (data.isEmpty()) return false
            val sampleSize = minOf(data.size, 256)
            var printable = 0
            for (i in 0 until sampleSize) {
                val b = data[i].toInt() and 0xFF
                if (b in 32..126 || b == 9 || b == 10 || b == 13) printable++
            }
            return printable.toDouble() / sampleSize > 0.85
        }
    }
}
