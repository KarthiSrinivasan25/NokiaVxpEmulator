package com.nokia.vxp.mre

import com.nokia.vxp.audio.AudioManager
import com.nokia.vxp.resource.AudioResource
import com.nokia.vxp.resource.Resource
import com.nokia.vxp.resource.ResourceType
import com.nokia.vxp.utils.Logger

private const val TAG = "VmAudio"

/**
 * Implements audio-related vm_* API surface. vm_midi_play_by_bytes and
 * vm_midi_stop are confirmed real function names (gtrxAC/peanut.vxp's
 * .symtab, MIT licensed) - these replace an earlier, unconfirmed
 * "vm_snd_play_frequency" guess. vm_midi_play_by_bytes genuinely plays
 * real MIDI data: the guest passes a pointer+length to an in-memory
 * MIDI byte buffer, which gets read out of guest memory and routed
 * through audio.AudioPlayer's real MediaPlayer-backed MIDI playback
 * (Android's built-in Sonivox software synth) - not synthesized/faked.
 * [audioManager] is nullable: pass null for a headless session (no
 * Context available) and these handlers fall back to logging only.
 */
object VmAudio {
    fun registerHandlers(dispatcher: VmDispatcher, audioManager: AudioManager?) {
        dispatcher.registerHandler("vm_midi_play_by_bytes", VmApiTable.MIDI_PLAY_BY_BYTES) { args ->
            // r0 = guest pointer to MIDI byte buffer, r1 = length in bytes
            val length = args.r1.toInt()
            if (audioManager == null || length <= 0) {
                Logger.w(TAG, "vm_midi_play_by_bytes(len=$length) - no AudioManager attached or invalid length, ignoring")
                return@registerHandler 0L
            }

            val midiBytes = args.memory.read(args.r0, length)
            if (midiBytes == null) {
                Logger.w(TAG, "vm_midi_play_by_bytes: failed to read $length bytes from guest pointer 0x${args.r0.toString(16)}")
                return@registerHandler -1L
            }

            val resource = Resource(id = 0, rawTypeId = 0, data = midiBytes, detectedType = ResourceType.sniff(midiBytes))
            val audioResource = AudioResource.from(resource)
            if (audioResource == null) {
                Logger.w(TAG, "vm_midi_play_by_bytes: ${midiBytes.size} bytes didn't sniff as a recognized audio format")
                return@registerHandler -1L
            }

            val started = audioManager.player.play(audioResource)
            if (started) 0L else -1L
        }

        dispatcher.registerHandler("vm_midi_stop", VmApiTable.MIDI_STOP) {
            if (audioManager != null) {
                audioManager.player.stop()
            } else {
                Logger.w(TAG, "vm_midi_stop() - no AudioManager attached, ignoring")
            }
            0L
        }
    }
}
