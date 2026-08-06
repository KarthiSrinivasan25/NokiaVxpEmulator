package com.nokia.vxp.mre

import com.nokia.vxp.utils.Logger

private const val TAG = "SysEventRegistry"

/**
 * Records the guest function addresses registered via
 * vm_reg_sysevt_callback / vm_reg_keyboard_callback / vm_reg_pen_callback
 * (all confirmed real function names - gtrxAC/peanut.vxp's .symtab, MIT
 * licensed). Once recorded, these addresses are exactly what
 * mre.VmSystem.callGuestFunction needs to actually deliver an event
 * (e.g. VM_MSG_CREATE) into the guest - not wired up to fire
 * automatically yet (that needs EmulatorLoop or Emulator to decide
 * *when* to deliver which events), but the addresses are now captured
 * and available the moment that's built.
 *
 * One instance per running session - Emulator constructs one and passes
 * it to VmSystem.registerHandlers.
 */
class SysEventRegistry {
    var sysEventCallbackAddress: Long? = null
        private set
    var keyboardCallbackAddress: Long? = null
        private set
    var penCallbackAddress: Long? = null
        private set

    fun registerSysEvent(address: Long) {
        sysEventCallbackAddress = address
        Logger.i(TAG, "Guest registered sysevt callback @ 0x${address.toString(16)}")
    }

    fun registerKeyboard(address: Long) {
        keyboardCallbackAddress = address
        Logger.i(TAG, "Guest registered keyboard callback @ 0x${address.toString(16)}")
    }

    fun registerPen(address: Long) {
        penCallbackAddress = address
        Logger.i(TAG, "Guest registered pen callback @ 0x${address.toString(16)}")
    }
}
