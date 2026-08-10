package com.nokia.vxp.memory

/**
 * Minimal read-only guest-memory access contract. MemoryManager
 * implements this; mre/ handlers that only need to read guest memory
 * (e.g. to pull a null-terminated string out of a pointer argument) can
 * depend on this interface instead of the concrete MemoryManager class,
 * which makes them testable against a trivial fake instead of requiring
 * a real Unicorn engine.
 */
interface GuestMemoryReader {
    fun read(address: Long, length: Int): ByteArray?
}
