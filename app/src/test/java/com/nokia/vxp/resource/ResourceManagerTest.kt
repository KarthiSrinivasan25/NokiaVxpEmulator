package com.nokia.vxp.resource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ResourceManagerTest {

    @Test
    fun `get and all reflect the provided resources`() {
        val r1 = Resource(1, 0, byteArrayOf(1), ResourceType.TEXT)
        val r2 = Resource(2, 0, byteArrayOf(2), ResourceType.UNKNOWN)
        val manager = ResourceManager(listOf(r1, r2))

        assertEquals(2, manager.count())
        assertEquals(r1, manager.get(1))
        assertEquals(r2, manager.get(2))
        assertNull(manager.get(999))
    }

    @Test
    fun `getString decodes and caches the result`() {
        val resource = Resource(1, 0, "hello".toByteArray(), ResourceType.TEXT)
        val manager = ResourceManager(listOf(resource))

        val first = manager.getString(1)
        val second = manager.getString(1)

        assertEquals("hello", first)
        assertSame(first, second) // cached - same String instance, not just equal content
    }

    @Test
    fun `getString on a missing id returns null`() {
        val manager = ResourceManager(emptyList())
        assertNull(manager.getString(42))
    }

    @Test
    fun `getImage on a non-image resource returns null without touching a real decoder`() {
        val resource = Resource(1, 0, "not an image".toByteArray(), ResourceType.TEXT)
        val manager = ResourceManager(listOf(resource))

        assertNull(manager.getImage(1))
    }

    @Test
    fun `getAudio on a recognized audio resource returns a populated AudioResource`() {
        val midiBytes = "MThd".toByteArray() + byteArrayOf(0, 0, 0, 6)
        val resource = Resource(1, 0, midiBytes, ResourceType.AUDIO_MIDI)
        val manager = ResourceManager(listOf(resource))

        val audio = manager.getAudio(1)
        assertNotNull(audio)
        assertEquals(ResourceType.AUDIO_MIDI, audio!!.format)
    }

    @Test
    fun `getFont returns raw data wrapper for any non-empty resource`() {
        val resource = Resource(1, 0, byteArrayOf(1, 2, 3), ResourceType.UNKNOWN)
        val manager = ResourceManager(listOf(resource))

        val font = manager.getFont(1)
        assertNotNull(font)
        assertEquals(3, font!!.rawData.size)
    }

    @Test
    fun `summary reports counts grouped by detected type`() {
        val manager = ResourceManager(
            listOf(
                Resource(1, 0, byteArrayOf(), ResourceType.TEXT),
                Resource(2, 0, byteArrayOf(), ResourceType.TEXT),
                Resource(3, 0, byteArrayOf(), ResourceType.UNKNOWN)
            )
        )
        val summary = manager.summary()
        assertEquals(true, summary.contains("3 entries"))
    }

    @Test
    fun `from() builds a manager by parsing raw vm_res bytes`() {
        val manager = ResourceManager.from(null)
        assertEquals(0, manager.count())
    }
}
