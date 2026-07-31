package com.nokia.vxp.input

/**
 * Maps a touch point within a view of a given width/height to whichever
 * grid cell (and therefore NokiaKey) it falls in, based on KeyLayout's
 * row/column grid. Pure geometry - no Android View dependency - so it's
 * easy to unit test independent of View measurement/layout timing.
 */
object TouchMapper {

    fun keyAt(touchX: Float, touchY: Float, viewWidth: Int, viewHeight: Int): NokiaKey? {
        if (viewWidth <= 0 || viewHeight <= 0) return null
        val cols = KeyLayout.columns
        val rows = KeyLayout.rows
        if (cols == 0 || rows == 0) return null

        val cellWidth = viewWidth.toFloat() / cols
        val cellHeight = viewHeight.toFloat() / rows

        val col = (touchX / cellWidth).toInt().coerceIn(0, cols - 1)
        val row = (touchY / cellHeight).toInt().coerceIn(0, rows - 1)

        val rowCells = KeyLayout.grid.getOrNull(row) ?: return null
        return rowCells.getOrNull(col)
    }

    /** Bounding rect [left, top, right, bottom] for a given grid cell, for VirtualKeypadView to draw labels/highlights. */
    fun cellRect(row: Int, col: Int, viewWidth: Int, viewHeight: Int): FloatArray {
        val cols = KeyLayout.columns
        val rows = KeyLayout.rows
        val cellWidth = viewWidth.toFloat() / cols
        val cellHeight = viewHeight.toFloat() / rows
        val left = col * cellWidth
        val top = row * cellHeight
        return floatArrayOf(left, top, left + cellWidth, top + cellHeight)
    }
}
