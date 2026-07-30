package com.nokia.vxp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.nokia.vxp.nativecore.NativeBridge

/**
 * Launcher activity. Verifies the native core loads correctly, then
 * hands off to MainActivity. Kept deliberately simple at this stage -
 * once the emulator/ and memory/ modules exist, real init work
 * (mapping default memory regions, warming up Unicorn) can move here.
 */
class SplashActivity : AppCompatActivity() {

    private val minSplashMillis = 700L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val statusView = findViewById<TextView>(R.id.txtBootStatus)
        val startedAt = System.currentTimeMillis()

        val status = if (NativeBridge.isLoaded) {
            "native core: ${runCatching { NativeBridge.getNativeVersion() }.getOrDefault("loaded")}"
        } else {
            "native core failed to load: ${NativeBridge.lastLoadError ?: "unknown error"}"
        }
        statusView.text = status

        val elapsed = System.currentTimeMillis() - startedAt
        val remainingDelay = (minSplashMillis - elapsed).coerceAtLeast(0)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, remainingDelay)
    }
}
