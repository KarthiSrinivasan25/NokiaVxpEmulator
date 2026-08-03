package com.nokia.vxp.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerTest {

    private fun configWith(
        targetFps: Int = 30,
        initial: Long = 50_000L,
        min: Long = 1_000L,
        max: Long = 2_000_000L
    ) = EmulatorConfig(
        targetFps = targetFps,
        instructionsPerFrameInitial = initial,
        instructionsPerFrameMin = min,
        instructionsPerFrameMax = max
    )

    @Test
    fun `starts at the configured initial batch size`() {
        val scheduler = Scheduler(configWith(initial = 12_345L))
        assertEquals(12_345L, scheduler.instructionsForNextFrame())
    }

    @Test
    fun `slow batch shrinks the next batch size`() {
        // targetFrameNanos for 30fps ~= 33.3ms. If 1000 instructions took
        // 33.3ms total (i.e. way slower per-instruction than budget allows),
        // the next batch should shrink well below the initial size.
        val scheduler = Scheduler(configWith(targetFps = 30, initial = 100_000L))
        val targetFrameNanos = 1_000_000_000L / 30

        scheduler.recordFrameTiming(instructionsRun = 1000, elapsedNanos = targetFrameNanos * 10)

        assertTrue(scheduler.instructionsForNextFrame() < 100_000L)
    }

    @Test
    fun `fast batch grows the next batch size up to the max`() {
        val scheduler = Scheduler(configWith(targetFps = 30, initial = 1_000L, max = 5_000_000L))
        val targetFrameNanos = 1_000_000_000L / 30

        // 1000 instructions took a tiny fraction of the frame budget -> next batch should grow a lot.
        scheduler.recordFrameTiming(instructionsRun = 1000, elapsedNanos = targetFrameNanos / 1000)

        assertTrue(scheduler.instructionsForNextFrame() > 1_000L)
    }

    @Test
    fun `never goes below configured minimum`() {
        val scheduler = Scheduler(configWith(min = 5_000L))
        val targetFrameNanos = 1_000_000_000L / 30

        // Absurdly slow instructions - would compute an ideal count far below min.
        scheduler.recordFrameTiming(instructionsRun = 1, elapsedNanos = targetFrameNanos * 1_000_000)

        assertEquals(5_000L, scheduler.instructionsForNextFrame())
    }

    @Test
    fun `never exceeds configured maximum`() {
        val scheduler = Scheduler(configWith(max = 10_000L))
        val targetFrameNanos = 1_000_000_000L / 30

        // Absurdly fast instructions - would compute an ideal count far above max.
        scheduler.recordFrameTiming(instructionsRun = 1_000_000, elapsedNanos = 1)

        assertEquals(10_000L, scheduler.instructionsForNextFrame())
    }

    @Test
    fun `zero elapsed or zero instructions is ignored (no divide-by-zero, no change)`() {
        val scheduler = Scheduler(configWith(initial = 42_000L))
        scheduler.recordFrameTiming(instructionsRun = 0, elapsedNanos = 1000)
        scheduler.recordFrameTiming(instructionsRun = 1000, elapsedNanos = 0)
        assertEquals(42_000L, scheduler.instructionsForNextFrame())
    }

    @Test
    fun `reset returns to the initial batch size`() {
        val scheduler = Scheduler(configWith(initial = 7_000L))
        scheduler.recordFrameTiming(instructionsRun = 1, elapsedNanos = 1)
        assertTrue(scheduler.instructionsForNextFrame() != 7_000L)

        scheduler.reset()
        assertEquals(7_000L, scheduler.instructionsForNextFrame())
    }
}
