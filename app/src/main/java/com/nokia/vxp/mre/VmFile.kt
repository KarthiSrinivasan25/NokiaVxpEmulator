package com.nokia.vxp.mre

import com.nokia.vxp.utils.Logger

private const val TAG = "VmFile"

/**
 * Placeholder for file-system vm_file_* API surface. No filesystem
 * sandbox/module exists yet for guest apps to read/write into, so these
 * handlers log and fail safe (return -1, a conventional "error" result)
 * rather than pretending file I/O succeeded.
 */
object VmFile {
    fun registerHandlers(dispatcher: VmDispatcher) {
        dispatcher.registerHandler("vm_file_open", VmApiTable.FILE_OPEN) { args ->
            // r0 = guest pointer to a null-terminated path string
            val path = readGuestCString(args.memory, args.r0)
            Logger.w(TAG, "vm_file_open('$path') - file system not implemented yet, returning error")
            -1L
        }
    }
}
