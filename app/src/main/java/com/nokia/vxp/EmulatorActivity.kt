package com.nokia.vxp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Hosts the running emulator: LCDRenderer/EmulatorSurfaceView in the top
 * frame, VirtualKeypadView in the bottom frame (see activity_emulator.xml).
 *
 * Intentionally a stub for now - it just reads the VXP file Uri handed to
 * it. Actual wiring (VxpLoader -> MemoryManager -> Unicorn -> EmulatorLoop
 * -> GraphicsEngine) happens once those modules exist.
 */
class EmulatorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VXP_URI = "extra_vxp_uri"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emulator)

        val vxpUriString = intent.getStringExtra(EXTRA_VXP_URI)
        requireNotNull(vxpUriString) { "EmulatorActivity requires EXTRA_VXP_URI" }

        // TODO once loader/ + emulator/ modules exist:
        //  1. VxpLoader.load(contentResolver, Uri.parse(vxpUriString))
        //  2. MemoryManager.mapModule(...)
        //  3. Emulator(scheduler, memory, graphics, audio, input).start()
    }
}
