package com.nokia.vxp.emulator

/** Tunable parameters for one emulator session. */
data class EmulatorConfig(
    // Nokia S40-era MRE content commonly targeted ~25-30fps on real hardware.
    val targetFps: Int = 30,
    val instructionsPerFrameInitial: Long = 50_000L,
    val instructionsPerFrameMax: Long = 2_000_000L,
    val instructionsPerFrameMin: Long = 1_000L,
    val debugLoggingEnabled: Boolean = true
)
