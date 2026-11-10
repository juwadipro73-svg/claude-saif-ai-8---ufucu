/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.android.apksig.ApkVerifier
import com.example.builder.AutonomousAppBuilder
import com.example.compiler.AlignedZipWriter
import com.example.compiler.AlignedZipWriter.ZipItem
import com.example.compiler.AppCompilerEngine
import com.example.compiler.AppHtmlSynthesizer
import com.example.compiler.BuildResult
import com.example.compiler.OnDeviceApkBuildManager
import com.example.gemini.data.remote.AndroidLocalBridgeServer
import com.example.ui.components.ProjectData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdvancedAppBuildUnitTest {

    @Test
    fun testExtractHtmlDocumentSuccess() {
        val rawResponse = """
            Here is your app:
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>Calculator</title>
            </head>
            <body>
                <div id="calc">Calculator App</div>
            </body>
            </html>
            Enjoy the app!
        """.trimIndent()

        val extracted = AutonomousAppBuilder.extractHtmlDocument(rawResponse)
        assertNotNull("Extracted HTML should not be null", extracted)
        assertTrue(extracted!!.startsWith("<!DOCTYPE html", ignoreCase = true))
        assertTrue(extracted.endsWith("</html>", ignoreCase = true))
        assertFalse(extracted.contains("Here is your app"))
    }

    @Test
    fun testExtractHtmlDocumentTruncationDetection() {
        val truncatedResponse = """
            <!DOCTYPE html>
            <html lang="en">
            <head><title>Incomplete</title></head>
            <body>
                <div class="container">
        """.trimIndent()

        val extracted = AutonomousAppBuilder.extractHtmlDocument(truncatedResponse)
        assertNull("Truncated response missing </html> should return null", extracted)
    }

    @Test
    fun testHealTruncatedHtmlClosesUnclosedScriptAndTags() {
        val truncatedCode = """
            <!DOCTYPE html>
            <html lang="en">
            <head><title>Stopwatch</title></head>
            <body>
                <div id="display">00:00</div>
                <script>
                    let time = 0;
                    console.log("Starting timer");
        """.trimIndent()

        val healed = AutonomousAppBuilder.healTruncatedHtml(truncatedCode)
        assertNotNull(healed)
        assertTrue(healed!!.contains("</script>"))
        assertTrue(healed.contains("</body>"))
        assertTrue(healed.endsWith("</html>"))
    }

    @Test
    fun testAppHtmlSynthesizerReturnsExistingHtmlAndThrowsWhenMissing() {
        val project = ProjectData(
            appName = "Calculator",
            packageName = "com.example.calculator",
            minSdk = 24,
            targetSdk = 34,
            buildStudio = "Build Studio",
            language = "Java",
            iconBitmap = null
        )
        val tempDir = File.createTempFile("test_proj_", "").apply { delete(); mkdirs() }
        try {
            try {
                AppHtmlSynthesizer.synthesize(project, tempDir)
                fail("Should have thrown IllegalStateException when index.html does not exist")
            } catch (e: IllegalStateException) {
                assertTrue(e.message?.contains("Build the app first") == true)
            }

            val sampleHtml = "<!DOCTYPE html><html><head><!-- saif-ai-build --></head><body><h1>Working Calculator</h1></body></html>"
            File(tempDir, "index.html").writeText(sampleHtml)
            assertEquals(sampleHtml, AppHtmlSynthesizer.synthesize(project, tempDir))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testAlignedZipPackagingFourByteAlignment() {
        val tempZip = File.createTempFile("aligned_apk_test_", ".apk")
        try {
            val dummyArsc = ByteArray(1024) { (it % 251).toByte() }
            val dummyPng = ByteArray(513) { (it % 127).toByte() }
            val htmlBytes = "<!DOCTYPE html><html><body>Expense Pro</body></html>".toByteArray(Charsets.UTF_8)

            AlignedZipWriter.write(
                items = listOf(
                    ZipItem("AndroidManifest.xml", "<manifest/>".toByteArray(), isStored = false),
                    ZipItem("resources.arsc", dummyArsc, isStored = true),
                    ZipItem("res/mipmap-xxxhdpi-v4/ic_launcher.png", dummyPng, isStored = true),
                    ZipItem("assets/index.html", htmlBytes, isStored = false)
                ),
                outputFile = tempZip
            )

            val rawBytes = tempZip.readBytes()
            assertEquals(0, findStoredEntryDataOffset(rawBytes, "resources.arsc") % 4)
            assertEquals(0, findStoredEntryDataOffset(rawBytes, "res/mipmap-xxxhdpi-v4/ic_launcher.png") % 4)

            ZipFile(tempZip).use { zf ->
                assertEquals(ZipEntry.STORED, zf.getEntry("resources.arsc").method)
            }
        } finally {
            tempZip.delete()
        }
    }

    @Test
    fun testSanitizePackageNameEdgeCases() {
        assertEquals("com.saifai.tempmail", AppCompilerEngine.sanitizePackageName("", "Temp Mail"))
        assertEquals("com.app_2048", AppCompilerEngine.sanitizePackageName("com.2048", "2048 Pro"))
        assertEquals("com.saifai.app_2048pro", AppCompilerEngine.sanitizePackageName("", "2048 Pro"))
        assertEquals("com.switch_app.class_app", AppCompilerEngine.sanitizePackageName("com.switch.class", "Test"))
        assertEquals("com.saifai.generated", AppCompilerEngine.sanitizePackageName("", "मेरा ऐप"))
    }

    @Test
    fun testRealEndToEndApkCompileVerifyAndDirectDownloadServer() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Verify base_runner.apk is bundled in assets
        val baseBytes = context.assets.open("base_runner.apk").use { it.readBytes() }
        assertTrue("Bundled base_runner.apk must be a valid non-empty APK", baseBytes.size > 10_000)

        // 2. Stage a Temp Mail project with custom icon & HTML
        val appName = "Temp Mail Pro"
        val sanitized = "Temp_Mail_Pro"
        val projectDir = File(context.filesDir, "projects/$sanitized").apply { mkdirs() }
        val html = """
            <!DOCTYPE html>
            <html><head><!-- saif-ai-build --><title>Temp Mail Pro</title></head>
            <body><h1>Disposable Inbox Ready</h1></body></html>
        """.trimIndent()
        File(projectDir, "index.html").writeText(html)

        val customIcon = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(37, 99, 235))
        }
        val project = ProjectData(
            appName = appName,
            packageName = "com.saifai.tempmailpro",
            minSdk = 24,
            targetSdk = 34,
            buildStudio = "SAIF AI Studio",
            language = "Java",
            iconBitmap = customIcon
        )

        // 3. Compile & cryptographically sign APK on-device
        val result = AppCompilerEngine.compileProject(
            context = context,
            project = project,
            onStepChanged = { _, _ -> },
            onLogAdded = {}
        )
        assertTrue("compileProject must succeed, got: $result", result is BuildResult.Success)
        val apkFile = (result as BuildResult.Success).apkFile
        assertTrue("Signed APK must exist", apkFile.isFile && apkFile.length() > 10_000)

        // 4. Verify APK cryptographic signatures (v2 + v3 on Android 7.0+ and v1 JAR entries) using official Android ApkVerifier
        val verifierResult = ApkVerifier.Builder(apkFile)
            .setMinCheckedPlatformVersion(24)
            .build()
            .verify()
        assertTrue(
            "APK must pass official Android ApkVerifier! Errors: ${verifierResult.errors}",
            verifierResult.isVerified
        )
        assertTrue("v2 APK signature block must be verified", verifierResult.isVerifiedUsingV2Scheme)
        assertTrue("v3 APK signature block must be verified", verifierResult.isVerifiedUsingV3Scheme)

        // 5. Inspect packaged entries & alignment
        val rawSignedBytes = apkFile.readBytes()
        val arscOffset = findStoredEntryDataOffset(rawSignedBytes, "resources.arsc")
        assertEquals("Signed APK resources.arsc must remain 4-byte aligned", 0, arscOffset % 4)

        ZipFile(apkFile).use { zf ->
            val names = zf.entries().asSequence().map { it.name }.toList()
            assertEquals("ZIP must contain zero duplicate entries", names.distinct().size, names.size)
            assertTrue(names.contains("AndroidManifest.xml"))
            assertTrue(names.contains("classes.dex"))
            assertTrue(names.contains("resources.arsc"))
            assertTrue(names.contains("res/drawable/ic_launcher.png"))
            val assetHtml = zf.getInputStream(zf.getEntry("assets/index.html")).bufferedReader().readText()
            assertTrue(assetHtml.contains("Disposable Inbox Ready"))
            val arscBytes = zf.getInputStream(zf.getEntry("resources.arsc")).readBytes()
            assertTrue(
                "resources.arsc string pool must contain customized app label",
                String(arscBytes, Charsets.UTF_8).contains("Temp Mail Pro")
            )
            assertFalse(
                "Default 'App Runner' label must be replaced in resources.arsc",
                String(arscBytes, Charsets.UTF_8).contains("App Runner")
            )
        }

        // 6. Verify direct HTTP download URL serves exact signed APK bytes without X-Device-Id
        try {
            val directUrl = AndroidLocalBridgeServer.instance.registerDownloadFile(
                context = context,
                file = apkFile,
                desiredFileName = "Temp_Mail_Pro.apk"
            )
            assertTrue(directUrl.startsWith("http://127.0.0.1:"))
            val conn = (URL(directUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 5_000
            }
            assertEquals(200, conn.responseCode)
            assertEquals("application/vnd.android.package-archive", conn.contentType)
            val downloadedBytes = conn.inputStream.use { it.readBytes() }
            assertArrayEquals("Downloaded APK bytes must match signed APK byte-for-byte", rawSignedBytes, downloadedBytes)
        } finally {
            AndroidLocalBridgeServer.instance.stop()
        }
    }

    @Test
    fun testFollowUpInferenceAndEmbeddedSaifApkCompilation() = runBlocking {
        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val history = listOf(
                "user" to "ek temporary mail app kaise banaye",
                "model" to "Main aapko Temporary Mail app banane me madad kar sakta hu."
            )
            val inferred = OnDeviceApkBuildManager.inferAppName(
                prompt = "mujhe iske apk ka direct download link do",
                history = history
            )
            assertEquals("Temporary Mail", inferred)

            val assistantReply = """
                Yeh lijiye aapka Temporary Mail Android app:
                <saif-apk name="Temp Mail" package="com.saifai.tempmail">
                <!DOCTYPE html><html><head><title>Temp Mail</title></head><body><h1>Temp Mail Inbox</h1></body></html>
                </saif-apk>
            """.trimIndent()

            val processed = OnDeviceApkBuildManager.processAssistantReplyForEmbeddedApk(
                context = context,
                userPrompt = "mujhe iske apk ka direct download link do",
                assistantReply = assistantReply
            )
            assertFalse(processed.contains("<saif-apk"))
            assertTrue("Processed reply must include direct HTTP download link", processed.contains("http://127.0.0.1:"))
            assertTrue("Processed reply must include one-tap file:// install link", processed.contains("file://"))
        } finally {
            AndroidLocalBridgeServer.instance.stop()
        }
    }

    private fun findStoredEntryDataOffset(zipBytes: ByteArray, targetName: String): Int {
        var i = 0
        while (i + 30 < zipBytes.size) {
            if (zipBytes[i] == 0x50.toByte() &&
                zipBytes[i + 1] == 0x4B.toByte() &&
                zipBytes[i + 2] == 0x03.toByte() &&
                zipBytes[i + 3] == 0x04.toByte()
            ) {
                val nameLen = (zipBytes[i + 26].toInt() and 0xFF) or ((zipBytes[i + 27].toInt() and 0xFF) shl 8)
                val extraLen = (zipBytes[i + 28].toInt() and 0xFF) or ((zipBytes[i + 29].toInt() and 0xFF) shl 8)
                if (i + 30 + nameLen <= zipBytes.size) {
                    val name = String(zipBytes, i + 30, nameLen, Charsets.UTF_8)
                    val dataStart = i + 30 + nameLen + extraLen
                    if (name == targetName) {
                        return dataStart
                    }
                }
            }
            i++
        }
        return -1
    }
}
