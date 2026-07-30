package com.nokia.vxp.graphics

/**
 * Rolling-average FPS counter driven by actual *presented* frames
 * (EmulatorSurfaceView calls recordFrame() each time it draws) -
 * distinct from emulator.Scheduler's internal CPU-batch-timing estimate,
 * which measures instruction throughput rather than presentation rate.
 */
class FPSCounter(private val windowSize: Int = 30) {
    private val frameTimestampsNanos = ArrayDeque<Long>()

    fun recordFrame(nowNanos: Long = System.nanoTime()) {
        frameTimestampsNanos.addLast(nowNanos)
        while (frameTimestampsNanos.size > windowSize) {
            frameTimestampsNanos.removeFirst()
        }
    }

    fun currentFps(): Double {
        if (frameTimestampsNanos.size < 2) return 0.0
        val elapsedNanos = frameTimestampsNanos.last() - frameTimestampsNanos.first()
        if (elapsedNanos <= 0) return 0.0
        val frameCount = frameTimestampsNanos.size - 1
        return frameCount * 1_000_000_000.0 / elapsedNanos
    }

    fun reset() = frameTimestampsNanos.clear()
}
