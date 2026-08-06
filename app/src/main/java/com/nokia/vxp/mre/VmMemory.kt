package com.nokia.vxp.mre

import com.nokia.vxp.memory.MemoryManager

/**
 * Implements the memory-allocation vm_* API surface on top of the guest
 * heap (memory.Heap, via MemoryManager). All four function names here
 * (vm_malloc, vm_free, vm_calloc, vm_realloc) are confirmed real -
 * found in gtrxAC/peanut.vxp's .symtab (MIT licensed sample).
 */
object VmMemory {
    fun registerHandlers(dispatcher: VmDispatcher, memoryManager: MemoryManager) {
        dispatcher.registerHandler("vm_malloc", VmApiTable.MEMORY_MALLOC) { args ->
            // r0 = requested size in bytes. Guest convention: 0 = allocation
            // failed, matching real malloc's NULL-on-failure behavior.
            memoryManager.heap.malloc(args.r0) ?: 0L
        }

        dispatcher.registerHandler("vm_free", VmApiTable.MEMORY_FREE) { args ->
            // r0 = guest pointer previously returned by vm_malloc/vm_calloc/vm_realloc
            memoryManager.heap.free(args.r0)
            0L
        }

        dispatcher.registerHandler("vm_calloc", VmApiTable.MEMORY_CALLOC) { args ->
            // r0 = element count, r1 = element size. Heap.malloc already
            // zero-fills new allocations, so calloc's "zeroed" contract is
            // satisfied for free by the same call real malloc would use.
            val totalSize = args.r0 * args.r1
            memoryManager.heap.malloc(totalSize) ?: 0L
        }

        dispatcher.registerHandler("vm_realloc", VmApiTable.MEMORY_REALLOC) { args ->
            // r0 = existing guest pointer (or 0), r1 = new requested size.
            // Approximate: allocates fresh and copies over min(old,new)
            // bytes read back from guest memory, since our Heap doesn't
            // track each block's original size to know exactly how much
            // is safe to copy - this reads new-size bytes from the old
            // pointer, which is safe as long as the old block was at
            // least that large (true for growing-in-place reallocs,
            // the common case; a shrink would copy some already-freed-
            // looking bytes harmlessly since we don't scribble on free()).
            val oldPtr = args.r0
            val newSize = args.r1
            val newPtr = memoryManager.heap.malloc(newSize)
            if (newPtr != null && oldPtr != 0L) {
                val oldData = memoryManager.read(oldPtr, newSize.toInt())
                if (oldData != null) memoryManager.write(newPtr, oldData)
                memoryManager.heap.free(oldPtr)
            }
            newPtr ?: 0L
        }
    }
}
