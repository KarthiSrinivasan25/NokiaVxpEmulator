package com.nokia.vxp.mre

import com.nokia.vxp.cpu.CpuState
import com.nokia.vxp.cpu.Executor
import com.nokia.vxp.cpu.Registers
import com.nokia.vxp.cpu.RunResult
import com.nokia.vxp.utils.Logger

private const val TAG = "VmSystem"

/**
 * System-level vm_* API surface (vm_app_log is a confirmed real
 * function name, seen in research for this project) plus a
 * general-purpose mechanism for calling INTO guest code - needed to
 * eventually invoke the guest's registered handle_sysevt(VMINT message,
 * VMINT param) function (confirmed real callback pattern - see
 * UstadMobile/ustadmobile-mre's README), once mre/ knows that function's
 * address. How the guest tells us that address in the first place isn't
 * confirmed - likely via its own registration call (something like
 * vm_reg_sysevt_observer) that would need to be trapped and recorded
 * here once its real name/address is known.
 */
object VmSystem {

    fun registerHandlers(dispatcher: VmDispatcher) {
        dispatcher.registerHandler("vm_app_log", VmApiTable.SYSTEM_LOG) { args ->
            // r0 = guest pointer to a null-terminated ASCII log message
            val message = readGuestCString(args.memory, args.r0)
            Logger.i("GuestApp", message)
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
     * on any execution error). Used to deliver events into the guest's
     * registered handler once mre/ knows that handler's address.
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

        // Save what we're about to clobber, so a call made mid-session
        // (e.g. a timer firing) doesn't corrupt whatever the "main"
        // execution flow had in these registers.
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

        // Restore the caller's state, so this looks like a transparent,
        // side-effect-free call from the rest of the emulator's perspective.
        for ((reg, value) in savedGeneralRegs) cpuState.setRegister(reg, value)
        cpuState.setRegister(Registers.PC, savedPc)
        cpuState.setRegister(Registers.LR, savedLr)

        return returnValue
    }
}
