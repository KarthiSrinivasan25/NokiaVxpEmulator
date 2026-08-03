package com.nokia.vxp.graphics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FPSCounterTest {

    @Test
    fun `fewer than two frames reports zero`() {
        val counter = FPSCounter()
        assertEquals(0.0, counter.currentFps(), 0.0001)
        counter.recordFrame(1_000_000L)
        assertEquals(0.0, counter.currentFps(), 0.0001)
    }

    @Test
    fun `evenly spaced frames compute the expected fps`() {
        val counter = FPSCounter(windowSize = 10)
        val frameIntervalNanos = 1_000_000_000L / 30 // simulate a steady 30fps

        var t = 0L
        repeat(5) {
            counter.recordFrame(t)
            t += frameIntervalNanos
        }

        assertEquals(30.0, counter.currentFps(), 0.5)
    }

    @Test
    fun `window size caps how many samples are kept`() {
        val counter = FPSCounter(windowSize = 3)
        // Record frames at a very irregular pace, then a clean fast burst at
        // the end - only the last (windowSize+1) samples should matter.
        counter.recordFrame(0L)
        counter.recordFrame(10_000_000_000L) // huge irregular gap, should be evicted
        counter.recordFrame(10_000_000_100L)
        counter.recordFrame(10_000_000_200L)
        counter.recordFrame(10_000_000_300L)

        // Only the last windowSize (3) timestamps are kept, evenly spaced
        // 100ns apart -> a very high but finite, sane fps (not NaN/Infinity,
        // and not dragged down by the huge evicted gap).
        assertTrue(counter.currentFps().isFinite())
        assertTrue(counter.currentFps() > 1000.0)
    }

    @Test
    fun `reset clears all recorded frames`() {
        val counter = FPSCounter()
        counter.recordFrame(0L)
        counter.recordFrame(1_000_000L)
        counter.reset()
        assertEquals(0.0, counter.currentFps(), 0.0001)
    }
}
