package com.nokia.vxp.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FPSMonitorTest {

    @Test
    fun `fewer than two frames reports zero fps and GOOD health`() {
        val monitor = FPSMonitor(targetFps = 30)
        assertEquals(0.0, monitor.currentFps(), 0.001)
        assertEquals(PerformanceHealth.GOOD, monitor.health())
    }

    @Test
    fun `steady frames at target rate report GOOD health`() {
        val monitor = FPSMonitor(targetFps = 30, windowSize = 10)
        val intervalNanos = 1_000_000_000L / 30
        var t = 0L
        repeat(5) {
            monitor.recordFrame(t)
            t += intervalNanos
        }

        assertEquals(30.0, monitor.currentFps(), 0.5)
        assertEquals(PerformanceHealth.GOOD, monitor.health())
    }

    @Test
    fun `frames running at half speed report DEGRADED or POOR health`() {
        val monitor = FPSMonitor(targetFps = 30, windowSize = 10)
        val intervalNanos = 1_000_000_000L / 15 // half of target fps
        var t = 0L
        repeat(5) {
            monitor.recordFrame(t)
            t += intervalNanos
        }

        assertTrue(monitor.health() == PerformanceHealth.DEGRADED || monitor.health() == PerformanceHealth.POOR)
    }

    @Test
    fun `a frame taking more than 2x budget counts as dropped`() {
        val monitor = FPSMonitor(targetFps = 30)
        val budgetNanos = 1_000_000_000L / 30

        monitor.recordFrame(0L)
        monitor.recordFrame(budgetNanos * 3) // way over budget

        assertEquals(1, monitor.droppedFrameCount())
    }

    @Test
    fun `frames within budget are not counted as dropped`() {
        val monitor = FPSMonitor(targetFps = 30)
        val budgetNanos = 1_000_000_000L / 30

        monitor.recordFrame(0L)
        monitor.recordFrame(budgetNanos)
        monitor.recordFrame(budgetNanos * 2)

        assertEquals(0, monitor.droppedFrameCount())
    }

    @Test
    fun `reset clears fps history and dropped count`() {
        val monitor = FPSMonitor(targetFps = 30)
        val budgetNanos = 1_000_000_000L / 30
        monitor.recordFrame(0L)
        monitor.recordFrame(budgetNanos * 5)

        monitor.reset()

        assertEquals(0, monitor.droppedFrameCount())
        assertEquals(0.0, monitor.currentFps(), 0.001)
    }

    @Test
    fun `window size limits how many samples are kept`() {
        val monitor = FPSMonitor(targetFps = 30, windowSize = 3)
        repeat(10) { monitor.recordFrame(it * 1_000_000L) }
        // Just verifying it doesn't throw and produces a sane finite value
        // with only the last few samples retained.
        assertTrue(monitor.currentFps().isFinite())
    }
}
