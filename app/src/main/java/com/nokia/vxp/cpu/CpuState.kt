package com.nokia.vxp.cpu

import com.nokia.vxp.memory.MemoryManager
import com.nokia.vxp.utils.Logger

private const val TAG = "CpuState"

/**

* Register-level view into one running emulator's CPU state.
*
* Registers and memory live in the same Unicorn engine instance,
* so this class borrows MemoryManager's engine handle rather than
* owning its own engine.
  */
  class CpuState(
  private val memoryManager: MemoryManager
  ) {

  private fun handle(): Long = memoryManager.nativeEngineHandle()

  fun getRegister(reg: Registers): Long {
  if (!memoryManager.isEngineReady) {
  Logger.w(TAG, "getRegister($reg) called before engine setup")
  return 0L
  }


   return nativeGetRegister(
       handle(),
       reg.id
   )


  }

  fun setRegister(
  reg: Registers,
  value: Long
  ): Boolean {
  if (!memoryManager.isEngineReady) {
  Logger.w(TAG, "setRegister($reg) called before engine setup")
  return false
  }


   return nativeSetRegister(
       handle(),
       reg.id,
       value
   )

  }

  fun getCpsr(): Long =
  getRegister(Registers.CPSR)

  fun setCpsr(value: Long): Boolean =
  setRegister(Registers.CPSR, value)

  fun getPc(): Long =
  getRegister(Registers.PC)

  fun getSp(): Long =
  getRegister(Registers.SP)

  fun getLr(): Long =
  getRegister(Registers.LR)

  /**

  * Snapshot of every register, useful for debugger/register viewer.
    */
    fun snapshot(): Map<Registers, Long> =
    Registers.values().associateWith { getRegister(it) }

  /**

  * Initialize the CPU state before the first execution.
    */
    fun initEntry(
    entryPoint: Long,
    initialSp: Long
    ) {
    setRegister(
    Registers.PC,
    entryPoint
    )

    setRegister(
    Registers.SP,
    initialSp
    )

    // Keep LR pointing at the entry point initially.
    // This prevents an immediate jump into an arbitrary unmapped
    // address if a top-level stub executes "bx lr".
    setRegister(
    Registers.LR,
    entryPoint
    )
    }

  private external fun nativeGetRegister(
  handle: Long,
  regId: Int
  ): Long

  private external fun nativeSetRegister(
  handle: Long,
  regId: Int,
  value: Long
  ): Boolean

  companion object {
  init {
  System.loadLibrary("vxpnative")
  }
  }
  }
