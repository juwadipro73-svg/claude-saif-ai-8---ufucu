/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example

import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.compiler.LinuxApkBuildBridge
import com.example.data.local.AuthManager
import com.example.gemini.data.remote.SavedAiSkill
import com.example.gemini.data.remote.SavedAiSkills
import com.example.gemini.data.remote.SkillBundle
import com.example.gemini.data.remote.SkillBundleFile
import com.example.github.GitHubAccountProfile
import com.example.github.GitHubIntegrationManager
import com.example.ui.ChatAttachmentStager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PostBuild98FixesVerificationTest {

    @Test
    fun mergedManifestHasZeroPlayProtectFraudTriggersAndNoLegacyEngineActivity() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val pm = app.packageManager
        @Suppress("DEPRECATION")
        val pkgInfo = pm.getPackageInfo(
            app.packageName,
            PackageManager.GET_PERMISSIONS or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_ACTIVITIES
        )

        val requestedPerms = pkgInfo.requestedPermissions?.toSet().orEmpty()
        val forbiddenPerms = listOf(
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.CALL_PHONE",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_SETTINGS",
            "android.permission.REQUEST_INSTALL_PACKAGES",
            "android.permission.REQUEST_DELETE_PACKAGES"
        )
        for (perm in forbiddenPerms) {
            assertFalse("Play Protect flagged permission must be absent: $perm", perm in requestedPerms)
        }

        val servicePerms = pkgInfo.services?.mapNotNull { it.permission }.orEmpty()
        assertFalse("BIND_ACCESSIBILITY_SERVICE must be absent", "android.permission.BIND_ACCESSIBILITY_SERVICE" in servicePerms)
        assertFalse("BIND_NOTIFICATION_LISTENER_SERVICE must be absent", "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" in servicePerms)

        val receiverPerms = pkgInfo.receivers?.mapNotNull { it.permission }.orEmpty()
        assertFalse("BIND_DEVICE_ADMIN must be absent", "android.permission.BIND_DEVICE_ADMIN" in receiverPerms)

        val activityNames = pkgInfo.activities?.map { it.name }.orEmpty()
        assertFalse("Legacy com.example.gemini.MainActivity must be removed", "com.example.gemini.MainActivity" in activityNames)
    }

    @Test
    fun largeMultiFileGitHubSkillBundlePersistsAndReloadsAbove256Kb() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AuthManager.init(app)
        val user = AuthManager.signInAsGuest()
        SavedAiSkills.bind(app, user.uid)

        // 95 files totaling ~600 KB (simulating morluto/rea.git skill collection > 256 KB & > 80 files)
        val largeFiles = (1..95).map { idx ->
            val path = if (idx == 1) ".agents/skills/rea/SKILL.md" else ".agents/skills/rea/references/ref_$idx.md"
            SkillBundleFile(path, "# Reference $idx\n" + "Reverse engineering workflow step detail.\n".repeat(150))
        }
        val doc = SkillBundle.compose(
            name = "REA",
            sourceUrl = "https://github.com/morluto/rea",
            files = largeFiles,
            maxBytes = SkillBundle.IMPORT_MAX_BYTES,
            maxFiles = SkillBundle.IMPORT_MAX_FILES
        )
        assertTrue("Bundle should exceed legacy 256 KB cap", doc.toByteArray(Charsets.UTF_8).size > 500 * 1024)

        SavedAiSkills.save(
            SavedAiSkill(
                name = "REA",
                description = "Reverse Engineer Anything",
                sourceUrl = "https://github.com/morluto/rea",
                instructions = doc,
                savedAt = System.currentTimeMillis()
            )
        )

        // Force reload from disk
        SavedAiSkills.bind(app, null)
        SavedAiSkills.bind(app, user.uid)
        val reloaded = SavedAiSkills.find("REA")
        assertNotNull("Large REA skill must reload from disk cleanly", reloaded)
        assertEquals(doc, reloaded?.instructions)
    }

    @Test
    fun chatAttachmentStagerStagesLargeMp4AndZipDirectlyIntoLinuxUploads() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val tempDir = File(app.cacheDir, "test_uploads").apply { mkdirs() }

        // Create a 4 MB synthetic .mp4 file (exceeds old 3 MB limit!)
        val mp4File = File(tempDir, "43273.mp4")
        mp4File.writeBytes(ByteArray(4 * 1024 * 1024) { (it % 251).toByte() })

        // Create a sample .zip archive
        val zipFile = File(tempDir, "project_bundle.zip")
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("README.md"))
            zos.write("# Sample Zip".toByteArray())
            zos.closeEntry()
        }

        val staged = ChatAttachmentStager.prepareForAntigravity(
            context = app,
            uris = listOf(Uri.fromFile(mp4File), Uri.fromFile(zipFile))
        )

        assertTrue(staged.promptContextBlock.contains("/root/uploads/43273.mp4"))
        assertTrue(staged.promptContextBlock.contains("/root/uploads/project_bundle.zip"))
        assertTrue(staged.promptContextBlock.contains("README.md"))
        assertTrue(File(app.filesDir, "ubuntu/root/uploads/43273.mp4").isFile)
        assertTrue(File(app.filesDir, "home/uploads/project_bundle.zip").isFile)
    }

    @Test
    fun linuxApkBuildBridgeAndGitHubSyncInstallWorkspaceCredentialsAndScripts() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()

        LinuxApkBuildBridge.installLinuxCliTools(app, 8767, "device_secret_123456")
        val buildBin = File(app.filesDir, "ubuntu/usr/local/bin/saif-build-apk")
        val linkBin = File(app.filesDir, "ubuntu/usr/local/bin/saif-apk-link")
        assertTrue(buildBin.isFile && buildBin.canExecute())
        assertTrue(linkBin.isFile && linkBin.canExecute())

        val wsDir = File(app.filesDir, "home/demo_app").apply { mkdirs() }
        assertEquals(
            wsDir.canonicalPath,
            LinuxApkBuildBridge.resolveLinuxPathOnHost(app, "/root/workspace/demo_app")?.canonicalPath
        )
        assertNull("Path traversal must be rejected", LinuxApkBuildBridge.resolveLinuxPathOnHost(app, "/root/workspace/../../etc/passwd"))

        val profile = GitHubAccountProfile(
            login = "saifdev",
            name = "Saif Developer",
            email = "saif@users.noreply.github.com",
            avatarUrl = "",
            htmlUrl = "https://github.com/saifdev",
            publicRepos = 5,
            privateRepos = 2,
            connectedAt = 1000L
        )
        GitHubIntegrationManager.syncCredentialsToLinuxWorkspace(app, "ghp_testToken1234567890", profile)
        val gitConfig = File(app.filesDir, "ubuntu/root/.gitconfig")
        val gitCreds = File(app.filesDir, "ubuntu/root/.git-credentials")
        assertTrue(gitConfig.isFile && gitConfig.readText().contains("Saif Developer"))
        assertTrue(gitCreds.isFile && gitCreds.readText().contains("saifdev:ghp_testToken1234567890@github.com"))
    }

    @Test
    fun uiSourceInvariantsMatchUserRequirements() {
        val saifAppFile = listOf(
            File("src/main/java/com/example/ui/SaifAiApp.kt"),
            File("app/src/main/java/com/example/ui/SaifAiApp.kt")
        ).first { it.isFile }
        val code = saifAppFile.readText()

        assertTrue("Placeholder must be REPLY TO SAIF AI", code.contains("placeholderText = \"REPLY TO SAIF AI\""))
        assertFalse("CloudBuildStatusCard must not be rendered in SaifAiApp", code.contains("CloudBuildStatusCard()"))
        assertFalse("EmbeddedEngineSettings must not be rendered in SaifAiApp", code.contains("EmbeddedEngineSettings("))
        assertTrue("Standalone GitHubConnectDialog must be wired", code.contains("GitHubConnectDialog("))
    }
}
