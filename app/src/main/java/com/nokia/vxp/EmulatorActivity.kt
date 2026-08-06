package com.nokia.vxp

import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.nokia.vxp.debug.DebugOverlay
import com.nokia.vxp.debug.FPSMonitor
import com.nokia.vxp.debug.MemoryViewer
import com.nokia.vxp.emulator.Emulator
import com.nokia.vxp.emulator.EmulatorCallback
import com.nokia.vxp.graphics.EmulatorSurfaceView
import com.nokia.vxp.graphics.GraphicsEngine
import com.nokia.vxp.input.InputManager
import com.nokia.vxp.input.NokiaKey
import com.nokia.vxp.input.VirtualKeypadView
import com.nokia.vxp.utils.Constants
import com.nokia.vxp.utils.Logger
import kotlin.concurrent.thread

/**
 * Hosts the running emulator: EmulatorSurfaceView in the top frame,
 * VirtualKeypadView in the bottom frame (see activity_emulator.xml).
 *
 * The full pipeline is now real end-to-end: loader -> memory -> cpu ->
 * mre -> emulator -> graphics -> input all genuinely run, including
 * mre/VmDispatcher's guest-call trap. Since we don't know real MRE OS
 * API addresses (see mre/VmDispatcher's doc comment), a loaded game's
 * actual vm_graphic_*vm_reg_keyboard_callback calls will still fault rather
 * than being handled - so onFrameRendered below still draws a
 * self-contained test pattern rather than real game output, but it DOES
 * respond to the on-screen keypad (UP/DOWN nudge the box, SELECT
 * recenters it), proving the whole VirtualKeypadView -> TouchMapper ->
 * InputManager -> Emulator chain works end to end.
 */
class EmulatorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VXP_URI = "extra_vxp_uri"
    }

    private lateinit var emulator: Emulator
    private lateinit var statusView: TextView
    private lateinit var surfaceView: EmulatorSurfaceView
    private lateinit var graphicsEngine: GraphicsEngine
    private lateinit var inputManager: InputManager
    private lateinit var keypadView: VirtualKeypadView

    private var testPatternFrame = 0
    private var verticalNudge = 0
    private val fpsMonitor = FPSMonitor(targetFps = 30)

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

        // inputManager must exist before constructing Emulator, since
        // Emulator registers mre/VmInput's handlers against it at load time.
        inputManager = InputManager { event ->
            // Forward every key transition to the guest-facing event
            // queue - real plumbing now: mre/VmInput's handler (if
            // registered) reads pressed state straight from this same
            // InputManager instance if a guest vm_reg_keyboard_callback
            // registration is ever actually delivered an event, so this is
            // already "live" wiring, not just scaffolding waiting on a
            // future module.
            if (event.down) emulator.sendKeyDown(event.key.guestCode) else emulator.sendKeyUp(event.key.guestCode)

            // Also drive the local test pattern directly, so pressing
            // keys visibly does something right now regardless of
            // whether the loaded game's own vm_reg_keyboard_callback registration lands
            // on a real (currently unknown) address.
            if (event.down) {
                when (event.key) {
                    NokiaKey.UP -> verticalNudge = (verticalNudge - 4).coerceAtLeast(-40)
                    NokiaKey.DOWN -> verticalNudge = (verticalNudge + 4).coerceAtMost(40)
                    NokiaKey.SELECT -> verticalNudge = 0
                    else -> {}
                }
            }
        }
        keypadView = findViewById<FrameLayout>(R.id.frameVirtualKeypad).let { container ->
            VirtualKeypadView(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                inputManager = this@EmulatorActivity.inputManager
                container.addView(this)
            }
        }

        emulator = Emulator(graphicsEngine = graphicsEngine, inputManager = inputManager, context = this)

        statusView.text = "Loading…"

        // Loader does file I/O + parsing; keep it off the main thread.
        thread(name = "VxpLoadThread") {
            emulator.load(contentResolver, uri, object : EmulatorCallback {
                override fun onLoaded(versionInfo: String) {
                    runOnUiThread { statusView.text = "$versionInfo loaded. Running…" }
                    emulator.currentRuntime()?.let { runtime ->
                        Logger.i("MemoryLayout", "Mapped regions for this session:")
                        for (line in MemoryViewer.listRegions(runtime.memoryManager)) {
                            Logger.i("MemoryLayout", line)
                        }
                    }
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

    val detail = emulator.currentRuntime()?.let { runtime ->

        val pc = runtime.cpuState.getPc()
        val sp = runtime.cpuState.getSp()
        val lr = runtime.cpuState.getLr()

        """
        Emulator faulted:

        $reason

        PC : 0x${pc.toString(16)}
        SP : 0x${sp.toString(16)}
        LR : 0x${lr.toString(16)}
        """.trimIndent()

    } ?: reason


    runOnUiThread {
        statusView.text = detail
    }
}
            })
        }
    }

    /**
     * Stand-in for real guest-driven rendering: proves FrameBuffer,
     * DoubleBuffer, FontRenderer, ScreenScaler, EmulatorSurfaceView, and
     * (via verticalNudge) the input pipeline all work together. Delete
     * this once real MRE OS API addresses are known and mre/VmGraphics's
     * handlers are actually being hit by a loaded game's real calls.
     */
    private fun drawTestPattern() {
        val w = Constants.DEFAULT_SCREEN_WIDTH
        val h = Constants.DEFAULT_SCREEN_HEIGHT
        val boxSize = 12
        val period = (w - boxSize) * 2

        testPatternFrame++
        val pos = testPatternFrame % period
        val bx = if (pos < w - boxSize) pos else period - pos
        val by = (h / 2 - boxSize / 2 + verticalNudge).coerceIn(0, h - boxSize)

        graphicsEngine.clear(0xFF0F380F.toInt()) // classic LCD-green-ish background
        graphicsEngine.fillRect(bx, by, boxSize, boxSize, 0xFF9BBB0F.toInt())

        fpsMonitor.recordFrame()
        DebugOverlay.draw(graphicsEngine, fpsMonitor, x = 4, y = 4, scale = 2)

        graphicsEngine.presentFrame()
        surfaceView.requestRender()
    }

    override fun onPause() {
        super.onPause()
        if (::emulator.isInitialized) emulator.pause()
        if (::inputManager.isInitialized) inputManager.releaseAll()
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
