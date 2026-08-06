package com.nokia.vxp.audio

/**
 * Convenience wrapper for one-shot synthesized sound effects (as
 * opposed to AudioPlayer's file-based playback for decoded resource
 * audio). Not currently called by mre.VmAudio - that module now routes
 * through AudioPlayer for real vm_midi_play_by_bytes playback instead
 * (vm_snd_play_frequency was an earlier, unconfirmed guess at the real
 * audio API and has been replaced). Kept as standalone, genuinely
 * useful tone-synthesis functionality for whenever a confirmed
 * frequency-beep API name turns up, or for UI sound effects.
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
