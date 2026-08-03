package com.nokia.vxp.graphics

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenScalerTest {

    @Test
    fun `exact multiple scales cleanly with no letterboxing`() {
        val viewport = ScreenScaler.computeViewport(128, 160, 256, 320)
        assertEquals(2, viewport.scale)
        assertEquals(256, viewport.scaledWidth)
        assertEquals(320, viewport.scaledHeight)
        assertEquals(0, viewport.left)
        assertEquals(0, viewport.top)
    }

    @Test
    fun `non-matching aspect ratio picks the limiting dimension and centers`() {
        // View is much wider than the guest screen's aspect ratio - height should be the limiting factor.
        val viewport = ScreenScaler.computeViewport(128, 160, 1000, 320)
        assertEquals(2, viewport.scale) // limited by height: 320/160 = 2, width 1000/128 = 7 (not used)
        assertEquals(256, viewport.scaledWidth)
        assertEquals(320, viewport.scaledHeight)
        assertEquals((1000 - 256) / 2, viewport.left)
        assertEquals(0, viewport.top)
    }

    @Test
    fun `never scales below 1x even if the view is smaller than the guest screen`() {
        val viewport = ScreenScaler.computeViewport(128, 160, 50, 50)
        assertEquals(1, viewport.scale)
    }

    @Test
    fun `zero or negative dimensions return a safe empty viewport`() {
        val viewport = ScreenScaler.computeViewport(0, 160, 256, 320)
        assertEquals(0, viewport.scaledWidth)
        assertEquals(0, viewport.scaledHeight)
    }
}
