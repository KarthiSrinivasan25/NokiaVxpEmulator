package com.nokia.vxp.graphics

/**
 * Decoded pixel data for one image resource: plain ARGB8888 pixels plus
 * an optional transparent color key (Nokia-era sprite formats commonly
 * used a single "magic" color for transparency rather than a true alpha
 * channel). resource/ImageResource will produce these once that module
 * exists; solid() below is a stand-in until then.
 */
class BitmapBuffer(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
    val transparentColor: Int? = null
) {
    init {
        require(pixels.size == width * height) {
            "BitmapBuffer pixel array size ${pixels.size} doesn't match ${width}x$height"
        }
    }

    fun getPixel(x: Int, y: Int): Int = pixels[y * width + x]

    /** Draws this bitmap onto [target] at ([dstX], [dstY]), skipping transparentColor pixels. Clips to target bounds. */
    fun blit(target: FrameBuffer, dstX: Int, dstY: Int, flipH: Boolean = false, flipV: Boolean = false) {
        for (srcY in 0 until height) {
            val ty = dstY + srcY
            if (ty < 0 || ty >= target.height) continue

            for (srcX in 0 until width) {
                val tx = dstX + srcX
                if (tx < 0 || tx >= target.width) continue

                val readX = if (flipH) width - 1 - srcX else srcX
                val readY = if (flipV) height - 1 - srcY else srcY
                val color = pixels[readY * width + readX]

                if (transparentColor != null && color == transparentColor) continue
                target.setPixel(tx, ty, color)
            }
        }
    }

    companion object {
        /** Solid-color placeholder bitmap - handy for tests/prototyping before real sprite decoding exists. */
        fun solid(width: Int, height: Int, argbColor: Int): BitmapBuffer =
            BitmapBuffer(width, height, IntArray(width * height) { argbColor })
    }
}
