package com.nokia.vxp.emulator

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.nokia.vxp.audio.AudioManager
import com.nokia.vxp.graphics.GraphicsEngine
import com.nokia.vxp.input.InputManager
import com.nokia.vxp.loader.LoadResult
import com.nokia.vxp.loader.VxpLoader
import com.nokia.vxp.mre.VmAudio
import com.nokia.vxp.mre.VmDispatcher
import com.nokia.vxp.mre.VmFile
import com.nokia.vxp.mre.VmGraphics
import com.nokia.vxp.mre.VmInput
import com.nokia.vxp.mre.VmMemory
import com.nokia.vxp.mre.VmNetwork
import com.nokia.vxp.mre.VmSystem
import com.nokia.vxp.mre.VmTimer
import com.nokia.vxp.resource.ResourceManager
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
 * memory/, cpu/, mre/, and this module's own EmulatorLoop - callers
 * don't need to touch any of those directly.
 *
 * [graphicsEngine] and [inputManager] are optional: pass them in if the
 * caller has a display/keypad to wire up (EmulatorActivity does); omit
 * them for a headless session (e.g. a future test harness) and the
 * corresponding mre.VmGraphics/VmInput handlers simply won't be
 * registered, so guest calls to those APIs will fault instead of being
 * silently ignored - visible in logs rather than hidden. [context] is
 * similarly optional and only needed for real audio.AudioManager
 * playback (AudioTrack/MediaPlayer both need a Context); without it,
 * mre.VmAudio falls back to logging only.
 */
class Emulator(
    private val config: EmulatorConfig = EmulatorConfig(),
    private val graphicsEngine: GraphicsEngine? = null,
    private val inputManager: InputManager? = null,
    private val context: Context? = null
) {

    private var runtime: Runtime? = null
    private var loop: EmulatorLoop? = null
    private var resourceManager: ResourceManager? = null
    private var audioManager: AudioManager? = null

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

                resourceManager = ResourceManager.from(result.vxpFile.resourceSectionData)
                Logger.i(TAG, resourceManager!!.summary())

                val vmDispatcher = VmDispatcher()
                VmSystem.registerHandlers(vmDispatcher)
                VmMemory.registerHandlers(vmDispatcher, builtRuntime.memoryManager)
                VmTimer.registerHandlers(vmDispatcher, timerManager)
                audioManager = context?.let { AudioManager(it) }
                VmAudio.registerHandlers(vmDispatcher, audioManager)
                VmFile.registerHandlers(vmDispatcher)
                VmNetwork.registerHandlers(vmDispatcher)
                graphicsEngine?.let { VmGraphics.registerHandlers(vmDispatcher, it) }
                inputManager?.let { VmInput.registerHandlers(vmDispatcher, it) }

                val scheduler = Scheduler(config)
                val frameLimiter = FrameLimiter(config.targetFps)

                loop = EmulatorLoop(
                    executor = builtRuntime.executor,
                    memoryManager = builtRuntime.memoryManager,
                    eventQueue = eventQueue,
                    timerManager = timerManager,
                    scheduler = scheduler,
                    frameLimiter = frameLimiter,
                    vmDispatcher = vmDispatcher,
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
        resourceManager = null
        audioManager?.stopAll()
        audioManager = null
    }

    /** Exposes this session's parsed .vm_res resources for debug tooling or a future UI. Null before a successful load. */
    fun currentResources(): ResourceManager? = resourceManager

    fun isRunning(): Boolean = loop?.isRunning() ?: false
    fun currentFps(): Double = loop?.currentFps() ?: 0.0

    /** Exposes the current session's Runtime (memory/cpu/executor) for debug tooling. Null before a successful load. */
    fun currentRuntime(): Runtime? = runtime
}
