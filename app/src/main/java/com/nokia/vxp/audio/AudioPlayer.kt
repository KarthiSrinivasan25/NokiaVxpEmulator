package com.nokia.vxp.audio

import android.content.Context
import android.media.MediaPlayer
import com.nokia.vxp.resource.AudioResource
import com.nokia.vxp.resource.ResourceType
import com.nokia.vxp.utils.Logger
import java.io.File
import java.io.FileOutputStream

private const val TAG = "AudioPlayer"

/**
 * Plays resource.AudioResource content (MIDI/MP3/WAV/AMR - whatever
 * format ResourceType.sniff recognized) via android.media.MediaPlayer,
 * which has built-in decoders for all of those on Android (MIDI via the
 * platform's Sonivox software synthesizer). MediaPlayer needs a file or
 * content Uri as its source rather than a raw byte array, so this
 * writes the resource bytes to the app's cache directory first.
 */
class AudioPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    fun play(resource: AudioResource, onCompletion: (() -> Unit)? = null): Boolean {
        stop()

        val tempFile = try {
            writeToCacheFile(resource)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to write audio resource ${resource.resourceId} to cache", e)
            return false
        }

        return try {
            val player = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener {
                    tempFile.delete()
                    onCompletion?.invoke()
                }
                prepare()
                start()
            }
            mediaPlayer = player
            true
        } catch (e: Exception) {
            Logger.e(TAG, "MediaPlayer failed to play resource ${resource.resourceId} (format=${resource.format})", e)
            tempFile.delete()
            false
        }
    }

    fun stop() {
        val current = mediaPlayer ?: return
        try {
            if (current.isPlaying) current.stop()
            current.release()
        } catch (e: Exception) {
            Logger.w(TAG, "Error stopping MediaPlayer: ${e.message}")
        }
        mediaPlayer = null
    }

    private fun writeToCacheFile(resource: AudioResource): File {
        val extension = when (resource.format) {
            ResourceType.AUDIO_MIDI -> "mid"
            ResourceType.AUDIO_WAV -> "wav"
            ResourceType.AUDIO_MP3 -> "mp3"
            ResourceType.AUDIO_AMR -> "amr"
            else -> "bin"
        }
        val file = File(context.cacheDir, "vxp_audio_${resource.resourceId}_${System.currentTimeMillis()}.$extension")
        FileOutputStream(file).use { it.write(resource.rawData) }
        return file
    }
}
