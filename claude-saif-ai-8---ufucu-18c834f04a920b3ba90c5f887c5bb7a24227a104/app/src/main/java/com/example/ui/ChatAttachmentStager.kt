/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.ui

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import com.example.gemini.domain.model.ChatAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

data class StagedAttachmentsBundle(
    val inlineAttachments: List<ChatAttachment>,
    val promptContextBlock: String
)

object ChatAttachmentStager {

    private const val MAX_DIRECT_INLINE_BYTES = 1_600_000 // ~1.6 MB safe per inline attachment
    private const val MAX_TOTAL_INLINE_BYTES = 2_600_000  // Keep well below 3 MB hub payload cap
    private const val MAX_TEXT_PREVIEW_BYTES = 96 * 1024

    private val textExtensions = setOf(
        "txt", "md", "markdown", "json", "jsonl", "yaml", "yml", "xml", "html", "htm", "css",
        "js", "jsx", "ts", "tsx", "kt", "kts", "java", "py", "sh", "bash", "c", "cpp", "h",
        "hpp", "rs", "go", "rb", "php", "sql", "csv", "tsv", "ini", "toml", "gradle", "properties", "log"
    )

    private val videoExtensions = setOf("mp4", "mkv", "mov", "webm", "avi", "3gp", "m4v")
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")
    private val audioExtensions = setOf("mp3", "wav", "m4a", "aac", "ogg", "flac", "opus")
    private val archiveExtensions = setOf("apk", "zip", "jar", "aab", "tar", "gz")

    suspend fun prepareForAntigravity(
        context: Context,
        uris: List<Uri>
    ): StagedAttachmentsBundle = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext StagedAttachmentsBundle(emptyList(), "")

        val ubuntuUploadsDir = File(context.filesDir, "ubuntu/root/uploads").apply { mkdirs() }
        val workspaceUploadsDir = File(context.filesDir, "home/uploads").apply { mkdirs() }

        val inlineList = mutableListOf<ChatAttachment>()
        val summaryBlocks = mutableListOf<String>()
        var totalInlineBytes = 0

