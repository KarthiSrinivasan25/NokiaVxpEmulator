package com.nokia.vxp.mre

import com.nokia.vxp.utils.Logger

private const val TAG = "VmFile"

/**
 * Placeholder for file-system vm_file_* API surface. ALL of the names
 * registered here are confirmed real (gtrxAC/peanut.vxp's .symtab, MIT
 * licensed) - vm_file_open, vm_file_close, vm_file_read, vm_file_write,
 * vm_file_commit, vm_file_getfilesize, vm_file_get_attributes,
 * vm_file_mkdir, vm_find_first, vm_find_next, vm_find_close. No
 * filesystem sandbox/module exists yet for guest apps to read/write
 * into, so every handler here logs and fails safe (returns -1, a
 * conventional "error" result) rather than pretending file I/O succeeded.
 */
object VmFile {
    fun registerHandlers(dispatcher: VmDispatcher) {
        dispatcher.registerHandler("vm_file_open", VmApiTable.FILE_OPEN) { args ->
            val path = readGuestCString(args.memory, args.r0)
            Logger.w(TAG, "vm_file_open('$path') - file system not implemented yet, returning error")
            -1L
        }

        val simpleFailStubs = mapOf(
            "vm_file_close" to VmApiTable.FILE_CLOSE,
            "vm_file_read" to VmApiTable.FILE_READ,
            "vm_file_write" to VmApiTable.FILE_WRITE,
            "vm_file_commit" to VmApiTable.FILE_COMMIT,
            "vm_file_getfilesize" to VmApiTable.FILE_GETFILESIZE,
            "vm_file_get_attributes" to VmApiTable.FILE_GET_ATTRIBUTES,
            "vm_file_mkdir" to VmApiTable.FILE_MKDIR,
            "vm_find_first" to VmApiTable.FIND_FIRST,
            "vm_find_next" to VmApiTable.FIND_NEXT,
            "vm_find_close" to VmApiTable.FIND_CLOSE
        )
        for ((name, address) in simpleFailStubs) {
            dispatcher.registerHandler(name, address) {
                Logger.w(TAG, "$name() - file system not implemented yet, returning error")
                -1L
            }
        }
    }
}
