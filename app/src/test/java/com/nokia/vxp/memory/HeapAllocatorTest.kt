package com.nokia.vxp.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeapAllocatorTest {

    @Test
    fun `first allocation starts at offset zero`() {
        val heap = HeapAllocator(1024)
        val offset = heap.alloc(64)
        assertEquals(0L, offset)
    }

    @Test
    fun `sequential allocations do not overlap`() {
        val heap = HeapAllocator(1024)
        val a = heap.alloc(64)!!
        val b = heap.alloc(128)!!
        assertTrue(b >= a + 64) // b must start at or after a's (aligned) end
    }

    @Test
    fun `allocation larger than remaining space fails`() {
        val heap = HeapAllocator(128)
        assertNotNull(heap.alloc(100))
        val tooBig = heap.alloc(64) // only ~28 bytes left after alignment
        assertNull(tooBig)
    }

    @Test
    fun `freeing and reallocating reuses space`() {
        val heap = HeapAllocator(256)
        val a = heap.alloc(64)!!
        heap.alloc(64) // b, keeps a from being the only block
        assertTrue(heap.free(a))

        val c = heap.alloc(64)
        assertEquals(a, c) // first-fit should reuse a's freed slot
    }

    @Test
    fun `freeing an offset that was never allocated returns false`() {
        val heap = HeapAllocator(256)
        assertTrue(!heap.free(999))
    }

    @Test
    fun `adjacent free blocks coalesce so a larger allocation can succeed`() {
        val heap = HeapAllocator(128)
        val a = heap.alloc(64)!!
        val b = heap.alloc(64)!!

        assertTrue(heap.free(a))
        assertTrue(heap.free(b))

        // Without coalescing, two separate 64-byte free blocks couldn't
        // satisfy a single 100-byte request.
        val big = heap.alloc(100)
        assertNotNull(big)
    }

    @Test
    fun `usedBytes and freeBytes stay consistent with capacity`() {
        val capacity = 512L
        val heap = HeapAllocator(capacity)
        heap.alloc(100)
        heap.alloc(50)

        assertEquals(capacity, heap.usedBytes() + heap.freeBytes())
    }
}
