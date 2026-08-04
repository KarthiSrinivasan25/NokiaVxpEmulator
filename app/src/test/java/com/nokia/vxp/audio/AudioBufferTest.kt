package com.nokia.vxp.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AudioBufferTest {

    @Test
    fun `sine tone produces the expected sample count for a given duration`() {
        val format = AudioFormat(sampleRateHz = 16000)
        val buffer = AudioBuffer.sineTone(440.0, durationMillis = 1000, format = format)

        assertEquals(16000, buffer.samples.size)
    }

    @Test
    fun `square tone produces the expected sample count for a given duration`() {
        val format = AudioFormat(sampleRateHz = 8000)
        val buffer = AudioBuffer.squareTone(440.0, durationMillis = 500, format = format)

        assertEquals(4000, buffer.samples.size)
    }

    @Test
    fun `silence produces all-zero samples of the correct length`() {
        val format = AudioFormat(sampleRateHz = 16000)
        val buffer = AudioBuffer.silence(durationMillis = 250, format = format)

        assertEquals(4000, buffer.samples.size)
        assertTrue(buffer.samples.all { it == 0.toShort() })
    }

    @Test
    fun `sine tone amplitude stays within the requested bound`() {
        val buffer = AudioBuffer.sineTone(440.0, durationMillis = 100, amplitude = 0.5)
        val maxExpected = (Short.MAX_VALUE * 0.5).toInt()

        for (sample in buffer.samples) {
            assertTrue(abs(sample.toInt()) <= maxExpected + 1) // +1 for rounding slack
        }
    }

    @Test
    fun `square tone only takes on two amplitude values`() {
        val buffer = AudioBuffer.squareTone(1000.0, durationMillis = 50, amplitude = 0.8)
        val distinctValues = buffer.samples.toSet()

        assertEquals(2, distinctValues.size)
    }

    @Test
    fun `square tone alternates between positive and negative`() {
        val buffer = AudioBuffer.squareTone(1000.0, durationMillis = 50, amplitude = 0.8)
        val values = buffer.samples.toSet()

        assertTrue(values.any { it > 0 })
        assertTrue(values.any { it < 0 })
    }

    @Test
    fun `zero amplitude produces silence-equivalent output`() {
        val buffer = AudioBuffer.sineTone(440.0, durationMillis = 100, amplitude = 0.0)
        assertTrue(buffer.samples.all { it == 0.toShort() })
    }

    @Test
    fun `zero duration produces an empty buffer, not a crash`() {
        val buffer = AudioBuffer.sineTone(440.0, durationMillis = 0)
        assertEquals(0, buffer.samples.size)
    }

    @Test
    fun `durationMillis round-trips correctly for a mono buffer`() {
        val format = AudioFormat(sampleRateHz = 16000, channelCount = 1)
        val buffer = AudioBuffer.silence(durationMillis = 300, format = format)

        assertEquals(300L, buffer.durationMillis)
    }

    @Test
    fun `amplitude above 1 is clamped rather than overflowing`() {
        val buffer = AudioBuffer.sineTone(440.0, durationMillis = 100, amplitude = 5.0) // way over 1.0
        for (sample in buffer.samples) {
            assertTrue(abs(sample.toInt()) <= Short.MAX_VALUE)
        }
    }
}
