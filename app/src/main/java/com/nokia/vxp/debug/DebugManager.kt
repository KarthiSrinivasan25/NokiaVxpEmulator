package com.nokia.vxp.debug

import com.nokia.vxp.cpu.CpuState
import com.nokia.vxp.cpu.Executor
import com.nokia.vxp.memory.MemoryManager

/**
 * Top-level facade bundling the debug/ module's pieces for one running
 * session: register/memory inspection, breakpoints, a log console, and
 * FPS/health monitoring. A future debug screen (or EmulatorActivity,
 * behind a "debug mode" toggle) would hold one of these alongside its
 * emulator.Runtime.
 */
class DebugManager(
    private val cpuState: CpuState,
    private val memoryManager: MemoryManager,
    private val executor: Executor,
    val breakpoints: BreakpointManager = BreakpointManager(),
    val console: LogConsole = LogConsole(),
    val fpsMonitor: FPSMonitor = FPSMonitor(targetFps = 30)
) {
    fun registerSnapshot(): List<String> = RegisterViewer.captureAndFormat(cpuState)

    fun memoryDump(address: Long, length: Int): List<String> = MemoryViewer.dumpMemory(memoryManager, address, length)

    fun regionList(): List<String> = MemoryViewer.listRegions(memoryManager)

    /**
     * Runs until the next enabled breakpoint at or after the current PC
     * (or [maxInstructions] instructions, whichever comes first) - reuses
     * Executor.run's existing endAddress parameter, so no new native
     * code was needed to support breakpoints. A breakpoint address of 0
     * (meaning "no breakpoint ahead") falls back to Unicorn's normal
     * "run until stopped/instruction limit" behavior.
     */
    fun runToNextBreakpoint(maxInstructions: Long = 10_000_000L) {
        val nextBp = breakpoints.nextBreakpointAtOrAfter(cpuState.getPc()) ?: 0L
        executor.run(endAddress = nextBp, maxInstructions = maxInstructions)
    }
}
