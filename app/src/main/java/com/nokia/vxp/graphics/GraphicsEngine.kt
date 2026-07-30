package com.nokia.vxp.graphics

/**
 * Top-level facade for guest-visible drawing operations. mre/VmGraphics
 * (once built) will call these in response to guest syscalls; until
 * then, it's also directly usable for our own debug/status overlays -
 * see EmulatorActivity's onFrameRendered handler.
 */
class GraphicsEngine(val width: Int, val height: Int) {

    private val doubleBuffer = DoubleBuffer(width, height)
    private val sprites = mutableListOf<Sprite>()
    var palette: Palette = Palette.nokiaLcdGreen()

    fun clear(argbColor: Int = 0xFF000000.toInt()) = doubleBuffer.back().clear(argbColor)
    fun setPixel(x: Int, y: Int, argbColor: Int) = doubleBuffer.back().setPixel(x, y, argbColor)
    fun fillRect(x: Int, y: Int, w: Int, h: Int, argbColor: Int) = doubleBuffer.back().fillRect(x, y, w, h, argbColor)
    fun drawLine(x0: Int, y0: Int, x1: Int, y1: Int, argbColor: Int) =
        doubleBuffer.back().drawLine(x0, y0, x1, y1, argbColor)

    fun drawText(x: Int, y: Int, text: String, argbColor: Int, scale: Int = 1) =
        FontRenderer.drawText(doubleBuffer.back(), x, y, text, argbColor, scale)

    fun addSprite(sprite: Sprite) { sprites += sprite }
    fun removeSprite(sprite: Sprite) { sprites -= sprite }
    fun clearSprites() = sprites.clear()

    /** Draws all registered sprites atop whatever's already in the back buffer, then presents (swaps) the frame. Call once per rendered frame. */
    fun presentFrame() {
        for (sprite in sprites) sprite.draw(doubleBuffer.back())
        doubleBuffer.swap()
    }

    fun currentFrame(): FrameBuffer = doubleBuffer.front()

    /**
     * Internal escape hatch for LCDRenderer (the guest-VRAM-driven video
     * driver path) to write directly into the back buffer, bypassing the
     * sprite-drawing pass in presentFrame(). Not part of the public API
     * surface other callers should reach for.
     */
    internal fun backBufferForDriver(): FrameBuffer = doubleBuffer.back()
}
