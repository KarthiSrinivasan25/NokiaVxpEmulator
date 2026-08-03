package com.nokia.vxp.resource

import com.nokia.vxp.utils.Logger

private const val TAG = "AudioResource"

/**
 * Placeholder for audio resource handling - mirrors mre/VmAudio's
 * status: the audio/ module (actual playback) doesn't exist yet, so
 * this just classifies the format (via ResourceType.sniff's confirmed
 * magic-byte detection) and exposes raw bytes for whenever audio/ gets built.
 */
class AudioResource private constructor(val resourceId: Int, val format: ResourceType, val rawData: ByteArray) {
    companion object {
        fun from(resource: Resource): AudioResource? {
            if (!resource.detectedType.isAudio) {
                Logger.w(TAG, "Resource ${resource.id} isn't a recognized audio format (detected: ${resource.detectedType})")
                return null
            }
            return AudioResource(resource.id, resource.detectedType, resource.data)
        }
    }
}
