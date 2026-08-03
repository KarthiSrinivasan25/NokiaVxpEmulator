package com.nokia.vxp.resource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ResourceLoaderTest {

    /** Builds a synthetic .vm_res blob: repeated [id(2), typeId(2), size(4), data...] entries. */
    private fun buildVmRes(entries: List<Triple<Int, Int, ByteArray>>): ByteArray {
        val totalSize = entries.sumOf { 8 + it.third.size }
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        for ((id, typeId, data) in entries) {
            buffer.putShort(id.toShort())
            buffer.putShort(typeId.toShort())
            buffer.putInt(data.size)
            buffer.put(data)
        }
        return buffer.array()
    }

    @Test
    fun `null or empty input produces no resources`() {
        assertTrue(ResourceLoader.parse(null).isEmpty())
        assertTrue(ResourceLoader.parse(ByteArray(0)).isEmpty())
    }

    @Test
    fun `parses a single entry correctly`() {
        val pngBytes = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        val blob = buildVmRes(listOf(Triple(1, 100, pngBytes)))

        val resources = ResourceLoader.parse(blob)

        assertEquals(1, resources.size)
        assertEquals(1, resources[0].id)
        assertEquals(100, resources[0].rawTypeId)
        assertEquals(ResourceType.IMAGE_PNG, resources[0].detectedType)
        assertTrue(pngBytes.contentEquals(resources[0].data))
    }

    @Test
    fun `parses multiple sequential entries`() {
        val entry1 = "hello".toByteArray()
        val entry2 = "world!".toByteArray()
        val blob = buildVmRes(listOf(Triple(1, 0, entry1), Triple(2, 0, entry2)))

        val resources = ResourceLoader.parse(blob)

        assertEquals(2, resources.size)
        assertEquals(1, resources[0].id)
        assertEquals(2, resources[1].id)
        assertTrue(entry1.contentEquals(resources[0].data))
        assertTrue(entry2.contentEquals(resources[1].data))
    }

    @Test
    fun `entry claiming a size larger than remaining data stops parsing there`() {
        val blob = buildVmRes(listOf(Triple(1, 0, "ok".toByteArray())))
        // Corrupt the second (nonexistent) entry's declared size by appending
        // a header that claims more data than actually follows.
        val corrupted = blob + byteArrayOf(2, 0, 0, 0, 0, 0, 0, 0x7F) // size = 0x7F000000, way too big

        val resources = ResourceLoader.parse(corrupted)

        // The first valid entry should still parse; the corrupted second one should not.
        assertEquals(1, resources.size)
        assertEquals(1, resources[0].id)
    }

    @Test
    fun `trailing bytes too short for another header are ignored, not an error`() {
        val blob = buildVmRes(listOf(Triple(1, 0, "data".toByteArray()))) + byteArrayOf(1, 2, 3) // 3 stray bytes

        val resources = ResourceLoader.parse(blob)
        assertEquals(1, resources.size)
    }

    @Test
    fun `zero-length resource data is valid`() {
        val blob = buildVmRes(listOf(Triple(5, 0, ByteArray(0))))
        val resources = ResourceLoader.parse(blob)

        assertEquals(1, resources.size)
        assertEquals(0, resources[0].data.size)
    }
}
