package com.nokia.vxp.debug

/** One log line captured for a future debug console UI. */
data class ConsoleEntry(val tag: String, val message: String, val timestampMillis: Long = System.currentTimeMillis())

/**
 * Small ring-buffer log sink for a future debug console view. Separate
 * from utils.Logger (which just forwards to Android's logcat) - this
 * keeps recent entries in memory so a debug screen can display them
 * without needing logcat/adb access, which matters on a real device.
 */
class LogConsole(private val capacity: Int = 500) {
    private val entries = ArrayDeque<ConsoleEntry>()

    @Synchronized
    fun log(tag: String, message: String) {
        entries.addLast(ConsoleEntry(tag, message))
        while (entries.size > capacity) entries.removeFirst()
    }

    @Synchronized
    fun entries(): List<ConsoleEntry> = entries.toList()

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun filteredBy(tag: String): List<ConsoleEntry> = entries.filter { it.tag == tag }

    fun size(): Int = entries.size
}
