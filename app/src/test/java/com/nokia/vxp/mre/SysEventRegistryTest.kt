package com.nokia.vxp.mre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SysEventRegistryTest {

    @Test
    fun `starts with no registered callbacks`() {
        val registry = SysEventRegistry()
        assertNull(registry.sysEventCallbackAddress)
        assertNull(registry.keyboardCallbackAddress)
        assertNull(registry.penCallbackAddress)
    }

    @Test
    fun `registerSysEvent records the address`() {
        val registry = SysEventRegistry()
        registry.registerSysEvent(0x1234L)
        assertEquals(0x1234L, registry.sysEventCallbackAddress)
    }

    @Test
    fun `registerKeyboard and registerPen are independent of sysevt and each other`() {
        val registry = SysEventRegistry()
        registry.registerKeyboard(0x1000L)
        registry.registerPen(0x2000L)

        assertEquals(0x1000L, registry.keyboardCallbackAddress)
        assertEquals(0x2000L, registry.penCallbackAddress)
        assertNull(registry.sysEventCallbackAddress)
    }

    @Test
    fun `re-registering overwrites the previous address`() {
        val registry = SysEventRegistry()
        registry.registerSysEvent(0x1000L)
        registry.registerSysEvent(0x2000L)
        assertEquals(0x2000L, registry.sysEventCallbackAddress)
    }
}
