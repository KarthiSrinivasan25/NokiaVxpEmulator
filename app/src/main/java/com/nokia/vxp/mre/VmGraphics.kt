package com.nokia.vxp.mre

import com.nokia.vxp.graphics.GraphicsEngine
import com.nokia.vxp.utils.Logger

private const val TAG = "VmGraphics"

/**
 * Implements the graphics-related vm_graphic_* API surface on top of
 * graphics.GraphicsEngine. get_screen_width/height,
 * get_character_height, get_string_width, setcolor, fill_rect_ex,
 * textout_to_layer, create_layer, create_layer_cf, create_canvas_cf,
 * delete_layer, get_canvas_buffer, release_canvas, set_clip, set_font,
 * and flush_layer are ALL confirmed real function names
 * (gtrxAC/peanut.vxp's .symtab, MIT licensed).
 *
 * The layer/canvas-handle family (create_layer, get_canvas_buffer,
 * etc.) implies a layer/canvas abstraction this emulator doesn't model
 * yet - those are registered as honest logging stubs (returning 0/a
 * sentinel invalid handle) rather than left unregistered, so
 * mre.VmSymbolBinder still patches their jump-table slots and calls to
 * them get caught and logged instead of jumping into unmapped memory
 * unrecognized.
 */
object VmGraphics {

    fun registerHandlers(dispatcher: VmDispatcher, engine: GraphicsEngine) {
        // Per-session draw state, captured by the closures below. Each
        // call to registerHandlers() (once per Emulator.load()) creates a
        // fresh local variable here, so state never leaks between sessions.
        var currentColor = 0xFFFFFFFF.toInt()

        dispatcher.registerHandler("vm_graphic_get_screen_width", VmApiTable.GRAPHIC_GET_SCREEN_WIDTH) {
            engine.width.toLong()
        }

        dispatcher.registerHandler("vm_graphic_get_screen_height", VmApiTable.GRAPHIC_GET_SCREEN_HEIGHT) {
            engine.height.toLong()
        }

        dispatcher.registerHandler("vm_graphic_get_character_height", VmApiTable.GRAPHIC_GET_CHARACTER_HEIGHT) {
            // Matches graphics.FontRenderer's fixed cell height.
            5L
        }

        dispatcher.registerHandler("vm_graphic_get_string_width", VmApiTable.GRAPHIC_GET_STRING_WIDTH) { args ->
            // r0 = guest pointer to a null-terminated string, r1 = scale
            val text = readGuestCString(args.memory, args.r0)
            val scale = if (args.r1 > 0) args.r1.toInt() else 1
            com.nokia.vxp.graphics.FontRenderer.measureWidth(text, scale).toLong()
        }

        dispatcher.registerHandler("vm_graphic_setcolor", VmApiTable.GRAPHIC_SETCOLOR) { args ->
            // r0 = ARGB8888 color - stored for subsequent draw calls that don't take an explicit color.
            currentColor = args.r0.toInt()
            0L
        }

        dispatcher.registerHandler("vm_graphic_fill_rect_ex", VmApiTable.GRAPHIC_FILL_RECT_EX) { args ->
            // r0=x, r1=y, r2=(width<<16 | height) - packed since this trap
            // only reads 4 registers; uses the color set via vm_graphic_setcolor.
            val width = (args.r2 ushr 16) and 0xFFFF
            val height = args.r2 and 0xFFFF
            engine.fillRect(args.r0.toInt(), args.r1.toInt(), width.toInt(), height.toInt(), currentColor)
            0L
        }

        dispatcher.registerHandler("vm_graphic_textout_to_layer", VmApiTable.GRAPHIC_TEXTOUT_TO_LAYER) { args ->
            // r0=x, r1=y, r2=guest pointer to a null-terminated string (layer handle argument not representable in 4 registers - ignored, draws straight to the screen)
            val text = readGuestCString(args.memory, args.r2)
            engine.drawText(args.r0.toInt(), args.r1.toInt(), text, currentColor)
            0L
        }

        // --- Layer/canvas-handle family: no layer/canvas model exists yet, so these are honest stubs. ---
        val unimplementedLayerCalls = mapOf(
            "vm_graphic_create_layer" to VmApiTable.GRAPHIC_CREATE_LAYER,
            "vm_graphic_create_layer_cf" to VmApiTable.GRAPHIC_CREATE_LAYER_CF,
            "vm_graphic_create_canvas_cf" to VmApiTable.GRAPHIC_CREATE_CANVAS_CF,
            "vm_graphic_delete_layer" to VmApiTable.GRAPHIC_DELETE_LAYER,
            "vm_graphic_get_canvas_buffer" to VmApiTable.GRAPHIC_GET_CANVAS_BUFFER,
            "vm_graphic_release_canvas" to VmApiTable.GRAPHIC_RELEASE_CANVAS,
            "vm_graphic_set_clip" to VmApiTable.GRAPHIC_SET_CLIP,
            "vm_graphic_set_font" to VmApiTable.GRAPHIC_SET_FONT,
            "vm_graphic_flush_layer" to VmApiTable.GRAPHIC_FLUSH_LAYER
        )
        for ((name, address) in unimplementedLayerCalls) {
            dispatcher.registerHandler(name, address) {
                Logger.w(TAG, "$name() - layer/canvas abstraction not implemented yet, returning 0")
                0L
            }
        }
    }
}
