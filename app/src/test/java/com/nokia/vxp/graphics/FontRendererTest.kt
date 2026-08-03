package com.nokia.vxp.graphics

import org.junit.Assert.assertEquals
import org.junit.Test

class FontRendererTest {

    private val ink = 0xFFFFFFFF.toInt()
    private val bg = 0xFF000000.toInt()

    @Test
    fun `digit 1 lights only the top-right and bottom-right segments`() {
        val fb = FrameBuffer(3, 5)
        fb.clear(bg)
        FontRenderer.drawText(fb, 0, 0, "1", ink, scale = 1)

        // Segments b (top-right, rows 0-2) and c (bottom-right, rows 2-4)
        // occupy column 2; column 0 (a/f/e/g's left edge) should stay dark.
        for (row in 0..4) {
            assertEquals("col2 row$row", ink, fb.getPixel(2, row))
            assertEquals("col0 row$row", bg, fb.getPixel(0, row))
        }
    }

    @Test
    fun `digit 8 lights every segment`() {
        val fb = FrameBuffer(3, 5)
        fb.clear(bg)
        FontRenderer.drawText(fb, 0, 0, "8", ink, scale = 1)

        // Every border pixel of the 3x5 cell should be lit for an "8".
        for (col in 0 until 3) {
            assertEquals(ink, fb.getPixel(col, 0)) // top
            assertEquals(ink, fb.getPixel(col, 2)) // middle
            assertEquals(ink, fb.getPixel(col, 4)) // bottom
        }
        for (row in 0..4) {
            assertEquals(ink, fb.getPixel(0, row)) // left side (f+e)
            assertEquals(ink, fb.getPixel(2, row)) // right side (b+c)
        }
    }

    @Test
    fun `space glyph draws nothing`() {
        val fb = FrameBuffer(3, 5)
        fb.clear(bg)
        FontRenderer.drawText(fb, 0, 0, " ", ink, scale = 1)

        for (y in 0 until 5) for (x in 0 until 3) {
            assertEquals(bg, fb.getPixel(x, y))
        }
    }

    @Test
    fun `scale multiplies glyph size`() {
        val fb = FrameBuffer(20, 20)
        fb.clear(bg)
        FontRenderer.drawText(fb, 0, 0, "1", ink, scale = 2)

        // At scale=2, the top-right segment's column (col 2 unscaled -> cols 4-5) should be lit near the top.
        assertEquals(ink, fb.getPixel(4, 0))
        assertEquals(ink, fb.getPixel(5, 1))
    }

    @Test
    fun `measureWidth accounts for glyph spacing between characters`() {
        val single = FontRenderer.measureWidth("1", scale = 1)
        val triple = FontRenderer.measureWidth("111", scale = 1)
        // 3 glyphs + 2 inter-glyph gaps, vs. 1 glyph with no gap.
        assertEquals(single * 3 + 2, triple)
    }
}
