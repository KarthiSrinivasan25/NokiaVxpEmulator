package com.nokia.vxp.graphics

import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapBufferTest {

    @Test
    fun `blit copies all pixels when fully in bounds`() {
        val bmp = BitmapBuffer.solid(2, 2, 0xFFFF0000.toInt())
        val fb = FrameBuffer(4, 4)
        fb.clear(0)

        bmp.blit(fb, 1, 1)

        assertEquals(0xFFFF0000.toInt(), fb.getPixel(1, 1))
        assertEquals(0xFFFF0000.toInt(), fb.getPixel(2, 2))
        assertEquals(0, fb.getPixel(0, 0)) // untouched outside the bitmap
    }

    @Test
    fun `blit clips pixels that land outside target bounds`() {
        val bmp = BitmapBuffer.solid(4, 4, 0xFF00FF00.toInt())
        val fb = FrameBuffer(2, 2)
        fb.clear(0)

        bmp.blit(fb, -1, -1) // most of the bitmap is off-buffer

        assertEquals(0xFF00FF00.toInt(), fb.getPixel(0, 0)) // the one in-bounds corner
    }

    @Test
    fun `transparent color key pixels are skipped`() {
        val transparent = 0xFF00FF00.toInt()
        val pixels = intArrayOf(
            0xFFFF0000.toInt(), transparent,
            transparent, 0xFF0000FF.toInt()
        )
        val bmp = BitmapBuffer(2, 2, pixels, transparentColor = transparent)
        val fb = FrameBuffer(2, 2)
        fb.clear(0xFF000000.toInt())

        bmp.blit(fb, 0, 0)

        assertEquals(0xFFFF0000.toInt(), fb.getPixel(0, 0)) // drawn
        assertEquals(0xFF000000.toInt(), fb.getPixel(1, 0)) // skipped, background shows through
        assertEquals(0xFF000000.toInt(), fb.getPixel(0, 1)) // skipped
        assertEquals(0xFF0000FF.toInt(), fb.getPixel(1, 1)) // drawn
    }

    @Test
    fun `horizontal flip mirrors columns`() {
        val pixels = intArrayOf(
            0xFFFF0000.toInt(), 0xFF00FF00.toInt() // red, green
        )
        val bmp = BitmapBuffer(2, 1, pixels)
        val fb = FrameBuffer(2, 1)

        bmp.blit(fb, 0, 0, flipH = true)

        assertEquals(0xFF00FF00.toInt(), fb.getPixel(0, 0)) // green now on the left
        assertEquals(0xFFFF0000.toInt(), fb.getPixel(1, 0)) // red now on the right
    }
}
