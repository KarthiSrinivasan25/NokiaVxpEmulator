package com.nokia.vxp.cpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlagsTest {

    @Test
    fun `all flags clear on zero cpsr`() {
        val cpsr = 0L
        assertFalse(Flags.negative(cpsr))
        assertFalse(Flags.zero(cpsr))
        assertFalse(Flags.carry(cpsr))
        assertFalse(Flags.overflow(cpsr))
        assertFalse(Flags.thumbMode(cpsr))
    }

    @Test
    fun `setting N Z C V bits is detected correctly`() {
        var cpsr = 0L
        cpsr = Flags.withBit(cpsr, Flags.BIT_N, true)
        cpsr = Flags.withBit(cpsr, Flags.BIT_Z, true)
        cpsr = Flags.withBit(cpsr, Flags.BIT_C, true)
        cpsr = Flags.withBit(cpsr, Flags.BIT_V, true)

        assertTrue(Flags.negative(cpsr))
        assertTrue(Flags.zero(cpsr))
        assertTrue(Flags.carry(cpsr))
        assertTrue(Flags.overflow(cpsr))
    }

    @Test
    fun `thumb bit toggling does not disturb NZCV`() {
        var cpsr = Flags.withBit(0L, Flags.BIT_Z, true)
        cpsr = Flags.withBit(cpsr, Flags.BIT_T, true)

        assertTrue(Flags.thumbMode(cpsr))
        assertTrue(Flags.zero(cpsr))
        assertFalse(Flags.negative(cpsr))
    }

    @Test
    fun `clearing a bit that was set works`() {
        var cpsr = Flags.withBit(0L, Flags.BIT_N, true)
        assertTrue(Flags.negative(cpsr))

        cpsr = Flags.withBit(cpsr, Flags.BIT_N, false)
        assertFalse(Flags.negative(cpsr))
    }

    @Test
    fun `describe reflects current mode and flags`() {
        var cpsr = Flags.withBit(0L, Flags.BIT_Z, true)
        cpsr = Flags.withBit(cpsr, Flags.BIT_T, true)

        val desc = Flags.describe(cpsr)
        assertEquals("nZcv THUMB", desc)
    }
}
