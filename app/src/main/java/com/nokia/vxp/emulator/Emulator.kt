package com.nokia.vxp.emulator

import android.content.ContentResolver
import android.net.Uri
import com.nokia.vxp.loader.LoadResult
import com.nokia.vxp.loader.VxpLoader
import com.nokia.vxp.utils.Logger

private const val TAG = "Emulator"

interface EmulatorCallback {
    fun onLoaded(versionInfo: String)
    fun onLoadFailed(reason: String)
    fun onFrameRendered() {}
    fun onFault(reason: String) {}
}

/**
 * Top-level façade that EmulatorActivity talks to: load a VXP file, then
 * start/pause/resume/stop the running session. Wires together loader/,
 * memory/, cpu/, and this module's own EmulatorLoop - callers don't need
 * to touch any of those directly.
 */
class Emulator(private val config: EmulatorConfig = EmulatorConfig()) {

    private var runtime: Runtime? = null
    private var loop: EmulatorLoop? = null

    val eventQueue = EventQueue()
    private val timerManager = TimerManager()

    /** Loads and prepares (but does not yet start) a session. Safe to call from a background thread. */
    fun load(contentResolver: ContentResolver, uri: Uri, callback: EmulatorCallback) {
        when (val result = VxpLoader.load(contentResolver, uri)) {
            is LoadResult.Failure -> {
                Logger.e(TAG, "Load failed: ${result.reason}")
                callback.onLoadFailed(result.reason)
            }
            is LoadResult.Success -> {
                val builtRuntime = Runtime.from(result)
                if (builtRuntime == null) {
                    callback.onLoadFailed("Memory setup failed (see logcat tag 'MemoryManager')")
                    return
                }
                runtime = builtRuntime

                val scheduler = Scheduler(config)
                val frameLimiter = FrameLimiter(config.targetFps)

                loop = EmulatorLoop(
                    executor = builtRuntime.executor,
                    eventQueue = eventQueue,
                    timerManager = timerManager,
                    scheduler = scheduler,
                    frameLimiter = frameLimiter,
                    onFrameRendered = { callback.onFrameRendered() },
                    onFault = { reason -> callback.onFault(reason) }
                )

                callback.onLoaded("VXP (ELF, entry=0x${result.memoryLayout.entryPoint.toString(16)})")
            }
        }
    }

    fun start() = loop?.start()
    fun pause() = eventQueue.post(EmulatorEvent.Pause)
    fun resume() = eventQueue.post(EmulatorEvent.Resume)
    fun sendKeyDown(keyCode: Int) = eventQueue.post(EmulatorEvent.KeyDown(keyCode))
    fun sendKeyUp(keyCode: Int) = eventQueue.post(EmulatorEvent.KeyUp(keyCode))

    fun stop() {
        loop?.stop()
        runtime?.teardown()
        runtime = null
        loop = null
    }

    fun isRunning(): Boolean = loop?.isRunning() ?: false
    fun currentFps(): Double = loop?.currentFps() ?: 0.0
}
