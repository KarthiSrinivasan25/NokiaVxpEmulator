package com.nokia.vxp.loader

import com.nokia.vxp.utils.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds a synthetic-but-structurally-valid VXP file in memory (using the
 * layout assumed in Constants.kt) and checks VxpParser round-trips it
 * correctly. This does NOT prove the layout matches real Nokia .vxp
 * files - it proves the parser is internally consistent, which is what
 * we can verify without a real sample file on hand.
 */
class VxpParserTest {

    private fun buildFakeVxpFile(
        code: ByteArray = byteArrayOf(0x01, 0x02, 0x03, 0x04),
        data: ByteArray = byteArrayOf(0x0A, 0x0B),
        resources: List<VxpResourceEntry> = listOf(VxpResourceEntry(id = 1, typeId = 2, offset = 0, size = 4))
    ): ByteArray {
        val headerSize = Constants.VXP_HEADER_SIZE
        val codeOffset = headerSize
        val dataOffset = codeOffset + code.size
        val resourceTableOffset = dataOffset + data.size
        val totalSize = resourceTableOffset + resources.size * 16

        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.position(Constants.OFFSET_MAGIC)
        buffer.put(Constants.VXP_MAGIC)

        buffer.position(Constants.OFFSET_VERSION)
        buffer.putShort((((1 shl 8) or 5) and 0xFFFF).toShort()) // v1.5

        buffer.position(Constants.OFFSET_FLAGS)
        buffer.putShort(0)

        buffer.position(Constants.OFFSET_CODE_OFFSET)
        buffer.putInt(codeOffset)
        buffer.position(Constants.OFFSET_CODE_SIZE)
        buffer.putInt(code.size)

        buffer.position(Constants.OFFSET_DATA_OFFSET)
        buffer.putInt(dataOffset)
        buffer.position(Constants.OFFSET_DATA_SIZE)
        buffer.putInt(data.size)

        buffer.position(Constants.OFFSET_RESOURCE_TABLE_OFFSET)
        buffer.putInt(resourceTableOffset)
        buffer.position(Constants.OFFSET_RESOURCE_COUNT)
        buffer.putInt(resources.size)

        buffer.position(codeOffset)
        buffer.put(code)

        buffer.position(dataOffset)
        buffer.put(data)

        buffer.position(resourceTableOffset)
        for (r in resources) {
            buffer.putInt(r.id)
            buffer.putInt(r.typeId)
            buffer.putInt(r.offset.toInt())
            buffer.putInt(r.size.toInt())
        }

        return buffer.array()
    }

    @Test
    fun `valid file parses successfully with matching segments`() {
        val code = byteArrayOf(1, 2, 3, 4, 5)
        val data = byteArrayOf(9, 8, 7)
        val bytes = buildFakeVxpFile(code = code, data = data)

        val result = VxpParser.parse("test.vxp", bytes)

        assertTrue(result is ParseResult.Success)
        result as ParseResult.Success
        assertEquals("1.5", result.file.header.version)
        assertTrue(code.contentEquals(result.file.code))
        assertTrue(data.contentEquals(result.file.data))
        assertEquals(1, result.file.resources.size)
        assertEquals(1, result.file.resources[0].id)
    }

    @Test
    fun `bad magic bytes are rejected`() {
        val bytes = buildFakeVxpFile()
        bytes[0] = 0x00 // corrupt the magic

        val result = VxpParser.parse("bad.vxp", bytes)

        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun `truncated file is rejected before header read`() {
        val bytes = ByteArray(4) // way smaller than VXP_HEADER_SIZE

        val result = VxpParser.parse("tiny.vxp", bytes)

        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun `code segment overrunning file size is rejected`() {
        val bytes = buildFakeVxpFile()
        // Corrupt code size to claim it's huge, pointing past EOF.
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(Constants.OFFSET_CODE_SIZE)
        buffer.putInt(bytes.size + 1000)

        val result = VxpParser.parse("overrun.vxp", bytes)

        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun `module mapper produces four regions with expected properties`() {
        val bytes = buildFakeVxpFile()
        val parsed = VxpParser.parse("map.vxp", bytes) as ParseResult.Success

        val layout = ModuleMapper.map(parsed.file)

        assertEquals(4, layout.regions.size)
        assertTrue(layout.codeRegion.executable)
        assertTrue(!layout.codeRegion.writable)
        assertTrue(layout.dataRegion.writable)
        assertTrue(!layout.dataRegion.executable)
        assertEquals(layout.codeRegion.baseAddress, layout.entryPoint)
    }
}
