package com.nokia.vxp.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NokiaKeyTest {

    @Test
    fun `fromGuestCode finds the matching key for digits`() {
        assertEquals(NokiaKey.NUM5, NokiaKey.fromGuestCode(53))
        assertEquals(NokiaKey.NUM0, NokiaKey.fromGuestCode(48))
    }

    @Test
    fun `fromGuestCode finds star and pound`() {
        assertEquals(NokiaKey.STAR, NokiaKey.fromGuestCode(42))
        assertEquals(NokiaKey.POUND, NokiaKey.fromGuestCode(35))
    }

    @Test
    fun `fromGuestCode finds navigation keys`() {
        assertEquals(NokiaKey.UP, NokiaKey.fromGuestCode(NokiaKey.UP.guestCode))
        assertEquals(NokiaKey.SELECT, NokiaKey.fromGuestCode(NokiaKey.SELECT.guestCode))
    }

    @Test
    fun `fromGuestCode returns null for an unknown code`() {
        assertNull(NokiaKey.fromGuestCode(999_999))
    }

    @Test
    fun `every key has a unique guest code`() {
        val codes = NokiaKey.values().map { it.guestCode }
        assertEquals(codes.size, codes.toSet().size)
    }
}