        uris.forEachIndexed { index, uri ->
            val rawName = queryDisplayName(context, uri) ?: "attachment_${index + 1}"
            val safeName = sanitizeFileName(rawName)
            val ext = safeName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val resolvedMime = resolveMimeType(context, uri, ext)

            // 1. Copy full file into Linux /root/uploads/<safeName> and /root/workspace/uploads/<safeName>
            val linuxRootFile = uniqueTargetFile(ubuntuUploadsDir, safeName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                linuxRootFile.outputStream().use { output -> input.copyTo(output, bufferSize = 64 * 1024) }
            } ?: error("Could not read attached file: $rawName")

            val workspaceFile = File(workspaceUploadsDir, linuxRootFile.name)
            runCatching {
                if (linuxRootFile.length() <= 150L * 1024 * 1024) {
                    linuxRootFile.copyTo(workspaceFile, overwrite = true)
                }
            }

            val linuxPath = "/root/uploads/${linuxRootFile.name}"
            val workspacePath = "/root/workspace/uploads/${linuxRootFile.name}"
            val sizeBytes = linuxRootFile.length()
            val humanSize = formatBytes(sizeBytes)

            val isVideo = resolvedMime.startsWith("video/") || ext in videoExtensions
            val isImage = resolvedMime.startsWith("image/") || ext in imageExtensions
            val isAudio = resolvedMime.startsWith("audio/") || ext in audioExtensions
            val isArchive = ext in archiveExtensions || resolvedMime.contains("zip") ||
                resolvedMime == "application/vnd.android.package-archive"
            val isText = resolvedMime.startsWith("text/") || resolvedMime == "application/json" || ext in textExtensions

            when {
                isVideo -> {
                    val (videoMeta, frameAttachments) = inspectAndExtractVideoFrames(
                        file = linuxRootFile,
                        baseName = linuxRootFile.name,
                        remainingBudget = (MAX_TOTAL_INLINE_BYTES - totalInlineBytes).coerceAtLeast(0)
                    )
                    frameAttachments.forEach { att ->
                        inlineList.add(att)
                        totalInlineBytes += (att.base64?.length ?: 0) * 3 / 4
                    }
                    summaryBlocks.add(
                        buildString {
                            appendLine("- **Attached Video:** `${linuxRootFile.name}` ($humanSize, `$resolvedMime`)")
                            appendLine("  - Staged in Linux at: `$linuxPath` (also `$workspacePath`)")
                            if (videoMeta.isNotBlank()) appendLine("  - Video Metadata: $videoMeta")
                            if (frameAttachments.isNotEmpty()) {
                                appendLine("  - Visual Preview: ${frameAttachments.size} keyframes extracted from the video and attached inline as images so you can see the video content directly.")
                            }
                            append("  - You can answer questions about what is in this video directly from the keyframes & metadata, or run `ffprobe`/`ffmpeg`/Python on `$linuxPath` if deeper processing or editing is requested.")
                        }
                    )
                }

                isImage -> {
                    val imgBytes = encodeImageWithinBudget(
                        file = linuxRootFile,
                        mime = resolvedMime,
                        maxBytes = minOf(MAX_DIRECT_INLINE_BYTES, (MAX_TOTAL_INLINE_BYTES - totalInlineBytes).coerceAtLeast(0))
                    )
                    if (imgBytes != null) {
                        val actualMime = if (sizeBytes > MAX_DIRECT_INLINE_BYTES) "image/jpeg" else resolvedMime
                        inlineList.add(
                            ChatAttachment(
                                name = linuxRootFile.name,
                                path = linuxPath,
                                localUri = uri.toString(),
                                isImage = true,
                                isAudio = false,
                                size = imgBytes.size.toLong(),
                                mimeType = actualMime,
                                base64 = Base64.encodeToString(imgBytes, Base64.NO_WRAP)
                            )
                        )
                        totalInlineBytes += imgBytes.size
                    }
                    summaryBlocks.add(
                        "- **Attached Image:** `${linuxRootFile.name}` ($humanSize, `$resolvedMime`) — staged in Linux at `$linuxPath` (and `$workspacePath`)."
                    )
                }

                isArchive -> {
                    val archiveSummary = inspectArchiveOrApk(context, linuxRootFile, ext)
                    summaryBlocks.add(
                        buildString {
                            appendLine("- **Attached ${ext.uppercase(Locale.ROOT).ifBlank { "Archive" }} File:** `${linuxRootFile.name}` ($humanSize, `$resolvedMime`)")
                            appendLine("  - Staged in Linux at: `$linuxPath` (also `$workspacePath`)")
                            if (archiveSummary.isNotBlank()) appendLine(archiveSummary)
                            append("  - Inspect, extract, modify, or use `$linuxPath` directly with shell/Python tools if needed.")
                        }
                    )
                }

                isText -> {
                    val previewBytes = linuxRootFile.inputStream().use { inp ->
                        val buf = ByteArray(minOf(sizeBytes, MAX_TEXT_PREVIEW_BYTES.toLong()).toInt())
                        val n = inp.read(buf)
                        if (n > 0) buf.copyOf(n) else ByteArray(0)
                    }
                    if (previewBytes.isNotEmpty() && totalInlineBytes + previewBytes.size <= MAX_TOTAL_INLINE_BYTES) {
                        inlineList.add(
                            ChatAttachment(
                                name = linuxRootFile.name,
                                path = linuxPath,
                                localUri = uri.toString(),
                                isImage = false,
                                isAudio = false,
                                size = previewBytes.size.toLong(),
                                mimeType = if (resolvedMime.startsWith("text/") || resolvedMime == "application/json") resolvedMime else "text/plain",
                                base64 = Base64.encodeToString(previewBytes, Base64.NO_WRAP)
                            )
                        )
                        totalInlineBytes += previewBytes.size
                    }
                    summaryBlocks.add(
                        "- **Attached Text/Code File:** `${linuxRootFile.name}` ($humanSize) — full file staged in Linux at `$linuxPath` (and `$workspacePath`)."
                    )
                }

                isAudio -> {
                    val audioMeta = inspectAudioMetadata(linuxRootFile)
                    if (sizeBytes in 1..MAX_DIRECT_INLINE_BYTES.toLong() &&
                        totalInlineBytes + sizeBytes.toInt() <= MAX_TOTAL_INLINE_BYTES
                    ) {
                        val bytes = linuxRootFile.readBytes()
                        inlineList.add(
                            ChatAttachment(
                                name = linuxRootFile.name,
                                path = linuxPath,
                                localUri = uri.toString(),
                                isImage = false,
                                isAudio = true,
                                size = bytes.size.toLong(),
                                mimeType = resolvedMime,
                                base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            )
                        )
                        totalInlineBytes += bytes.size
                    }
                    summaryBlocks.add(
                        buildString {
                            append("- **Attached Audio:** `${linuxRootFile.name}` ($humanSize, `$resolvedMime`) — staged in Linux at `$linuxPath`.")
                            if (audioMeta.isNotBlank()) append(" Metadata: $audioMeta")
                        }
                    )
                }

                else -> {
                    summaryBlocks.add(
                        "- **Attached File:** `${linuxRootFile.name}` ($humanSize, `$resolvedMime`) — staged in Linux at `$linuxPath` (also `$workspacePath`). Inspect or process it directly using your Linux workspace tools."
                    )
                }
            }
        }

