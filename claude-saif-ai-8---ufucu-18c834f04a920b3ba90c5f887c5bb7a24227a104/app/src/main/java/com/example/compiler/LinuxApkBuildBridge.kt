/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.compiler

import android.content.Context
import com.example.builder.AutonomousAppBuilder
import com.example.gemini.data.remote.AndroidLocalBridgeServer
import com.example.ui.components.ProjectData
import com.example.util.ProjectDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

object LinuxApkBuildBridge {

    /**
     * Resolves a path seen inside the PRoot Ubuntu environment (`/root/workspace/...` or `/root/...`)
     * to its underlying Android host `File` inside `context.filesDir`.
     */
    fun resolveLinuxPathOnHost(context: Context, rawPath: String): File? {
        val cleaned = rawPath.trim()
            .removePrefix("file://")
            .substringBefore('#')
            .substringBefore('?')
        if (cleaned.isBlank() || cleaned.split('/', '\\').any { it == ".." }) return null

        val filesCanonical = runCatching { context.filesDir.canonicalPath }.getOrDefault(context.filesDir.absolutePath)
        val cacheCanonical = runCatching { context.cacheDir.canonicalPath }.getOrDefault(context.cacheDir.absolutePath)

        // Direct host path inside app sandbox
        val direct = File(cleaned)
        if (direct.exists()) {
            val can = runCatching { direct.canonicalPath }.getOrDefault("")
            if (can.startsWith(filesCanonical) || can.startsWith(cacheCanonical)) return direct
        }

        // /root/workspace is bind-mounted to filesDir/home
        if (cleaned == "/root/workspace" || cleaned.startsWith("/root/workspace/")) {
            val sub = cleaned.removePrefix("/root/workspace").trimStart('/')
            val homeRoot = File(context.filesDir, "home")
            val candidate = if (sub.isEmpty()) homeRoot else File(homeRoot, sub)
            val homeCan = runCatching { homeRoot.canonicalPath }.getOrDefault(homeRoot.absolutePath)
            val candCan = runCatching { candidate.canonicalPath }.getOrDefault("")
            return if (candCan == homeCan || candCan.startsWith("$homeCan${File.separator}")) candidate else null
        }

        // General Linux rootfs path mapped to filesDir/ubuntu/<path>
        if (cleaned.startsWith("/")) {
            val ubuntuRoot = File(context.filesDir, "ubuntu")
            val candidate = File(ubuntuRoot, cleaned.trimStart('/'))
            val ubuntuCan = runCatching { ubuntuRoot.canonicalPath }.getOrDefault(ubuntuRoot.absolutePath)
            val candCan = runCatching { candidate.canonicalPath }.getOrDefault("")
            if (candCan == ubuntuCan || candCan.startsWith("$ubuntuCan${File.separator}")) {
                return candidate
            }
        }

        return null
    }

