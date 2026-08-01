package com.nokia.vxp.mre

import com.nokia.vxp.memory.GuestMemoryReader
import com.nokia.vxp.memory.MemoryManager
import com.nokia.vxp.utils.Logger

private const val TAG = "VmDispatcher"

/** Arguments passed to a registered handler: raw register values (R0-R3, per AAPCS) plus read-only guest memory access for pointer/string args. */
data class VmCallArgs(val r0: Long, val r1: Long, val r2: Long, val r3: Long, val memory: GuestMemoryReader)

typealias VmSyscallHandler = (VmCallArgs) -> Long

/**
 * Traps guest calls to MRE's vm_* OS API functions and dispatches them
 * to registered Kotlin handlers.
 *
 * HOW THIS WORKS, AND WHY IT'S PROVISIONAL: real MRE binaries call vm_*
 * functions as ordinary direct calls, resolved against the phone's real
 * firmware at addresses we don't have (no MediaTek firmware dump, no
 * confirmed relocation/import-table format - see loader/'s comments for
 * what IS confirmed about the VXP/ELF format itself). Since we haven't
 * mapped that firmware, any such call becomes a "fetch from unmapped
 * memory" fault in Unicorn - which is exactly the hook this class rides
 * on as a stand-in dispatch mechanism: when guest code tries to execute
 * from an address nobody's mapped, check whether a handler is registered
 * for that address, and if so, treat it as an API call (read args from
 * R0-R3, run the handler, write the result to R0, and jump back to LR
 * as if the "function" had returned normally). If no handler is
 * registered, the real fault propagates normally, so genuine bugs still
 * surface as crashes rather than being silently swallowed.
 *
 * THE PLACEHOLDER ADDRESSES in VmApiTable are NOT the real MRE OS
 * addresses - they can't be, since those depend on the specific phone
 * firmware being emulated, which we don't have. Getting real VXP games
 * running will require either (a) a real firmware/ROM dump mapped
 * alongside the game (removing the need for this trap entirely), or (b)
 * reverse-engineering a real VXP file's relocations to learn which
 * addresses its calls actually target, then registering handlers there
 * instead of the placeholders below.
 */
class VmDispatcher {

    private data class RegisteredHandler(val name: String, val handler: VmSyscallHandler)

    private val handlersByAddress = mutableMapOf<Long, RegisteredHandler>()
    private var engineHandle: Long = 0
    private var hookHandle: Long = 0
    private lateinit var memoryManagerRef: MemoryManager

    fun registerHandler(name: String, address: Long, handler: VmSyscallHandler) {
        val existing = handlersByAddress[address]
        if (existing != null) {
            Logger.w(TAG, "Overwriting handler at 0x${address.toString(16)} ('${existing.name}' -> '$name')")
        }
        handlersByAddress[address] = RegisteredHandler(name, handler)
    }

    fun unregisterHandler(address: Long) {
        handlersByAddress.remove(address)
    }

    fun registeredCount(): Int = handlersByAddress.size
    fun handlerNameAt(address: Long): String? = handlersByAddress[address]?.name

    /**
     * MUST be called from the same thread that will subsequently call
     * Executor.run()/step() for this session (typically EmulatorLoop's
     * own thread, not whatever thread built the Runtime) - see
     * vm_dispatch_bridge.h for why this matters.
     */
    fun install(memoryManager: MemoryManager): Boolean {
        if (!memoryManager.isEngineReady) {
            Logger.e(TAG, "install() called before MemoryManager engine setup")
            return false
        }
        memoryManagerRef = memoryManager
        engineHandle = memoryManager.nativeEngineHandle()
        hookHandle = nativeInstall(engineHandle)
        if (hookHandle == 0L) {
            Logger.e(TAG, "Native dispatch hook installation failed")
            return false
        }
        Logger.i(TAG, "VmDispatcher installed with ${handlersByAddress.size} registered handlers")
        return true
    }

    fun uninstall() {
        if (hookHandle != 0L) {
            nativeRemove(engineHandle, hookHandle)
            hookHandle = 0
        }
    }

    /** Called from native code (vm_dispatch_bridge.cpp) on every unmapped-fetch trap. Not for direct use. */
    @Suppress("unused") // invoked via JNI reflection, not from Kotlin
    private fun onSyscallTrap(address: Long, r0: Long, r1: Long, r2: Long, r3: Long): Long {
        val registered = handlersByAddress[address] ?: return UNHANDLED_SENTINEL
        return try {
            registered.handler(VmCallArgs(r0, r1, r2, r3, memoryManagerRef))
        } catch (e: Exception) {
            Logger.e(TAG, "Handler '${registered.name}' threw - treating this call as unhandled", e)
            UNHANDLED_SENTINEL
        }
    }

    private external fun nativeInstall(engineHandle: Long): Long
    private external fun nativeRemove(engineHandle: Long, hookHandle: Long)

    companion object {
        // Must match vm_dispatch_bridge.cpp's UNHANDLED_SENTINEL exactly.
        const val UNHANDLED_SENTINEL = Long.MIN_VALUE

        init { System.loadLibrary("vxpnative") }
    }
}
