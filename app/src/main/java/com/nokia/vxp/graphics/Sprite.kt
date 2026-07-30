package com.nokia.vxp.graphics

/**
 * A positioned bitmap ready to be blitted onto a FrameBuffer each frame.
 * Pixel data lives in a BitmapBuffer; Sprite just tracks where/how to
 * draw it (position, visibility, flip) - GraphicsEngine owns the list of
 * active sprites and draws them during presentFrame().
 */
class Sprite(
    var bitmap: BitmapBuffer,
    var x: Int = 0,
    var y: Int = 0,
    var visible: Boolean = true,
    var flipHorizontal: Boolean = false,
    var flipVertical: Boolean = false
) {
    fun draw(target: FrameBuffer) {
        if (!visible) return
        bitmap.blit(target, x, y, flipHorizontal, flipVertical)
    }
}
