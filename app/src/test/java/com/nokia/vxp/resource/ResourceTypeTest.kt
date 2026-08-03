package com.nokia.vxp.resource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceTypeTest {

    @Test
    fun `sniffs PNG magic`() {
        val data = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A)
        assertEquals(ResourceType.IMAGE_PNG, ResourceType.sniff(data))
    }

    @Test
    fun `sniffs JPEG magic`() {
        val data = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        assertEquals(ResourceType.IMAGE_JPEG, ResourceType.sniff(data))
    }

    @Test
    fun `sniffs BMP magic`() {
        val data = "BM".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0, 0, 0)
        assertEquals(ResourceType.IMAGE_BMP, ResourceType.sniff(data))
    }

    @Test
    fun `sniffs MIDI magic`() {
        val data = "MThd".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0, 0, 6)
        assertEquals(ResourceType.AUDIO_MIDI, ResourceType.sniff(data))
    }

    @Test
    fun `sniffs WAV magic`() {
        val data = "RIFF".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0, 0, 0)
        assertEquals(ResourceType.AUDIO_WAV, ResourceType.sniff(data))
    }

    @Test
    fun `sniffs MP3 via ID3 tag`() {
        val data = "ID3".toByteArray(Charsets.US_ASCII) + byteArrayOf(3, 0)
        assertEquals(ResourceType.AUDIO_MP3, ResourceType.sniff(data))
    }

    @Test
    fun `sniffs MP3 via frame sync`() {
        val data = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00)
        assertEquals(ResourceType.AUDIO_MP3, ResourceType.sniff(data))
    }

    @Test
    fun `sniffs AMR magic`() {
        val data = "#!AMR\n".toByteArray(Charsets.US_ASCII)
        assertEquals(ResourceType.AUDIO_AMR, ResourceType.sniff(data))
    }

    @Test
    fun `sniffs plain text`() {
        val data = "Hello, this is a plain readable string resource.".toByteArray(Charsets.US_ASCII)
        assertEquals(ResourceType.TEXT, ResourceType.sniff(data))
    }

    @Test
    fun `random binary data with no matching signature is UNKNOWN`() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertEquals(ResourceType.UNKNOWN, ResourceType.sniff(data))
    }

    @Test
    fun `empty data is UNKNOWN not a crash`() {
        assertEquals(ResourceType.UNKNOWN, ResourceType.sniff(ByteArray(0)))
    }

    @Test
    fun `isImage and isAudio classify correctly`() {
        assertTrue(ResourceType.IMAGE_PNG.isImage)
        assertTrue(!ResourceType.IMAGE_PNG.isAudio)
        assertTrue(ResourceType.AUDIO_WAV.isAudio)
        assertTrue(!ResourceType.AUDIO_WAV.isImage)
        assertTrue(!ResourceType.TEXT.isImage && !ResourceType.TEXT.isAudio)
    }

    @Test
    fun `data shorter than a magic prefix does not crash and is UNKNOWN`() {
        val data = byteArrayOf(0x89.toByte()) // just the first byte of PNG's magic
        assertEquals(ResourceType.UNKNOWN, ResourceType.sniff(data))
    }
}
