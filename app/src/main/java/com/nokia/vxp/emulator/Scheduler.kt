package com.nokia.vxp.emulator

/**
 * Decides how many guest instructions to run per frame slice. Unicorn
 * executes synchronously - we can't pause mid-instruction to render a
 * frame - so instead we run a bounded batch via Executor, render/mix a
 * frame, then run the next batch. This adapts batch size based on how
 * long the previous batch actually took, aiming to stay near the frame
 * budget without constantly over- or undershooting it.
 */
class Scheduler(private val config: EmulatorConfig) {

    private var currentInstructionsPerFrame = config.instructionsPerFrameInitial
    private val targetFrameNanos = 1_000_000_000L / config.targetFps

    fun instructionsForNextFrame(): Long = currentInstructionsPerFrame

    /** Feed back how long running [instructionsRun] instructions actually took, to size the next batch. */
    fun recordFrameTiming(instructionsRun: Long, elapsedNanos: Long) {
        if (instructionsRun <= 0 || elapsedNanos <= 0) return

        val nanosPerInstruction = elapsedNanos.toDouble() / instructionsRun
        val idealCount = (targetFrameNanos / nanosPerInstruction).toLong()

        currentInstructionsPerFrame = idealCount
            .coerceIn(config.instructionsPerFrameMin, config.instructionsPerFrameMax)
    }

    fun reset() {
        currentInstructionsPerFrame = config.instructionsPerFrameInitial
    }
}
