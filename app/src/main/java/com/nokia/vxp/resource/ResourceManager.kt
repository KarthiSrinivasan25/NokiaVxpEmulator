package com.nokia.vxp.resource

import com.nokia.vxp.graphics.BitmapBuffer

/**
 * Holds all resources parsed from one loaded VXP module's .vm_res
 * section, indexed by id, with type-specific decode helpers. Decoded
 * results (e.g. BitmapBuffer) are cached after first access, since
 * decoding isn't free and a game might reference the same sprite many
 * times per frame.
 */
class ResourceManager(private val resources: List<Resource>) {

    private val byId: Map<Int, Resource> = resources.associateBy { it.id }
    private val imageCache = mutableMapOf<Int, BitmapBuffer?>()
    private val stringCache = mutableMapOf<Int, String?>()

    fun get(id: Int): Resource? = byId[id]
    fun all(): List<Resource> = resources
    fun count(): Int = resources.size

    fun getImage(id: Int): BitmapBuffer? = imageCache.getOrPut(id) {
        byId[id]?.let { ImageResource.decode(it) }
    }

    fun getString(id: Int): String? = stringCache.getOrPut(id) {
        byId[id]?.let { StringResource.decode(it) }
    }

    fun getFont(id: Int): FontResource? = byId[id]?.let { FontResource.from(it) }
    fun getAudio(id: Int): AudioResource? = byId[id]?.let { AudioResource.from(it) }

    fun summary(): String {
        val byType = resources.groupingBy { it.detectedType }.eachCount()
        return "ResourceManager: ${resources.size} entries, by detected type: $byType"
    }

    companion object {
        fun from(vmResData: ByteArray?): ResourceManager = ResourceManager(ResourceLoader.parse(vmResData))
    }
}
