package com.nokia.vxp.graphics

/**
 * The emulated LCD's pixel memory, stored as ARGB8888 for direct use
 * with android.graphics.Bitmap.setPixels(). Guest writes (RGB565 today;
 * palette-indexed once mre/VmGraphics exists) get converted on the way
 * in via Color565/Palette - this class itself is just pixel storage plus
 * a handful of drawing primitives.
 */
class FrameBuffer(val width: Int, val height: Int) {
    val pixels = IntArray(width * height)

    fun clear(argbColor: Int = 0xFF000000.toInt()) {
        pixels.fill(argbColor)
    }

    fun setPixel(x: Int, y: Int, argbColor: Int) {
        if (x !in 0 until width || y !in 0 until height) return
        pixels[y * width + x] = argbColor
    }

    fun getPixel(x: Int, y: Int): Int {
        if (x !in 0 until width || y !in 0 until height) return 0
        return pixels[y * width + x]
    }

    fun setPixelRgb565(x: Int, y: Int, rgb565: Int) = setPixel(x, y, Color565.toArgb8888(rgb565))

    fun fillRect(x: Int, y: Int, w: Int, h: Int, argbColor: Int) {
        val x0 = x.coerceAtLeast(0)
        val y0 = y.coerceAtLeast(0)
        val x1 = (x + w).coerceAtMost(width)
        val y1 = (y + h).coerceAtMost(height)
        for (row in y0 until y1) {
            val rowStart = row * width
            for (col in x0 until x1) {
                pixels[rowStart + col] = argbColor
            }
        }
    }

    /** Bresenham's line algorithm - simple, integer-only, plenty for a retro LCD's worth of pixels per frame. */
    fun drawLine(x0: Int, y0: Int, x1: Int, y1: Int, argbColor: Int) {
        var x = x0
        var y = y0
        val dx = Math.abs(x1 - x0)
        val dy = -Math.abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy

        while (true) {
            setPixel(x, y, argbColor)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    /** Bulk-loads raw RGB565 data (as read straight from guest VRAM) into this buffer, row-major. */
    fun loadFromRgb565(source: ShortArray) {
        val count = minOf(source.size, pixels.size)
        for (i in 0 until count) {
            pixels[i] = Color565.toArgb8888(source[i].toInt() and 0xFFFF)
        }
    }

    fun copyFrom(other: FrameBuffer) {
        require(other.width == width && other.height == height) {
            "Cannot copy FrameBuffer of size ${other.width}x${other.height} into ${width}x$height"
        }
        System.arraycopy(other.pixels, 0, pixels, 0, pixels.size)
    }
}
