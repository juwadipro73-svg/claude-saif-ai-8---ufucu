/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.cloud

import android.content.Context
import android.net.Uri
import com.example.studio.export.ExportConfig
import com.example.studio.model.AudioItem
import com.example.studio.model.CodeSceneItem
import com.example.studio.model.ImageItem
import com.example.studio.model.StudioProject
import com.example.studio.model.TextItem
import com.example.studio.model.VideoItem
import com.example.studio.model.VoiceItem
import com.example.ui.components.ProjectData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.util.UUID

/** Plain-text project sources go to private staging as JSON; binary assets use signed streaming PUTs. */
object CloudProjectPayload {
    data class Payload(val project: JSONObject, val assets: List<CloudAsset>)
    private val textExtensions = setOf("java", "kt", "xml", "html", "css", "js", "json", "txt", "svg")
    private val binaryMime = mapOf("png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
        "webp" to "image/webp", "ttf" to "font/ttf", "otf" to "font/otf", "mp3" to "audio/mpeg", "wav" to "audio/wav")

    suspend fun androidProject(context: Context, project: ProjectData): Payload = withContext(Dispatchers.IO) {
        val folder = project.appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "App" }
        val base = File(context.filesDir, "projects/$folder")
        val root = base.canonicalPath + File.separator
        val files = JSONArray()
        val assets = mutableListOf<CloudAsset>()
        var textBytes = 0
        if (base.exists()) base.walkTopDown().filter { it.isFile }.forEach { file ->
            require(!Files.isSymbolicLink(file.toPath()) && file.canonicalPath.startsWith(root)) { "Unsafe project file" }
            val path = file.relativeTo(base).invariantSeparatorsPath
            if (!path.startsWith("app/src/main/")) return@forEach
            val ext = file.extension.lowercase()
            when {
                ext in textExtensions -> {
                    require(file.length() in 0L..512_000L) { "Project source file is too large" }
                    textBytes += file.length().toInt()
                    require(textBytes <= 1_500_000 && files.length() < 80) { "Project source exceeds cloud staging limit" }
                    files.put(JSONObject().put("path", path).put("content", file.readText(Charsets.UTF_8)))
                }
                ext in binaryMime -> {
                    require(assets.size < 20) { "Too many project assets" }
                    assets.add(CloudAsset(UUID.randomUUID().toString(), Uri.fromFile(file), binaryMime.getValue(ext), path))
                }
            }
        }
        Payload(JSONObject().put("type", "android").put("appName", project.appName.take(80))
            .put("packageName", project.packageName).put("files", files), assets)
    }

    suspend fun videoProject(context: Context, project: StudioProject, config: ExportConfig): Payload = withContext(Dispatchers.IO) {
        require(project.items.size in 1..100 && project.totalDurationUs in 1_000_000L..(180L * 60 * 1_000_000)) {
            "Select a timeline with a duration between 1 second and 180 minutes"
        }
        val assets = linkedMapOf<String, CloudAsset>()
        fun media(uri: String, fallbackType: String): String {
            val a = assets.getOrPut(uri) {
                val ref = Uri.parse(uri)
                // File and content URIs are read by Android and uploaded to the owner's private stage.
                require(ref.scheme in listOf("file", "content")) { "Only local project media can be rendered privately" }
                CloudAsset(UUID.randomUUID().toString(), ref,
                    context.contentResolver.getType(ref)?.takeIf { it.matches(Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+")) }
                        ?: fallbackType)
            }
            return a.assetId
        }
        val items = JSONArray()
        for (item in project.items) {
            val segment = JSONObject().put("startUs", item.startUs).put("durationUs", item.durationUs)
            when (item) {
                is TextItem -> segment.put("type", "text").put("text", item.text.take(2000))
                    .put("fontSize", item.fontSizeSp.toDouble()).put("color", "#%06x".format(item.textColorHex and 0xFFFFFF))
                is CodeSceneItem -> segment.put("type", "code").put("title", item.title.take(200))
                    .put("htmlCode", item.htmlCode.take(80_000))
                is ImageItem -> segment.put("type", "image").put("assetId", media(item.mediaUri, "image/png"))
                is VideoItem -> segment.put("type", "video").put("assetId", media(item.mediaUri, "video/mp4"))
                    .put("sourceInUs", item.sourceInUs)
                is AudioItem -> segment.put("type", "audio")
                    .apply { item.audioUri?.let { put("assetId", media(it, "audio/mpeg")) } }
                is VoiceItem -> segment.put("type", "voice").put("text", item.text.take(2000))
                    .apply { item.audioUri?.let { put("assetId", media(it, "audio/mpeg")) } }
            }
            items.put(segment)
        }
        require(assets.size <= 20) { "Too many video assets (max 20)" }
        Payload(JSONObject().put("type", "video").put("name", project.name.take(80))
            .put("width", config.width).put("height", config.height).put("fps", config.fps)
            .put("bitrate", config.bitrate).put("durationUs", project.totalDurationUs).put("items", items), assets.values.toList())
    }
}
