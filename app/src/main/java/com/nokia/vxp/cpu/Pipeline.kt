package com.nokia.vxp.cpu

enum class PipelineState { IDLE, RUNNING, PAUSED, STOPPED, FAULTED }

/**
 * Tracks the emulator's coarse execution state. Doesn't do any actual
 * stepping/running itself (Executor does that via Unicorn) - this is
 * just the small state machine that emulator/EmulatorLoop and the debug
 * UI both read to decide what controls to show/enable.
 */
class Pipeline {
    var state: PipelineState = PipelineState.IDLE
        private set

    var lastFaultReason: String? = null
        private set

    fun markRunning() {
        state = PipelineState.RUNNING
        lastFaultReason = null
    }

    fun markPaused() {
        state = PipelineState.PAUSED
    }

    fun markStopped() {
        state = PipelineState.STOPPED
    }

    fun markFaulted(reason: String) {
        state = PipelineState.FAULTED
        lastFaultReason = reason
    }

    val isRunning: Boolean get() = state == PipelineState.RUNNING
    val canStep: Boolean get() = state == PipelineState.PAUSED || state == PipelineState.IDLE
}
