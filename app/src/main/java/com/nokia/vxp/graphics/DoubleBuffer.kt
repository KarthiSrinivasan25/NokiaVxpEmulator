package com.nokia.vxp.graphics

/**
 * Two FrameBuffers with an explicit swap: drawing code writes into
 * back(), then calls swap() once a frame is complete, so the presenter
 * (EmulatorSurfaceView) always reads a fully-drawn front() buffer
 * instead of one that's still being drawn into.
 */
class DoubleBuffer(width: Int, height: Int) {
    private val bufferA = FrameBuffer(width, height)
    private val bufferB = FrameBuffer(width, height)

    @Volatile private var frontIsA = true

    fun back(): FrameBuffer = if (frontIsA) bufferB else bufferA
    fun front(): FrameBuffer = if (frontIsA) bufferA else bufferB

    @Synchronized
    fun swap() {
        frontIsA = !frontIsA
    }
}
