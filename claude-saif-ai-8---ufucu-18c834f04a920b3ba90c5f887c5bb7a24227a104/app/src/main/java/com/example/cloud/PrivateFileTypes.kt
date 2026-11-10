/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.cloud

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale

/** UI/chat policy. The backend independently validates the extension, MIME, size and hash. */
object PrivateFileTypes {
    const val INLINE_LIMIT = 3L * 1024 * 1024
    const val UPLOAD_LIMIT = 300L * 1024 * 1024

    enum class Delivery { INLINE, WEBSITE, UNSUPPORTED }
    data class Selection(val name: String, val mimeType: String, val size: Long?, val delivery: Delivery)

    private val deliverables = mapOf(
        "apk" to "application/vnd.android.package-archive", "zip" to "application/zip",
        "mp4" to "video/mp4", "m4v" to "video/mp4", "mov" to "video/quicktime",
        "webm" to "video/webm", "mkv" to "video/x-matroska", "3gp" to "video/3gpp"
    )
    private val otherFiles = mapOf(
        "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
        "webp" to "image/webp", "pdf" to "application/pdf",
        "txt" to "text/plain", "json" to "application/json",
        "md" to "text/plain", "kt" to "text/plain", "java" to "text/plain",
        "js" to "text/plain", "xml" to "text/plain", "html" to "text/plain",
        "css" to "text/plain", "py" to "text/plain", "sh" to "text/plain", "csv" to "text/plain"
    )

    fun classify(displayName: String, reportedMime: String?, reportedSize: Long?): Selection {
        val filename = displayName.substringAfterLast('/').substringAfterLast('\\').trim()
        val extension = filename.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mime = deliverables[extension] ?: otherFiles[extension]
            ?: reportedMime?.substringBefore(';')?.lowercase(Locale.ROOT)
            ?: "application/octet-stream"
        val size = reportedSize?.takeIf { it >= 0 }
        val route = when {
            extension in deliverables -> Delivery.WEBSITE // even a 1 MB APK is a deliverable, not a chat image
            size != null && size >= INLINE_LIMIT && extension in otherFiles -> Delivery.WEBSITE
            size != null && size >= INLINE_LIMIT -> Delivery.UNSUPPORTED
            mime.startsWith("image/") || mime.startsWith("text/") || mime == "application/json" -> Delivery.INLINE
            else -> Delivery.UNSUPPORTED
        }
        return Selection(filename, mime, size, route)
    }

    /** SAF display names and sizes are metadata hints; stream limits are enforced again during upload. */
    fun inspect(context: Context, uri: Uri): Selection {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "attachment"
        var size: Long? = null
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let { name = cursor.getString(it) ?: name }
                cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let { size = cursor.getLong(it) }
            }
        }
        if (size == null) context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            if (descriptor.length >= 0) size = descriptor.length
        }
        return classify(name, context.contentResolver.getType(uri), size)
    }

    fun safeUploadName(name: String): String {
        val last = name.substringAfterLast('/').substringAfterLast('\\')
        val extension = last.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val base = last.substringBeforeLast('.', last).replace(Regex("[^A-Za-z0-9 _-]"), "_")
            .trim('_', ' ', '-').take(100).ifBlank { "SAIF-file" }
        val cleaned = "$base.$extension"
        require(cleaned.matches(Regex("[A-Za-z0-9][A-Za-z0-9._ -]*\\.[A-Za-z0-9]{2,5}"))) {
            "Rename this file with a safe APK, ZIP or video filename before uploading"
        }
        return cleaned
    }
}
