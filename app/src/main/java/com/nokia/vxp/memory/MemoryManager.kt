```kotlin
package com.nokia.vxp.memory

import com.nokia.vxp.loader.MappedRegion
import com.nokia.vxp.loader.ModuleMemoryLayout
import com.nokia.vxp.utils.Logger

private const val TAG = "MemoryManager"

/**
 * Owns one Unicorn engine instance's memory for one running emulator
 * session: creates the native engine, maps every region from a
 * loader.ModuleMemoryLayout, and exposes read/write/heap/stack to the
 * rest of the app.
 *
 * One instance per running emulator.
 */
class MemoryManager : GuestMemoryReader {

    private var engineHandle: Long = 0L
    private var faultDiagnosticsHookHandle: Long = 0L

    private val map = MemoryMap()

    private var heapImpl: Heap? = null
    private var stackImpl: Stack? = null

    val isEngineReady: Boolean
        get() = engineHandle != 0L

    val heap: Heap
        get() = heapImpl
            ?: error("Heap not initialized - call setup() first")

    val stack: Stack
        get() = stackImpl
            ?: error("Stack not initialized - call setup() first")

    /**
     * Creates the native Unicorn engine and maps all regions from [layout].
     *
     * Returns false on any failure.
     */
    fun setup(layout: ModuleMemoryLayout): Boolean {
        // Guard against setup() being called twice without teardown().
        teardown()

        val handle = nativeCreateEngine()

        if (handle == 0L) {
            Logger.e(
                TAG,
                "Failed to create native Unicorn engine"
            )
            return false
        }

        engineHandle = handle

        // Map all ELF segments, heap and stack.
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
                    "Failed to map region '${region.name}' " +
                        "at 0x${region.baseAddress.toString(16)} " +
                        "(size=${region.size})"
                )

                teardown()
                return false
            }

            map.add(kotlinRegion)

            Logger.i(
                TAG,
                "Mapped '${region.name}' " +
                    "base=0x${region.baseAddress.toString(16)} " +
                    "size=${region.size}"
            )
        }

        // Initialize heap and stack wrappers after their regions
        // have successfully been mapped.
        heapImpl = Heap(
            this,
            layout.heapRegion.baseAddress,
            layout.heapRegion.size
        )

        stackImpl = Stack(
            layout.stackRegion.baseAddress,
            layout.stackRegion.size
        )

        /*
         * Diagnostic-only fault hook.
         *
         * This lets the native side report the actual guest address,
         * PC, registers and nearby code when Unicorn encounters a
         * memory fault.
         */
        faultDiagnosticsHookHandle =
            nativeInstallFaultDiagnostics(engineHandle)

        if (faultDiagnosticsHookHandle == 0L) {
            Logger.w(
                TAG,
                "Fault diagnostics hook failed to install - " +
                    "memory faults will log with less detail"
            )
        } else {
            Logger.i(
                TAG,
                "Fault diagnostics hook installed"
            )
        }

        /*
         * TEMP DEBUG PROBE
         *
         * The current fault occurs around:
         *
         *   PC = 0x8058
         *   R0 = 0x324f0
         *   R1 = 0x324fc
         *   R2 = 0x324fc
         *   R3 = 0x32514
         *   R4 = 0x32518
         *
         * The segment ends at approximately 0x32F90, so 0x324E0
         * is inside the loaded ELF segment.
         *
         * This probe verifies that the expected bytes were actually
         * loaded into Unicorn memory.
         */
        val probeAddress = 0x324E0L
        val probeLength = 0x50

        val probe = read(
            probeAddress,
            probeLength
        )

        if (probe == null) {
            Logger.e(
                TAG,
                "PROBE FAILED: cannot read " +
                    "0x${probeAddress.toString(16)}"
            )
        } else {
            val lines = probe
                .toList()
                .chunked(4)
                .mapIndexed { index, bytes ->
                    var value = 0L

                    for (i in bytes.indices) {
                        value = value or (
                            (bytes[i].toInt() and 0xFF).toLong()
                                shl (i * 8)
                        )
                    }

                    val address =
                        probeAddress + (index * 4L)

                    "0x${address.toString(16)}=%08x".format(value)
                }

            Logger.i(
                TAG,
                "PROBE memory:\n${lines.joinToString(" ")}"
            )
        }

        Logger.i(
            TAG,
            "Memory setup complete: " +
                "${layout.regions.size} regions mapped"
        )

        return true
    }

    /**
     * Tears down the native Unicorn engine and clears all
     * Kotlin-side memory state.
     */
    fun teardown() {
        if (engineHandle != 0L) {
            if (faultDiagnosticsHookHandle != 0L) {
                nativeRemoveFaultDiagnostics(
                    engineHandle,
                    faultDiagnosticsHookHandle
                )

                faultDiagnosticsHookHandle = 0L
            }

            nativeDestroyEngine(engineHandle)
            engineHandle = 0L
        }

        map.clear()
        heapImpl = null
        stackImpl = null
    }

    /**
     * Reads guest memory.
     *
     * Returns null if the engine is not initialized or the native
     * memory read fails.
     */
    override fun read(
        address: Long,
        length: Int
    ): ByteArray? {
        if (!isEngineReady) {
            Logger.w(
                TAG,
                "read() called with no engine set up"
            )
            return null
        }

        if (length <= 0) {
            Logger.w(
                TAG,
                "read() called with invalid length=$length"
            )
            return null
        }

        return nativeReadBytes(
            engineHandle,
            address,
            length
        )
    }

    /**
     * Writes guest memory.
     *
     * Returns false if the engine is not initialized or the native
     * memory write fails.
     */
    fun write(
        address: Long,
        data: ByteArray
    ): Boolean {
        if (!isEngineReady) {
            Logger.w(
                TAG,
                "write() called with no engine set up"
            )
            return false
        }

        if (data.isEmpty()) {
            Logger.w(
                TAG,
                "write() called with empty data"
            )
            return false
        }

        return nativeWriteBytes(
            engineHandle,
            address,
            data
        )
    }

    /**
     * Returns the memory region containing [address], or null if
     * the address is not mapped according to the Kotlin memory map.
     */
    fun regionAt(
        address: Long
    ): MemoryRegion? {
        return map.regionAt(address)
    }

    /**
     * Returns all currently mapped Kotlin-side memory regions.
     */
    fun regions(): List<MemoryRegion> {
        return map.all()
    }

    /**
     * Raw native Unicorn engine handle.
     *
     * Used by CpuState and EmulatorLoop for operations that need
     * direct access to the Unicorn engine.
     *
     * Treat this value as opaque and do not persist it across
     * teardown()/setup() cycles.
     */
    fun nativeEngineHandle(): Long {
        return engineHandle
    }

    private fun toMemoryRegion(
        mapped: MappedRegion
    ): MemoryRegion {
        return MemoryRegion(
            name = mapped.name,
            baseAddress = mapped.baseAddress,
            size = mapped.size,
            readable = mapped.readable,
            writable = mapped.writable,
            executable = mapped.executable
        )
    }

    // -----------------------------------------------------------------
    // JNI
    // Implemented in app/src/main/cpp/jni_bridge.cpp
    // -----------------------------------------------------------------

    private external fun nativeCreateEngine(): Long

    private external fun nativeDestroyEngine(
        handle: Long
    )

    private external fun nativeMapRegion(
        handle: Long,
        base: Long,
        size: Long,
        perms: Int,
        initialData: ByteArray?
    ): Boolean

    private external fun nativeReadBytes(
        handle: Long,
        address: Long,
        length: Int
    ): ByteArray?

    private external fun nativeWriteBytes(
        handle: Long,
        address: Long,
        data: ByteArray
    ): Boolean

    private external fun nativeInstallFaultDiagnostics(
        handle: Long
    ): Long

    private external fun nativeRemoveFaultDiagnostics(
        handle: Long,
        hookHandle: Long
    )

    companion object {
        init {
            /*
             * NativeBridge normally loads libvxpnative.
             * Loading here as well makes MemoryManager safe to use
             * independently, for example from an instrumented test.
             */
            System.loadLibrary("vxpnative")
        }
    }
}
