package com.nokia.vxp

import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.nokia.vxp.emulator.Emulator
import com.nokia.vxp.emulator.EmulatorCallback
import com.nokia.vxp.graphics.EmulatorSurfaceView
import com.nokia.vxp.graphics.GraphicsEngine
import com.nokia.vxp.utils.Constants
import kotlin.concurrent.thread

/**
 * Hosts the running emulator: EmulatorSurfaceView in the top frame,
 * VirtualKeypadView in the bottom frame (see activity_emulator.xml -
 * the keypad frame is still an empty placeholder until input/ exists).
 *
 * The full pipeline is now real end-to-end: loader -> memory -> cpu ->
 * emulator all genuinely run. Graphics is real too, but there's no
 * mre/VmGraphics yet to translate guest draw calls into GraphicsEngine
 * calls - so onFrameRendered below draws a small self-contained test
 * pattern (a bouncing box + live FPS readout) instead of real game
 * output, just to prove the whole draw/present/scale/surface pipeline
 * actually works end to end.
 */
class EmulatorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VXP_URI = "extra_vxp_uri"
    }

    private lateinit var emulator: Emulator
    private lateinit var statusView: TextView
    private lateinit var surfaceView: EmulatorSurfaceView
    private lateinit var graphicsEngine: GraphicsEngine

    private var testPatternFrame = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emulator)

        statusView = findViewById(R.id.txtEmulatorStatus)

        graphicsEngine = GraphicsEngine(Constants.DEFAULT_SCREEN_WIDTH, Constants.DEFAULT_SCREEN_HEIGHT)
        surfaceView = EmulatorSurfaceView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            attachGraphicsEngine(graphicsEngine)
        }
        findViewById<FrameLayout>(R.id.frameEmulatorSurface).addView(surfaceView, 0)

        val vxpUriString = intent.getStringExtra(EXTRA_VXP_URI)
        requireNotNull(vxpUriString) { "EmulatorActivity requires EXTRA_VXP_URI" }
        val uri = Uri.parse(vxpUriString)

        emulator = Emulator()
        statusView.text = "Loading…"

        // Loader does file I/O + parsing; keep it off the main thread.
        thread(name = "VxpLoadThread") {
            emulator.load(contentResolver, uri, object : EmulatorCallback {
                override fun onLoaded(versionInfo: String) {
                    runOnUiThread { statusView.text = "$versionInfo loaded. Running…" }
                    emulator.start()
                }

                override fun onLoadFailed(reason: String) {
                    runOnUiThread { statusView.text = "Load failed: $reason" }
                }

                override fun onFrameRendered() {
                    drawTestPattern()
                    runOnUiThread {
                        statusView.text = "" // let the on-screen FPS readout speak for itself
                    }
                }

                override fun onFault(reason: String) {
                    runOnUiThread { statusView.text = "Emulator faulted: $reason" }
                }
            })
        }
    }

    /**
     * Stand-in for real guest-driven rendering: proves FrameBuffer,
     * DoubleBuffer, FontRenderer, ScreenScaler, and EmulatorSurfaceView
     * all work together, using a bouncing box + FPS digits. Delete this
     * once mre/VmGraphics exists and actually feeds LCDRenderer from
     * guest VRAM writes instead.
     */
    private fun drawTestPattern() {
        val w = Constants.DEFAULT_SCREEN_WIDTH
        val h = Constants.DEFAULT_SCREEN_HEIGHT
        val boxSize = 12
        val period = (w - boxSize) * 2

        testPatternFrame++
        val pos = testPatternFrame % period
        val bx = if (pos < w - boxSize) pos else period - pos

        graphicsEngine.clear(0xFF0F380F.toInt()) // classic LCD-green-ish background
        graphicsEngine.fillRect(bx, h / 2 - boxSize / 2, boxSize, boxSize, 0xFF9BBB0F.toInt())
        graphicsEngine.drawText(4, 4, "%.0f".format(surfaceView.currentFps()), 0xFF9BBB0F.toInt(), scale = 2)

        graphicsEngine.presentFrame()
        surfaceView.requestRender()
    }

    override fun onPause() {
        super.onPause()
        if (::emulator.isInitialized) emulator.pause()
    }

    override fun onResume() {
        super.onResume()
        if (::emulator.isInitialized) emulator.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::emulator.isInitialized) emulator.stop()
    }
}
