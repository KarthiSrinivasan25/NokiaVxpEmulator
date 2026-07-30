package com.nokia.vxp.memory

/** Small helpers for building initial region contents (zero-filled heap/stack, etc). */
object Ram {
    fun zeroed(size: Long): ByteArray {
        require(size in 0..Int.MAX_VALUE.toLong()) {
            "Region too large for a single ByteArray: $size bytes " +
                "(would need chunked native writes - not needed for VXP-scale modules)"
        }
        return ByteArray(size.toInt())
    }
}
