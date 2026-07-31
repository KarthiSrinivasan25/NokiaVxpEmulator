package com.nokia.vxp.input

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Draws a simple Nokia-style virtual keypad and forwards taps to an
 * InputManager. Deliberately plain rounded rectangles + labels rather
 * than skinned key art - matches this project's "functional first"
 * approach; a real key-cap look can replace the drawing code later
 * without touching the touch-handling logic below.
 */
class VirtualKeypadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var inputManager: InputManager? = null

    private val keyPaint = Paint().apply { color = 0xFF232830.toInt(); isAntiAlias = true }
    private val pressedPaint = Paint().apply { color = 0xFF3DDC84.toInt(); isAntiAlias = true }
    private val borderPaint = Paint().apply {
        color = 0xFF3A414C.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    private val labelPaint = Paint().apply {
        color = 0xFFE6E6E6.toInt()
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val pressedLabelPaint = Paint(labelPaint).apply { color = 0xFF0E1116.toInt() }

    private var pressedKey: NokiaKey? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rows = KeyLayout.rows
        val cols = KeyLayout.columns
        if (rows == 0 || cols == 0 || width == 0 || height == 0) return

        labelPaint.textSize = (height / rows) * 0.32f
        pressedLabelPaint.textSize = labelPaint.textSize

        for (row in 0 until rows) {
            val rowCells = KeyLayout.grid.getOrNull(row) ?: continue
            for (col in rowCells.indices) {
                val key = rowCells[col] ?: continue
                drawKey(canvas, key, row, col)
            }
        }
    }

    private fun drawKey(canvas: Canvas, key: NokiaKey, row: Int, col: Int) {
        val rect = TouchMapper.cellRect(row, col, width, height)
        val left = rect[0]
        val top = rect[1]
        val right = rect[2]
        val bottom = rect[3]
        val inset = 4f

        val isPressed = key == pressedKey
        val fillPaint = if (isPressed) pressedPaint else keyPaint
        val textPaint = if (isPressed) pressedLabelPaint else labelPaint

        canvas.drawRoundRect(left + inset, top + inset, right - inset, bottom - inset, 12f, 12f, fillPaint)
        canvas.drawRoundRect(left + inset, top + inset, right - inset, bottom - inset, 12f, 12f, borderPaint)
        canvas.drawText(
            key.label,
            (left + right) / 2f,
            (top + bottom) / 2f + textPaint.textSize / 3f,
            textPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val key = TouchMapper.keyAt(event.x, event.y, width, height)
                if (key != pressedKey) {
                    pressedKey?.let { inputManager?.keyUp(it) }
                    key?.let { inputManager?.keyDown(it) }
                    pressedKey = key
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pressedKey?.let { inputManager?.keyUp(it) }
                pressedKey = null
                invalidate()
            }
        }
        return true
    }
}
