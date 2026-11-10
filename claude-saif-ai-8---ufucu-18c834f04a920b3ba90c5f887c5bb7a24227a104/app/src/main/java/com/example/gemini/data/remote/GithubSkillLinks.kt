/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.gemini.data.remote

import java.net.URI

/** Parse ordinary GitHub browser links without ever treating an arbitrary URL as a fetch target. */
sealed interface GithubSkillSource {
    data class Repository(val owner: String, val repo: String) : GithubSkillSource
    data class Directory(val owner: String, val repo: String, val refAndPath: List<String>) : GithubSkillSource
    data class File(
        val rawUrl: String,
        val fileName: String,
        val owner: String,
        val repo: String,
        /** Ref (which can contain slashes) followed by the repository-relative file path. */
        val refAndPath: List<String>
    ) : GithubSkillSource
}

object GithubSkillLinks {
    private val segment = Regex("[A-Za-z0-9_.-]{1,120}")

    fun parse(input: String): GithubSkillSource {
        val uri = runCatching { URI(input.trim()) }.getOrNull()
            ?: throw IllegalArgumentException("Paste a public GitHub repository, skill folder or Markdown file URL")
        require(uri.scheme.equals("https", true) && uri.userInfo == null && uri.port == -1 &&
            (uri.rawQuery == null || Regex("tab=[A-Za-z0-9_-]{1,48}").matches(uri.rawQuery))) {
            "Use a public HTTPS GitHub link without tracking or redirect parameters"
        }
        val host = uri.host?.lowercase()
        require(host == "github.com" || host == "raw.githubusercontent.com") {
            "Only github.com skill links are supported"
        }
        // Browser UI query strings/fragments are ignored; they are never forwarded to GitHub's API/raw host.
        val parts = uri.rawPath?.trim('/')?.split('/') ?: emptyList()
        require(parts.size in 2..24 && parts.all { segment.matches(it) && it != "." && it != ".." }) {
            "Invalid GitHub repository or file path"
        }
        val owner = parts[0]
        val repo = parts[1].removeSuffix(".git")
        require(repo.isNotBlank() && repo != "." && repo != "..") { "Invalid GitHub repository" }
        if (host == "raw.githubusercontent.com") {
            require(parts.size >= 4 && (parts.last().endsWith(".md", true) || parts.last().endsWith(".txt", true))) {
                "Paste a GitHub Markdown/text skill file"
            }
            val path = (listOf(owner, repo) + parts.drop(2)).joinToString("/")
            return GithubSkillSource.File("https://raw.githubusercontent.com/$path", parts.last(), owner, repo, parts.drop(2))
        }
        if (parts.size == 2) return GithubSkillSource.Repository(owner, repo)
        require(parts.size >= 5 && parts[2] in listOf("tree", "blob", "raw")) {
            "Paste a repository, skill folder, or GitHub file link"
        }
        if (parts[2] == "tree") return GithubSkillSource.Directory(owner, repo, parts.drop(3))
        require(parts.last().endsWith(".md", true) || parts.last().endsWith(".txt", true)) {
            "Paste a GitHub Markdown/text skill file"
        }
        val path = (listOf(owner, repo) + parts.drop(3)).joinToString("/")
        return GithubSkillSource.File("https://raw.githubusercontent.com/$path", parts.last(), owner, repo, parts.drop(3))
    }

    fun rawFileUrl(input: String): String = (parse(input) as? GithubSkillSource.File)?.rawUrl
        ?: throw IllegalArgumentException("This link points to a repository/folder, not a single skill file")
}

/** A single URL suffices: the command and description come from SKILL.md frontmatter when present. */
object GithubSkillMetadata {
    private val field = Regex("(?m)^([A-Za-z][A-Za-z_-]*):[ \\t]*(.+?)[ \\t]*$")

    private fun frontmatter(text: String): Map<String, String> {
        if (!text.startsWith("---\n")) return emptyMap()
        val end = text.indexOf("\n---", startIndex = 4).takeIf { it in 4..2048 } ?: return emptyMap()
        return field.findAll(text.substring(4, end)).associate { it.groupValues[1].lowercase() to
            it.groupValues[2].trim().trim('"', '\'') }
    }

    fun name(text: String, hint: String): String {
        val proposed = frontmatter(text)["name"].orEmpty().ifBlank { hint }
            .removeSuffix(".md").removeSuffix(".txt").lowercase()
            .replace(Regex("[^a-z0-9_-]+"), "-").trim('-', '_').take(48)
        return proposed.ifBlank { "skill" }
    }

    fun description(text: String, fallback: String): String = frontmatter(text)["description"]
        ?.takeIf { it.isNotBlank() && it != "|" && it != ">" }?.take(500)
        ?: fallback.take(500)
}
