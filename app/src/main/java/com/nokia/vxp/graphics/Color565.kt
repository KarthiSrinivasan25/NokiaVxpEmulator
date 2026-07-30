package com.nokia.vxp.graphics

/**
 * Conversions between RGB565 (the 16-bit format most Nokia MRE-era LCDs
 * used for their framebuffer) and ARGB8888 (what Android's Bitmap/Canvas
 * want). FrameBuffer stores everything internally as ARGB8888 for fast
 * Bitmap.setPixels() calls, so every guest pixel write goes through
 * toArgb8888() once on the way in.
 */
object Color565 {
    fun toArgb8888(rgb565: Int): Int {
        val r5 = (rgb565 ushr 11) and 0x1F
        val g6 = (rgb565 ushr 5) and 0x3F
        val b5 = rgb565 and 0x1F

        // Bit-replicate up to 8 bits per channel rather than a naive
        // left-shift, so pure white (0x1F/0x3F/0x1F) maps to 0xFF/0xFF/0xFF
        // instead of landing a few shades short at 0xF8/0xFC/0xF8.
        val r8 = (r5 shl 3) or (r5 ushr 2)
        val g8 = (g6 shl 2) or (g6 ushr 4)
        val b8 = (b5 shl 3) or (b5 ushr 2)

        return (0xFF shl 24) or (r8 shl 16) or (g8 shl 8) or b8
    }

    fun fromArgb8888(argb: Int): Int {
        val r8 = (argb ushr 16) and 0xFF
        val g8 = (argb ushr 8) and 0xFF
        val b8 = argb and 0xFF

        val r5 = r8 ushr 3
        val g6 = g8 ushr 2
        val b5 = b8 ushr 3

        return (r5 shl 11) or (g6 shl 5) or b5
    }
}
