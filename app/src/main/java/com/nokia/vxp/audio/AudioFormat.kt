package com.nokia.vxp.audio

/** PCM format descriptor. Nokia MRE-era phones commonly used 8-16kHz mono audio. */
data class AudioFormat(
    val sampleRateHz: Int = 16000,
    val channelCount: Int = 1,
    val bitsPerSample: Int = 16
) {
    companion object {
        val DEFAULT = AudioFormat()
    }
}
