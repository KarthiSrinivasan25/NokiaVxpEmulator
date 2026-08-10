package com.nokia.vxp.mre

import com.nokia.vxp.graphics.GraphicsEngine
import com.nokia.vxp.graphics.Color565
import com.nokia.vxp.utils.Logger
import com.nokia.vxp.memory.MemoryManager

private const val TAG = "VmGraphics"

/**
 * MRE graphics bridge.
 *
 * IMPORTANT: layer/canvas handles are opaque guest pointers backed by the
 * emulator heap. Canvas buffers are real guest-mapped memory, not 0/null.
 * This prevents the classic WRITE_UNMAPPED crash when an MRE game writes
 * directly into the canvas returned by vm_graphic_get_canvas_buffer().
 *
 * The exact MediaTek ABI differs between MRE SDK builds. The implementation
 * therefore keeps the handle opaque and uses conservative dimension parsing.
 */
object VmGraphics {

    private data class Layer(
        val handle: Long,
        val buffer: Long,
        val width: Int,
        val height: Int,
        val x: Int,
        val y: Int,
        val bytesPerPixel: Int = 2,
        var clipLeft: Int = 0,
        var clipTop: Int = 0,
        var clipRight: Int,
        var clipBottom: Int = 0
    )

    fun registerHandlers(
        dispatcher: VmDispatcher,
        engine: GraphicsEngine,
        memory: MemoryManager
    ) {
        var currentColor = 0xFFFFFFFF.toInt()
        val layers = mutableMapOf<Long, Layer>()

        fun sane(v: Long, max: Int = 4096): Boolean =
            v in 1L..max.toLong()

        fun allocateLayer(width0: Int, height0: Int, x: Int = 0, y: Int = 0): Long {
            val width = width0.coerceIn(1, 4096)
            val height = height0.coerceIn(1, 4096)

            // RGB565 is the safest default for MRE canvas memory.
            val bytes = width.toLong() * height.toLong() * 2L
            val buffer = memory.heap.malloc(bytes)
                ?: run {
                    Logger.e(TAG, "Cannot allocate canvas: ${width}x$height ($bytes bytes)")
                    return 0L
                }

            val handle = memory.heap.malloc(64)
                ?: run {
                    memory.heap.free(buffer)
                    Logger.e(TAG, "Cannot allocate layer handle")
                    return 0L
                }

            val layer = Layer(
                handle = handle,
                buffer = buffer,
                width = width,
                height = height,
                x = x,
                y = y,
                clipRight = width,
                clipBottom = height
            )
            layers[handle] = layer

            // Clear the guest VRAM so the first frame is deterministic.
            memory.write(buffer, ByteArray(bytes.toInt()))
            Logger.d(
                TAG,
                "Created layer handle=0x${handle.toString(16)} " +
                    "buffer=0x${buffer.toString(16)} ${width}x$height"
            )
            return handle
        }

        fun dimensionsForCreate(args: VmCallArgs): Triple<Int, Int, Pair<Int, Int>> {
            // Common MRE shape: x,y,width,height.
            if (sane(args.r2) && sane(args.r3)) {
                return Triple(args.r2.toInt(), args.r3.toInt(), Pair(args.r0.toInt(), args.r1.toInt()))
            }
            // Some wrappers use width,height first.
            val w = if (sane(args.r0)) args.r0.toInt() else engine.width
            val h = if (sane(args.r1)) args.r1.toInt() else engine.height
            return Triple(w, h, Pair(0, 0))
        }

        dispatcher.registerHandler("vm_graphic_get_screen_width", VmApiTable.GRAPHIC_GET_SCREEN_WIDTH) {
            engine.width.toLong()
        }

        dispatcher.registerHandler("vm_graphic_get_screen_height", VmApiTable.GRAPHIC_GET_SCREEN_HEIGHT) {
            engine.height.toLong()
        }

        dispatcher.registerHandler("vm_graphic_get_character_height", VmApiTable.GRAPHIC_GET_CHARACTER_HEIGHT) {
            5L
        }

        dispatcher.registerHandler("vm_graphic_get_string_width", VmApiTable.GRAPHIC_GET_STRING_WIDTH) { args ->
            val text = readGuestCString(args.memory, args.r0)
            val scale = if (args.r1 > 0) args.r1.toInt() else 1
            com.nokia.vxp.graphics.FontRenderer.measureWidth(text, scale).toLong()
        }

        dispatcher.registerHandler("vm_graphic_setcolor", VmApiTable.GRAPHIC_SETCOLOR) { args ->
            currentColor = args.r0.toInt()
            0L
        }

        dispatcher.registerHandler("vm_graphic_fill_rect_ex", VmApiTable.GRAPHIC_FILL_RECT_EX) { args ->
            val width = (args.r2 ushr 16) and 0xFFFF
            val height = args.r2 and 0xFFFF
            engine.fillRect(
                args.r0.toInt(), args.r1.toInt(),
                width.toInt(), height.toInt(), currentColor
            )
            0L
        }

        dispatcher.registerHandler("vm_graphic_textout_to_layer", VmApiTable.GRAPHIC_TEXTOUT_TO_LAYER) { args ->
            val text = readGuestCString(args.memory, args.r2)
            engine.drawText(args.r0.toInt(), args.r1.toInt(), text, currentColor)
            0L
        }

        dispatcher.registerHandler("vm_graphic_create_layer", VmApiTable.GRAPHIC_CREATE_LAYER) { args ->
            val (w, h, xy) = dimensionsForCreate(args)
            allocateLayer(w, h, xy.first, xy.second)
        }

        dispatcher.registerHandler("vm_graphic_create_layer_cf", VmApiTable.GRAPHIC_CREATE_LAYER_CF) { args ->
            val (w, h, xy) = dimensionsForCreate(args)
            allocateLayer(w, h, xy.first, xy.second)
        }

        dispatcher.registerHandler("vm_graphic_create_canvas_cf", VmApiTable.GRAPHIC_CREATE_CANVAS_CF) { args ->
            val w = if (sane(args.r0)) args.r0.toInt() else engine.width
            val h = if (sane(args.r1)) args.r1.toInt() else engine.height
            allocateLayer(w, h)
        }

        dispatcher.registerHandler("vm_graphic_get_canvas_buffer", VmApiTable.GRAPHIC_GET_CANVAS_BUFFER) { args ->
            val layer = layers[args.r0]
            if (layer == null) {
                Logger.w(TAG, "get_canvas_buffer: unknown handle=0x${args.r0.toString(16)}")
                0L
            } else {
                layer.buffer
            }
        }

        dispatcher.registerHandler("vm_graphic_delete_layer", VmApiTable.GRAPHIC_DELETE_LAYER) { args ->
            val layer = layers.remove(args.r0)
            if (layer != null) {
                memory.heap.free(layer.buffer)
                memory.heap.free(layer.handle)
            }
            0L
        }

        dispatcher.registerHandler("vm_graphic_release_canvas", VmApiTable.GRAPHIC_RELEASE_CANVAS) { args ->
            // The canvas buffer remains owned by its layer. Freeing it here
            // would make a later get_canvas_buffer return dangling memory.
            0L
        }

        dispatcher.registerHandler("vm_graphic_set_clip", VmApiTable.GRAPHIC_SET_CLIP) { args ->
            val layer = layers[args.r0]
            if (layer != null) {
                layer.clipLeft = args.r1.toInt().coerceIn(0, layer.width)
                layer.clipTop = args.r2.toInt().coerceIn(0, layer.height)
                layer.clipRight = layer.width
                layer.clipBottom = layer.height
            }
            0L
        }

        dispatcher.registerHandler("vm_graphic_set_font", VmApiTable.GRAPHIC_SET_FONT) {
            0L
        }

        dispatcher.registerHandler("vm_graphic_flush_layer", VmApiTable.GRAPHIC_FLUSH_LAYER) { args ->
            val layer = layers[args.r0]
            if (layer != null) {
                presentRgb565Layer(layer, memory, engine)
            }
            0L
        }
    }

