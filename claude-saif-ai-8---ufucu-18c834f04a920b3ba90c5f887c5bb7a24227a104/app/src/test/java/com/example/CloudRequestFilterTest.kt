/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example

import com.example.cloud.CloudRequestFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRequestFilterTest {
    private fun route(input: String) = CloudRequestFilter.route(input)

    @Test fun explicitHeavyRequestsUseCloud() {
        assertEquals(CloudRequestFilter.Route.APK, route("Build an APK for my game"))
        assertEquals(CloudRequestFilter.Route.APK, route("Build an app"))
        assertEquals(CloudRequestFilter.Route.APK, route("Compile my Android app"))
        assertEquals(CloudRequestFilter.Route.APK, route("ऐप बनाओ"))
        assertEquals(CloudRequestFilter.Route.APK, route("mujhe iske apk ka direct download link do"))
        assertEquals(
            CloudRequestFilter.Route.APK,
            route("are bhai tum mujhe apk file mere phone par hi compile kar ke mujhe ek apk file ka link do na")
        )
        assertEquals(CloudRequestFilter.Route.APK, route("iska apk bana do"))
        assertEquals(CloudRequestFilter.Route.APK, route("give me the apk download link"))
        assertEquals(CloudRequestFilter.Route.VIDEO, route("Render a video in MP4"))
        assertEquals(CloudRequestFilter.Route.VIDEO, route("वीडियो बना दो"))
        assertEquals(CloudRequestFilter.Route.ZIP, route("Export a ZIP of my source project"))
        assertEquals(CloudRequestFilter.Route.ZIP, route("App का ज़िप बनाओ"))
    }

    @Test fun lightRequestsAndQuestionsStayNative() {
        assertEquals(CloudRequestFilter.Route.IN_APP, route("Write a Kotlin code snippet for a game"))
        assertEquals(CloudRequestFilter.Route.IN_APP, route("How do I build an APK?"))
        assertEquals(CloudRequestFilter.Route.IN_APP, route("apk kya hota hai?"))
        assertEquals(CloudRequestFilter.Route.IN_APP, route("Create a small image for a video thumbnail"))
        assertEquals(CloudRequestFilter.Route.IN_APP, route("Make an app logo"))
        assertEquals(CloudRequestFilter.Route.ZIP, route("Export a ZIP of my app screenshots"))
        assertEquals(CloudRequestFilter.Route.IN_APP, route("Explain video editing"))
        assertEquals(CloudRequestFilter.Route.IN_APP, route("hello"))
    }

    @Test fun inlineBudgetIsThreeMegabytes() {
        assertTrue(CloudRequestFilter.shouldDisplayInline(3 * 1024 * 1024 - 1))
        assertFalse(CloudRequestFilter.shouldDisplayInline(3 * 1024 * 1024))
    }
}
