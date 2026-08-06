package com.nokia.vxp.memory

/**
 * Guest heap: combines HeapAllocator's offset bookkeeping with actual
 * Unicorn-backed memory access, so callers (mre/VmMemory, once it
 * exists) get a plain malloc/free/read/write API without needing to
 * know about offsets vs absolute addresses.
 */
class Heap(
    private val memoryManager: MemoryManager,
    private val heapBase: Long,
    private val heapSize: Long
) {
    private val allocator = HeapAllocator(heapSize)

    /** Returns the guest-visible absolute address of the new block, or null if out of heap space. */
    fun malloc(size: Long): Long? {

    if (size <= 0 || size > heapSize) {
        return null
    }

    val offset = allocator.alloc(size) ?: return null

    if (offset + size > heapSize) {
        return null
    }

    val address = heapBase + offset

    memoryManager.write(
        address,
        Ram.zeroed(size)
    )

    return address
}

    fun free(address: Long): Boolean {
        val offset = address - heapBase
        if (offset < 0 || offset >= heapSize) return false
        return allocator.free(offset)
    }

    fun read(address: Long, length: Int): ByteArray? = memoryManager.read(address, length)

    fun write(address: Long, data: ByteArray): Boolean = memoryManager.write(address, data)

    fun usedBytes(): Long = allocator.usedBytes()
    fun freeBytes(): Long = allocator.freeBytes()
    fun capacity(): Long = heapSize
    fun base(): Long = heapBase
}
