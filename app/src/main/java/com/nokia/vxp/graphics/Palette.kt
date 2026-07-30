package com.nokia.vxp.graphics

/**
 * Indexed-color palette for 4bpp/8bpp resource images (common for
 * Nokia-era sprite assets, to save space vs true color). Colors are
 * stored as ARGB8888 for direct use with FrameBuffer/BitmapBuffer.
 */
class Palette(val colors: IntArray) {

    fun colorAt(index: Int): Int =
        if (index in colors.indices) colors[index] else MISSING_INDEX_COLOR

    companion object {
        // Bright magenta as a deliberate "this index doesn't exist" tell,
        // rather than silently returning black and hiding a resource bug.
        private const val MISSING_INDEX_COLOR = 0xFFFF00FF.toInt()

        /** Plain grayscale ramp - a reasonable fallback before a real palette resource is loaded. */
        fun grayscale(levels: Int = 16): Palette {
            val colors = IntArray(levels) { i ->
                val v = i * 255 / (levels - 1).coerceAtLeast(1)
                (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
            return Palette(colors)
        }

        /** Classic monochrome-LCD-style green-on-dark ramp, as a stylistic default (matches app theme colors). */
        fun nokiaLcdGreen(levels: Int = 4): Palette {
            val colors = IntArray(levels) { i ->
                val t = i.toFloat() / (levels - 1).coerceAtLeast(1)
                val r = (0x0F + t * (0x9B - 0x0F)).toInt()
                val g = (0x38 + t * (0xBB - 0x38)).toInt()
                val b = 0x0F
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            return Palette(colors)
        }
    }
}
