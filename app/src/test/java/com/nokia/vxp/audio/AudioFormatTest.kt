package com.nokia.vxp.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioFormatTest {

    @Test
    fun `default format is mono 16-bit 16kHz`() {
        val format = AudioFormat.DEFAULT
        assertEquals(16000, format.sampleRateHz)
        assertEquals(1, format.channelCount)
        assertEquals(16, format.bitsPerSample)
    }

    @Test
    fun `custom format retains given values`() {
        val format = AudioFormat(sampleRateHz = 8000, channelCount = 2, bitsPerSample = 8)
        assertEquals(8000, format.sampleRateHz)
        assertEquals(2, format.channelCount)
        assertEquals(8, format.bitsPerSample)
    }
}
