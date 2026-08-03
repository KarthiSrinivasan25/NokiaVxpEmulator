package com.nokia.vxp.resource

/**
 * Decodes a text-ish resource entry as a string, preferring UTF-8 (the
 * most broadly safe assumption for an unconfirmed encoding) and falling
 * back to Latin-1 (which never throws on arbitrary bytes) if the UTF-8
 * decode looks like it produced replacement characters from invalid
 * sequences - rather than silently returning mojibake with no
 * indication anything went wrong.
 */
object StringResource {
    fun decode(resource: Resource): String {
        val bytes = resource.data
        val utf8 = String(bytes, Charsets.UTF_8)

        val result = if (utf8.contains('\uFFFD') && !String(bytes, Charsets.ISO_8859_1).contains('\uFFFD')) {
            String(bytes, Charsets.ISO_8859_1)
        } else {
            utf8
        }

        return result.trimEnd('\u0000') // strip a trailing null terminator if present
    }
}
