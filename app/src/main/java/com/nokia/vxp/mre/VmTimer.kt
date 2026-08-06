package com.nokia.vxp.mre

import com.nokia.vxp.emulator.TimerManager

/**
 * Implements timer-related vm_* API surface on top of
 * emulator.TimerManager. vm_create_timer and vm_get_tick_count are
 * confirmed real names (gtrxAC/peanut.vxp's .symtab, MIT licensed) - no
 * confirmed "delete timer" counterpart was found there, so TIMER_DELETE
 * keeps our own placeholder name pending further evidence.
 */
object VmTimer {
    fun registerHandlers(dispatcher: VmDispatcher, timerManager: TimerManager) {
        // The guest-visible timer "handle" is just TimerManager's own int id.
        dispatcher.registerHandler("vm_create_timer", VmApiTable.TIMER_CREATE) { args ->
            // r0 = period in ms, r1 = repeat (0 = one-shot, nonzero = repeating)
            val period = args.r0
            val repeating = args.r1 != 0L
            val id = timerManager.schedule(
                delayMillis = period,
                periodMillis = if (repeating) period else null
            ) {
                // TODO: once a mechanism for recording *which guest
                // function* this timer should call back into is wired up
                // (the guest would need to pass a function pointer here,
                // requiring a 3rd argument beyond period/repeat), invoke
                // it via mre.VmSystem.callGuestFunction from here.
                //
                // IMPORTANT: this callback fires from
                // emulator.TimerManager.advance(), which EmulatorLoop
                // calls AFTER Executor.run() has already returned for the
                // frame - never call VmSystem.callGuestFunction (or
                // anything that calls Executor.run/step) from a context
                // that's still inside an active Executor.run() call, e.g.
                // directly from a VmDispatcher handler - Unicorn does not
                // support reentrant uc_emu_start calls on the same engine.
            }
            id.toLong()
        }

        dispatcher.registerHandler("vm_timer_delete", VmApiTable.TIMER_DELETE) { args ->
            // r0 = timer id previously returned by vm_create_timer
            timerManager.cancel(args.r0.toInt())
            0L
        }

        dispatcher.registerHandler("vm_get_tick_count", VmApiTable.GET_TICK_COUNT) {
            // Returns emulated elapsed time in ms, driven by how much
            // guest code has actually run (see TimerManager's doc
            // comment) rather than wall-clock time.
            timerManager.now()
        }
    }
}
