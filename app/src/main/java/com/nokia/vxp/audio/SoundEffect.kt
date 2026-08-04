package com.nokia.vxp.audio

/**
 * Convenience wrapper for one-shot synthesized sound effects (as
 * opposed to AudioPlayer's file-based playback for decoded resource
 * audio). This is what feeds mre/VmAudio's vm_snd_play_frequency call.
 */
object SoundEffect {
    enum class Waveform { SINE, SQUARE }

    fun playTone(mixer: AudioMixer, frequencyHz: Double, durationMillis: Long, waveform: Waveform = Waveform.SQUARE) {
        val buffer = when (waveform) {
            Waveform.SINE -> AudioBuffer.sineTone(frequencyHz, durationMillis)
            Waveform.SQUARE -> AudioBuffer.squareTone(frequencyHz, durationMillis)
        }
        mixer.play(buffer)
    }
}
