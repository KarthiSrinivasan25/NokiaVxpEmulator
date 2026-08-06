package com.nokia.vxp.mre

import com.nokia.vxp.cpu.CpuState
import com.nokia.vxp.cpu.Executor
import com.nokia.vxp.cpu.Registers
import com.nokia.vxp.cpu.RunResult
import com.nokia.vxp.utils.Logger

private const val TAG = "VmSystem"

/**
 * System-level vm_* API surface. ALL of vm_app_log, vm_exit_app,
 * vm_reg_sysevt_callback, vm_reg_keyboard_callback,
 * vm_reg_pen_callback, and vm_switch_power_saving_mode are confirmed
 * real function names (gtrxAC/peanut.vxp's .symtab, MIT licensed).
 *
 * vm_reg_sysevt_callback in particular is the confirmed real mechanism
 * for the guest registering its handle_sysevt(VMINT message, VMINT
 * param) function (see mre.SysEventRegistry) - this was previously an
 * educated guess based on a decompiled sample's README; now it's a
 * verified real symbol name found directly in an actual VXP's symbol table.
 */
object VmSystem {

    fun registerHandlers(dispatcher: VmDispatcher, registry: SysEventRegistry) {
        dispatcher.registerHandler("vm_app_log", VmApiTable.SYSTEM_LOG) { args ->
            // r0 = guest pointer to a null-terminated ASCII log message
            val message = readGuestCString(args.memory, args.r0)
            Logger.i("GuestApp", message)
            0L
        }

        dispatcher.registerHandler("vm_exit_app", VmApiTable.EXIT_APP) {
            Logger.i(TAG, "Guest called vm_exit_app() - requesting emulator stop")
            // TODO: needs a way to signal Emulator/EmulatorLoop to actually
            // stop (e.g. posting EmulatorEvent.Stop through the event
            // queue) once VmSystem has a reference to one - not wired yet.
            0L
        }

        dispatcher.registerHandler("vm_reg_sysevt_callback", VmApiTable.REG_SYSEVT_CALLBACK) { args ->
            // r0 = guest function pointer to call back with (message, param)
            registry.registerSysEvent(args.r0)
            0L
        }

        dispatcher.registerHandler("vm_reg_keyboard_callback", VmApiTable.REG_KEYBOARD_CALLBACK) { args ->
            registry.registerKeyboard(args.r0)
            0L
        }

        dispatcher.registerHandler("vm_reg_pen_callback", VmApiTable.REG_PEN_CALLBACK) { args ->
            registry.registerPen(args.r0)
            0L
        }

        dispatcher.registerHandler("vm_switch_power_saving_mode", VmApiTable.SWITCH_POWER_SAVING_MODE) {
            Logger.i(TAG, "Guest called vm_switch_power_saving_mode() - no-op (no power model in this emulator)")
            0L
        }
    }

    // Deliberately an address extremely unlikely to ever be a real guest
    // code address (ELF segments/heap/stack all live at far lower
    // addresses per loader.ModuleMapper) - used purely as an "I'm done"
    // marker for Executor.run's endAddress parameter.
    private const val SENTINEL_RETURN_ADDRESS = 0xFFFFFFF0L
    private const val MAX_CALL_INSTRUCTIONS = 10_000_000L

    /**
     * Calls a guest function at [functionAddress] with up to 4 integer
     * args, runs until it returns, and yields its R0 return value (null
     * on any execution error). This is how mre.SysEventRegistry's
     * recorded callback addresses get invoked to actually deliver an
     * event once something decides *when* to (not wired up
     * automatically yet).
     *
     * Implementation note: rather than needing a special native hook,
     * this reuses Executor.run(endAddress=...) - LR is pointed at a
     * sentinel address guaranteed to be unmapped, so the guest
     * function's own epilogue (`bx lr`) naturally lands there, and
     * Executor.run's `until` parameter stops emulation right at that
     * point, same as a normal function return.
     *
     * IMPORTANT: only call this from a context where Executor.run() is
     * NOT already active on the call stack (e.g. from EmulatorLoop
     * itself, or from a TimerManager callback fired via
     * TimerManager.advance() - both happen after the frame's
     * Executor.run() call has already returned). Calling this from
     * inside a VmDispatcher handler would be a reentrant uc_emu_start
     * call, which Unicorn does not support.
     */
    fun callGuestFunction(
        cpuState: CpuState,
        executor: Executor,
        functionAddress: Long,
        args: List<Long> = emptyList()
    ): Long? {
        require(args.size <= 4) { "callGuestFunction only supports up to 4 arguments (R0-R3)" }

        val savedPc = cpuState.getPc()
        val savedLr = cpuState.getLr()
        val savedGeneralRegs = Registers.generalPurpose.associateWith { cpuState.getRegister(it) }

        cpuState.setRegister(Registers.LR, SENTINEL_RETURN_ADDRESS)
        cpuState.setRegister(Registers.PC, functionAddress)
        val argRegs = listOf(Registers.R0, Registers.R1, Registers.R2, Registers.R3)
        for (i in args.indices) cpuState.setRegister(argRegs[i], args[i])

        val result = executor.run(endAddress = SENTINEL_RETURN_ADDRESS, maxInstructions = MAX_CALL_INSTRUCTIONS)
        val returnValue = if (result is RunResult.Ok) cpuState.getRegister(Registers.R0) else null

        if (result is RunResult.Error) {
            Logger.e(TAG, "callGuestFunction(0x${functionAddress.toString(16)}) failed: ${result.message}")
        }

        for ((reg, value) in savedGeneralRegs) cpuState.setRegister(reg, value)
        cpuState.setRegister(Registers.PC, savedPc)
        cpuState.setRegister(Registers.LR, savedLr)

        return returnValue
    }
}
