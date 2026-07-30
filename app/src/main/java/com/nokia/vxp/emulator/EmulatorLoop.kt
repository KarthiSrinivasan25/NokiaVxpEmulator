package com.nokia.vxp.emulator

import com.nokia.vxp.cpu.Executor
import com.nokia.vxp.cpu.RunResult
import com.nokia.vxp.utils.Logger

private const val TAG = "EmulatorLoop"

/**
 * Background thread driving one running session: drains input/lifecycle
 * events, runs a bounded batch of guest instructions via Executor,
 * advances emulated timers, and paces itself to the configured FPS.
 * Graphics/audio rendering hooks are plain callbacks so this class
 * doesn't need to depend on the graphics/ or audio/ modules directly
 * (neither exists yet).
 */
class EmulatorLoop(
    private val executor: Executor,
    private val eventQueue: EventQueue,
    private val timerManager: TimerManager,
    private val scheduler: Scheduler,
    private val frameLimiter: FrameLimiter,
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

                timerManager.advance(elapsedNanos / 1_000_000)
                onFrameRendered?.invoke()
            }

            frameLimiter.endFrameAndWait()
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
