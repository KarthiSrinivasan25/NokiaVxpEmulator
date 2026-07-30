package com.nokia.vxp.graphics

data class ScaledViewport(
    val left: Int,
    val top: Int,
    val scaledWidth: Int,
    val scaledHeight: Int,
    val scale: Int
)

/**
 * Maps the guest's fixed logical resolution onto whatever size the
 * Android view actually is. Uses integer nearest-neighbor scaling only
 * (never fractional) so pixel art doesn't blur - centers the result and
 * letterboxes any leftover space, like a phone screen bezel.
 */
object ScreenScaler {
    fun computeViewport(guestWidth: Int, guestHeight: Int, viewWidth: Int, viewHeight: Int): ScaledViewport {
        if (guestWidth <= 0 || guestHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            return ScaledViewport(0, 0, 0, 0, 1)
        }
        val scale = maxOf(1, minOf(viewWidth / guestWidth, viewHeight / guestHeight))
        val scaledWidth = guestWidth * scale
        val scaledHeight = guestHeight * scale
        val left = (viewWidth - scaledWidth) / 2
        val top = (viewHeight - scaledHeight) / 2
        return ScaledViewport(left, top, scaledWidth, scaledHeight, scale)
    }
}
