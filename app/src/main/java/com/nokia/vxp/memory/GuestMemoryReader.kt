
package com.nokia.vxp.memory

/**
 * Minimal read-only guest-memory access contract.
 *
 * MemoryManager implements this interface. MRE handlers that only need
 * to read guest memory can depend on this interface instead of the
 * concrete MemoryManager class.
 */
interface GuestMemoryReader {

    fun read(
        address: Long,
        length: Int
    ): ByteArray?
}
