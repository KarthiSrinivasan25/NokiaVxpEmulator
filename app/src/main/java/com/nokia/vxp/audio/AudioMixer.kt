package com.nokia.vxp.audio

/**
 * Manages a fixed pool of AudioChannel "voices" so multiple sound
 * effects can play at once (footstep + explosion + UI blip
 * simultaneously, a common game need). When all channels are busy,
 * steals the one that's been playing longest - simple, predictable
 * behavior for a modest polyphony budget rather than dropping the new
 * sound silently.
 */
class AudioMixer(private val voiceCount: Int = 4) {
    private val channels = List(voiceCount) { AudioChannel() }

    fun play(buffer: AudioBuffer) {
        val channel = channels.firstOrNull { !it.busy } ?: channels.minByOrNull { it.lastUsedAtMillis }
        channel?.play(buffer)
    }

    fun stopAll() {
        for (channel in channels) channel.stop()
    }

    fun activeVoiceCount(): Int = channels.count { it.busy }
    fun totalVoiceCount(): Int = voiceCount
}
