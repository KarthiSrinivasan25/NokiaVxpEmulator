package com.nokia.vxp.mre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VmApiTableTest {

    private fun allSlots(): List<Long> = listOf(
        VmApiTable.MEMORY_MALLOC,
        VmApiTable.MEMORY_FREE,
        VmApiTable.MEMORY_CALLOC,
        VmApiTable.MEMORY_REALLOC,
        VmApiTable.TIMER_CREATE,
        VmApiTable.TIMER_DELETE,
        VmApiTable.GET_TICK_COUNT,
        VmApiTable.SYSTEM_LOG,
        VmApiTable.EXIT_APP,
        VmApiTable.REG_SYSEVT_CALLBACK,
        VmApiTable.REG_KEYBOARD_CALLBACK,
        VmApiTable.REG_PEN_CALLBACK,
        VmApiTable.SWITCH_POWER_SAVING_MODE,
        VmApiTable.GRAPHIC_GET_SCREEN_WIDTH,
        VmApiTable.GRAPHIC_GET_SCREEN_HEIGHT,
        VmApiTable.GRAPHIC_GET_CHARACTER_HEIGHT,
        VmApiTable.GRAPHIC_GET_STRING_WIDTH,
        VmApiTable.GRAPHIC_SETCOLOR,
        VmApiTable.GRAPHIC_FILL_RECT_EX,
        VmApiTable.GRAPHIC_TEXTOUT_TO_LAYER,
        VmApiTable.GRAPHIC_CREATE_LAYER,
        VmApiTable.GRAPHIC_CREATE_LAYER_CF,
        VmApiTable.GRAPHIC_CREATE_CANVAS_CF,
        VmApiTable.GRAPHIC_DELETE_LAYER,
        VmApiTable.GRAPHIC_GET_CANVAS_BUFFER,
        VmApiTable.GRAPHIC_RELEASE_CANVAS,
        VmApiTable.GRAPHIC_SET_CLIP,
        VmApiTable.GRAPHIC_SET_FONT,
        VmApiTable.GRAPHIC_FLUSH_LAYER,
        VmApiTable.KBD_SET_MODE,
        VmApiTable.INPUT_TEXT2,
        VmApiTable.FILE_OPEN,
        VmApiTable.FILE_CLOSE,
        VmApiTable.FILE_READ,
        VmApiTable.FILE_WRITE,
        VmApiTable.FILE_COMMIT,
        VmApiTable.FILE_GETFILESIZE,
        VmApiTable.FILE_GET_ATTRIBUTES,
        VmApiTable.FILE_MKDIR,
        VmApiTable.FIND_FIRST,
        VmApiTable.FIND_NEXT,
        VmApiTable.FIND_CLOSE,
        VmApiTable.MIDI_PLAY_BY_BYTES,
        VmApiTable.MIDI_STOP,
        VmApiTable.ASCII_TO_UCS2,
        VmApiTable.UCS2_TO_ASCII,
        VmApiTable.WSTRLEN,
        VmApiTable.VSPRINTF,
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

    @Test
    fun `slot count has not silently shrunk`() {
        // Sanity guard: if this regresses below the confirmed real API
        // surface size, something got dropped from VmApiTable by accident.
        assertTrue(allSlots().size >= 48)
    }
}
