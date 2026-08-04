package com.nokia.vxp.audio

import android.media.AudioAttributes
import android.media.AudioTrack
import com.nokia.vxp.utils.Logger

private const val TAG = "AudioChannel"

/**
 * One playback voice, backed by a real android.media.AudioTrack in
 * STATIC mode - the whole buffer is written up front, which is fine for
 * the short sound-effect-length buffers this emulator deals with.
 * STREAMING mode would be needed for long background music, which isn't
 * implemented here (no confirmed use case for it yet - see mre/VmAudio).
 */
class AudioChannel {
    private var track: AudioTrack? = null

    var busy: Boolean = false
        private set
    var lastUsedAtMillis: Long = 0
        private set

    fun play(buffer: AudioBuffer) {
        stop()
        if (buffer.samples.isEmpty()) return

        val channelMask = if (buffer.format.channelCount == 1) {
            android.media.AudioFormat.CHANNEL_OUT_MONO
        } else {
            android.media.AudioFormat.CHANNEL_OUT_STEREO
        }

        val minBufferSize = AudioTrack.getMinBufferSize(
            buffer.format.sampleRateHz,
            channelMask,
            android.media.AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSizeBytes = maxOf(minBufferSize, buffer.samples.size * 2)

        val newTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setSampleRate(buffer.format.sampleRateHz)
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(channelMask)
                        .build()
                )
                .setBufferSizeInBytes(bufferSizeBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create AudioTrack", e)
            return
        }

        newTrack.write(buffer.samples, 0, buffer.samples.size)
        newTrack.setNotificationMarkerPosition(buffer.samples.size)
        newTrack.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack?) {
                busy = false
            }
            override fun onPeriodicNotification(t: AudioTrack?) {}
        })
        newTrack.play()

        track = newTrack
        busy = true
        lastUsedAtMillis = System.currentTimeMillis()
    }

    fun stop() {
        val current = track ?: return
        try {
            current.stop()
            current.release()
        } catch (e: Exception) {
            Logger.w(TAG, "Error stopping AudioTrack: ${e.message}")
        }
        track = null
        busy = false
    }

    fun setVolume(volume: Float) {
        track?.setVolume(volume.coerceIn(0f, 1f))
    }
}
