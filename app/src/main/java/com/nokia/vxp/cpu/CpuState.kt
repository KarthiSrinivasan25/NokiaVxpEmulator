package com.nokia.vxp.cpu

import com.nokia.vxp.memory.MemoryManager
import com.nokia.vxp.utils.Logger

private const val TAG = "CpuState"

/**
 * Register-level view into one running emulator's CPU state. Registers
 * and memory live in the same uc_engine instance, so this class borrows
 * MemoryManager's engine handle rather than owning its own.
 */
class CpuState(private val memoryManager: MemoryManager) {

    private fun handle(): Long = memoryManager.nativeEngineHandle()

    fun getRegister(reg: Registers): Long {
        if (!memoryManager.isEngineReady) {
            Logger.w(TAG, "getRegister($reg) called before engine setup")
            return 0
        }
        return nativeGetRegister(handle(), reg.id)
    }

    fun setRegister(reg: Registers, value: Long): Boolean {
        if (!memoryManager.isEngineReady) {
            Logger.w(TAG, "setRegister($reg) called before engine setup")
            return false
        }
        return nativeSetRegister(handle(), reg.id, value)
    }

    fun getCpsr(): Long = getRegister(Registers.CPSR)
    fun setCpsr(value: Long): Boolean = setRegister(Registers.CPSR, value)

    fun getPc(): Long = getRegister(Registers.PC)
    fun getSp(): Long = getRegister(Registers.SP)
    fun getLr(): Long = getRegister(Registers.LR)

    /** Snapshot of every register, e.g. for debug.RegisterViewer. */
    fun snapshot(): Map<Registers, Long> = Registers.values().associateWith { getRegister(it) }

    /** Initializes SP/PC/LR for a freshly mapped module before the first run/step call. */
    fun initEntry(entryPoint: Long, initialSp: Long) {
        setRegister(Registers.PC, entryPoint)
        setRegister(Registers.SP, initialSp)
        // LR also set to the entry point: some MRE-era stubs do a bare
        // `bx lr` as a "return to loader" convention at top level: if
        // that happens before we've set up a real call stack, this at
        // least loops back to a mapped address instead of jumping into
        // unmapped memory and faulting.
        setRegister(Registers.LR, 0)
    }

    private external fun nativeGetRegister(handle: Long, regId: Int): Long
    private external fun nativeSetRegister(handle: Long, regId: Int, value: Long): Boolean

    companion object {
        init { System.loadLibrary("vxpnative") }
    }
}
