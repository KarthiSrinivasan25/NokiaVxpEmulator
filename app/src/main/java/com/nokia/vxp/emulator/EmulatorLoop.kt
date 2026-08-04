package com.nokia.vxp.emulator

import com.nokia.vxp.cpu.Executor
import com.nokia.vxp.cpu.RunResult
import com.nokia.vxp.memory.MemoryManager
import com.nokia.vxp.mre.VmDispatcher
import com.nokia.vxp.utils.Logger

private const val TAG = "EmulatorLoop"

/**
 * Background thread driving one running session: drains input/lifecycle
 * events, runs a bounded batch of guest instructions via Executor,
 * advances emulated timers, and paces itself to the configured FPS.
 * Graphics/audio rendering hooks are plain callbacks so this class
 * doesn't need to depend on the graphics/ or audio/ modules directly.
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
    private val onFrameRendered: (() -> Unit)? = null,
    private val onFault: ((String) -> Unit)? = null,
    private val onKeyEvent: ((EmulatorEvent) -> Unit)? = null
) {

    @Volatile private var running = false
    @Volatile private var paused = false
    private var thread: Thread? = null

    fun start() {
        if (thread != null) {
            Logger.w(TAG, "start() called while already running - ignoring")
            return
        }
        running = true
        paused = false
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
val result = try {
    executor.run(maxInstructions = instructionCount)
} catch (e: Throwable) {
    Logger.e(TAG, "CPU execution crash", e)
    onFault?.invoke("CPU crash: ${e.message}")
    running = false
    break
}
                    val elapsedNanos = System.nanoTime() - startedNanos

                    scheduler.recordFrameTiming(instructionCount, elapsedNanos)

                    if (result is RunResult.Error) {
                        Logger.e(TAG, "Execution fault: ${result.message}")
                        onFault?.invoke(result.message)
                        running = false
                        break
                    }

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
