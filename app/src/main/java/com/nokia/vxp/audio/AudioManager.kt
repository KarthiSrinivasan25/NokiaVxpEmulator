package com.nokia.vxp.audio

import android.content.Context

/**
 * Top-level facade for the audio/ module: tone synthesis + mixing for
 * sound effects (SoundEffect/AudioMixer), and file-based playback for
 * decoded resource audio (AudioPlayer). This is what mre/VmAudio calls
 * into now, replacing its previous log-only stub.
 */
class AudioManager(context: Context, voiceCount: Int = 4) {
    val mixer = AudioMixer(voiceCount)
    val player = AudioPlayer(context)

    fun playTone(frequencyHz: Double, durationMillis: Long) {
        SoundEffect.playTone(mixer, frequencyHz, durationMillis)
    }

    fun stopAll() {
        mixer.stopAll()
        player.stop()
    }
}