    /**
     * Installs `/usr/local/bin/saif-build-apk` and `/usr/local/bin/saif-apk-link` inside the
     * Ubuntu rootfs so Antigravity can compile native signed Android APKs and mint download
     * links directly from its normal Linux terminal commands in chat.
     */
    fun installLinuxCliTools(context: Context, boundPort: Int, deviceId: String) {
        if (deviceId.length <= 5) return
        runCatching {
            val envDirs = listOf(
                File(context.filesDir, "ubuntu/root/.config"),
                File(context.filesDir, "home/.config")
            )
            val envText = buildString {
                appendLine("SAIF_BRIDGE_URL=\"http://127.0.0.1:$boundPort\"")
                appendLine("SAIF_DEVICE_ID=\"$deviceId\"")
            }
            for (d in envDirs) {
                d.mkdirs()
                val f = File(d, "saif-bridge.env")
                f.writeText(envText)
                f.setReadable(false, false)
                f.setReadable(true, true)
            }

            val binDir = File(context.filesDir, "ubuntu/usr/local/bin").apply { mkdirs() }

            val buildScript = File(binDir, "saif-build-apk")
            buildScript.writeText(
                """#!/bin/sh
set -e
ENV_FILE="/root/.config/saif-bridge.env"
[ -f "${'$'}ENV_FILE" ] || ENV_FILE="/root/workspace/.config/saif-bridge.env"
if [ ! -f "${'$'}ENV_FILE" ]; then
  echo "ERROR: SAIF bridge environment file not found" >&2
  exit 1
fi
. "${'$'}ENV_FILE"

PROJECT_DIR=""
APP_NAME=""
PKG_NAME=""

while [ "${'$'}#" -gt 0 ]; do
  case "${'$'}1" in
    --name|-n)
      APP_NAME="${'$'}2"
      shift 2
      ;;
    --package|-p)
      PKG_NAME="${'$'}2"
      shift 2
      ;;
    *)
      if [ -z "${'$'}PROJECT_DIR" ]; then
        PROJECT_DIR="${'$'}1"
      fi
      shift
      ;;
  esac
done

if [ -z "${'$'}PROJECT_DIR" ]; then
  PROJECT_DIR="${'$'}(pwd)"
fi
case "${'$'}PROJECT_DIR" in
  /*) ;;
  *) PROJECT_DIR="${'$'}(cd "${'$'}PROJECT_DIR" && pwd)" ;;
esac

python3 - "${'$'}SAIF_BRIDGE_URL/api/builder/compile-apk" "${'$'}SAIF_DEVICE_ID" "${'$'}PROJECT_DIR" "${'$'}APP_NAME" "${'$'}PKG_NAME" <<'PY'
import json, sys, urllib.request, urllib.error

url, dev_id, proj_dir, app_name, pkg_name = sys.argv[1:6]
payload = json.dumps({
    "projectDir": proj_dir,
    "appName": app_name,
    "packageName": pkg_name
}).encode("utf-8")

req = urllib.request.Request(
    url,
    data=payload,
    headers={"Content-Type": "application/json", "X-Device-Id": dev_id},
    method="POST"
)
try:
    with urllib.request.urlopen(req, timeout=180) as resp:
        data = json.loads(resp.read().decode("utf-8"))
except urllib.error.HTTPError as e:
    err_body = e.read().decode("utf-8", errors="replace")
    print(f"BUILD_FAILED: {err_body}", file=sys.stderr)
    sys.exit(1)

if not data.get("success"):
    print("BUILD_FAILED: " + str(data.get("error", "Unknown error")), file=sys.stderr)
    sys.exit(1)

print("APK_BUILT=" + str(data.get("linuxApkPath", "")))
print("DOWNLOAD_URL=" + str(data.get("downloadUrl", "")))
print("SIZE_BYTES=" + str(data.get("sizeBytes", 0)))
PY
"""
            )
            buildScript.setExecutable(true, false)
            buildScript.setReadable(true, false)

            val linkScript = File(binDir, "saif-apk-link")
            linkScript.writeText(
                """#!/bin/sh
set -e
ENV_FILE="/root/.config/saif-bridge.env"
[ -f "${'$'}ENV_FILE" ] || ENV_FILE="/root/workspace/.config/saif-bridge.env"
. "${'$'}ENV_FILE"

TARGET="${'$'}1"
if [ -z "${'$'}TARGET" ]; then
  echo "Usage: saif-apk-link <file-path>" >&2
  exit 1
fi
case "${'$'}TARGET" in
  /*) ;;
  *) TARGET="${'$'}(cd "${'$'}(dirname "${'$'}TARGET")" && pwd)/${'$'}(basename "${'$'}TARGET")" ;;
esac

python3 - "${'$'}SAIF_BRIDGE_URL/api/builder/publish-file" "${'$'}SAIF_DEVICE_ID" "${'$'}TARGET" <<'PY'
import json, sys, urllib.request, urllib.error

url, dev_id, target = sys.argv[1:4]
payload = json.dumps({"linuxPath": target}).encode("utf-8")
req = urllib.request.Request(
    url,
    data=payload,
    headers={"Content-Type": "application/json", "X-Device-Id": dev_id},
    method="POST"
)
try:
    with urllib.request.urlopen(req, timeout=60) as resp:
        data = json.loads(resp.read().decode("utf-8"))
except urllib.error.HTTPError as e:
    print("PUBLISH_FAILED: " + e.read().decode("utf-8", errors="replace"), file=sys.stderr)
    sys.exit(1)

if not data.get("success"):
    print("PUBLISH_FAILED: " + str(data.get("error", "Unknown error")), file=sys.stderr)
    sys.exit(1)

print("DOWNLOAD_URL=" + str(data.get("downloadUrl", "")))
PY
"""
            )
            linkScript.setExecutable(true, false)
            linkScript.setReadable(true, false)
        }
    }

