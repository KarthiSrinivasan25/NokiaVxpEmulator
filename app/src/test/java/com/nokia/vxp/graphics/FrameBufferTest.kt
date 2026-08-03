package com.nokia.vxp.graphics

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameBufferTest {

    @Test
    fun `clear fills every pixel with the given color`() {
        val fb = FrameBuffer(4, 4)
        fb.clear(0xFF112233.toInt())
        assertEquals(0xFF112233.toInt(), fb.getPixel(0, 0))
        assertEquals(0xFF112233.toInt(), fb.getPixel(3, 3))
    }

    @Test
    fun `setPixel out of bounds is silently ignored, not a crash`() {
        val fb = FrameBuffer(4, 4)
        fb.setPixel(-1, 0, 0xFFFFFFFF.toInt())
        fb.setPixel(100, 100, 0xFFFFFFFF.toInt())
        // no assertion needed beyond "didn't throw" - reaching here is the pass
    }

    @Test
    fun `getPixel out of bounds returns zero`() {
        val fb = FrameBuffer(4, 4)
        assertEquals(0, fb.getPixel(-1, 0))
        assertEquals(0, fb.getPixel(4, 4))
    }

    @Test
    fun `fillRect clips to buffer bounds`() {
        val fb = FrameBuffer(4, 4)
        fb.clear(0)
        fb.fillRect(2, 2, 10, 10, 0xFFFFFFFF.toInt()) // extends way past the 4x4 buffer

        assertEquals(0xFFFFFFFF.toInt(), fb.getPixel(3, 3)) // inside clipped region
        assertEquals(0, fb.getPixel(0, 0)) // untouched
    }

    @Test
    fun `drawLine reaches both endpoints`() {
        val fb = FrameBuffer(8, 8)
        fb.clear(0)
        fb.drawLine(0, 0, 7, 7, 0xFFFFFFFF.toInt())

        assertEquals(0xFFFFFFFF.toInt(), fb.getPixel(0, 0))
        assertEquals(0xFFFFFFFF.toInt(), fb.getPixel(7, 7))
    }

    @Test
    fun `loadFromRgb565 converts each source value`() {
        val fb = FrameBuffer(2, 1)
        val source = shortArrayOf(0xFFFF.toShort(), 0x0000)
        fb.loadFromRgb565(source)

        assertEquals(0xFFFFFFFF.toInt(), fb.getPixel(0, 0))
        assertEquals(0xFF000000.toInt(), fb.getPixel(1, 0))
    }

    @Test
    fun `copyFrom duplicates all pixels from a same-size buffer`() {
        val src = FrameBuffer(2, 2)
        src.clear(0xFFABCDEF.toInt())
        val dst = FrameBuffer(2, 2)
        dst.clear(0)

        dst.copyFrom(src)

        assertEquals(0xFFABCDEF.toInt(), dst.getPixel(1, 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `copyFrom rejects mismatched sizes`() {
        val src = FrameBuffer(2, 2)
        val dst = FrameBuffer(3, 3)
        dst.copyFrom(src)
    }
}
