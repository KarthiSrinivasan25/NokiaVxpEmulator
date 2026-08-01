package com.nokia.vxp.mre

import com.nokia.vxp.utils.Logger

private const val TAG = "VmAudio"

/**
 * Placeholder for audio-related vm_snd_ vm_media_* API surface. The
 * audio/ module (AudioManager, AudioMixer, etc) hasn't been built yet,
 * so every handler here just logs the call and returns 0 rather than
 * silently pretending to succeed in a way that could mask a real
 * missing-feature bug once a game actually depends on audio behavior.
 */
object VmAudio {
    fun registerHandlers(dispatcher: VmDispatcher) {
        dispatcher.registerHandler("vm_snd_play_frequency", VmApiTable.AUDIO_PLAY_FREQUENCY) { args ->
            Logger.w(TAG, "vm_snd_play_frequency(freq=${args.r0}, durationMs=${args.r1}) - audio/ module not implemented yet, ignoring")
            0L
        }

        dispatcher.registerHandler("vm_snd_stop", VmApiTable.AUDIO_STOP) {
            Logger.w(TAG, "vm_snd_stop() - audio/ module not implemented yet, ignoring")
            0L
        }
    }
}
