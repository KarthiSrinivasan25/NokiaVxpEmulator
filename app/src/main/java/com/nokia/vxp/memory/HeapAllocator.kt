package com.nokia.vxp.memory

/**
 * First-fit free-list allocator over a fixed heap region. Tracks which
 * byte ranges (relative to heapBase) are in use; the actual guest bytes
 * still live in Unicorn's memory and are accessed through MemoryManager.
 *
 * Deliberately simple, not a production malloc - VXP games run with
 * heaps in the tens-to-low-hundreds of KB, so first-fit with adjacent-
 * block coalescing is more than adequate and easy to reason about /
 * debug. Can be swapped out later without touching callers (mre/VmMemory,
 * Heap.kt) since they only see alloc()/free()/usedBytes()/freeBytes().
 */
class HeapAllocator(private val heapSize: Long) {

    private data class Block(var offset: Long, var size: Long, var free: Boolean)

    private val blocks = mutableListOf(Block(0, heapSize, free = true))
    private val minAlignment = 8L

    /** Returns the offset (relative to heap base) of the new allocation, or null if out of memory. */
    @Synchronized
    fun alloc(requestedSize: Long): Long? {
        if (requestedSize <= 0) return null
        val size = alignUp(requestedSize)

        val index = blocks.indexOfFirst { it.free && it.size >= size }
        if (index == -1) return null

        val block = blocks[index]
        if (block.size > size) {
            blocks.add(index + 1, Block(block.offset + size, block.size - size, free = true))
            block.size = size
        }
        block.free = false
        return block.offset
    }

    /** [offset] must be a value previously returned by alloc(). Returns false if not currently allocated. */
    @Synchronized
    fun free(offset: Long): Boolean {
        val index = blocks.indexOfFirst { it.offset == offset && !it.free }
        if (index == -1) return false

        blocks[index].free = true
        mergeAdjacentFreeBlocks()
        return true
    }

    @Synchronized
    fun freeBytes(): Long = blocks.filter { it.free }.sumOf { it.size }

    @Synchronized
    fun usedBytes(): Long = heapSize - freeBytes()

    @Synchronized
    fun largestFreeBlock(): Long = blocks.filter { it.free }.maxOfOrNull { it.size } ?: 0L

    private fun mergeAdjacentFreeBlocks() {
        var i = 0
        while (i < blocks.size - 1) {
            val a = blocks[i]
            val b = blocks[i + 1]
            if (a.free && b.free) {
                a.size += b.size
                blocks.removeAt(i + 1)
            } else {
                i++
            }
        }
    }

    private fun alignUp(size: Long): Long =
        (size + minAlignment - 1) and (minAlignment - 1).inv()
}
