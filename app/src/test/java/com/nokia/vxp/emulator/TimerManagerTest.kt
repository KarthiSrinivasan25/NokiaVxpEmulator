package com.nokia.vxp.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerManagerTest {

    @Test
    fun `one-shot timer does not fire before it is due`() {
        val timers = TimerManager()
        var fired = 0
        timers.schedule(delayMillis = 100) { fired++ }

        timers.advance(50)
        assertEquals(0, fired)
    }

    @Test
    fun `one-shot timer fires once it is due and does not fire again`() {
        val timers = TimerManager()
        var fired = 0
        timers.schedule(delayMillis = 100) { fired++ }

        timers.advance(100)
        assertEquals(1, fired)

        timers.advance(1000)
        assertEquals(1, fired) // one-shot: should not fire a second time
    }

    @Test
    fun `periodic timer fires repeatedly`() {
        val timers = TimerManager()
        var fired = 0
        timers.schedule(delayMillis = 10, periodMillis = 10) { fired++ }

        timers.advance(10) // t=10, fires once
        timers.advance(10) // t=20, fires again
        timers.advance(10) // t=30, fires again

        assertEquals(3, fired)
    }

    @Test
    fun `cancel prevents a timer from firing`() {
        val timers = TimerManager()
        var fired = 0
        val id = timers.schedule(delayMillis = 10) { fired++ }
        timers.cancel(id)

        timers.advance(100)
        assertEquals(0, fired)
    }

    @Test
    fun `advance accumulates emulated time correctly`() {
        val timers = TimerManager()
        timers.advance(30)
        timers.advance(20)
        assertEquals(50L, timers.now())
    }

    @Test
    fun `reset clears timers and time`() {
        val timers = TimerManager()
        var fired = 0
        timers.schedule(delayMillis = 5) { fired++ }
        timers.advance(10)
        assertEquals(1, fired)

        timers.reset()
        assertEquals(0L, timers.now())
        assertEquals(0, timers.activeCount())
    }

    @Test
    fun `multiple due timers in one advance call all fire`() {
        val timers = TimerManager()
        var fired = 0
        timers.schedule(delayMillis = 10) { fired++ }
        timers.schedule(delayMillis = 15) { fired++ }

        timers.advance(20) // both are due by t=20
        assertTrue(fired == 2)
    }
}
