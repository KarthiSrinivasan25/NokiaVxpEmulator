package com.nokia.vxp.debug

enum class PerformanceHealth { GOOD, DEGRADED, POOR }

/**
 * Tracks recent frame timings against a target FPS and classifies
 * overall emulator health. Distinct from graphics.FPSCounter (which
 * just computes a raw rolling FPS number for display): this adds the
 * judgment call ("is this actually keeping up, and how badly not if
 * not") that a debug overlay can show as a simple status rather than
 * making the user interpret a raw FPS number themselves.
 */
class FPSMonitor(private val targetFps: Int, private val windowSize: Int = 30) {
    private val frameTimestampsNanos = ArrayDeque<Long>()
    private var droppedFrames = 0

    fun recordFrame(nowNanos: Long = System.nanoTime()) {
        if (frameTimestampsNanos.isNotEmpty()) {
            val delta = nowNanos - frameTimestampsNanos.last()
            val budgetNanos = 1_000_000_000L / targetFps
            if (delta > budgetNanos * 2) droppedFrames++ // took more than 2x budget - count it as dropped
        }
        frameTimestampsNanos.addLast(nowNanos)
        while (frameTimestampsNanos.size > windowSize) frameTimestampsNanos.removeFirst()
    }

    fun currentFps(): Double {
        if (frameTimestampsNanos.size < 2) return 0.0
        val elapsed = frameTimestampsNanos.last() - frameTimestampsNanos.first()
        if (elapsed <= 0) return 0.0
        return (frameTimestampsNanos.size - 1) * 1_000_000_000.0 / elapsed
    }

    fun droppedFrameCount(): Int = droppedFrames

    fun health(): PerformanceHealth {
        val fps = currentFps()
        if (fps <= 0.0) return PerformanceHealth.GOOD // not enough data yet - don't alarm prematurely
        val ratio = fps / targetFps
        return when {
            ratio >= 0.9 -> PerformanceHealth.GOOD
            ratio >= 0.6 -> PerformanceHealth.DEGRADED
            else -> PerformanceHealth.POOR
        }
    }

    fun reset() {
        frameTimestampsNanos.clear()
        droppedFrames = 0
    }
}
