package com.nokia.vxp.emulator

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Thread-safe queue between the UI thread (posting input/lifecycle
 * events) and the emulator loop thread (draining them once per frame).
 */
class EventQueue {
    private val queue = ConcurrentLinkedQueue<EmulatorEvent>()

    fun post(event: EmulatorEvent) {
        queue.add(event)
    }

    /** Drains everything currently queued, in FIFO order. Call once per loop iteration. */
    fun drainAll(): List<EmulatorEvent> {
        val drained = mutableListOf<EmulatorEvent>()
        while (true) {
            val event = queue.poll() ?: break
            drained += event
        }
        return drained
    }

    fun isEmpty(): Boolean = queue.isEmpty()
}
