package com.nokia.vxp.debug

import com.nokia.vxp.graphics.GraphicsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugOverlayTest {

    @Test
    fun `draw does not throw on an empty (no-data) fps monitor`() {
        val engine = GraphicsEngine(64, 64)
        val monitor = FPSMonitor(targetFps = 30)
        DebugOverlay.draw(engine, monitor)
        engine.presentFrame() // reaching here without an exception is the pass
    }

    @Test
    fun `health indicator square uses the GOOD color when fps is on target`() {
        val engine = GraphicsEngine(64, 64)
        val monitor = FPSMonitor(targetFps = 30, windowSize = 10)
        val intervalNanos = 1_000_000_000L / 30
        var t = 0L
        repeat(5) {
            monitor.recordFrame(t)
            t += intervalNanos
        }

        DebugOverlay.draw(engine, monitor, x = 4, y = 4, scale = 1)
        engine.presentFrame()

        // The health square is drawn just to the right of the FPS digits;
        // scan a small area near the top-left for the expected GOOD green.
        val frame = engine.currentFrame()
        var foundGoodColor = false
        for (y in 0 until 12) {
            for (x in 0 until 40) {
                if (frame.getPixel(x, y) == 0xFF3DDC84.toInt()) foundGoodColor = true
            }
        }
        assertTrue(foundGoodColor)
    }

    @Test
    fun `overlay stays within a small footprint near the origin`() {
        val engine = GraphicsEngine(128, 160)
        val monitor = FPSMonitor(targetFps = 30)
        monitor.recordFrame(0L)
        monitor.recordFrame(33_000_000L)

        DebugOverlay.draw(engine, monitor, x = 4, y = 4, scale = 1)
        engine.presentFrame()

        val frame = engine.currentFrame()
        // Far corner of the screen should be untouched by the overlay.
        assertEquals(0, frame.getPixel(127, 159))
    }
}
