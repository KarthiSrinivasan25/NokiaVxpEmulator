package com.nokia.vxp.emulator

/**
 * Paces EmulatorLoop to a target FPS by sleeping out the remainder of
 * each frame's time budget. Also tracks the most recently measured FPS
 * for graphics.FPSCounter / debug.FPSMonitor to display later.
 */
class FrameLimiter(targetFps: Int) {

    private val frameBudgetNanos = 1_000_000_000L / targetFps
    private var lastFrameStartNanos = System.nanoTime()
    private var measuredFps = 0.0

    fun startFrame() {
        lastFrameStartNanos = System.nanoTime()
    }

    /** Call at the end of a frame's work; sleeps out any remaining budget. */
    fun endFrameAndWait() {
        val elapsed = System.nanoTime() - lastFrameStartNanos
        measuredFps = if (elapsed > 0) 1_000_000_000.0 / elapsed else 0.0

        val remaining = frameBudgetNanos - elapsed
        if (remaining > 0) {
            val millis = remaining / 1_000_000
            val nanos = (remaining % 1_000_000).toInt()
            try {
                Thread.sleep(millis, nanos)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    fun currentFps(): Double = measuredFps
}
