package com.agentworkspace.github

import android.net.Uri

data class GitHubRemoteProject(
    val owner: String,
    val name: String,
    val baseBranch: String,
) {
    val fullName: String get() = "$owner/$name"
}

fun isGitHubProjectPath(path: String): Boolean =
    path.startsWith("github://") &&
        '#' !in path &&
        path.removePrefix("github://")
            .substringBefore('?')
            .split('/')
            .let { segments -> segments.size == 2 && segments.all(String::isNotBlank) }

fun githubProjectPath(owner: String, name: String, branch: String): String =
    Uri.Builder()
        .scheme("github")
        .authority(owner)
        .appendPath(name)
        .appendQueryParameter("branch", branch)
        .build()
        .toString()

fun parseGitHubProjectPath(path: String): GitHubRemoteProject {
    val uri = Uri.parse(path)
    require(uri.scheme == "github") { "Project is not a GitHub remote project." }
    val owner = uri.host?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("GitHub project owner is missing.")
    val repo = uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("GitHub repository name is missing.")
    val branch = uri.getQueryParameter("branch")?.takeIf { it.isNotBlank() } ?: "main"
    return GitHubRemoteProject(owner = owner, name = repo, baseBranch = branch)
}

fun parseGitHubRepositoryInput(input: String): Pair<String, String> {
    val trimmed = input.trim()
    require(trimmed.isNotBlank()) { "GitHub repository is required." }

    val path = when {
        trimmed.startsWith("git@github.com:") -> trimmed.substringAfter("git@github.com:")
        trimmed.startsWith("https://github.com/") -> trimmed.substringAfter("https://github.com/")
        trimmed.startsWith("http://github.com/") -> trimmed.substringAfter("http://github.com/")
        trimmed.startsWith("github.com/") -> trimmed.substringAfter("github.com/")
        else -> trimmed
    }
        .substringBefore("?")
        .substringBefore("#")
        .trim('/')
        .removeSuffix(".git")

    val parts = path.split('/').filter { it.isNotBlank() }
    require(parts.size >= 2) { "Use a GitHub URL or owner/repo format." }
    return parts[0] to parts[1]
}
