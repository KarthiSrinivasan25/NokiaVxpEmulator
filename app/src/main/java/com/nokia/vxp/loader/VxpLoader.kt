package com.nokia.vxp.loader

import android.content.ContentResolver
import android.net.Uri
import com.nokia.vxp.utils.Logger
import java.io.IOException

private const val TAG = "VxpLoader"

sealed class LoadResult {
    data class Success(
        val vxpFile: VxpFile,
        val memoryLayout: ModuleMemoryLayout,
        val log: LoaderLog
    ) : LoadResult()

    data class Failure(
        val reason: String,
        val log: LoaderLog
    ) : LoadResult()
}

/**
 * Entry point for the whole loader/ module. MainActivity/EmulatorActivity
 * call VxpLoader.load(...) with a content Uri picked via SAF; everything
 * downstream (parsing, validation, memory layout) happens here.
 */
object VxpLoader {

    fun load(contentResolver: ContentResolver, uri: Uri, displayName: String? = null): LoadResult {
        val log = LoaderLog()
        val name = displayName ?: uri.lastPathSegment ?: uri.toString()
        log.log("start", "Loading $name")

        val bytes = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: IOException) {
            log.log("io_error", e.message ?: "unknown IO error")
            return LoadResult.Failure("Could not read file: ${e.message}", log)
        }

        if (bytes == null) {
            log.log("io_error", "openInputStream returned null")
            return LoadResult.Failure("Could not open file for reading", log)
        }
        log.log("read", "Read ${bytes.size} bytes")

        return when (val parseResult = VxpParser.parse(name, bytes)) {
            is ParseResult.Failure -> {
                log.log("parse_failed", parseResult.reason)
                Logger.w(TAG, "Parse failed for $name: ${parseResult.reason}")
                LoadResult.Failure(parseResult.reason, log)
            }
            is ParseResult.Success -> {
                log.log(
                    "parsed",
                    "version=${parseResult.file.header.version} " +
                        "code=${parseResult.file.code.size}B " +
                        "data=${parseResult.file.data.size}B " +
                        "resources=${parseResult.file.resources.size}"
                )

                val layout = ModuleMapper.map(parseResult.file)
                log.log(
                    "mapped",
                    "entryPoint=0x${layout.entryPoint.toString(16)} regions=${layout.regions.map { it.name }}"
                )

                LoadResult.Success(parseResult.file, layout, log)
            }
        }
    }
}
