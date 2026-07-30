package com.nokia.vxp.emulator

import com.nokia.vxp.cpu.CpuState
import com.nokia.vxp.cpu.Executor
import com.nokia.vxp.loader.LoadResult
import com.nokia.vxp.memory.MemoryManager
import com.nokia.vxp.utils.Logger

private const val TAG = "Runtime"

/**
 * Everything needed to actually run one loaded VXP module: the memory
 * manager (owns the Unicorn engine + mapped regions), CPU state
 * (register access), and Executor (run/step/stop control). Built once
 * per successful load; call teardown() when the session ends.
 */
class Runtime private constructor(
    val memoryManager: MemoryManager,
    val cpuState: CpuState,
    val executor: Executor
) {
    fun teardown() {
        executor.stop()
        memoryManager.teardown()
    }

    companion object {
        /** Builds a Runtime from a successful loader.LoadResult, and initializes PC/SP/LR for entry. Null on memory setup failure. */
        fun from(loadResult: LoadResult.Success): Runtime? {
            val memoryManager = MemoryManager()
            if (!memoryManager.setup(loadResult.memoryLayout)) {
                Logger.e(TAG, "MemoryManager.setup() failed - cannot build Runtime")
                return null
            }

            val cpuState = CpuState(memoryManager)
            cpuState.initEntry(
                entryPoint = loadResult.memoryLayout.entryPoint,
                initialSp = memoryManager.stack.initialStackPointer
            )

            val executor = Executor(memoryManager, cpuState)
            return Runtime(memoryManager, cpuState, executor)
        }
    }
}
