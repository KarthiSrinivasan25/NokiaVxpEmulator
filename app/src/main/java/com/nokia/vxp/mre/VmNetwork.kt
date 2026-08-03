package com.nokia.vxp.mre

import com.nokia.vxp.utils.Logger

private const val TAG = "VmNetwork"

/**
 * Placeholder for network-related vm_net_*/vm_http_* API surface.
 * Deliberately left inert (always fails, never actually connects)
 * rather than implemented - this emulator runs old, often unsigned or
 * unverified third-party VXP binaries, and silently granting them real
 * network access by default isn't something this scaffold should do
 * without a deliberate, explicit opt-in mechanism at a higher level
 * first. Every attempted call is logged, so it's visible if a loaded
 * game tries to use the network.
 */
object VmNetwork {
    fun registerHandlers(dispatcher: VmDispatcher) {
        dispatcher.registerHandler("vm_net_connect", VmApiTable.NETWORK_CONNECT) {
            Logger.w(TAG, "vm_net_connect() called by guest - network access is intentionally disabled, returning error")
            -1L
        }
    }
}
