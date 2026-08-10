package com.kesepain.kemoapp.update

import android.content.Context
import androidx.core.content.FileProvider
import com.kesepain.kemoapp.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class GitHubRelease(
    val tagName: String,
    val title: String,
    val notes: String,
    val publishedAt: String,
    val pageUrl: String,
    val apkName: String,
    val apkUrl: String,
    val apkSize: Long,
)

sealed interface ReleaseCheckResult {
    data class UpdateAvailable(val release: GitHubRelease) : ReleaseCheckResult
    data class UpToDate(val release: GitHubRelease) : ReleaseCheckResult
    data class ReleaseWithoutApk(val release: GitHubRelease) : ReleaseCheckResult
    data object NoPublishedRelease : ReleaseCheckResult
}

class AppUpdateRepository(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun loadGitHubAvatar(): ByteArray? = withContext(Dispatchers.IO) {
        val cache = File(context.cacheDir, AVATAR_CACHE_FILE)
        if (cache.isFile && cache.length() > 0L) return@withContext cache.readBytes()

        runCatching {
            val request = Request.Builder()
                .url(GITHUB_AVATAR_URL)
                .header("Accept", "image/*")
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body?.bytes()?.takeIf { it.isNotEmpty() } ?: return@use null
                runCatching { cache.writeBytes(bytes) }
                bytes
            }
        }.getOrNull()
    }

    suspend fun checkLatestRelease(currentVersion: String): ReleaseCheckResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext ReleaseCheckResult.NoPublishedRelease
            if (!response.isSuccessful) error("release check failed")
            val root = ApiClient.json.parseToJsonElement(response.body?.string().orEmpty()) as? JsonObject
                ?: error("invalid release response")
            val release = root.toRelease()
            when {
                release.apkUrl.isBlank() -> ReleaseCheckResult.ReleaseWithoutApk(release)
                compareVersions(release.tagName, currentVersion) > 0 -> ReleaseCheckResult.UpdateAvailable(release)
                else -> ReleaseCheckResult.UpToDate(release)
            }
        }
    }

    suspend fun downloadApk(
        release: GitHubRelease,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        require(isTrustedReleaseAsset(release.apkUrl))
        val updates = File(context.filesDir, UPDATES_DIRECTORY).apply { mkdirs() }
        val fileName = release.apkName.safeApkName(release.tagName)
        val target = File(updates, fileName)
        val partial = File(updates, "$fileName.part")
        partial.delete()

        val request = Request.Builder()
            .url(release.apkUrl)
            .header("Accept", "application/vnd.android.package-archive, application/octet-stream")
            .header("User-Agent", USER_AGENT)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("apk download failed")
                val body = response.body ?: error("empty apk response")
                val total = body.contentLength().takeIf { it > 0L } ?: release.apkSize
                body.byteStream().use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            onProgress(downloaded, total)
                        }
                    }
                }
            }
            if (partial.length() <= 0L) error("empty apk file")
            target.delete()
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            target
        } catch (failure: Throwable) {
            partial.delete()
            throw failure
        }
    }

    fun contentUri(file: File) = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    private fun JsonObject.toRelease(): GitHubRelease {
        val assets = this["assets"] as? JsonArray ?: JsonArray(emptyList())
        val apk = assets
            .mapNotNull { it as? JsonObject }
            .filter { asset ->
                val name = asset.text("name")
                name.endsWith(".apk", ignoreCase = true) && asset.text("browser_download_url").isNotBlank()
            }
            .sortedWith(
                compareByDescending<JsonObject> { it.text("name").contains("universal", ignoreCase = true) }
                    .thenByDescending { it.long("size") },
            )
            .firstOrNull()
        return GitHubRelease(
            tagName = text("tag_name"),
            title = text("name").ifBlank { text("tag_name") },
            notes = text("body"),
            publishedAt = text("published_at"),
            pageUrl = text("html_url").ifBlank { GITHUB_RELEASES_URL },
            apkName = apk?.text("name").orEmpty(),
            apkUrl = apk?.text("browser_download_url").orEmpty(),
            apkSize = apk?.long("size") ?: 0L,
        )
    }

    private fun JsonObject.text(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.long(key: String): Long = text(key).toLongOrNull() ?: 0L

    companion object {
        const val GITHUB_PROFILE_URL = "https://github.com/kesepain-KE"
        const val GITHUB_PROJECT_URL = "https://github.com/kesepain-KE/kemo-agent-app"
        const val GITHUB_RELEASES_URL = "$GITHUB_PROJECT_URL/releases"
        const val APACHE_LICENSE_URL = "$GITHUB_PROJECT_URL/blob/main/LICENSE"

        private const val LATEST_RELEASE_API = "https://api.github.com/repos/kesepain-KE/kemo-agent-app/releases/latest"
        private const val GITHUB_AVATAR_URL = "https://avatars.githubusercontent.com/u/157551852?v=4&s=256"
        private const val AVATAR_CACHE_FILE = "kesepain_github_avatar.png"
        private const val UPDATES_DIRECTORY = "updates"
        private const val USER_AGENT = "kemo-agent-app"

        internal fun compareVersions(latest: String, current: String): Int {
            val latestCore = latest.substringBefore('-').substringBefore('+')
            val currentCore = current.substringBefore('-').substringBefore('+')
            val latestNumbers = Regex("\\d+").findAll(latestCore).map { it.value.toLongOrNull() ?: 0L }.toList()
            val currentNumbers = Regex("\\d+").findAll(currentCore).map { it.value.toLongOrNull() ?: 0L }.toList()
            if (latestNumbers.isEmpty() || currentNumbers.isEmpty()) return 0
            repeat(maxOf(latestNumbers.size, currentNumbers.size)) { index ->
                val left = latestNumbers.getOrElse(index) { 0L }
                val right = currentNumbers.getOrElse(index) { 0L }
                if (left != right) return left.compareTo(right)
            }
            val latestPrerelease = latest.substringAfter('-', "").isNotBlank()
            val currentPrerelease = current.substringAfter('-', "").isNotBlank()
            return when {
                latestPrerelease == currentPrerelease -> 0
                latestPrerelease -> -1
                else -> 1
            }
        }

        private fun isTrustedReleaseAsset(url: String): Boolean = runCatching {
            val parsed = java.net.URI(url)
            parsed.scheme.equals("https", ignoreCase = true) && (
                parsed.host.equals("github.com", ignoreCase = true) ||
                    parsed.host.endsWith(".github.com", ignoreCase = true) ||
                    parsed.host.endsWith(".githubusercontent.com", ignoreCase = true)
                )
        }.getOrDefault(false)

        private fun String.safeApkName(tagName: String): String {
            val source = if (endsWith(".apk", ignoreCase = true)) this else "kemo-agent-app-$tagName.apk"
            val safe = source.replace(Regex("[^A-Za-z0-9._-]"), "_")
            return safe.take(120).let { if (it.endsWith(".apk", true)) it else "$it.apk" }
        }
    }
}
