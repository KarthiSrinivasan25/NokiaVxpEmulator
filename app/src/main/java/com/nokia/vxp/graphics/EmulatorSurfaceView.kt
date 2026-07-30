package com.nokia.vxp.graphics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Presents GraphicsEngine's current front buffer onto the screen,
 * nearest-neighbor scaled and centered via ScreenScaler. Drawing is
 * triggered externally (EmulatorActivity calls requestRender() from
 * Emulator's onFrameRendered callback) rather than this view owning its
 * own render thread - frames are already paced by EmulatorLoop's
 * FrameLimiter, so a second independent render loop here would just add
 * complexity for no benefit.
 */
class EmulatorSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private var graphicsEngine: GraphicsEngine? = null
    private var backingBitmap: Bitmap? = null
    private val paint = Paint().apply { isFilterBitmap = false } // nearest-neighbor, not smoothed
    private val fpsCounter = FPSCounter()

    @Volatile private var surfaceReady = false

    init {
        holder.addCallback(this)
    }

    fun attachGraphicsEngine(engine: GraphicsEngine) {
        graphicsEngine = engine
        backingBitmap = Bitmap.createBitmap(engine.width, engine.height, Bitmap.Config.ARGB_8888)
    }

    /** Call once per frame after GraphicsEngine.presentFrame(). Safe to call from any thread. */
    fun requestRender() {
        if (!surfaceReady) return
        val engine = graphicsEngine ?: return
        val bitmap = backingBitmap ?: return

        val frame = engine.currentFrame()
        bitmap.setPixels(frame.pixels, 0, frame.width, 0, 0, frame.width, frame.height)

        val canvas = holder.lockCanvas() ?: return
        try {
            canvas.drawColor(Color.BLACK)
            val viewport = ScreenScaler.computeViewport(frame.width, frame.height, canvas.width, canvas.height)
            val dstRect = Rect(
                viewport.left, viewport.top,
                viewport.left + viewport.scaledWidth, viewport.top + viewport.scaledHeight
            )
            canvas.drawBitmap(bitmap, null, dstRect, paint)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
        fpsCounter.recordFrame()
    }

    fun currentFps(): Double = fpsCounter.currentFps()

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // No-op: requestRender() reads canvas.width/height fresh on every call.
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
    }
}
