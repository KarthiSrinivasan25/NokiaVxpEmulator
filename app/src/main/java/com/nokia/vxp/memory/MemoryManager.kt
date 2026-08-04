package com.nokia.vxp.memory

import com.nokia.vxp.loader.MappedRegion
import com.nokia.vxp.loader.ModuleMemoryLayout
import com.nokia.vxp.utils.Logger

private const val TAG = "MemoryManager"

/**
 * Owns one Unicorn engine instance's memory for one running emulator
 * session: creates the native engine, maps every region from a
 * loader.ModuleMemoryLayout, and exposes read/write/heap/stack to the
 * rest of the app (mre/VmMemory later, debug/MemoryViewer, cpu/ for
 * register-adjacent memory ops).
 *
 * One instance per running emulator - emulator/Emulator will own its
 * lifecycle (setup() at load time, teardown() on stop).
 */
class MemoryManager : GuestMemoryReader {

    private var engineHandle: Long = 0
    private val map = MemoryMap()
    private var heapImpl: Heap? = null
    private var stackImpl: Stack? = null

    val isEngineReady: Boolean get() = engineHandle != 0L

    val heap: Heap get() = heapImpl ?: error("Heap not initialized - call setup() first")
    val stack: Stack get() = stackImpl ?: error("Stack not initialized - call setup() first")

    /** Creates the native Unicorn engine and maps all regions from [layout]. Returns false on any failure. */
    fun setup(layout: ModuleMemoryLayout): Boolean {
        teardown() // guard against setup() being called twice without a teardown()

        val handle = nativeCreateEngine()
        if (handle == 0L) {
            Logger.e(TAG, "Failed to create native Unicorn engine")
            return false
        }
        engineHandle = handle

        for (region in layout.regions) {
            val kotlinRegion = toMemoryRegion(region)
            val ok = nativeMapRegion(
                engineHandle,
                region.baseAddress,
                region.size,
                kotlinRegion.toUnicornPerms(),
                region.initialContent
            )
            if (!ok) {
                Logger.e(
                    TAG,
                    "Failed to map region '${region.name}' at 0x${region.baseAddress.toString(16)} " +
                        "(size=${region.size})"
                )
                teardown()
                return false
            }
            map.add(kotlinRegion)
            Logger.i(
                TAG,
                "Mapped '${region.name}' base=0x${region.baseAddress.toString(16)} size=${region.size}"
            )
        }

        heapImpl = Heap(this, layout.heapRegion.baseAddress, layout.heapRegion.size)
        stackImpl = Stack(layout.stackRegion.baseAddress, layout.stackRegion.size)

        Logger.i(TAG, "Memory setup complete: ${layout.regions.size} regions mapped")
        return true
    }

    fun teardown() {
        if (engineHandle != 0L) {
            nativeDestroyEngine(engineHandle)
            engineHandle = 0L
        }
        map.clear()
        heapImpl = null
        stackImpl = null
    }

    override fun read(address: Long, length: Int): ByteArray? {
        if (!isEngineReady) {
            Logger.w(TAG, "read() called with no engine set up")
            return null
        }
        return nativeReadBytes(engineHandle, address, length)
    }

    fun write(address: Long, data: ByteArray): Boolean {
        if (!isEngineReady) {
            Logger.w(TAG, "write() called with no engine set up")
            return false
        }
        return nativeWriteBytes(engineHandle, address, data)
    }

    fun regionAt(address: Long): MemoryRegion? = map.regionAt(address)
    fun regions(): List<MemoryRegion> = map.all()

    /**
     * Raw engine handle for modules that need to call Unicorn APIs
     * directly (cpu/CpuState for registers, emulator/EmulatorLoop for
     * uc_emu_start/hooks). Treat as opaque - don't persist across
     * teardown()/setup() cycles.
     */
    fun nativeEngineHandle(): Long = engineHandle

    private fun toMemoryRegion(mapped: MappedRegion): MemoryRegion = MemoryRegion(
        name = mapped.name,
        baseAddress = mapped.baseAddress,
        size = mapped.size,
        readable = mapped.readable,
        writable = mapped.writable,
        executable = mapped.executable
    )

    // --- JNI (implemented in app/src/main/cpp/jni_bridge.cpp) -----------
    private external fun nativeCreateEngine(): Long
    private external fun nativeDestroyEngine(handle: Long)
    private external fun nativeMapRegion(
        handle: Long, base: Long, size: Long, perms: Int, initialData: ByteArray?
    ): Boolean
    private external fun nativeReadBytes(handle: Long, address: Long, length: Int): ByteArray?
    private external fun nativeWriteBytes(handle: Long, address: Long, data: ByteArray): Boolean

    companion object {
        init {
            // Usually already loaded via NativeBridge's init block, but
            // harmless to ensure it here too if MemoryManager is ever
            // used standalone (e.g. an instrumented test harness).
            System.loadLibrary("vxpnative")
        }
    }
}
