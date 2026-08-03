package com.nokia.vxp.debug

import com.nokia.vxp.graphics.FontRenderer
import com.nokia.vxp.graphics.GraphicsEngine

/**
 * Draws a compact on-screen debug overlay: FPS (digits render cleanly
 * via graphics.FontRenderer's real 7-segment digits) plus a health
 * indicator as a colored square rather than text - FontRenderer only
 * has real glyphs for digits/space/:/./%/-// today, and letters would
 * show as its labeled placeholder outline box, which isn't worth it for
 * a compact overlay. A future debug screen with a real Android TextView
 * (not this pixel overlay) is the better home for full register/memory
 * text - see RegisterViewer/MemoryViewer for that.
 */
object DebugOverlay {

    private val healthColors = mapOf(
        PerformanceHealth.GOOD to 0xFF3DDC84.toInt(),
        PerformanceHealth.DEGRADED to 0xFFFFC107.toInt(),
        PerformanceHealth.POOR to 0xFFFF4444.toInt()
    )

    fun draw(engine: GraphicsEngine, fpsMonitor: FPSMonitor, x: Int = 4, y: Int = 4, scale: Int = 1) {
        val fpsText = "%.0f".format(fpsMonitor.currentFps())
        engine.drawText(x, y, fpsText, 0xFFFFFFFF.toInt(), scale)

        val color = healthColors.getValue(fpsMonitor.health())
        val squareSize = 6 * scale
        val squareX = x + FontRenderer.measureWidth(fpsText, scale) + 4
        engine.fillRect(squareX, y, squareSize, squareSize, color)
    }
}
