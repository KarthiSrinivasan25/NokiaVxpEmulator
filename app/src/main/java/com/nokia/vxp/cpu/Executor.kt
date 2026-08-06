package com.nokia.vxp.cpu

import com.nokia.vxp.memory.MemoryManager
import com.nokia.vxp.utils.Logger

private const val TAG = "Executor"
private const val UC_ERR_OK = 0 // Unicorn's success code; see uc_strerror() for anything else

sealed class RunResult {
    object Ok : RunResult()
    data class Error(val code: Int, val message: String) : RunResult()
}

/**
 * Drives actual guest code execution via Unicorn's uc_emu_start, and
 * keeps a Pipeline updated so the UI/EmulatorLoop know what state
 * things are in. This is intentionally thin - all the real interpreting
 * happens inside Unicorn; we're just the run/step/stop control surface.
 */
class Executor(
    private val memoryManager: MemoryManager,
    val cpuState: CpuState,
    val pipeline: Pipeline = Pipeline()
) {

    private fun handle(): Long = memoryManager.nativeEngineHandle()

    /**
     * Runs from the current PC until [endAddress] (0 = no address limit -
     * relies on maxInstructions or an explicit stop() instead), for at
     * most [maxInstructions] instructions (0 = unlimited) and/or
     * [timeoutMicros] microseconds (0 = unlimited).
     */
    fun run(endAddress: Long = 0, maxInstructions: Long = 0, timeoutMicros: Long = 0): RunResult {
        if (!memoryManager.isEngineReady) {
            return RunResult.Error(-1, "Engine not set up")
        }

        pipeline.markRunning()
        val startAddress = cpuState.getPc()
        val code = nativeRun(handle(), startAddress, endAddress, timeoutMicros, maxInstructions)

        return if (code == UC_ERR_OK) {
            pipeline.markPaused()
            RunResult.Ok
        } else {
            val message = nativeErrorString(code)
            Logger.e(TAG, "run() stopped with error: $message (code=$code)")
            pipeline.markFaulted(message)
            RunResult.Error(code, message)
        }
    }

    /** Executes exactly one instruction at the current PC. */
    fun step(): RunResult {
        if (!memoryManager.isEngineReady) {
            return RunResult.Error(-1, "Engine not set up")
        }
        if (!pipeline.canStep) {
            return RunResult.Error(-1, "Cannot step from state ${pipeline.state}")
        }

        val code = nativeStep(handle())
        return if (code == UC_ERR_OK) {
            pipeline.markPaused()
            RunResult.Ok
        } else {
            val message = nativeErrorString(code)
            Logger.e(TAG, "step() failed: $message (code=$code)")
            pipeline.markFaulted(message)
            RunResult.Error(code, message)
        }
    }

    /** Requests the currently-running uc_emu_start call to stop as soon as possible. */
    fun stop() {
        if (memoryManager.isEngineReady) {
            nativeStop(handle())
        }
        pipeline.markStopped()
    }

    private external fun nativeRun(
        handle: Long, startAddress: Long, endAddress: Long, timeoutMicros: Long, maxInstructions: Long
    ): Int
    private external fun nativeStep(handle: Long): Int
    private external fun nativeStop(handle: Long)
    private external fun nativeErrorString(code: Int): String

    companion object {
        init { System.loadLibrary("vxpnative") }
    }
}
