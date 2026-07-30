package com.nokia.vxp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.nokia.vxp.loader.LoadResult
import com.nokia.vxp.loader.VxpLoader
import com.nokia.vxp.nativecore.NativeBridge

/**
 * Home screen. At this scaffolding stage it just:
 *  - shows native core status
 *  - lets the user pick a .vxp file (loader/ module will parse it later)
 *  - has a (currently disabled) button to jump into EmulatorActivity
 *    once a VXP file has been selected and validated.
 */
class MainActivity : AppCompatActivity() {

    private var selectedVxpUri: Uri? = null

    private val pickVxpFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Loader work touches disk I/O; keep it off the main thread in
            // a real build (Coroutines/WorkManager). Left synchronous here
            // since this is still scaffold-stage and files are small.
            when (val result = VxpLoader.load(contentResolver, uri)) {
                is LoadResult.Success -> {
                    selectedVxpUri = uri
                    Toast.makeText(
                        this,
                        "Loaded OK: v${result.vxpFile.header.version}, " +
                            "${result.vxpFile.resources.size} resources",
                        Toast.LENGTH_LONG
                    ).show()
                    findViewById<Button>(R.id.btnLaunchEmulator).isEnabled = true
                }
                is LoadResult.Failure -> {
                    selectedVxpUri = null
                    Toast.makeText(this, "Rejected: ${result.reason}", Toast.LENGTH_LONG).show()
                    findViewById<Button>(R.id.btnLaunchEmulator).isEnabled = false
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<TextView>(R.id.txtNativeVersion).text = if (NativeBridge.isLoaded) {
            runCatching { NativeBridge.getNativeVersion() }
                .getOrDefault("native core loaded, version call failed")
        } else {
            "native core NOT loaded: ${NativeBridge.lastLoadError ?: "unknown error"}"
        }

        findViewById<Button>(R.id.btnOpenGame).setOnClickListener {
            // VXP files don't have a registered MIME type on Android, so we
            // accept any file and rely on loader/VxpValidator to reject
            // anything that isn't actually a valid VXP module.
            pickVxpFile.launch(arrayOf("*/*"))
        }

        findViewById<Button>(R.id.btnLaunchEmulator).setOnClickListener {
            val uri = selectedVxpUri ?: return@setOnClickListener
            startActivity(
                Intent(this, EmulatorActivity::class.java).apply {
                    putExtra(EmulatorActivity.EXTRA_VXP_URI, uri.toString())
                }
            )
        }
    }
}
