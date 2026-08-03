package com.nokia.vxp.debug

data class Breakpoint(val address: Long, var enabled: Boolean = true, val label: String? = null)

/**
 * Tracks a set of breakpoints. Given the current PC, can compute the
 * nearest enabled breakpoint at or after it - which cpu.Executor.run's
 * existing endAddress parameter (Unicorn's own `until` address) can use
 * directly to stop exactly there, without needing any new native hook.
 */
class BreakpointManager {
    private val breakpoints = mutableMapOf<Long, Breakpoint>()

    fun add(address: Long, label: String? = null): Breakpoint {
        val bp = Breakpoint(address, enabled = true, label = label)
        breakpoints[address] = bp
        return bp
    }

    fun remove(address: Long) {
        breakpoints.remove(address)
    }

    fun toggle(address: Long) {
        breakpoints[address]?.let { it.enabled = !it.enabled }
    }

    fun isBreakpoint(address: Long): Boolean = breakpoints[address]?.enabled == true

    fun all(): List<Breakpoint> = breakpoints.values.sortedBy { it.address }

    /** Nearest enabled breakpoint at or after [fromAddress], or null if none - feed straight into Executor.run(endAddress=...). */
    fun nextBreakpointAtOrAfter(fromAddress: Long): Long? =
        breakpoints.values.filter { it.enabled && it.address >= fromAddress }.minOfOrNull { it.address }

    fun clear() = breakpoints.clear()
    fun count(): Int = breakpoints.size
}
