/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example

import com.example.cloud.PrivateFileTypes
import org.junit.Assert.*
import org.junit.Test

class PrivateFileTypesTest {
    @Test fun apkZipAndVideosAlwaysGoToPrivateWebsiteEvenWhenSmall() {
        val types = listOf("App.apk", "Project.zip", "Clip.mp4", "Movie.mov", "Video.webm", "Film.mkv")
        types.forEach { name ->
            assertEquals(name, PrivateFileTypes.Delivery.WEBSITE,
                PrivateFileTypes.classify(name, "application/octet-stream", 120L).delivery)
        }
        assertEquals("application/vnd.android.package-archive",
            PrivateFileTypes.classify("App.apk", "application/octet-stream", 120L).mimeType)
        assertEquals("video/mp4", PrivateFileTypes.classify("Clip.mp4", "video/*", 120L).mimeType)
    }

    @Test fun smallImagesAndTextStayInlineButLargeFilesGoToWebsite() {
        assertEquals(PrivateFileTypes.Delivery.INLINE,
            PrivateFileTypes.classify("photo.jpg", "image/jpeg", PrivateFileTypes.INLINE_LIMIT - 1).delivery)
        assertEquals(PrivateFileTypes.Delivery.INLINE,
            PrivateFileTypes.classify("code.kt", "application/octet-stream", 700L).delivery)
        assertEquals(PrivateFileTypes.Delivery.WEBSITE,
            PrivateFileTypes.classify("photo.jpg", "image/jpeg", PrivateFileTypes.INLINE_LIMIT).delivery)
        assertEquals(PrivateFileTypes.Delivery.WEBSITE,
            PrivateFileTypes.classify("report.pdf", "application/pdf", PrivateFileTypes.INLINE_LIMIT).delivery)
        assertEquals(PrivateFileTypes.Delivery.UNSUPPORTED,
            PrivateFileTypes.classify("debug.exe", "application/octet-stream", 400L).delivery)
    }

    @Test fun unsafeOrUnicodeNamesAreSafeAndMimesRemainExplicit() {
        assertEquals("SAIF-file.apk", PrivateFileTypes.safeUploadName("../मेल.apk"))
        assertEquals("App.zip", PrivateFileTypes.safeUploadName("App.zip"))
        assertTrue(runCatching { PrivateFileTypes.safeUploadName("file.apk/../thing") }.isFailure)
    }
}
