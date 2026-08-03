package com.nokia.vxp.graphics

import org.junit.Assert.assertEquals
import org.junit.Test

class Color565Test {

    @Test
    fun `pure white round-trips to full 8-bit white`() {
        val white565 = 0xFFFF // R=0x1F, G=0x3F, B=0x1F
        val argb = Color565.toArgb8888(white565)
        assertEquals(0xFFFFFFFF.toInt(), argb)
    }

    @Test
    fun `pure black stays black`() {
        val argb = Color565.toArgb8888(0x0000)
        assertEquals(0xFF000000.toInt(), argb)
    }

    @Test
    fun `pure red channel isolated correctly`() {
        val red565 = 0b11111_000000_00000 // R=0x1F, G=0, B=0
        val argb = Color565.toArgb8888(red565)
        assertEquals(0xFF, (argb ushr 16) and 0xFF) // R channel = 0xFF (bit-replicated)
        assertEquals(0, (argb ushr 8) and 0xFF)
        assertEquals(0, argb and 0xFF)
    }

    @Test
    fun `alpha channel is always fully opaque`() {
        val argb = Color565.toArgb8888(0x1234)
        assertEquals(0xFF, (argb ushr 24) and 0xFF)
    }

    @Test
    fun `fromArgb8888 is the approximate inverse of toArgb8888`() {
        val original565 = 0b10101_010101_10101
        val argb = Color565.toArgb8888(original565)
        val roundTripped = Color565.fromArgb8888(argb)
        // Not bit-exact (565->888 is lossy in the other direction due to
        // bit-replication), but should match after reducing 8-bit back to 565.
        assertEquals(original565, roundTripped)
    }
}
