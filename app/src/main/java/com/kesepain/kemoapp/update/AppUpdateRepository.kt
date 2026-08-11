package com.kesepain.kemoapp.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.Html
import androidx.core.content.FileProvider
import com.kesepain.kemoapp.data.remote.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@Serializable
data class GitHubRelease(
    val tagName: String,
    val title: String,
    val notes: String,
    val publishedAt: String,
    val pageUrl: String,
    val apkName: String,
    val apkUrl: String,
    val apkSize: Long,
    val apkSha256: String,
)

data class AppDownloadSource(
    val id: String,
    val displayName: String,
    val prefix: String = "",
    val official: Boolean = false,
) {
    fun resolve(assetUrl: String): String = if (official) assetUrl else prefix + assetUrl
}

data class DownloadedApk(
    val file: File,
    val source: AppDownloadSource,
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

    suspend fun checkLatestRelease(
        currentVersion: String,
        forceRefresh: Boolean = false,
    ): ReleaseCheckResult = withContext(Dispatchers.IO) {
        val cached = readCachedRelease()
        val release = if (!forceRefresh && cached != null && releaseCacheFile().lastModified() >= System.currentTimeMillis() - RELEASE_CACHE_FRESH_MS) {
            cached
        } else {
            runCatching { loadLatestReleaseFromApi() }
                .recoverCatching { loadLatestReleaseFromGitHubWeb() }
                .getOrElse { failure ->
                    if (failure is NoPublishedReleaseException && cached == null) {
                        return@withContext ReleaseCheckResult.NoPublishedRelease
                    }
                    cached ?: throw failure
                }
                .also(::writeCachedRelease)
        }
        val comparison = compareVersionsOrNull(release.tagName, currentVersion)
            ?: error("invalid release version")
        when {
            release.apkUrl.isBlank() -> ReleaseCheckResult.ReleaseWithoutApk(release)
            comparison > 0 -> ReleaseCheckResult.UpdateAvailable(release)
            else -> ReleaseCheckResult.UpToDate(release)
        }
    }

    private fun loadLatestReleaseFromApi(): GitHubRelease {
        val request = Request.Builder()
            .url(LATEST_RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) throw NoPublishedReleaseException()
            if (!response.isSuccessful) error("release check failed")
            val root = ApiClient.json.parseToJsonElement(response.body?.string().orEmpty()) as? JsonObject
                ?: error("invalid release response")
            return root.toRelease()
        }
    }

    private fun loadLatestReleaseFromGitHubWeb(): GitHubRelease {
        val latestRequest = Request.Builder()
            .url(GITHUB_RELEASES_LATEST_URL)
            .header("Accept", "text/html")
            .header("User-Agent", USER_AGENT)
            .build()
        val page = client.newCall(latestRequest).execute().use { response ->
            if (response.code == 404) throw NoPublishedReleaseException()
            if (!response.isSuccessful) error("GitHub release page failed")
            response.request.url.toString() to response.body?.string().orEmpty()
        }
        val tagName = page.first.substringAfterLast('/').trim()
        require(tagName.isNotBlank() && tagName != "latest") { "invalid GitHub release page" }
        val title = Regex("""<meta[^>]+(?:property|name)="(?:og:title|twitter:title)"[^>]+content="([^"]+)"""")
            .find(page.second)?.groupValues?.getOrNull(1)
            ?.let(::decodeHtml)
            ?.removePrefix("Release ")
            ?.substringBefore(" · kesepain-KE/kemo-agent-app")
            ?.trim()
            .orEmpty()
            .ifBlank { tagName }

        val assetsUrl = "$GITHUB_PROJECT_URL/releases/expanded_assets/$tagName"
        val assetsHtml = client.newCall(
            Request.Builder()
                .url(assetsUrl)
                .header("Accept", "text/html")
                .header("User-Agent", USER_AGENT)
                .build(),
        ).execute().use { response ->
            if (!response.isSuccessful) error("GitHub release assets failed")
            response.body?.string().orEmpty()
        }
        val apkBlock = Regex("(?s)<li\\b.*?</li>").findAll(assetsHtml)
            .map { it.value }
            .firstOrNull { block -> Regex("href=\"[^\"]+\\.apk\"").containsMatchIn(block) }
        val apkPath = apkBlock?.let { block ->
            Regex("href=\"([^\"]+\\.apk)\"").find(block)?.groupValues?.getOrNull(1)
        }.orEmpty()
        val digest = apkBlock?.let { block ->
            Regex("sha256:([0-9a-fA-F]{64})").find(block)?.groupValues?.getOrNull(1)
        }.orEmpty().lowercase()
        val publishedAt = apkBlock?.let { block ->
            Regex("datetime=\"([^\"]+)\"").find(block)?.groupValues?.getOrNull(1)
        }.orEmpty()
        val apkName = apkPath.substringAfterLast('/')
        return GitHubRelease(
            tagName = tagName,
            title = title,
            notes = "",
            publishedAt = publishedAt,
            pageUrl = page.first,
            apkName = apkName,
            apkUrl = apkPath.takeIf(String::isNotBlank)?.let { "https://github.com$it" }.orEmpty(),
            apkSize = 0L,
            apkSha256 = digest,
        )
    }

    private fun readCachedRelease(): GitHubRelease? = runCatching {
        releaseCacheFile().takeIf { it.isFile && it.length() > 0L }
            ?.readText()
            ?.let { ApiClient.json.decodeFromString<GitHubRelease>(it) }
    }.getOrNull()

    private fun writeCachedRelease(release: GitHubRelease) {
        runCatching { releaseCacheFile().writeText(ApiClient.json.encodeToString(release)) }
    }

    private fun releaseCacheFile() = File(context.cacheDir, RELEASE_CACHE_FILE)

    private fun decodeHtml(value: String): String = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()

    private class NoPublishedReleaseException : RuntimeException()

    suspend fun downloadApk(
        release: GitHubRelease,
        selectedSourceId: String,
        onProgress: (source: AppDownloadSource, downloaded: Long, total: Long) -> Unit,
    ): DownloadedApk = withContext(Dispatchers.IO) {
        require(isTrustedReleaseAsset(release.apkUrl))
        val updates = File(context.filesDir, UPDATES_DIRECTORY).apply { mkdirs() }
        val fileName = release.apkName.safeApkName(release.tagName)
        val target = File(updates, fileName)
        val partial = File(updates, "$fileName.part")
        val candidates = downloadCandidates(selectedSourceId)
        var lastFailure: Throwable? = null

        candidates.forEach { source ->
            partial.delete()
            try {
                val actualSha256 = downloadFromSource(release, source, partial, onProgress)
                validateDownloadedApk(partial, release, actualSha256)
                target.delete()
                if (!partial.renameTo(target)) {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                }
                return@withContext DownloadedApk(target, source)
            } catch (failure: CancellationException) {
                partial.delete()
                throw failure
            } catch (failure: Throwable) {
                partial.delete()
                lastFailure = failure
            }
        }
        throw IllegalStateException("all update download sources failed", lastFailure)
    }

    private fun downloadFromSource(
        release: GitHubRelease,
        source: AppDownloadSource,
        partial: File,
        onProgress: (source: AppDownloadSource, downloaded: Long, total: Long) -> Unit,
    ): String {
        val request = Request.Builder()
            .url(source.resolve(release.apkUrl))
            .header("Accept", "application/vnd.android.package-archive, application/octet-stream")
            .header("Cache-Control", "no-cache")
            .header("User-Agent", USER_AGENT)
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("update source returned ${response.code}")
            val contentType = response.header("Content-Type").orEmpty().lowercase()
            if (contentType.startsWith("text/html") || contentType.startsWith("text/plain")) {
                error("update source returned a non-APK response")
            }
            val body = response.body ?: error("empty apk response")
            val total = body.contentLength().takeIf { it > 0L } ?: release.apkSize
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            onProgress(source, 0L, total)
            body.byteStream().use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        downloaded += count
                        onProgress(source, downloaded, total)
                    }
                }
            }
            digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }
    }

    private fun validateDownloadedApk(file: File, release: GitHubRelease, actualSha256: String) {
        require(file.isFile && file.length() > 0L) { "empty apk file" }
        if (release.apkSize > 0L) require(file.length() == release.apkSize) { "apk size mismatch" }
        if (release.apkSha256.isNotBlank()) {
            require(actualSha256.equals(release.apkSha256, ignoreCase = true)) { "apk digest mismatch" }
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        @Suppress("DEPRECATION")
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("invalid apk package")
        require(archive.packageName == context.packageName) { "unexpected apk package" }
        require(compareVersionsOrNull(release.tagName, archive.versionName.orEmpty()) == 0) {
            "apk version does not match release"
        }
        @Suppress("DEPRECATION")
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        require(signingCertificates(archive) == signingCertificates(installed)) { "apk signing certificate mismatch" }
    }

    @Suppress("DEPRECATION")
    private fun signingCertificates(info: android.content.pm.PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
            else signingInfo.signingCertificateHistory
        } else {
            info.signatures
        }
        return signatures.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }.toSet()
    }

    private fun downloadCandidates(selectedSourceId: String): List<AppDownloadSource> {
        if (selectedSourceId == AUTO_DOWNLOAD_SOURCE_ID) return DOWNLOAD_SOURCES
        return listOf(DOWNLOAD_SOURCES.firstOrNull { it.id == selectedSourceId } ?: DOWNLOAD_SOURCES.first())
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
            apkSha256 = apk?.text("digest").orEmpty()
                .removePrefix("sha256:")
                .lowercase()
                .takeIf { it.matches(Regex("[0-9a-f]{64}")) }
                .orEmpty(),
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
        const val AUTO_DOWNLOAD_SOURCE_ID = "auto"

        val DOWNLOAD_SOURCES = listOf(
            AppDownloadSource("github", "GitHub Official", official = true),
            AppDownloadSource("ghproxy_net", "GHProxy.net", "https://ghproxy.net/"),
            AppDownloadSource("gh_proxy_com", "GH-Proxy Global", "https://gh-proxy.com/"),
            AppDownloadSource("gh_proxy_org", "GH-Proxy.org", "https://gh-proxy.org/"),
            AppDownloadSource("gh_proxy_ipv6", "GH-Proxy IPv6", "https://v6.gh-proxy.org/"),
        )

        private const val LATEST_RELEASE_API = "https://api.github.com/repos/kesepain-KE/kemo-agent-app/releases/latest"
        private const val GITHUB_RELEASES_LATEST_URL = "$GITHUB_RELEASES_URL/latest"
        private const val GITHUB_AVATAR_URL = "https://avatars.githubusercontent.com/u/157551852?v=4&s=256"
        private const val AVATAR_CACHE_FILE = "kesepain_github_avatar.png"
        private const val RELEASE_CACHE_FILE = "latest_app_release.json"
        private const val RELEASE_CACHE_FRESH_MS = 5L * 60L * 1000L
        private const val UPDATES_DIRECTORY = "updates"
        private const val USER_AGENT = "kemo-agent-app"

        internal fun compareVersions(latest: String, current: String): Int =
            compareVersionsOrNull(latest, current) ?: 0

        private fun compareVersionsOrNull(latest: String, current: String): Int? {
            val left = parseVersion(latest) ?: return null
            val right = parseVersion(current) ?: return null
            repeat(3) { index ->
                if (left.numbers[index] != right.numbers[index]) {
                    return left.numbers[index].compareTo(right.numbers[index])
                }
            }
            return comparePrerelease(left.prerelease, right.prerelease)
        }

        private fun parseVersion(value: String): ParsedVersion? {
            val match = VERSION_PATTERN.find(value.trim()) ?: return null
            return ParsedVersion(
                numbers = listOf(
                    match.groupValues[1].toLongOrNull() ?: return null,
                    match.groupValues[2].toLongOrNull() ?: 0L,
                    match.groupValues[3].toLongOrNull() ?: 0L,
                ),
                prerelease = match.groupValues[4].ifBlank { null },
            )
        }

        private fun comparePrerelease(left: String?, right: String?): Int {
            if (left == null && right == null) return 0
            if (left == null) return 1
            if (right == null) return -1
            val leftParts = left.split('.')
            val rightParts = right.split('.')
            repeat(maxOf(leftParts.size, rightParts.size)) { index ->
                val leftPart = leftParts.getOrNull(index) ?: return -1
                val rightPart = rightParts.getOrNull(index) ?: return 1
                val leftNumber = leftPart.toLongOrNull()
                val rightNumber = rightPart.toLongOrNull()
                val comparison = when {
                    leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                    leftNumber != null -> -1
                    rightNumber != null -> 1
                    else -> leftPart.compareTo(rightPart)
                }
                if (comparison != 0) return comparison
            }
            return 0
        }

        private data class ParsedVersion(val numbers: List<Long>, val prerelease: String?)

        private val VERSION_PATTERN = Regex(
            "(?i)(?:^|[^0-9A-Za-z])v?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:-([0-9A-Za-z.-]+))?",
        )

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
