package com.nokia.vxp.mre

import com.nokia.vxp.loader.ElfSymbol
import com.nokia.vxp.memory.MemoryManager
import com.nokia.vxp.utils.Logger

private const val TAG = "VmSymbolBinder"

/**
 * Finds and patches the guest's OS-API jump table using real ELF symbol
 * names, when present.
 *
 * CONFIRMED AGAINST A REAL SAMPLE (gtrxAC/peanut.vxp, MIT licensed):
 * this isn't a guess - inspecting a real, non-stripped VXP's .symtab
 * showed two related symbol families for every API function:
 *   - "vm_malloc" etc: a small compiled stub function inside .text that
 *     loads a function pointer from a fixed .bss slot and jumps to it.
 *   - "_vm_malloc" etc (single leading underscore): the .bss slot
 *     itself - a 4-byte function-pointer variable, zero-initialized
 *     (since .bss has no file content) until something patches it.
 *
 * On a real phone, the MRE OS loader presumably locates these "_vm_*"
 * symbols by name and patches each slot with the real address of that
 * API in firmware, before vm_main() runs. This class does the
 * equivalent: for every "_vm_X" symbol whose corresponding "X" handler
 * is registered in a VmDispatcher, it writes that handler's trap
 * address into the slot - so when the guest's compiled vm_X() stub
 * loads the pointer and jumps to it, it lands on our (deliberately
 * unmapped) trap address and gets caught by VmDispatcher's unmapped-fetch
 * hook, exactly as intended.
 *
 * Files with a stripped .symtab (no symbols at all) simply get nothing
 * patched here - their guest API calls will jump through a
 * still-zeroed slot instead, which lands at whatever's mapped at
 * address 0 rather than faulting, and won't reach our dispatcher. That
 * needs a different approach (e.g. scanning .text for the stub
 * function's own load pattern) not implemented yet.
 */
object VmSymbolBinder {

    private const val JUMP_TABLE_SYMBOL_PREFIX = "_vm_"

    /**
     * Patches every recognized jump-table slot in [symbols] with the
     * matching handler's trap address from [dispatcher], writing through
     * [memoryManager]. Returns how many slots were actually patched, for
     * logging/diagnostics.
     */
    fun bind(symbols: List<ElfSymbol>, dispatcher: VmDispatcher, memoryManager: MemoryManager): Int {
        var patchedCount = 0

        for (symbol in symbols) {
    if (!symbol.name.startsWith(JUMP_TABLE_SYMBOL_PREFIX)) continue
    if (symbol.name.startsWith("__")) continue

    val apiName = symbol.name.removePrefix("_")
    val trapAddress = dispatcher.addressForName(apiName) ?: continue

    val slotAddress = symbol.value

    if (memoryManager.regionAt(slotAddress) == null ||
        memoryManager.regionAt(slotAddress + 3) == null) {

        Logger.w(
            TAG,
            "Skipping '${symbol.name}' - address 0x${slotAddress.toString(16)} is not mapped"
        )
        continue
    }

    val patched = writeSlot(memoryManager, slotAddress, trapAddress)

    if (patched) {
        patchedCount++
        Logger.i(
            TAG,
            "Patched jump-table slot '${symbol.name}' @ 0x${symbol.value.toString(16)} -> 0x${trapAddress.toString(16)} ($apiName)"
        )
    } else {
        Logger.w(
            TAG,
            "Failed to write jump-table slot '${symbol.name}' @ 0x${symbol.value.toString(16)}"
        )
    }
}

        Logger.i(TAG, "VmSymbolBinder: patched $patchedCount of ${symbols.count { it.name.startsWith(JUMP_TABLE_SYMBOL_PREFIX) && !it.name.startsWith("__") }} recognized jump-table slots")
        return patchedCount
    }

    private fun writeSlot(memoryManager: MemoryManager, slotAddress: Long, value: Long): Boolean {
        val bytes = byteArrayOf(
            (value and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 24) and 0xFF).toByte()
        )
        return memoryManager.write(slotAddress, bytes)
    }
}
