package com.nokia.vxp.cpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineTest {

    @Test
    fun `starts idle and can step`() {
        val pipeline = Pipeline()
        assertEquals(PipelineState.IDLE, pipeline.state)
        assertTrue(pipeline.canStep)
        assertFalse(pipeline.isRunning)
    }

    @Test
    fun `markRunning clears any previous fault reason`() {
        val pipeline = Pipeline()
        pipeline.markFaulted("bad opcode")
        assertEquals("bad opcode", pipeline.lastFaultReason)

        pipeline.markRunning()
        assertTrue(pipeline.isRunning)
        assertNull(pipeline.lastFaultReason)
    }

    @Test
    fun `paused state allows stepping, running state does not`() {
        val pipeline = Pipeline()
        pipeline.markRunning()
        assertFalse(pipeline.canStep)

        pipeline.markPaused()
        assertTrue(pipeline.canStep)
    }

    @Test
    fun `faulted state retains its reason until next markRunning`() {
        val pipeline = Pipeline()
        pipeline.markFaulted("unmapped memory access at 0xdeadbeef")
        assertEquals(PipelineState.FAULTED, pipeline.state)
        assertEquals("unmapped memory access at 0xdeadbeef", pipeline.lastFaultReason)
        assertFalse(pipeline.canStep)
    }

    @Test
    fun `stopped state is terminal-ish and not steppable`() {
        val pipeline = Pipeline()
        pipeline.markRunning()
        pipeline.markStopped()
        assertEquals(PipelineState.STOPPED, pipeline.state)
        assertFalse(pipeline.canStep)
        assertFalse(pipeline.isRunning)
    }
}
