package com.nokia.vxp.resource

import com.nokia.vxp.utils.Logger

private const val TAG = "FontResource"

/**
 * Placeholder for font resource decoding. Real VXP/MRE bitmap font
 * layouts aren't documented anywhere found during research for this
 * project, and Android has no built-in decoder for an arbitrary custom
 * font format (unlike images, where BitmapFactory covers the common
 * cases) - so this class deliberately does NOT attempt to guess a
 * glyph-table layout that could render as plausible-looking garbage.
 * It just captures the raw bytes for whenever a real sample's font
 * format gets reverse-engineered. graphics.FontRenderer's built-in
 * 7-segment digits remain the only real text rendering available until then.
 */
class FontResource private constructor(val resourceId: Int, val rawData: ByteArray) {
    companion object {
        fun from(resource: Resource): FontResource? {
            if (resource.data.isEmpty()) return null
            Logger.i(
                TAG,
                "Resource ${resource.id} captured as raw font data (${resource.data.size} bytes) - " +
                    "no glyph decoder available yet, see FontResource's doc comment"
            )
            return FontResource(resource.id, resource.data)
        }
    }
}
