package com.nokia.vxp.mre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VmApiTableTest {

    private fun allSlots(): List<Long> = listOf(
        VmApiTable.GRAPHIC_GET_SCREEN_WIDTH,
        VmApiTable.GRAPHIC_GET_SCREEN_HEIGHT,
        VmApiTable.GRAPHIC_SET_PIXEL,
        VmApiTable.GRAPHIC_FILL_RECT,
        VmApiTable.GRAPHIC_DRAW_TEXT,
        VmApiTable.INPUT_GET_KEY_STATE,
        VmApiTable.TIMER_CREATE,
        VmApiTable.TIMER_DELETE,
        VmApiTable.MEMORY_ALLOC,
        VmApiTable.MEMORY_FREE,
        VmApiTable.SYSTEM_LOG,
        VmApiTable.AUDIO_PLAY_FREQUENCY,
        VmApiTable.AUDIO_STOP,
        VmApiTable.FILE_OPEN,
        VmApiTable.NETWORK_CONNECT
    )

    @Test
    fun `every placeholder address is unique`() {
        val slots = allSlots()
        assertEquals(slots.size, slots.toSet().size)
    }

    @Test
    fun `every placeholder address is in the recognizable 0xFEED range`() {
        for (address in allSlots()) {
            assertTrue(
                "0x${address.toString(16)} is not in the 0xFEED0000-0xFEEDFFFF placeholder range",
                address in 0xFEED0000L..0xFEEDFFFFL
            )
        }
    }

    @Test
    fun `addresses are spaced far enough apart to never be confused with real code`() {
        val sorted = allSlots().sorted()
        for (i in 1 until sorted.size) {
            assertTrue(sorted[i] - sorted[i - 1] >= 0x10L)
        }
    }
}
