package com.nokia.vxp.resource

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.nokia.vxp.graphics.BitmapBuffer
import com.nokia.vxp.utils.Logger

private const val TAG = "ImageResource"

/**
 * Decodes an image Resource into a graphics.BitmapBuffer. Only handles
 * standard formats Android can already decode (PNG/JPEG/BMP, detected
 * via ResourceType.sniff's confirmed magic-byte signatures). If a real
 * VXP file turns out to use some other proprietary raw pixel format
 * instead, this returns null rather than guessing at an undocumented
 * layout - a proprietary decoder can be added here once a real sample
 * confirms what that format actually looks like.
 */
object ImageResource {

    fun decode(resource: Resource): BitmapBuffer? {
        if (!resource.detectedType.isImage) {
            Logger.w(TAG, "Resource ${resource.id} isn't a recognized image format (detected: ${resource.detectedType}) - cannot decode")
            return null
        }

        val bitmap = BitmapFactory.decodeByteArray(resource.data, 0, resource.data.size)
        if (bitmap == null) {
            Logger.w(TAG, "BitmapFactory failed to decode resource ${resource.id} despite matching a known image signature")
            return null
        }

        return try {
            toBitmapBuffer(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun toBitmapBuffer(bitmap: Bitmap): BitmapBuffer {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return BitmapBuffer(width, height, pixels)
    }
}
