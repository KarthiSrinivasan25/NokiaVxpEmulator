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

        // endAddress=0 is OUR convention for "no address limit" - but
        // Unicorn's uc_emu_start treats `until` as a literal target
        // address, and 0 is a real, legitimate guest address for some
        // real VXP files (confirmed: gtrxAC/peanut.vxp's PT_LOAD segment
        // is mapped starting exactly at vaddr 0x0). Passing 0 through
        // unchanged would tell Unicorn to stop exactly where that guest's
        // own code begins executing - a genuine conflict, not a "no
        // limit" no-op. Translate our sentinel to an address guaranteed
        // to never be legitimately reached instead.
        val effectiveEndAddress = if (endAddress == 0L) NO_END_ADDRESS_LIMIT else endAddress

        val code = nativeRun(handle(), startAddress, effectiveEndAddress, timeoutMicros, maxInstructions)

        return if (code == UC_ERR_OK) {
            pipeline.markPaused()
            RunResult.Ok
        } else {
            val message = nativeErrorString(code)
            val pc = cpuState.getRegister(Registers.PC)
            val sp = cpuState.getRegister(Registers.SP)
            Logger.e(TAG, "run() stopped with error: $message (code=$code) - PC=0x${pc.toString(16)} SP=0x${sp.toString(16)} (see logcat tag 'VxpNative' for the exact faulting address, if this was a memory fault)")
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
            val pc = cpuState.getRegister(Registers.PC)
            Logger.e(TAG, "step() failed: $message (code=$code) - PC=0x${pc.toString(16)}")
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
        // An address guaranteed to never be legitimately reached by guest
        // code (all real ELF segments/heap/stack live at far lower
        // addresses per loader.ModuleMapper) - used as the "run with no
        // specific stop address" value actually passed to Unicorn,
        // instead of 0 (which Unicorn treats as a literal, and for some
        // real VXP files an actually-reachable, stop address).
        private const val NO_END_ADDRESS_LIMIT = 0xFFFFFFFFL

        init { System.loadLibrary("vxpnative") }
    }
}
