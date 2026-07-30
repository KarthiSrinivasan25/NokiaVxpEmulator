package com.nokia.vxp.emulator

/**
 * Emulated timers for guest code (mre/VmTimer will register callbacks
 * here once that module exists). Time advances in emulated milliseconds
 * driven by EmulatorLoop feeding back how long each instruction batch
 * took - NOT wall-clock time directly - so timer firing stays
 * deterministic relative to how much guest code has actually executed.
 */
class TimerManager {

    data class ScheduledTimer(
        val id: Int,
        var dueAtMillis: Long,
        val periodMillis: Long?, // null = one-shot
        val callback: () -> Unit
    )

    private var currentTimeMillis = 0L
    private val timers = mutableListOf<ScheduledTimer>()
    private var nextId = 1

    /** Schedules [callback] to fire after [delayMillis]; repeats every [periodMillis] if provided. Returns a cancellable id. */
    @Synchronized
    fun schedule(delayMillis: Long, periodMillis: Long? = null, callback: () -> Unit): Int {
        val id = nextId++
        timers += ScheduledTimer(id, currentTimeMillis + delayMillis, periodMillis, callback)
        return id
    }

    @Synchronized
    fun cancel(id: Int) {
        timers.removeAll { it.id == id }
    }

    /** Advances emulated time and fires any timers now due. Call once per frame with that frame's elapsed time. */
    @Synchronized
    fun advance(elapsedMillis: Long) {
        currentTimeMillis += elapsedMillis
        val due = timers.filter { it.dueAtMillis <= currentTimeMillis }
        for (timer in due) {
            timer.callback()
            if (timer.periodMillis != null) {
                timer.dueAtMillis = currentTimeMillis + timer.periodMillis
            } else {
                timers.remove(timer)
            }
        }
    }

    @Synchronized
    fun reset() {
        currentTimeMillis = 0L
        timers.clear()
    }

    fun now(): Long = currentTimeMillis

    @Synchronized
    fun activeCount(): Int = timers.size
}
