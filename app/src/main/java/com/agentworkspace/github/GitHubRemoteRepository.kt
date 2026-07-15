package com.agentworkspace.github

import android.content.Context
import android.util.Base64
import com.agentworkspace.data.security.CredentialVault
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class GitHubFileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val childCount: Int = 0,
)

data class GitHubBuildResult(
    val branch: String,
    val runUrl: String,
    val apkPath: String,
)

@Singleton
class GitHubRemoteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialVault: CredentialVault,
) {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun configureProject(
        repoInput: String,
        branchInput: String,
        tokenInput: String,
    ): GitHubRemoteProject = withContext(Dispatchers.IO) {
        val (owner, name) = parseGitHubRepositoryInput(repoInput)
        if (tokenInput.isNotBlank()) {
            credentialVault.put(GITHUB_CREDENTIAL_ID, CredentialVault.Field.ACCESS_TOKEN, tokenInput.trim())
        }
        requireToken()
        val branch = branchInput.trim().ifBlank {
            resolveDefaultBranch(GitHubRemoteProject(owner, name, "main"))
        }
        GitHubRemoteProject(owner = owner, name = name, baseBranch = branch)
    }

    fun hasSavedToken(): Boolean = !savedToken().isNullOrBlank()

    suspend fun createWorkingBranch(project: GitHubRemoteProject, taskId: String): String = withContext(Dispatchers.IO) {
        val safeTask = taskId.take(8).lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
            .ifBlank { System.currentTimeMillis().toString() }
        val branch = "agentworkspace/$safeTask"
        ensureBranch(project, project.baseBranch, branch)
        branch
    }

    suspend fun listFiles(
        project: GitHubRemoteProject,
        branch: String = project.baseBranch,
        path: String = "",
        recursive: Boolean = false,
    ): List<GitHubFileEntry> = withContext(Dispatchers.IO) {
        if (recursive) listTree(project, branch, path)
        else listContents(project, branch, path)
    }

    suspend fun readFile(project: GitHubRemoteProject, branch: String, path: String): String = withContext(Dispatchers.IO) {
        val content = getContentObject(project, branch, path)
        require(content.string("type") == "file") { "Path is not a file: $path" }
        val encoded = content.string("content")?.replace("\n", "").orEmpty()
        String(Base64.decode(encoded, Base64.DEFAULT))
    }

    suspend fun writeFile(
        project: GitHubRemoteProject,
        branch: String,
        path: String,
        content: String,
        overwrite: Boolean,
        message: String,
    ): String = withContext(Dispatchers.IO) {
        val existing = getContentObjectOrNull(project, branch, path)
        if (existing != null && !overwrite) {
            throw IllegalStateException("File exists; set overwrite=true to replace: $path")
        }
        putFile(project, branch, path, content, existing?.string("sha"), message)
        path
    }

    suspend fun editFile(
        project: GitHubRemoteProject,
        branch: String,
        path: String,
        oldText: String,
        newText: String,
    ): String = withContext(Dispatchers.IO) {
        val current = readFile(project, branch, path)
        require(current.contains(oldText)) { "old_text not found in file: $path" }
        writeFile(
            project = project,
            branch = branch,
            path = path,
            content = current.replace(oldText, newText),
            overwrite = true,
            message = "AgentWorkspace edit $path",
        )
    }

    suspend fun deleteFile(project: GitHubRemoteProject, branch: String, path: String): String = withContext(Dispatchers.IO) {
        val existing = getContentObject(project, branch, path)
        val sha = existing.string("sha") ?: throw IllegalStateException("Could not read file sha: $path")
        val body = buildJsonObject {
            put("message", "AgentWorkspace delete $path")
            put("sha", sha)
            put("branch", branch)
        }.toString().jsonBody()
        val request = githubRequest(contentsUrl(project, path))
            .delete(body)
            .build()
        executeObject(request)
        path
    }

    suspend fun searchFiles(
        project: GitHubRemoteProject,
        branch: String,
        rootPath: String,
        query: String,
        isRegex: Boolean,
    ): List<JsonObject> = withContext(Dispatchers.IO) {
        val regex = if (isRegex) Regex(query) else Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
        val matches = mutableListOf<JsonObject>()
        for (file in listTree(project, branch, rootPath).filter { !it.isDirectory && isLikelyText(it.path) }.take(80)) {
            val content = runCatching { readFile(project, branch, file.path) }.getOrNull() ?: continue
            var fileMatches = 0
            content.lineSequence().forEachIndexed { index, line ->
                if (matches.size >= 80 || fileMatches >= 20) return@forEachIndexed
                if (regex.containsMatchIn(line)) {
                    matches.add(buildJsonObject {
                        put("path", file.path)
                        put("line", index + 1)
                        put("snippet", line.take(240))
                    })
                    fileMatches++
                }
            }
            if (matches.size >= 80) break
        }
        matches
    }

    suspend fun buildApk(project: GitHubRemoteProject, branch: String): GitHubBuildResult = withContext(Dispatchers.IO) {
        ensureBuildWorkflow(project, branch)
        val startedAt = Instant.now().minusSeconds(10)
        triggerBuild(project, branch)
        val run = waitForSuccessfulRun(project, branch, startedAt)
        val apkPath = downloadFirstApk(project, run.id, project.fullName, branch)
        GitHubBuildResult(branch = branch, runUrl = run.url, apkPath = apkPath)
    }

    private fun resolveDefaultBranch(project: GitHubRemoteProject): String {
        val url = repoUrl(project)
        val request = githubRequest(url).get().build()
        val obj = executeObject(request)
        return obj.string("default_branch") ?: "main"
    }

    private fun ensureBranch(project: GitHubRemoteProject, baseBranch: String, workingBranch: String) {
        val existing = githubRequest(refUrl(project, workingBranch)).get().build()
        client.newCall(existing).execute().use { response ->
            if (response.isSuccessful) return
            if (response.code != 404) throw githubException(response)
        }

        val base = executeObject(githubRequest(refUrl(project, baseBranch)).get().build())
        val sha = base.obj("object")?.string("sha")
            ?: throw IllegalStateException("Could not resolve base branch: $baseBranch")
        val body = buildJsonObject {
            put("ref", "refs/heads/$workingBranch")
            put("sha", sha)
        }.toString().jsonBody()
        executeObject(
            githubRequest(refsUrl(project))
                .post(body)
                .build(),
        )
    }

    private fun listContents(project: GitHubRemoteProject, branch: String, path: String): List<GitHubFileEntry> {
        val request = githubRequest(contentsUrl(project, path, branch)).get().build()
        val element = executeElement(request)
        val array = element as? JsonArray ?: JsonArray(listOf(element))
        return array.mapNotNull { item ->
            val obj = item.jsonObject
            val itemPath = obj.string("path") ?: return@mapNotNull null
            val type = obj.string("type")
            GitHubFileEntry(
                name = obj.string("name") ?: itemPath.substringAfterLast('/'),
                path = itemPath,
                isDirectory = type == "dir",
            )
        }.sortedWith(compareByDescending<GitHubFileEntry> { it.isDirectory }.thenBy { it.name.lowercase(Locale.US) })
    }

    private fun listTree(project: GitHubRemoteProject, branch: String, rootPath: String): List<GitHubFileEntry> {
        val url = repoApiUrl(project)
            .addPathSegment("git")
            .addPathSegment("trees")
            .addPathSegment(branch)
            .addQueryParameter("recursive", "1")
            .build()
            .toString()
        val tree = executeObject(githubRequest(url).get().build())["tree"]?.jsonArray ?: return emptyList()
        val cleanRoot = rootPath.trim('/')
        return tree.mapNotNull { item ->
            val obj = item.jsonObject
            val path = obj.string("path") ?: return@mapNotNull null
            if (cleanRoot.isNotEmpty() && path != cleanRoot && !path.startsWith("$cleanRoot/")) return@mapNotNull null
            val type = obj.string("type")
            GitHubFileEntry(
                name = path.substringAfterLast('/'),
                path = path,
                isDirectory = type == "tree",
            )
        }.sortedWith(compareByDescending<GitHubFileEntry> { it.isDirectory }.thenBy { it.path.lowercase(Locale.US) })
    }

    private fun getContentObject(project: GitHubRemoteProject, branch: String, path: String): JsonObject =
        getContentObjectOrNull(project, branch, path)
            ?: throw IllegalStateException("File not found: $path")

    private fun getContentObjectOrNull(project: GitHubRemoteProject, branch: String, path: String): JsonObject? {
        val request = githubRequest(contentsUrl(project, path, branch)).get().build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw githubException(response)
            return json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
        }
    }

    private fun putFile(
        project: GitHubRemoteProject,
        branch: String,
        path: String,
        content: String,
        sha: String?,
        message: String,
    ) {
        val body = buildJsonObject {
            put("message", message)
            put("branch", branch)
            put("content", Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP))
            if (sha != null) put("sha", sha)
        }.toString().jsonBody()
        executeObject(
            githubRequest(contentsUrl(project, path))
                .put(body)
                .build(),
        )
    }

    private fun ensureBuildWorkflow(project: GitHubRemoteProject, branch: String) {
        val existing = getContentObjectOrNull(project, branch, BUILD_WORKFLOW_PATH)
        val current = existing?.string("content")
            ?.replace("\n", "")
            ?.let { encoded -> runCatching { String(Base64.decode(encoded, Base64.DEFAULT)) }.getOrNull() }
        if (current == BUILD_WORKFLOW) return
        putFile(
            project = project,
            branch = branch,
            path = BUILD_WORKFLOW_PATH,
            content = BUILD_WORKFLOW,
            sha = existing?.string("sha"),
            message = "AgentWorkspace add APK build workflow",
        )
    }

    private fun triggerBuild(project: GitHubRemoteProject, branch: String) {
        val body = buildJsonObject { put("ref", branch) }.toString().jsonBody()
        val dispatchRequest = githubRequest(
            repoApiUrl(project)
                .addPathSegment("actions")
                .addPathSegment("workflows")
                .addPathSegment(BUILD_WORKFLOW_FILE)
                .addPathSegment("dispatches")
                .build()
                .toString(),
        ).post(body).build()

        client.newCall(dispatchRequest).execute().use { response ->
            if (response.isSuccessful) return
        }

        val triggerPath = ".github/agentworkspace-build-trigger.txt"
        val existing = getContentObjectOrNull(project, branch, triggerPath)
        putFile(
            project = project,
            branch = branch,
            path = triggerPath,
            content = "build=${System.currentTimeMillis()}\n",
            sha = existing?.string("sha"),
            message = "AgentWorkspace trigger APK build",
        )
    }

    private suspend fun waitForSuccessfulRun(
        project: GitHubRemoteProject,
        branch: String,
        startedAt: Instant,
    ): WorkflowRun {
        repeat(BUILD_POLL_ATTEMPTS) {
            val run = latestBuildRun(project, branch, startedAt)
            if (run != null) {
                if (run.status == "completed" && run.conclusion == "success") return run
                if (run.status == "completed") {
                    throw IllegalStateException("GitHub Actions build failed: ${run.conclusion ?: "unknown"} (${run.url})")
                }
            }
            delay(BUILD_POLL_DELAY_MS)
        }
        throw IllegalStateException("Timed out waiting for GitHub Actions APK build.")
    }

    private fun latestBuildRun(project: GitHubRemoteProject, branch: String, startedAt: Instant): WorkflowRun? {
        val url = repoApiUrl(project)
            .addPathSegment("actions")
            .addPathSegment("runs")
            .addQueryParameter("branch", branch)
            .addQueryParameter("per_page", "20")
            .build()
            .toString()
        val runs = executeObject(githubRequest(url).get().build())["workflow_runs"]?.jsonArray ?: return null
        return runs.mapNotNull { item ->
            val obj = item.jsonObject
            val created = obj.string("created_at")?.let { runCatching { Instant.parse(it) }.getOrNull() }
            val name = obj.string("name")
            if (name != BUILD_WORKFLOW_NAME) return@mapNotNull null
            if (created != null && created.isBefore(startedAt)) return@mapNotNull null
            WorkflowRun(
                id = obj.long("id") ?: return@mapNotNull null,
                status = obj.string("status") ?: "",
                conclusion = obj.string("conclusion"),
                url = obj.string("html_url") ?: obj.string("url") ?: "",
                createdAt = created ?: Instant.EPOCH,
            )
        }.maxByOrNull { it.createdAt }
    }

    private fun downloadFirstApk(project: GitHubRemoteProject, runId: Long, repoName: String, branch: String): String {
        val artifactsUrl = repoApiUrl(project)
            .addPathSegment("actions")
            .addPathSegment("runs")
            .addPathSegment(runId.toString())
            .addPathSegment("artifacts")
            .build()
            .toString()
        val artifacts = executeObject(githubRequest(artifactsUrl).get().build())["artifacts"]?.jsonArray
            ?: throw IllegalStateException("Build completed without artifacts.")
        val artifact = artifacts.firstOrNull { item ->
            val obj = item.jsonObject
            obj.boolean("expired") != true && obj.string("name")?.contains("apk", ignoreCase = true) == true
        }?.jsonObject ?: throw IllegalStateException("Build completed, but no APK artifact was found.")
        val downloadUrl = artifact.string("archive_download_url")
            ?: throw IllegalStateException("APK artifact download URL is missing.")

        val first = githubRequest(downloadUrl).get().build()
        val response = openRedirect(first)
        response.use { archiveResponse ->
            if (!archiveResponse.isSuccessful) throw githubException(archiveResponse)
            val dir = File(context.getExternalFilesDir("apks") ?: context.filesDir, "github-builds").apply { mkdirs() }
            val safeName = "${repoName}_${branch}_${System.currentTimeMillis()}"
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
            ZipInputStream(archiveResponse.body?.byteStream()?.buffered()
                ?: throw IllegalStateException("APK artifact is empty.")).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                        val outFile = File(dir, "$safeName.apk")
                        outFile.outputStream().use { zip.copyTo(it) }
                        return outFile.absolutePath
                    }
                    zip.closeEntry()
                }
            }
        }
        throw IllegalStateException("APK artifact did not contain an .apk file.")
    }

    private fun openRedirect(request: Request): Response {
        var response = client.newCall(request).execute()
        if (response.isRedirect) {
            val location = response.header("Location")
                ?: throw IllegalStateException("GitHub redirect did not include a location.")
            response.close()
            response = client.newCall(githubRequest(location).get().build()).execute()
        }
        return response
    }

    private fun githubRequest(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "AgentWorkspace-Android")
            .apply { header("Authorization", "Bearer ${requireToken()}") }

    private fun executeElement(request: Request): kotlinx.serialization.json.JsonElement {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw githubException(response)
            return json.parseToJsonElement(response.body?.string().orEmpty())
        }
    }

    private fun executeObject(request: Request): JsonObject =
        executeElement(request).jsonObject

    private fun githubException(response: Response): IllegalStateException {
        val body = response.body?.string().orEmpty()
        val message = when (response.code) {
            401 -> "GitHub authentication failed. Check your token."
            403 -> "GitHub refused the request. The token needs contents/actions/workflows access, or the rate limit was reached."
            404 -> "GitHub resource not found, or the token does not have access."
            else -> "GitHub request failed (${response.code}): ${body.take(240)}"
        }
        return IllegalStateException(message)
    }

    private fun savedToken(): String? =
        credentialVault.get(GITHUB_CREDENTIAL_ID, CredentialVault.Field.ACCESS_TOKEN)

    private fun requireToken(): String =
        savedToken()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("GitHub token is required for remote editing and APK builds.")

    private fun repoUrl(project: GitHubRemoteProject): String = repoApiUrl(project).build().toString()

    private fun repoApiUrl(project: GitHubRemoteProject) =
        GITHUB_API.toHttpUrl().newBuilder()
            .addPathSegment("repos")
            .addPathSegment(project.owner)
            .addPathSegment(project.name)

    private fun refsUrl(project: GitHubRemoteProject): String =
        repoApiUrl(project)
            .addPathSegment("git")
            .addPathSegment("refs")
            .build()
            .toString()

    private fun refUrl(project: GitHubRemoteProject, branch: String): String =
        repoApiUrl(project)
            .addPathSegment("git")
            .addPathSegment("ref")
            .addPathSegments("heads/$branch")
            .build()
            .toString()

    private fun contentsUrl(project: GitHubRemoteProject, path: String, ref: String? = null): String {
        val builder = repoApiUrl(project)
            .addPathSegment("contents")
        path.trim('/').split('/').filter { it.isNotBlank() }.forEach { builder.addPathSegment(it) }
        if (ref != null) builder.addQueryParameter("ref", ref)
        return builder.build().toString()
    }

    private fun String.jsonBody() = toRequestBody("application/json".toMediaType())

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.obj(key: String): JsonObject? =
        this[key] as? JsonObject

    private fun isLikelyText(path: String): Boolean {
        val lower = path.lowercase(Locale.US)
        if (lower.substringAfterLast('.', "") in BINARY_EXTENSIONS) return false
        return true
    }

    private data class WorkflowRun(
        val id: Long,
        val status: String,
        val conclusion: String?,
        val url: String,
        val createdAt: Instant,
    )

    private companion object {
        const val GITHUB_API = "https://api.github.com"
        const val GITHUB_CREDENTIAL_ID = "github.project_import"
        const val BUILD_WORKFLOW_NAME = "AgentWorkspace APK Build"
        const val BUILD_WORKFLOW_FILE = "agentworkspace-build.yml"
        const val BUILD_WORKFLOW_PATH = ".github/workflows/$BUILD_WORKFLOW_FILE"
        const val BUILD_POLL_ATTEMPTS = 60
        const val BUILD_POLL_DELAY_MS = 10_000L

        val BINARY_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "ico", "pdf", "zip", "jar", "aar", "apk",
            "keystore", "jks", "so", "dll", "dylib", "class",
        )

        val BUILD_WORKFLOW = """
            name: $BUILD_WORKFLOW_NAME

            on:
              workflow_dispatch:
              push:
                branches:
                  - "**"

            jobs:
              build:
                runs-on: ubuntu-latest
                permissions:
                  contents: read
                  actions: read
                steps:
                  - uses: actions/checkout@v4
                  - uses: actions/setup-java@v4
                    with:
                      distribution: temurin
                      java-version: "17"
                  - name: Grant Gradle permission
                    run: chmod +x ./gradlew
                  - name: Build debug APK
                    run: ./gradlew assembleDebug
                  - name: Upload APK
                    uses: actions/upload-artifact@v4
                    with:
                      name: agentworkspace-debug-apk
                      path: |
                        **/build/outputs/apk/**/*.apk
        """.trimIndent()
    }
}
