package com.nokia.vxp.audio

import kotlin.math.PI
import kotlin.math.sin

/** Raw PCM sample data (signed 16-bit) plus the format it was generated/decoded at. */
class AudioBuffer(val samples: ShortArray, val format: AudioFormat) {

    val durationMillis: Long
        get() = (samples.size.toLong() * 1000L) / (format.sampleRateHz.toLong() * format.channelCount)

    companion object {
        /** Synthesizes a pure sine tone. */
        fun sineTone(
            frequencyHz: Double,
            durationMillis: Long,
            format: AudioFormat = AudioFormat.DEFAULT,
            amplitude: Double = 0.5
        ): AudioBuffer {
            val sampleCount = sampleCountFor(durationMillis, format)
            val samples = ShortArray(sampleCount)
            val maxAmplitude = Short.MAX_VALUE * amplitude.coerceIn(0.0, 1.0)
            for (i in 0 until sampleCount) {
                val t = i.toDouble() / format.sampleRateHz
                samples[i] = (maxAmplitude * sin(2.0 * PI * frequencyHz * t)).toInt().toShort()
            }
            return AudioBuffer(samples, format)
        }

        /** Synthesizes a square wave - a closer match to the simple "beeper" tones classic feature phones actually produced than a pure sine. */
        fun squareTone(
            frequencyHz: Double,
            durationMillis: Long,
            format: AudioFormat = AudioFormat.DEFAULT,
            amplitude: Double = 0.5
        ): AudioBuffer {
            val sampleCount = sampleCountFor(durationMillis, format)
            val samples = ShortArray(sampleCount)
            val maxAmplitude = (Short.MAX_VALUE * amplitude.coerceIn(0.0, 1.0)).toInt().toShort()
            val minAmplitude = (-maxAmplitude).toShort()
            val period = if (frequencyHz > 0) format.sampleRateHz / frequencyHz else Double.MAX_VALUE
            for (i in 0 until sampleCount) {
                val phase = if (period.isFinite() && period > 0) (i % period) / period else 0.0
                samples[i] = if (phase < 0.5) maxAmplitude else minAmplitude
            }
            return AudioBuffer(samples, format)
        }

        fun silence(durationMillis: Long, format: AudioFormat = AudioFormat.DEFAULT): AudioBuffer =
            AudioBuffer(ShortArray(sampleCountFor(durationMillis, format)), format)

        private fun sampleCountFor(durationMillis: Long, format: AudioFormat): Int =
            ((durationMillis * format.sampleRateHz) / 1000L).toInt().coerceAtLeast(0)
    }
}
