package com.nokia.vxp.graphics

/**
 * Draws text onto a FrameBuffer without depending on any real font
 * resource (resource/FontResource doesn't exist yet). Digits use a
 * standard 7-segment layout (self-consistent and easy to verify by eye,
 * unlike hand-transcribed pixel-font bitmaps which are easy to get
 * subtly wrong from memory). A handful of common symbols get real small
 * shapes. Anything else (letters, punctuation not listed below) draws
 * as a plain outlined block - deliberately NOT a guessed pixel glyph -
 * until resource/FontResource can supply real glyph bitmaps from a VXP
 * font resource.
 *
 * Segment layout per digit cell (3 wide x 5 tall grid, before scaling):
 *   a a a      row 0: top
 *   f     b    rows 0-2: top-left / top-right
 *   f     b
 *   g g g      row 2: middle
 *   e     c    rows 2-4: bottom-left / bottom-right
 *   e     c
 *   d d d      row 4: bottom
 */
object FontRenderer {

    private const val CELL_W = 3
    private const val CELL_H = 5
    private const val GLYPH_SPACING = 1 // gap between characters, in unscaled pixels

    // Standard 7-segment on/off patterns per digit: a,b,c,d,e,f,g
    private val DIGIT_SEGMENTS: Map<Char, BooleanArray> = mapOf(
        '0' to booleanArrayOf(true, true, true, true, true, true, false),
        '1' to booleanArrayOf(false, true, true, false, false, false, false),
        '2' to booleanArrayOf(true, true, false, true, true, false, true),
        '3' to booleanArrayOf(true, true, true, true, false, false, true),
        '4' to booleanArrayOf(false, true, true, false, false, true, true),
        '5' to booleanArrayOf(true, false, true, true, false, true, true),
        '6' to booleanArrayOf(true, false, true, true, true, true, true),
        '7' to booleanArrayOf(true, true, true, false, false, false, false),
        '8' to booleanArrayOf(true, true, true, true, true, true, true),
        '9' to booleanArrayOf(true, true, true, true, false, true, true)
    )

    /** Draws [text] with the top-left of the first glyph at ([x], [y]). Each unscaled pixel is drawn as a [scale]x[scale] block. */
    fun drawText(target: FrameBuffer, x: Int, y: Int, text: String, argbColor: Int, scale: Int = 1) {
        var cursorX = x
        for (ch in text) {
            drawGlyph(target, cursorX, y, ch, argbColor, scale)
            cursorX += (glyphWidth(ch) + GLYPH_SPACING) * scale
        }
    }

    fun measureWidth(text: String, scale: Int = 1): Int {
        if (text.isEmpty()) return 0
        val total = text.sumOf { (glyphWidth(it) + GLYPH_SPACING) }
        return (total - GLYPH_SPACING) * scale // no trailing gap after the last glyph
    }

    private fun glyphWidth(ch: Char): Int = when (ch) {
        ' ' -> CELL_W
        ':' , '.' -> 1
        else -> CELL_W
    }

    private fun drawGlyph(target: FrameBuffer, x: Int, y: Int, ch: Char, color: Int, scale: Int) {
        fun px(col: Int, row: Int) = target.fillRect(x + col * scale, y + row * scale, scale, scale, color)

        when {
            ch == ' ' -> return // nothing to draw

            DIGIT_SEGMENTS.containsKey(ch) -> {
                val (a, b, c, d, e, f, g) = DIGIT_SEGMENTS.getValue(ch).toSegments()
                if (a) for (col in 0 until CELL_W) px(col, 0)                  // top
                if (f) for (row in 0..2) px(0, row)                            // top-left
                if (b) for (row in 0..2) px(CELL_W - 1, row)                   // top-right
                if (g) for (col in 0 until CELL_W) px(col, 2)                  // middle
                if (e) for (row in 2..4) px(0, row)                            // bottom-left
                if (c) for (row in 2..4) px(CELL_W - 1, row)                   // bottom-right
                if (d) for (col in 0 until CELL_W) px(col, CELL_H - 1)         // bottom
            }

            ch == ':' -> {
                px(0, 1)
                px(0, 3)
            }

            ch == '.' -> {
                px(0, CELL_H - 1)
            }

            ch == '%' -> {
                px(0, 0); px(CELL_W - 1, CELL_H - 1) // two corner dots
                for (i in 0 until CELL_H) px(CELL_W - 1 - (i % CELL_W), i) // rough diagonal
            }

            ch == '-' -> {
                for (col in 0 until CELL_W) px(col, 2)
            }

            ch == '/' -> {
                for (row in 0 until CELL_H) {
                    val col = (CELL_W - 1) - (row * (CELL_W - 1) / (CELL_H - 1))
                    px(col, row)
                }
            }

            else -> {
                // Labeled placeholder: an outlined (not filled) box, so it
                // reads visually as "unknown glyph" rather than a solid
                // block that could be mistaken for an intentional shape.
                for (col in 0 until CELL_W) { px(col, 0); px(col, CELL_H - 1) }
                for (row in 0 until CELL_H) { px(0, row); px(CELL_W - 1, row) }
            }
        }
    }

    private fun BooleanArray.toSegments(): SevenSegments =
        SevenSegments(this[0], this[1], this[2], this[3], this[4], this[5], this[6])

    private data class SevenSegments(
        val a: Boolean, val b: Boolean, val c: Boolean, val d: Boolean,
        val e: Boolean, val f: Boolean, val g: Boolean
    )
}
