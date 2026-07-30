package com.nokia.vxp.loader

import com.nokia.vxp.utils.Logger

private const val TAG = "VxpLoader"

/** One step of the load pipeline, kept so EmulatorActivity / debug UI can show progress. */
data class LoadEvent(val step: String, val detail: String)

/**
 * Small append-only log of what happened during a load, distinct from
 * Logger (which is fire-and-forget). Useful for showing the user *why*
 * a file was rejected, and later for debug.LogConsole.
 */
class LoaderLog {
    private val events = mutableListOf<LoadEvent>()

    fun log(step: String, detail: String) {
        events += LoadEvent(step, detail)
        Logger.d(TAG, "[$step] $detail")
    }

    fun events(): List<LoadEvent> = events.toList()

    fun summary(): String = events.joinToString("\n") { "[${it.step}] ${it.detail}" }
}
