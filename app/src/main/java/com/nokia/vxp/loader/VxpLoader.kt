package com.nokia.vxp.loader

import android.content.ContentResolver
import android.net.Uri
import com.nokia.vxp.utils.Constants
import com.nokia.vxp.utils.Logger
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.InflaterInputStream
import java.util.zip.ZipInputStream

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
 * Entry point for the whole loader/ module. Handles all three known VXP
 * container forms before handing bytes to VxpParser:
 *  - raw ELF (most common for simpler/newer tooling, e.g. RePhone-built apps)
 *  - zlib-wrapped ELF (first byte 0x78 - the common MediaTek SDK output form)
 *  - ZIP-wrapped ELF + assets (magic "PK\x03\x04" - scans entries for the ELF payload)
 * See utils.Constants for citations on this format.
 */
object VxpLoader {

    fun load(contentResolver: ContentResolver, uri: Uri, displayName: String? = null): LoadResult {
        val log = LoaderLog()
        val name = displayName ?: uri.lastPathSegment ?: uri.toString()
        log.log("start", "Loading $name")

        val rawBytes = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: IOException) {
            log.log("io_error", e.message ?: "unknown IO error")
            return LoadResult.Failure("Could not read file: ${e.message}", log)
        }

        if (rawBytes == null) {
            log.log("io_error", "openInputStream returned null")
            return LoadResult.Failure("Could not open file for reading", log)
        }
        log.log("read", "Read ${rawBytes.size} bytes")

        val elfBytes = when (val unwrapped = unwrapContainer(rawBytes, log)) {
            is UnwrapResult.Success -> unwrapped.elfBytes
            is UnwrapResult.Failure -> {
                log.log("unwrap_failed", unwrapped.reason)
                return LoadResult.Failure(unwrapped.reason, log)
            }
        }

        return when (val parseResult = VxpParser.parse(name, elfBytes)) {
            is ParseResult.Failure -> {
                log.log("parse_failed", parseResult.reason)
                Logger.w(TAG, "Parse failed for $name: ${parseResult.reason}")
                LoadResult.Failure(parseResult.reason, log)
            }
            is ParseResult.Success -> {
                val file = parseResult.file
                log.log(
                    "parsed",
                    "entry=0x${file.header.entryPoint.toString(16)} " +
                        "segments=${file.programHeaders.count { it.isLoadable }} " +
                        "vm_res=${file.resourceSectionData?.size ?: 0}B tags=${file.tags.size}"
                )

                val layout = ModuleMapper.map(file)
                log.log(
                    "mapped",
                    "entryPoint=0x${layout.entryPoint.toString(16)} thumb=${layout.isThumbEntry} " +
                        "regions=${layout.regions.map { it.name }}"
                )

                LoadResult.Success(file, layout, log)
            }
        }
    }

    internal sealed class UnwrapResult {
        data class Success(val elfBytes: ByteArray) : UnwrapResult()
        data class Failure(val reason: String) : UnwrapResult()
    }

    internal fun unwrapContainer(bytes: ByteArray, log: LoaderLog): UnwrapResult {
        if (startsWith(bytes, Constants.ELF_MAGIC)) {
            log.log("container", "Raw ELF (no wrapper)")
            return UnwrapResult.Success(bytes)
        }

        if (startsWith(bytes, Constants.ZIP_MAGIC)) {
            log.log("container", "ZIP-wrapped - scanning entries for an ELF payload")
            return unwrapZip(bytes, log)
        }

        if (bytes.isNotEmpty() && bytes[0] == Constants.ZLIB_MAGIC_BYTE) {
            log.log("container", "zlib-wrapped (first byte 0x78) - inflating")
            return unwrapZlib(bytes, log)
        }

        return UnwrapResult.Failure(
            "Unrecognized file: not raw ELF, not ZIP, and doesn't start with the zlib magic byte " +
                "(0x${bytes.getOrNull(0)?.let { "%02X".format(it) } ?: "??"}). " +
                "This doesn't match any known VXP container format."
        )
    }

    internal fun unwrapZlib(bytes: ByteArray, log: LoaderLog): UnwrapResult {
        return try {
            val inflated = ByteArrayOutputStream()
            InflaterInputStream(bytes.inputStream()).use { it.copyTo(inflated) }
            val result = inflated.toByteArray()

            if (!startsWith(result, Constants.ELF_MAGIC)) {
                return UnwrapResult.Failure(
                    "Inflated zlib data (${result.size} bytes) doesn't start with the ELF magic - " +
                        "this VXP's wrapper format may differ from what was expected"
                )
            }
            log.log("unwrap", "Inflated to ${result.size} bytes")
            UnwrapResult.Success(result)
        } catch (e: Exception) {
            UnwrapResult.Failure("zlib decompression failed: ${e.message}")
        }
    }

    internal fun unwrapZip(bytes: ByteArray, log: LoaderLog): UnwrapResult {
        return try {
            ZipInputStream(bytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val entryBytes = ByteArrayOutputStream().apply { zip.copyTo(this) }.toByteArray()
                    if (startsWith(entryBytes, Constants.ELF_MAGIC)) {
                        log.log("unwrap", "Found ELF payload in ZIP entry '${entry.name}' (${entryBytes.size} bytes)")
                        return UnwrapResult.Success(entryBytes)
                    }
                    log.log("unwrap", "Skipping non-ELF ZIP entry '${entry.name}' (${entryBytes.size} bytes) - likely a bundled resource")
                    entry = zip.nextEntry
                }
            }
            UnwrapResult.Failure("ZIP container didn't contain any entry starting with the ELF magic bytes")
        } catch (e: Exception) {
            UnwrapResult.Failure("ZIP extraction failed: ${e.message}")
        }
    }

    internal fun startsWith(bytes: ByteArray, prefix: ByteArray): Boolean {
        if (bytes.size < prefix.size) return false
        for (i in prefix.indices) {
            if (bytes[i] != prefix[i]) return false
        }
        return true
    }
}
