package com.nokia.vxp.resource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ResourceLoaderTest {

    private val pngMagic = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
    private val iendType = "IEND".toByteArray(Charsets.US_ASCII)

    /** Builds a minimal-but-structurally-real PNG: signature + one IDAT-ish chunk + a real IEND chunk. */
    private fun buildFakePng(extraChunkData: ByteArray = byteArrayOf(1, 2, 3, 4)): ByteArray {
        val buffer = ByteBuffer.allocate(8 + (8 + extraChunkData.size + 4) + (8 + 0 + 4)).order(ByteOrder.BIG_ENDIAN)
        buffer.put(pngMagic)

        // One arbitrary chunk (doesn't need a real CRC for our parser - we don't validate CRCs)
        buffer.putInt(extraChunkData.size)
        buffer.put("IDAT".toByteArray(Charsets.US_ASCII))
        buffer.put(extraChunkData)
        buffer.putInt(0) // fake crc

        // Real IEND chunk: length=0, type="IEND", no data, fake crc
        buffer.putInt(0)
        buffer.put(iendType)
        buffer.putInt(0)

        return buffer.array()
    }

    @Test
    fun `null or empty input produces no resources`() {
        assertTrue(ResourceLoader.parse(null).isEmpty())
        assertTrue(ResourceLoader.parse(ByteArray(0)).isEmpty())
    }

    @Test
    fun `carves a single PNG embedded with no surrounding data`() {
        val png = buildFakePng()
        val resources = ResourceLoader.parse(png)

        assertEquals(1, resources.size)
        assertEquals(ResourceType.IMAGE_PNG, resources[0].detectedType)
        assertTrue(png.contentEquals(resources[0].data))
    }

    @Test
    fun `carves a PNG with junk bytes before and after it`() {
        val prefix = "AppLogo.img\u0000somejunkheaderbytes".toByteArray()
        val png = buildFakePng()
        val suffix = "trailingmetadatabytes".toByteArray()
        val blob = prefix + png + suffix

        val resources = ResourceLoader.parse(blob)

        assertEquals(1, resources.size)
        assertEquals(ResourceType.IMAGE_PNG, resources[0].detectedType)
        assertTrue(png.contentEquals(resources[0].data))
    }

    @Test
    fun `stops exactly at IEND rather than swallowing trailing bytes`() {
        val png = buildFakePng()
        val trailingJunk = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55)
        val blob = png + trailingJunk

        val resources = ResourceLoader.parse(blob)

        assertEquals(png.size, resources[0].data.size)
        assertTrue(!resources[0].data.contentEquals(blob)) // must NOT have swallowed the trailing junk
    }

    @Test
    fun `carves two separate PNGs from the same blob`() {
        val png1 = buildFakePng(byteArrayOf(1))
        val png2 = buildFakePng(byteArrayOf(2, 2))
        val blob = "name1.img".toByteArray() + png1 + "name2.img".toByteArray() + png2

        val resources = ResourceLoader.parse(blob)

        assertEquals(2, resources.size)
        assertTrue(png1.contentEquals(resources[0].data))
        assertTrue(png2.contentEquals(resources[1].data))
    }

    @Test
    fun `unrecognized binary data produces no carved resources`() {
        val blob = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        assertTrue(ResourceLoader.parse(blob).isEmpty())
    }

    @Test
    fun `resource ids are assigned sequentially in discovery order`() {
        val blob = buildFakePng(byteArrayOf(1)) + buildFakePng(byteArrayOf(2))
        val resources = ResourceLoader.parse(blob)

        assertEquals(0, resources[0].id)
        assertEquals(1, resources[1].id)
    }

    // --- Regression test using the REAL structure observed in gtrxAC/peanut.vxp -----
    // (MIT licensed .vxp sample fetched from its GitHub releases and inspected by hand;
    // this reproduces just the shape - a filename string, then real PNG bytes, then a
    // trailing marker string - not the actual copyrighted image bytes.)
    @Test
    fun `carves a PNG from a realistic vm_res-shaped blob (filename plus markers plus image)`() {
        val header = "AppLogo.img\u0000".toByteArray() +
            byteArrayOf(0x38, 0x68, 0x01, 0x00, 0xE0.toByte(), 0x07, 0x00, 0x00) +
            "mre-2.0\u0000".toByteArray() +
            "VREAPPLOGO09BVREPNG".toByteArray()
        val png = buildFakePng()
        val footer = "ZMENG".toByteArray() + ByteArray(20) { 0x24 } // simulated language-marker + filler bytes

        val blob = header + png + footer
        val resources = ResourceLoader.parse(blob)

        assertEquals(1, resources.size)
        assertEquals(ResourceType.IMAGE_PNG, resources[0].detectedType)
        assertTrue(png.contentEquals(resources[0].data))
    }
}
