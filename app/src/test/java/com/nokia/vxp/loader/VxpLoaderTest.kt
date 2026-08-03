package com.nokia.vxp.loader

import com.nokia.vxp.utils.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class VxpLoaderTest {

    private fun fakeElfBytes(payload: ByteArray = byteArrayOf(1, 2, 3, 4)): ByteArray =
        Constants.ELF_MAGIC + payload

    @Test
    fun `raw ELF bytes pass through unchanged`() {
        val elf = fakeElfBytes()
        val result = VxpLoader.unwrapContainer(elf, LoaderLog())

        assertTrue(result is VxpLoader.UnwrapResult.Success)
        result as VxpLoader.UnwrapResult.Success
        assertTrue(elf.contentEquals(result.elfBytes))
    }

    @Test
    fun `zlib-wrapped ELF is inflated back to the original bytes`() {
        val elf = fakeElfBytes()
        val compressed = ByteArrayOutputStream().apply {
            DeflaterOutputStream(this, Deflater(Deflater.DEFAULT_COMPRESSION)).use { it.write(elf) }
        }.toByteArray()

        // Sanity check our test data actually looks zlib-wrapped before testing the unwrapper.
        assertEquals(Constants.ZLIB_MAGIC_BYTE, compressed[0])

        val result = VxpLoader.unwrapContainer(compressed, LoaderLog())

        assertTrue(result is VxpLoader.UnwrapResult.Success)
        result as VxpLoader.UnwrapResult.Success
        assertTrue(elf.contentEquals(result.elfBytes))
    }

    @Test
    fun `zip-wrapped ELF is found among other entries`() {
        val elf = fakeElfBytes()
        val zipBytes = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zip ->
                zip.putNextEntry(ZipEntry("icon.png"))
                zip.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("app.axf"))
                zip.write(elf)
                zip.closeEntry()
            }
        }.toByteArray()

        assertTrue(zipBytes.copyOfRange(0, 4).contentEquals(Constants.ZIP_MAGIC))

        val result = VxpLoader.unwrapContainer(zipBytes, LoaderLog())

        assertTrue(result is VxpLoader.UnwrapResult.Success)
        result as VxpLoader.UnwrapResult.Success
        assertTrue(elf.contentEquals(result.elfBytes))
    }

    @Test
    fun `zip with no ELF entry fails clearly`() {
        val zipBytes = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zip ->
                zip.putNextEntry(ZipEntry("readme.txt"))
                zip.write("hello".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        val result = VxpLoader.unwrapContainer(zipBytes, LoaderLog())
        assertTrue(result is VxpLoader.UnwrapResult.Failure)
    }

    @Test
    fun `completely unrecognized bytes fail with a clear reason`() {
        val garbage = byteArrayOf(0x00, 0x11, 0x22, 0x33)
        val result = VxpLoader.unwrapContainer(garbage, LoaderLog())

        assertTrue(result is VxpLoader.UnwrapResult.Failure)
        result as VxpLoader.UnwrapResult.Failure
        assertTrue(result.reason.isNotBlank())
    }

    @Test
    fun `startsWith correctly matches and rejects prefixes`() {
        assertTrue(VxpLoader.startsWith(byteArrayOf(1, 2, 3, 4), byteArrayOf(1, 2)))
        assertTrue(!VxpLoader.startsWith(byteArrayOf(1, 2, 3), byteArrayOf(9, 9)))
        assertTrue(!VxpLoader.startsWith(byteArrayOf(1), byteArrayOf(1, 2))) // prefix longer than input
    }
}