    /**
     * Compiles a project directory written by Antigravity inside Linux (`/root/workspace/...` or `/root/...`)
     * into a signed v1/v2/v3 Android APK, copies the `.apk` back into `<projectDir>/build/`, saves to
     * Phone `Downloads/`, and returns JSON with `downloadUrl` and `linuxApkPath`.
     */
    suspend fun compileLinuxDirectoryToApk(context: Context, requestJson: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val rawDir = requestJson.optString("projectDir").trim()
            val hostSourceDir = resolveLinuxPathOnHost(context, rawDir)
                ?: return@withContext JSONObject()
                    .put("success", false)
                    .put("error", "Project directory not found: $rawDir")

            if (!hostSourceDir.exists() || !hostSourceDir.isDirectory) {
                return@withContext JSONObject()
                    .put("success", false)
                    .put("error", "Directory does not exist: $rawDir")
            }

            val inferredName = requestJson.optString("appName").trim().ifBlank {
                hostSourceDir.name
                    .replace(Regex("[-_]+"), " ")
                    .split(' ')
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { w -> w.replaceFirstChar { c -> c.titlecase(Locale.US) } }
                    .ifBlank { "SAIF App" }
            }
            val cleanPkg = AppCompilerEngine.sanitizePackageName(
                requestJson.optString("packageName").trim(),
                inferredName
            )

            val project = ProjectData(
                appName = inferredName,
                packageName = cleanPkg,
                minSdk = 24,
                targetSdk = 34,
                buildStudio = "SAIF AI Linux Builder",
                language = "Java",
                iconBitmap = ProjectDataManager.loadProjectIcon(context, inferredName)
            )

            val safeFolder = inferredName.replace(Regex("[^a-zA-Z0-9_]"), "_").trim('_').ifBlank { "SaifApp" }
            val buildProjectDir = File(File(context.filesDir, "projects"), safeFolder)
            if (buildProjectDir.exists()) buildProjectDir.deleteRecursively()
            buildProjectDir.mkdirs()

            // Detect whether source dir is a native Java Android project or a web/HTML5 app
            val hasManifest = File(hostSourceDir, "app/src/main/AndroidManifest.xml").isFile ||
                File(hostSourceDir, "AndroidManifest.xml").isFile
            val javaFiles = hostSourceDir.walkTopDown()
                .maxDepth(8)
                .filter { it.isFile && it.extension.equals("java", true) && !it.path.contains("/build/") }
                .toList()
            val htmlEntry = listOf(
                File(hostSourceDir, "index.html"),
                File(hostSourceDir, "public/index.html"),
                File(hostSourceDir, "dist/index.html"),
                File(hostSourceDir, "www/index.html"),
                File(hostSourceDir, "app/src/main/assets/index.html")
            ).firstOrNull { it.isFile } ?: hostSourceDir.walkTopDown()
                .maxDepth(3)
                .firstOrNull { it.isFile && it.extension.equals("html", true) }

            if (javaFiles.isNotEmpty() && hasManifest) {
                // Copy native Java Android project layout into compiler workspace
                if (File(hostSourceDir, "app/src/main").isDirectory) {
                    copyDirectorySafe(File(hostSourceDir, "app/src/main"), File(buildProjectDir, "app/src/main"))
                } else {
                    val mainOut = File(buildProjectDir, "app/src/main").apply { mkdirs() }
                    File(hostSourceDir, "AndroidManifest.xml").takeIf { it.isFile }
                        ?.copyTo(File(mainOut, "AndroidManifest.xml"), overwrite = true)
                    listOf("java", "src").firstOrNull { File(hostSourceDir, it).isDirectory }?.let { srcName ->
                        copyDirectorySafe(File(hostSourceDir, srcName), File(mainOut, "java"))
                    }
                    if (File(hostSourceDir, "res").isDirectory) {
                        copyDirectorySafe(File(hostSourceDir, "res"), File(mainOut, "res"))
                    }
                    if (File(hostSourceDir, "assets").isDirectory) {
                        copyDirectorySafe(File(hostSourceDir, "assets"), File(mainOut, "assets"))
                    }
                }
            } else if (htmlEntry != null) {
                AutonomousAppBuilder.ensureAndroidContainerStubs(buildProjectDir, project)
                val webRoot = htmlEntry.parentFile ?: hostSourceDir
                val assetsDir = File(buildProjectDir, "app/src/main/assets").apply { mkdirs() }
                copyDirectorySafe(webRoot, assetsDir)
                if (htmlEntry.name != "index.html") {
                    htmlEntry.copyTo(File(assetsDir, "index.html"), overwrite = true)
                }
                File(assetsDir, "index.html").copyTo(File(buildProjectDir, "index.html"), overwrite = true)
            } else {
                return@withContext JSONObject()
                    .put("success", false)
                    .put(
                        "error",
                        "No index.html or Java+AndroidManifest.xml found in $rawDir. Create index.html (with CSS/JS) or app/src/main/{AndroidManifest.xml,java,res} first."
                    )
            }

            ProjectDataManager.saveProject(context, project)

            val logs = mutableListOf<String>()
            val result = AppCompilerEngine.compileProject(
                context = context,
                project = project,
                onStepChanged = { _, _ -> },
                onLogAdded = { entry -> if (logs.size < 40) logs.add(entry.message) }
            )

            return@withContext when (result) {
                is BuildResult.Error -> {
                    JSONObject()
                        .put("success", false)
                        .put("error", "${result.errorStep.title}: ${result.message}")
                        .put("logs", logs.takeLast(15).joinToString("\n"))
                }
                is BuildResult.Success -> {
                    val apkFile = result.apkFile
                    val apkFileName = "$safeFolder.apk"
                    val linuxBuildOut = File(hostSourceDir, "build").apply { mkdirs() }
                    val linuxApkCopy = File(linuxBuildOut, apkFileName)
                    runCatching { apkFile.copyTo(linuxApkCopy, overwrite = true) }

                    AppCompilerEngine.saveApkToDownloads(
                        context = context,
                        apkFile = apkFile,
                        appName = project.appName,
                        showToast = false
                    )
                    val downloadUrl = AndroidLocalBridgeServer.instance.registerDownloadFile(
                        context = context,
                        file = apkFile,
                        desiredFileName = apkFileName
                    )
                    val cleanLinuxDir = rawDir.trimEnd('/')
                    JSONObject()
                        .put("success", true)
                        .put("appName", project.appName)
                        .put("packageName", project.packageName)
                        .put("linuxApkPath", "$cleanLinuxDir/build/$apkFileName")
                        .put("downloadUrl", downloadUrl)
                        .put("sizeBytes", apkFile.length())
                }
            }
        }

