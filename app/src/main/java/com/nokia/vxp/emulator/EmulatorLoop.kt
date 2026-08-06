package com.nokia.vxp.emulator

import com.nokia.vxp.cpu.Executor
import com.nokia.vxp.cpu.RunResult
import com.nokia.vxp.memory.MemoryManager
import com.nokia.vxp.mre.SysEventRegistry
import com.nokia.vxp.mre.VmDispatcher
import com.nokia.vxp.mre.VmMessages
import com.nokia.vxp.mre.VmSystem
import com.nokia.vxp.utils.Logger

private const val TAG = "EmulatorLoop"

/**
 * Background thread driving one running session: drains input/lifecycle
 * events, runs a bounded batch of guest instructions via Executor,
 * advances emulated timers, delivers the one confirmed real system
 * event (VM_MSG_CREATE, once the guest has registered a handler for
 * it), and paces itself to the configured FPS. Graphics/audio rendering
 * hooks are plain callbacks so this class doesn't need to depend on the
 * graphics/ or audio/ modules directly.
 *
 * [vmDispatcher], if provided, is installed here (inside loop(), i.e. on
 * this class's own dedicated thread) rather than wherever the caller
 * constructed things - see mre.VmDispatcher's install() doc for why
 * that thread-affinity matters (cached JNIEnv pointers are thread-local).
 */
class EmulatorLoop(
    private val executor: Executor,
    private val memoryManager: MemoryManager,
    private val eventQueue: EventQueue,
    private val timerManager: TimerManager,
    private val scheduler: Scheduler,
    private val frameLimiter: FrameLimiter,
    private val vmDispatcher: VmDispatcher? = null,
    private val sysEventRegistry: SysEventRegistry? = null,
    private val onFrameRendered: (() -> Unit)? = null,
    private val onFault: ((String) -> Unit)? = null,
    private val onKeyEvent: ((EmulatorEvent) -> Unit)? = null
) {

    @Volatile private var running = false
    @Volatile private var paused = false
    private var thread: Thread? = null
    private var deliveredCreateEvent = false

    fun start() {
        if (thread != null) {
            Logger.w(TAG, "start() called while already running - ignoring")
            return
        }
        running = true
        paused = false
        deliveredCreateEvent = false
        thread = Thread(::loop, "EmulatorLoop").apply { start() }
    }

    /** User-facing pause - distinct from cpu.Pipeline's internal run/step state, which Executor owns. */
    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    fun stop() {
        running = false
        executor.stop()
        thread?.join(1000)
        thread = null
    }

    private fun loop() {
        Logger.i(TAG, "EmulatorLoop started")

        // Installed here (not by whoever constructed EmulatorLoop) so the
        // dispatch hook's cached JNIEnv belongs to *this* thread, which is
        // the same thread that will call executor.run() below and
        // therefore the same thread the hook callback fires synchronously
        // on. See VmDispatcher.install()'s doc comment.
        val dispatcherInstalled = vmDispatcher?.install(memoryManager) ?: false
        if (vmDispatcher != null && !dispatcherInstalled) {
            Logger.w(TAG, "VmDispatcher failed to install - guest OS API calls will fault instead of being handled")
        }

        try {
            while (running) {
                frameLimiter.startFrame()

                processEvents()

                if (!paused && running) {
                    val instructionCount = scheduler.instructionsForNextFrame()
                    val startedNanos = System.nanoTime()
                    val result = executor.run(maxInstructions = instructionCount)
                    val elapsedNanos = System.nanoTime() - startedNanos

                    scheduler.recordFrameTiming(instructionCount, elapsedNanos)

                    if (result is RunResult.Error) {
                        Logger.e(TAG, "Execution fault: ${result.message}")
                        onFault?.invoke(result.message)
                        running = false
                        break
                    }

                    // Safe to call VmSystem.callGuestFunction here: this is
                    // AFTER executor.run() has already returned for this
                    // frame, not nested inside an active run() call (see
                    // callGuestFunction's doc comment on why that matters -
                    // Unicorn doesn't support reentrant uc_emu_start).
                    deliverCreateEventIfReady()

                    timerManager.advance(elapsedNanos / 1_000_000)
                    onFrameRendered?.invoke()
                }

                frameLimiter.endFrameAndWait()
            }
        } finally {
            if (dispatcherInstalled) vmDispatcher?.uninstall()
        }

        Logger.i(TAG, "EmulatorLoop stopped")
    }

    /**
     * Delivers VM_MSG_CREATE exactly once, the first time we notice the
     * guest has registered a sysevt callback (typically happens very
     * early, during the guest's own vm_main() - see
     * UstadMobile/ustadmobile-mre's README, confirmed real startup
     * sequence). Every VM_MSG_* value beyond CREATE isn't confirmed, so
     * this deliberately only delivers this one event for now rather than
     * guessing at an activity/lifecycle event stream.
     */
    private fun deliverCreateEventIfReady() {
        if (deliveredCreateEvent) return
        val registry = sysEventRegistry ?: return
        val callbackAddress = registry.sysEventCallbackAddress ?: return

        Logger.i(TAG, "Delivering VM_MSG_CREATE to guest callback @ 0x${callbackAddress.toString(16)}")
        VmSystem.callGuestFunction(
            executor.cpuState,
            executor,
            callbackAddress,
            listOf(VmMessages.VM_MSG_CREATE.toLong(), 0L)
        )
        deliveredCreateEvent = true
    }

    private fun processEvents() {
        for (event in eventQueue.drainAll()) {
            when (event) {
                is EmulatorEvent.Pause -> paused = true
                is EmulatorEvent.Resume -> paused = false
                is EmulatorEvent.Stop -> running = false
                is EmulatorEvent.KeyDown, is EmulatorEvent.KeyUp -> onKeyEvent?.invoke(event)
            }
        }
    }

    fun isRunning(): Boolean = running
    fun isPaused(): Boolean = paused
    fun currentFps(): Double = frameLimiter.currentFps()
}
