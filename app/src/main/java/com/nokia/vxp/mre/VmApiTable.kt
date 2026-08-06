package com.nokia.vxp.mre

/**
 * Trap addresses for MRE OS API entry points, registered by real
 * confirmed function names wherever possible.
 *
 * NAMES were confirmed against a real, non-stripped VXP sample
 * (gtrxAC/peanut.vxp, MIT licensed, via its .symtab - see loader.ElfSymbol
 * and mre.VmSymbolBinder for how they're actually used to patch a
 * per-file jump table). ADDRESSES remain placeholders - they can't be
 * real, since real ones depend on the specific phone firmware being
 * emulated, which we don't have. VmSymbolBinder writes these addresses
 * into each recognized "_vm_*" .bss slot at load time, so the
 * placeholder-ness of the address itself doesn't matter - what matters
 * is that it's (a) unmapped, so calling it traps into VmDispatcher, and
 * (b) consistently the same address a handler was registered under.
 *
 * Addresses are chosen from an obviously-fake, sparsely-spaced, high
 * range so they can never collide with a real ELF segment/heap/stack
 * address loader.ModuleMapper computes, and are visually recognizable
 * in logs as placeholders (0xFEED_xxxx).
 */
object VmApiTable {
    private const val BASE = 0xFEED0000L
    private const val STRIDE = 0x10L
    private var nextIndex = 0L
    private fun slot(): Long = BASE + (nextIndex++ * STRIDE)

    // --- memory (all four names confirmed real) ---
    val MEMORY_MALLOC = slot()
    val MEMORY_FREE = slot()
    val MEMORY_CALLOC = slot()
    val MEMORY_REALLOC = slot()

    // --- timer (both names confirmed real) ---
    val TIMER_CREATE = slot()
    val TIMER_DELETE = slot() // no confirmed real "delete" counterpart was found - kept as our own placeholder name
    val GET_TICK_COUNT = slot()

    // --- system / lifecycle / event registration (all confirmed real) ---
    val SYSTEM_LOG = slot() // vm_app_log
    val EXIT_APP = slot()
    val REG_SYSEVT_CALLBACK = slot()
    val REG_KEYBOARD_CALLBACK = slot()
    val REG_PEN_CALLBACK = slot()
    val SWITCH_POWER_SAVING_MODE = slot()

    // --- graphics (get_screen_width/height/character_height/string_width,
    // setcolor, fill_rect_ex, textout_to_layer, create/delete_layer,
    // create_canvas_cf, create_layer_cf, get_canvas_buffer,
    // release_canvas, set_clip, set_font, flush_layer are all confirmed
    // real names) ---
    val GRAPHIC_GET_SCREEN_WIDTH = slot()
    val GRAPHIC_GET_SCREEN_HEIGHT = slot()
    val GRAPHIC_GET_CHARACTER_HEIGHT = slot()
    val GRAPHIC_GET_STRING_WIDTH = slot()
    val GRAPHIC_SETCOLOR = slot()
    val GRAPHIC_FILL_RECT_EX = slot()
    val GRAPHIC_TEXTOUT_TO_LAYER = slot()
    val GRAPHIC_CREATE_LAYER = slot()
    val GRAPHIC_CREATE_LAYER_CF = slot()
    val GRAPHIC_CREATE_CANVAS_CF = slot()
    val GRAPHIC_DELETE_LAYER = slot()
    val GRAPHIC_GET_CANVAS_BUFFER = slot()
    val GRAPHIC_RELEASE_CANVAS = slot()
    val GRAPHIC_SET_CLIP = slot()
    val GRAPHIC_SET_FONT = slot()
    val GRAPHIC_FLUSH_LAYER = slot()

    // --- input / keyboard (confirmed real names) ---
    val KBD_SET_MODE = slot()
    val INPUT_TEXT2 = slot()

    // --- file (all confirmed real names) ---
    val FILE_OPEN = slot()
    val FILE_CLOSE = slot()
    val FILE_READ = slot()
    val FILE_WRITE = slot()
    val FILE_COMMIT = slot()
    val FILE_GETFILESIZE = slot()
    val FILE_GET_ATTRIBUTES = slot()
    val FILE_MKDIR = slot()
    val FIND_FIRST = slot()
    val FIND_NEXT = slot()
    val FIND_CLOSE = slot()

    // --- audio (vm_midi_play_by_bytes / vm_midi_stop are confirmed real names) ---
    val MIDI_PLAY_BY_BYTES = slot()
    val MIDI_STOP = slot()

    // --- string/encoding utilities (confirmed real names) ---
    val ASCII_TO_UCS2 = slot()
    val UCS2_TO_ASCII = slot()
    val WSTRLEN = slot()
    val VSPRINTF = slot()

    // --- network (NOT a confirmed name - no vm_net_*/vm_http_* symbol was
    // found in the real sample; intentionally inert regardless - see
    // mre/VmNetwork.kt) ---
    val NETWORK_CONNECT = slot()
}
