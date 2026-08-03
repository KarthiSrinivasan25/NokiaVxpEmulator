package com.nokia.vxp.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventQueueTest {

    @Test
    fun `drainAll returns events in FIFO order`() {
        val queue = EventQueue()
        queue.post(EmulatorEvent.KeyDown(1))
        queue.post(EmulatorEvent.KeyDown(2))
        queue.post(EmulatorEvent.Pause)

        val drained = queue.drainAll()

        assertEquals(3, drained.size)
        assertEquals(EmulatorEvent.KeyDown(1), drained[0])
        assertEquals(EmulatorEvent.KeyDown(2), drained[1])
        assertEquals(EmulatorEvent.Pause, drained[2])
    }

    @Test
    fun `drainAll empties the queue`() {
        val queue = EventQueue()
        queue.post(EmulatorEvent.Resume)
        queue.drainAll()

        assertTrue(queue.isEmpty())
        assertTrue(queue.drainAll().isEmpty())
    }

    @Test
    fun `isEmpty reflects queue state`() {
        val queue = EventQueue()
        assertTrue(queue.isEmpty())
        queue.post(EmulatorEvent.Stop)
        assertTrue(!queue.isEmpty())
    }
}