    fun publishLinuxFile(context: Context, requestJson: JSONObject): JSONObject {
        val rawPath = requestJson.optString("linuxPath").trim()
        val hostFile = resolveLinuxPathOnHost(context, rawPath)
            ?: return JSONObject().put("success", false).put("error", "File not found: $rawPath")
        if (!hostFile.isFile) {
            return JSONObject().put("success", false).put("error", "Not a regular file: $rawPath")
        }
        if (hostFile.extension.equals("apk", true)) {
            runCatching {
                AppCompilerEngine.saveApkToDownloads(
                    context = context,
                    apkFile = hostFile,
                    appName = hostFile.nameWithoutExtension,
                    showToast = false
                )
            }
        }
        val url = AndroidLocalBridgeServer.instance.registerDownloadFile(
            context = context,
            file = hostFile,
            desiredFileName = hostFile.name
        )
        return JSONObject()
            .put("success", true)
            .put("fileName", hostFile.name)
            .put("sizeBytes", hostFile.length())
            .put("downloadUrl", url)
    }

    private fun copyDirectorySafe(src: File, dst: File) {
        val skippedDirs = setOf(".git", "node_modules", "build", ".gradle")
        val srcPath = src.canonicalPath
        src.walkTopDown()
            .onEnter { dir -> dir == src || dir.name !in skippedDirs }
            .forEach { item ->
                val rel = item.canonicalPath.removePrefix(srcPath).trimStart(File.separatorChar)
                if (rel.isEmpty()) return@forEach
                val target = File(dst, rel)
                if (item.isDirectory) {
                    target.mkdirs()
                } else if (item.isFile && item.length() <= 64L * 1024 * 1024) {
                    target.parentFile?.mkdirs()
                    item.copyTo(target, overwrite = true)
                }
            }
    }
}
