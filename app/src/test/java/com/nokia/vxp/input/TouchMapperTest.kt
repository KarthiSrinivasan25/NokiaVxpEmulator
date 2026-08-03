package com.nokia.vxp.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TouchMapperTest {

    // A view sized to make each of the 9-row, 3-col grid cells exactly 30x30 for easy math.
    private val viewWidth = 90
    private val viewHeight = 270 // 9 rows * 30

    @Test
    fun `touch in top-left cell resolves to SOFT_LEFT`() {
        val key = TouchMapper.keyAt(5f, 5f, viewWidth, viewHeight)
        assertEquals(NokiaKey.SOFT_LEFT, key)
    }

    @Test
    fun `touch in top-middle cell (empty) resolves to null`() {
        val key = TouchMapper.keyAt(45f, 5f, viewWidth, viewHeight) // row 0, col 1 is null in the layout
        assertNull(key)
    }

    @Test
    fun `touch in bottom-right cell resolves to POUND`() {
        val key = TouchMapper.keyAt(85f, 265f, viewWidth, viewHeight) // row 8, col 2
        assertEquals(NokiaKey.POUND, key)
    }

    @Test
    fun `touch exactly on a cell boundary rounds down into the next cell`() {
        // x=30 is exactly the boundary between col0 and col1 at cellWidth=30.
        val key = TouchMapper.keyAt(30f, 5f, viewWidth, viewHeight)
        assertNull(key) // row0 col1 is the empty middle cell
    }

    @Test
    fun `touch outside bounds is clamped to the nearest edge cell`() {
        val key = TouchMapper.keyAt(-50f, -50f, viewWidth, viewHeight)
        assertEquals(NokiaKey.SOFT_LEFT, key) // clamped to row0 col0
    }

    @Test
    fun `zero-size view returns null instead of dividing by zero`() {
        assertNull(TouchMapper.keyAt(10f, 10f, 0, 0))
    }

    @Test
    fun `cellRect returns a sensible bounding box`() {
        val rect = TouchMapper.cellRect(row = 0, col = 0, viewWidth = viewWidth, viewHeight = viewHeight)
        assertEquals(0f, rect[0], 0.01f)  // left
        assertEquals(0f, rect[1], 0.01f)  // top
        assertEquals(30f, rect[2], 0.01f) // right
        assertEquals(30f, rect[3], 0.01f) // bottom
    }
}
