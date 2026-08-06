package com.nokia.vxp.mre

import org.junit.Assert.assertEquals
import org.junit.Test

class VmMessagesTest {

    @Test
    fun `VM_MSG_CREATE matches the confirmed real value`() {
        // Confirmed via two independent sources describing real MRE app
        // startup: message=4, param=0 - see VmMessages' doc comment.
        assertEquals(4, VmMessages.VM_MSG_CREATE)
    }
}
