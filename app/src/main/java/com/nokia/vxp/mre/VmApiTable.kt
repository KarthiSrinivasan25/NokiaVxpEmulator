package com.nokia.vxp.mre

/**
 * Placeholder trap addresses for MRE OS API entry points. THESE ARE NOT
 * CONFIRMED REAL ADDRESSES - see VmDispatcher's file-level comment for
 * why they can't be: they'd depend on the specific phone firmware being
 * emulated, which we don't have a dump of. They're chosen from an
 * obviously-fake, sparsely-spaced, high address range so that (a) they
 * can never collide with a real ELF segment/heap/stack address that
 * loader.ModuleMapper computes, and (b) they're visually recognizable
 * in logs as placeholders (0xFEED_xxxx) rather than looking like a
 * plausible real address someone might mistake for confirmed data.
 *
 * Once a real VXP sample's relocations or a firmware dump reveal actual
 * addresses, replace these constants - or better, replace this whole
 * fixed-table approach with dynamically registering handlers by
 * resolved relocation address per loaded file.
 */
object VmApiTable {
    private const val BASE = 0xFEED0000L
    private const val STRIDE = 0x10L
    private var nextIndex = 0L
    private fun slot(): Long = BASE + (nextIndex++ * STRIDE)

    // --- graphics (vm_graphic_get_screen_width/height are confirmed real
    // names; the rest follow the same naming convention as a plausible
    // guess, not confirmed against a real SDK header) ---
    val GRAPHIC_GET_SCREEN_WIDTH = slot()
    val GRAPHIC_GET_SCREEN_HEIGHT = slot()
    val GRAPHIC_SET_PIXEL = slot()
    val GRAPHIC_FILL_RECT = slot()
    val GRAPHIC_DRAW_TEXT = slot()

    // --- input (name is a plausible guess, not confirmed) ---
    val INPUT_GET_KEY_STATE = slot()

    // --- timer (name is a plausible guess, not confirmed) ---
    val TIMER_CREATE = slot()
    val TIMER_DELETE = slot()

    // --- memory (name is a plausible guess, not confirmed) ---
    val MEMORY_ALLOC = slot()
    val MEMORY_FREE = slot()

    // --- system (vm_app_log is a confirmed real name) ---
    val SYSTEM_LOG = slot()

    // --- audio (audio/ module not implemented yet - handlers just log) ---
    val AUDIO_PLAY_FREQUENCY = slot()
    val AUDIO_STOP = slot()

    // --- file (no filesystem sandbox implemented yet - handlers fail safe) ---
    val FILE_OPEN = slot()

    // --- network (intentionally inert - see mre/VmNetwork.kt) ---
    val NETWORK_CONNECT = slot()
}