        val contextBlock = if (summaryBlocks.isNotEmpty()) {
            buildString {
                appendLine("Attached user files available in your local Linux environment:")
                summaryBlocks.forEach { appendLine(it) }
            }.trim()
        } else ""

        StagedAttachmentsBundle(
            inlineAttachments = inlineList,
            promptContextBlock = contextBlock
        )
    }

    private fun inspectAndExtractVideoFrames(
        file: File,
        baseName: String,
        remainingBudget: Int
    ): Pair<String, List<ChatAttachment>> {
        val frames = mutableListOf<ChatAttachment>()
        val metaParts = mutableListOf<String>()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()

            if (durationMs > 0) {
                val totalSec = durationMs / 1000
                metaParts.add("duration=${totalSec / 60}m ${totalSec % 60}s (${durationMs}ms)")
            }
            if (width > 0 && height > 0) {
                metaParts.add("resolution=${width}x${height}" + if (rotation != 0) " (rot ${rotation}°)" else "")
            }
            if (!hasAudio.isNullOrBlank()) {
                metaParts.add("hasAudio=${hasAudio == "yes"}")
            }
            if (bitrate != null && bitrate > 0) {
                metaParts.add("bitrate=${bitrate / 1000} kbps")
            }

            if (remainingBudget > 80_000) {
                val fractions = if (durationMs >= 2_500L) listOf(0.10, 0.36, 0.64, 0.90) else listOf(0.25)
                var usedBytes = 0
                fractions.forEachIndexed { idx, frac ->
                    if (usedBytes + 70_000 > remainingBudget) return@forEachIndexed
                    val timeUs = if (durationMs > 0) ((durationMs * frac).toLong() * 1000L) else 0L
                    val bmp = runCatching {
                        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: retriever.getFrameAtTime(timeUs)
                    }.getOrNull() ?: return@forEachIndexed

                    val scaled = downscaleBitmap(bmp, maxDim = 640)
                    val out = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 76, out)
                    if (scaled !== bmp) scaled.recycle()
                    bmp.recycle()

                    val jpegBytes = out.toByteArray()
                    if (jpegBytes.isNotEmpty() && usedBytes + jpegBytes.size <= remainingBudget) {
                        val stampSec = timeUs / 1_000_000L
                        frames.add(
                            ChatAttachment(
                                name = "${baseName}_frame_${idx + 1}_${stampSec}s.jpg",
                                path = "",
                                localUri = "",
                                isImage = true,
                                isAudio = false,
                                size = jpegBytes.size.toLong(),
                                mimeType = "image/jpeg",
                                base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                            )
                        )
                        usedBytes += jpegBytes.size
                    }
                }
            }
        } catch (_: Throwable) {
            // Retriever may fail on corrupted/non-standard containers; file is still staged in /root/uploads
        } finally {
            runCatching { retriever.release() }
        }
        return metaParts.joinToString(", ") to frames
    }

    private fun inspectAudioMetadata(file: File): String {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            buildList {
                if (durMs > 0) add("duration=${durMs / 1000}s")
                if (!title.isNullOrBlank()) add("title=$title")
                if (!artist.isNullOrBlank()) add("artist=$artist")
            }.joinToString(", ")
        } catch (_: Throwable) {
            ""
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun inspectArchiveOrApk(context: Context, file: File, ext: String): String {
        val lines = mutableListOf<String>()
        if (ext == "apk") {
            runCatching {
                @Suppress("DEPRECATION")
                val pkgInfo = context.packageManager.getPackageArchiveInfo(
                    file.absolutePath,
                    android.content.pm.PackageManager.GET_PERMISSIONS
                )
                if (pkgInfo != null) {
                    val verCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        pkgInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        pkgInfo.versionCode.toLong()
                    }
                    val minSdk = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        pkgInfo.applicationInfo?.minSdkVersion ?: 0
                    } else 0
                    val targetSdk = pkgInfo.applicationInfo?.targetSdkVersion ?: 0
                    lines.add("  - APK Package: `${pkgInfo.packageName}`, versionName=`${pkgInfo.versionName ?: "N/A"}` ($verCode), minSdk=$minSdk, targetSdk=$targetSdk")
                    val perms = pkgInfo.requestedPermissions?.take(12).orEmpty()
                    if (perms.isNotEmpty()) {
                        lines.add("  - Requested Permissions (sample): ${perms.joinToString(", ")}")
                    }
                }
            }
        }
        runCatching {
            ZipFile(file).use { zf ->
                val totalEntries = zf.size()
                val sampleNames = zf.entries().asSequence()
                    .filter { !it.isDirectory }
                    .take(25)
                    .map { it.name }
                    .toList()
                lines.add("  - Archive Entries ($totalEntries total): ${sampleNames.joinToString(", ")}")
            }
        }
        return lines.joinToString("\n")
    }

    private fun encodeImageWithinBudget(file: File, mime: String, maxBytes: Int): ByteArray? {
        if (maxBytes <= 16_384) return null
        if (file.length() in 1..maxBytes.toLong()) {
            return runCatching { file.readBytes() }.getOrNull()
        }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) {
                sample *= 2
            }
            val decoded = BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return null
            val scaled = downscaleBitmap(decoded, maxDim = 1280)
            val out = ByteArrayOutputStream()
            val fmt = if (mime == "image/png" && scaled.width * scaled.height < 600_000) {
                Bitmap.CompressFormat.PNG
            } else {
                Bitmap.CompressFormat.JPEG
            }
            scaled.compress(fmt, 82, out)
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
            out.toByteArray().takeIf { it.size <= maxBytes }
        } catch (_: Throwable) {
            null
        }
    }

    private fun downscaleBitmap(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxDim && h <= maxDim) return src
        val ratio = minOf(maxDim.toFloat() / w.toFloat(), maxDim.toFloat() / h.toFloat())
        val nw = (w * ratio).toInt().coerceAtLeast(1)
        val nh = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else uri.lastPathSegment
        } catch (_: Exception) {
            uri.lastPathSegment
        } finally {
            cursor?.close()
        }
    }

    private fun resolveMimeType(context: Context, uri: Uri, ext: String): String {
        val crMime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        if (!crMime.isNullOrBlank() && crMime != "application/octet-stream") return crMime
        if (ext == "apk") return "application/vnd.android.package-archive"
        if (ext == "mp4") return "video/mp4"
        if (ext == "zip") return "application/zip"
        val mapMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        return mapMime ?: "application/octet-stream"
    }

    private fun sanitizeFileName(raw: String): String {
        val cleaned = raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
        return cleaned.ifBlank { "attachment.bin" }
    }

    private fun uniqueTargetFile(dir: File, name: String): File {
        val direct = File(dir, name)
        if (!direct.exists()) return direct
        val stem = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        val stamp = (System.currentTimeMillis() % 100_000)
        val candidateName = if (ext.isNotBlank() && ext != name) "${stem}_$stamp.$ext" else "${stem}_$stamp"
        return File(dir, candidateName)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