    /**
     * Copy a guest RGB565 canvas into the emulator's Android-facing frame
     * buffer. This is deliberately bounded by the allocated layer size.
     */
    private fun presentRgb565Layer(
        layer: Layer,
        memory: MemoryManager,
        engine: GraphicsEngine
    ) {
        val byteCount = layer.width.toLong() * layer.height.toLong() * 2L
        if (byteCount > Int.MAX_VALUE) return

        val raw = memory.read(layer.buffer, byteCount.toInt()) ?: return
        val fb = engine.backBufferForDriver()

        var src = 0
        val maxW = minOf(layer.width, engine.width - layer.x)
        val maxH = minOf(layer.height, engine.height - layer.y)
        if (maxW <= 0 || maxH <= 0) return

        for (y in 0 until maxH) {
            for (x in 0 until maxW) {
                if (src + 1 >= raw.size) return
                val rgb565 =
                    (raw[src].toInt() and 0xFF) or
                    ((raw[src + 1].toInt() and 0xFF) shl 8)
                fb.setPixel(
                    layer.x + x,
                    layer.y + y,
                    Color565.toArgb8888(rgb565)
                )
                src += 2
            }
            // Skip any remainder of the guest row if it extends beyond
            // the visible Android screen.
            src += (layer.width - maxW) * 2
        }
    }

    private fun readGuestCString(memory: com.nokia.vxp.memory.GuestMemoryReader, address: Long): String {
        if (address == 0L) return ""
        val bytes = memory.read(address, 1024) ?: return ""
        val end = bytes.indexOf(0)
        return bytes.copyOf(if (end >= 0) end else bytes.size)
            .toString(Charsets.ISO_8859_1)
    }
}
