package com.nokia.vxp.resource

import org.junit.Assert.assertEquals
import org.junit.Test

class StringResourceTest {

    private fun resourceOf(bytes: ByteArray) = Resource(1, 0, bytes, ResourceType.TEXT)

    @Test
    fun `decodes plain ASCII text`() {
        val resource = resourceOf("Hello World".toByteArray(Charsets.US_ASCII))
        assertEquals("Hello World", StringResource.decode(resource))
    }

    @Test
    fun `decodes valid UTF-8 with multibyte characters`() {
        val text = "café résumé"
        val resource = resourceOf(text.toByteArray(Charsets.UTF_8))
        assertEquals(text, StringResource.decode(resource))
    }

    @Test
    fun `strips a trailing null terminator`() {
        val bytes = "hello".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
        val resource = resourceOf(bytes)
        assertEquals("hello", StringResource.decode(resource))
    }

    @Test
    fun `empty data decodes to empty string`() {
        val resource = resourceOf(ByteArray(0))
        assertEquals("", StringResource.decode(resource))
    }

    @Test
    fun `falls back to Latin-1 for bytes that are not valid UTF-8`() {
        // 0xFF alone is not a valid UTF-8 sequence start.
        val bytes = byteArrayOf('A'.code.toByte(), 0xFF.toByte(), 'B'.code.toByte())
        val resource = resourceOf(bytes)

        val result = StringResource.decode(resource)
        // Should not contain the UTF-8 replacement character.
        assertEquals(false, result.contains('\uFFFD'))
    }
}
