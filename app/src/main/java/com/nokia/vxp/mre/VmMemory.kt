package com.nokia.vxp.mre

import com.nokia.vxp.memory.MemoryManager

/**
 * Implements a malloc/free-style vm_* API surface on top of the guest
 * heap (memory.Heap, via MemoryManager). Naming is a plausible
 * placeholder, not confirmed against a real SDK header.
 */
object VmMemory {
    fun registerHandlers(dispatcher: VmDispatcher, memoryManager: MemoryManager) {
        dispatcher.registerHandler("vm_malloc", VmApiTable.MEMORY_ALLOC) { args ->
            // r0 = requested size in bytes. Guest convention: 0 = allocation
            // failed, matching real malloc's NULL-on-failure behavior.
            memoryManager.heap.malloc(args.r0) ?: 0L
        }

        dispatcher.registerHandler("vm_free", VmApiTable.MEMORY_FREE) { args ->
            // r0 = guest pointer previously returned by vm_malloc
            memoryManager.heap.free(args.r0)
            0L
        }
    }
}
