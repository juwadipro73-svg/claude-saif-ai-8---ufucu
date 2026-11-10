/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.cloud

/** Intent-based filter: output size is unknown before generation, so explicit build/render
 * requests take priority. Text, snippets, small images, and questions stay in the app. */
object CloudRequestFilter {
    const val MAX_INLINE_BYTES = 3 * 1024 * 1024
    enum class Route { IN_APP, APK, VIDEO, ZIP, UPLOAD }

    private val informational = Regex("(?i)^\\s*(how (?:do|can|to)|what is|why |can you explain|explain|teach|show me how|guide (?:me|to)|kaise|kya|kyu|kyon|bat(a|ao|aiye)|samjha)")
    private val pureQuestionAboutApk = Regex("(?i)\\b(what is an? apk|apk kya hota|apk kaise banta|how to build an? apk)\\b")
    private val action = Regex("(?i)\\b(build|compile|package|assemble|render|export|produce|create|make|generate)\\b|बनाओ|बना दो|बनाकर|तैयार करो|बना(?:na|o|do|ke|kar)|bna(?:o|do|kar)|ban(?:ao|ado|ake|akar)")
    private val apk = Regex("(?i)\\b(apk|android app|android application|installable app|android game|game apk|app|application)\\b|एपीके|ऐप")
    private val explicitApkWord = Regex("(?i)\\bapk\\b|एपीके")
    private val apkDeliveryRequest = Regex("(?i)\\b(download|link|file|give|send|provide|share|install|compile|de|do|dedo|dijiye|chahiye|lao)\\b|लिंक|डाउनलोड|दे दो|दो|चाहिए")
    private val game = Regex("(?i)\\b(game|गेम)\\b")
    private val video = Regex("(?i)\\b(video|videos|reel|movie|mp4|full clip)\\b|वीडियो|रील")
    private val zip = Regex("(?i)\\b(zip|source archive|project archive)\\b|ज़िप")
    private val compile = Regex("(?i)\\b(build|compile|package|assemble|export)\\b")
    private val render = Regex("(?i)\\b(render|export|produce|generate|make|create)\\b|बनाओ|बना दो|बनाकर|बना(?:na|o|do|ke|kar)|bna(?:o|do|kar)|ban(?:ao|ado|ake|akar)")
    private val stillImage = Regex("(?i)\\b(image|photo|picture|thumbnail|screenshot|poster|icon|logo)\\b|तस्वीर|फोटो")
    private val explicitVideoRender = Regex("(?i)\\b(render|export|produce)\\b.{0,36}\\b(video|mp4|reel)\\b")

    fun route(prompt: String): Route {
        val text = prompt.trim()
        if (text.isEmpty() || informational.containsMatchIn(text) || pureQuestionAboutApk.containsMatchIn(text)) {
            return Route.IN_APP
        }
        // Direct or follow-up APK build / download link requests (e.g. "mujhe iske apk ka direct download link do")
        if (explicitApkWord.containsMatchIn(text) && apkDeliveryRequest.containsMatchIn(text)) {
            if (!stillImage.containsMatchIn(text)) return Route.APK
        }
        if (!action.containsMatchIn(text)) return Route.IN_APP
        if (zip.containsMatchIn(text)) return Route.ZIP
        if (stillImage.containsMatchIn(text) && !explicitVideoRender.containsMatchIn(text)) return Route.IN_APP
        if (apk.containsMatchIn(text) || (game.containsMatchIn(text) && compile.containsMatchIn(text))) return Route.APK
        if (video.containsMatchIn(text) && render.containsMatchIn(text)) return Route.VIDEO
        return Route.IN_APP
    }

    fun shouldDisplayInline(bytes: Int): Boolean = bytes in 0 until MAX_INLINE_BYTES
}
