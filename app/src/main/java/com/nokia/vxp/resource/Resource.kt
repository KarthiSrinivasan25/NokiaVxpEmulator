package com.nokia.vxp.resource

/**
 * One generic resource entry extracted from the .vm_res section.
 * [rawTypeId] is repurposed by loader.ResourceLoader's carving approach
 * to record the byte offset the resource was found at within .vm_res,
 * since carving (as opposed to a real, fully-cracked directory parse)
 * has no genuine type-id field to report - see ResourceLoader's doc
 * comment for why carving is used instead.
 */
data class Resource(
    val id: Int,
    val rawTypeId: Int,
    val data: ByteArray,
    val detectedType: ResourceType
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Resource) return false
        return id == other.id &&
            rawTypeId == other.rawTypeId &&
            data.contentEquals(other.data) &&
            detectedType == other.detectedType
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + rawTypeId
        result = 31 * result + data.contentHashCode()
        result = 31 * result + detectedType.hashCode()
        return result
    }
}
