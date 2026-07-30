package com.nokia.vxp.graphics

/**
 * The "video driver" side of graphics: translates raw guest framebuffer
 * writes (as read from mapped VRAM by mre/VmGraphics, once that module
 * exists) into direct pixel writes. Kept separate from GraphicsEngine's
 * own drawing primitives (fillRect/drawText/sprites) so guest-driven
 * whole-buffer blits and our own debug-overlay drawing don't have to
 * share a call path that assumes one or the other.
 */
class LCDRenderer(private val engine: GraphicsEngine) {

    /** Guest wrote an entire RGB565 framebuffer's worth of data - replace the back buffer wholesale. */
    fun blitFullFrame(rgb565Data: ShortArray) {
        engine.backBufferForDriver().loadFromRgb565(rgb565Data)
    }

    /** Guest wrote a single pixel (e.g. a direct VRAM write trapped by a memory hook). */
    fun writePixelRgb565(x: Int, y: Int, rgb565: Int) {
        engine.backBufferForDriver().setPixelRgb565(x, y, rgb565)
    }

    /** Guest wrote a palette-indexed pixel; resolves through the engine's currently active palette. */
    fun writePixelIndexed(x: Int, y: Int, paletteIndex: Int) {
        val color = engine.palette.colorAt(paletteIndex)
        engine.backBufferForDriver().setPixel(x, y, color)
    }
}
