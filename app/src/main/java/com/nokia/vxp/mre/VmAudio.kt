package com.nokia.vxp.mre

import com.nokia.vxp.audio.AudioManager
import com.nokia.vxp.utils.Logger

private const val TAG = "VmAudio"

/**
 * Implements audio-related vm_snd_* API surface on top of the real
 * audio/ module. Naming is a plausible placeholder, not confirmed
 * against a real SDK header. [audioManager] is nullable: pass null for
 * a headless session (no Context available) and these handlers fall
 * back to logging only, same as before audio/ existed - so a missing
 * Context degrades gracefully instead of crashing.
 */
object VmAudio {
    fun registerHandlers(dispatcher: VmDispatcher, audioManager: AudioManager?) {
        dispatcher.registerHandler("vm_snd_play_frequency", VmApiTable.AUDIO_PLAY_FREQUENCY) { args ->
            // r0 = frequency in Hz, r1 = duration in ms
            if (audioManager != null) {
                audioManager.playTone(args.r0.toDouble(), args.r1)
            } else {
                Logger.w(TAG, "vm_snd_play_frequency(freq=${args.r0}, durationMs=${args.r1}) - no AudioManager attached, ignoring")
            }
            0L
        }

        dispatcher.registerHandler("vm_snd_stop", VmApiTable.AUDIO_STOP) {
            if (audioManager != null) {
                audioManager.stopAll()
            } else {
                Logger.w(TAG, "vm_snd_stop() - no AudioManager attached, ignoring")
            }
            0L
        }
    }
}
