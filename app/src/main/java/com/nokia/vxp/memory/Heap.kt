
package com.nokia.vxp.memory

/**
 * Guest heap.
 *
 * Combines HeapAllocator bookkeeping with actual Unicorn-backed memory
 * access.
 */
class Heap(
    private val memoryManager: MemoryManager,
    private val heapBase: Long,
    private val heapSize: Long
) {

    private val allocator = HeapAllocator(heapSize)

    /**
     * Returns the guest-visible absolute address of a newly allocated
     * block, or null if the heap is full.
     */
    fun malloc(size: Long): Long? {
        val offset = allocator.alloc(size) ?: return null
        val address = heapBase + offset

        // Newly allocated guest memory starts zeroed.
        memoryManager.write(
            address,
            Ram.zeroed(size)
        )

        return address
    }

    fun free(address: Long): Boolean {
        val offset = address - heapBase

        if (offset < 0 || offset >= heapSize) {
            return false
        }

        return allocator.free(offset)
    }

    fun read(
        address: Long,
        length: Int
    ): ByteArray? {
        return memoryManager.read(address, length)
    }

    fun write(
        address: Long,
        data: ByteArray
    ): Boolean {
        return memoryManager.write(address, data)
    }

    fun usedBytes(): Long {
        return allocator.usedBytes()
    }

    fun freeBytes(): Long {
        return allocator.freeBytes()
    }

    fun capacity(): Long {
        return heapSize
    }

    fun base(): Long {
        return heapBase
    }
}
