package com.nokia.vxp.emulator

import com.nokia.vxp.cpu.CpuState
import com.nokia.vxp.cpu.Executor
import com.nokia.vxp.cpu.Flags
import com.nokia.vxp.cpu.Registers
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
            if (loadResult.memoryLayout.isThumbEntry) {
                // ELF entry point had its low bit set - standard ARM
                // interworking convention for "this code starts in Thumb
                // state". Set CPSR's T bit so Unicorn decodes correctly
                // from the very first instruction.
                val cpsrWithThumb = Flags.withBit(cpuState.getCpsr(), Flags.BIT_T, true)
                cpuState.setCpsr(cpsrWithThumb)
                Logger.i(TAG, "Entry point is Thumb-mode - set CPSR T bit")
            }
            loadResult.memoryLayout.staticBaseAddress?.let { staticBase ->
                // ARM RWPI (Read-Write Position Independence): the entry
                // point's own scatter-load startup code expects R9 to
                // already point at a real, writable runtime address for
                // this module's global/static data before it runs a
                // single instruction - see ModuleMemoryLayout.staticBaseAddress's
                // doc comment for the full explanation. Without this, the
                // very first ER_RW/ER_ZI scatter-load copy resolves its
                // destination address near 0 and faults immediately.
                cpuState.setRegister(Registers.R9, staticBase)
                Logger.i(TAG, "RWPI static base detected - R9 set to 0x${staticBase.toString(16)}")
            }

            val executor = Executor(memoryManager, cpuState)
            return Runtime(memoryManager, cpuState, executor)
        }
    }
}
