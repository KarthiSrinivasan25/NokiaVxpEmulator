package com.nokia.vxp.mre

import com.nokia.vxp.graphics.GraphicsEngine

/**
 * Implements the graphics-related vm_graphic_* API surface on top of
 * graphics.GraphicsEngine. get_screen_width/height are confirmed real
 * function names (seen referenced in a real MRE sample's source - see
 * UstadMobile/ustadmobile-mre's README); set_pixel/fill_rect/draw_text
 * follow the same naming convention as a plausible guess but aren't
 * confirmed against a real SDK header.
 */
object VmGraphics {

    fun registerHandlers(dispatcher: VmDispatcher, engine: GraphicsEngine) {
        dispatcher.registerHandler("vm_graphic_get_screen_width", VmApiTable.GRAPHIC_GET_SCREEN_WIDTH) {
            engine.width.toLong()
        }

        dispatcher.registerHandler("vm_graphic_get_screen_height", VmApiTable.GRAPHIC_GET_SCREEN_HEIGHT) {
            engine.height.toLong()
        }

        dispatcher.registerHandler("vm_graphic_set_pixel", VmApiTable.GRAPHIC_SET_PIXEL) { args ->
            // r0=x, r1=y, r2=ARGB8888 color
            engine.setPixel(args.r0.toInt(), args.r1.toInt(), args.r2.toInt())
            0L
        }

        dispatcher.registerHandler("vm_graphic_fill_rect", VmApiTable.GRAPHIC_FILL_RECT) { args ->
            // r0=x, r1=y, r2=(width<<16 | height), r3=ARGB8888 color.
            // Packed into one register since this trap only reads 4 of
            // them; a real ABI would likely pass a struct pointer or use
            // the stack for a 5th+ argument instead - provisional.
            val width = (args.r2 ushr 16) and 0xFFFF
            val height = args.r2 and 0xFFFF
            engine.fillRect(args.r0.toInt(), args.r1.toInt(), width.toInt(), height.toInt(), args.r3.toInt())
            0L
        }

        dispatcher.registerHandler("vm_graphic_draw_text", VmApiTable.GRAPHIC_DRAW_TEXT) { args ->
            // r0=x, r1=y, r2=guest pointer to a null-terminated ASCII string, r3=ARGB8888 color
            val text = readGuestCString(args.memory, args.r2)
            engine.drawText(args.r0.toInt(), args.r1.toInt(), text, args.r3.toInt())
            0L
        }
    }
}
