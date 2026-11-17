package com.example.data.repository

import com.example.BuildConfig
import com.example.data.model.AppConstants
import com.example.data.model.AppSettings
import com.example.data.model.AppUpdateInfo
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

data class GitHubAssetDto(
    @Json(name = "name") val name: String = "",
    @Json(name = "browser_download_url") val browserDownloadUrl: String = "",
    @Json(name = "size") val size: Long = 0L
)

data class GitHubReleaseDto(
    @Json(name = "tag_name") val tagName: String = "",
    @Json(name = "name") val name: String = "",
    @Json(name = "body") val body: String = "",
    @Json(name = "html_url") val htmlUrl: String = "",
    @Json(name = "published_at") val publishedAt: String = "",
    @Json(name = "assets") val assets: List<GitHubAssetDto> = emptyList()
)

interface GitHubReleaseApi {
    @Headers(
        "Accept: application/vnd.github+json",
        "X-GitHub-Api-Version: 2022-11-28",
        "User-Agent: BinaceSmartEarn-Android"
    )
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubReleaseDto
}

class AppUpdateRepository(
    private val api: GitHubReleaseApi = createDefaultApi()
) {
    suspend fun fetchLatestUpdate(
        settings: AppSettings,
        currentVersionName: String = BuildConfig.VERSION_NAME,
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        embeddedRepoSlug: String = BuildConfig.GITHUB_REPOSITORY
    ): AppUpdateInfo {
        val normalizedCurrent = normalizeVersion(currentVersionName).ifBlank { "1.0.0" }
        val effectiveSlug = normalizeRepoSlug(
            settings.githubRepoSlug.ifBlank { embeddedRepoSlug }
        )?.let { (owner, repo) -> "$owner/$repo" }.orEmpty()

        val firestoreVersion = normalizeVersion(settings.latestVersionName)
        val firestoreCode = settings.latestVersionCode.toInt().coerceAtLeast(0)
        val firestoreUrl = settings.apkDownloadUrl.trim().takeIf { it.startsWith("https://") }
            ?: if (effectiveSlug.isNotBlank()) buildPublicLatestApkUrl(effectiveSlug) else ""

        var bestVersion = normalizedCurrent
        var bestCode = currentVersionCode
        var bestUrl = firestoreUrl
        var bestNotes = settings.releaseNotes.trim()
        var bestSizeBytes = 0L
        var bestPublishedAt = ""
        var sourceLabel = if (effectiveSlug.isNotBlank()) "GitHub Releases ($effectiveSlug)" else "Installed Build"
        var errorMessage: String? = null

        val firestoreAhead = (firestoreVersion.isNotBlank() && compareVersions(firestoreVersion, normalizedCurrent) > 0) ||
            (firestoreCode > currentVersionCode)
        if (firestoreVersion.isNotBlank() && firestoreAhead) {
            bestVersion = firestoreVersion
            bestCode = maxOf(firestoreCode, currentVersionCode)
            sourceLabel = "Platform Release Settings"
        } else if (firestoreVersion.isNotBlank() && bestVersion == normalizedCurrent) {
            bestVersion = firestoreVersion
        }

        val ownerRepo = normalizeRepoSlug(effectiveSlug)
        if (ownerRepo != null) {
            try {
                val (owner, repo) = ownerRepo
                val release = api.getLatestRelease(owner, repo)
                val remoteVersion = normalizeVersion(release.tagName.ifBlank { release.name })
                val (remoteApkUrl, remoteSizeBytes) = extractApkAsset(
                    assets = release.assets,
                    repoSlug = "$owner/$repo",
                    fallbackHtmlUrl = release.htmlUrl
                )
                val remoteNotes = release.body.trim()

                if (remoteVersion.isNotBlank() && compareVersions(remoteVersion, bestVersion) >= 0) {
                    bestVersion = remoteVersion
                    if (remoteApkUrl.isNotBlank()) {
                        bestUrl = remoteApkUrl
                    }
                    if (remoteSizeBytes > 0L) {
                        bestSizeBytes = remoteSizeBytes
                    }
                    if (release.publishedAt.isNotBlank()) {
                        bestPublishedAt = release.publishedAt
                    }
                    if (remoteNotes.isNotBlank()) {
                        bestNotes = remoteNotes
                    }
                    sourceLabel = "GitHub Release v$remoteVersion"
                } else {
                    if (bestUrl.isBlank() && remoteApkUrl.isNotBlank()) {
                        bestUrl = remoteApkUrl
                    }
                    if (bestSizeBytes == 0L && remoteSizeBytes > 0L) {
                        bestSizeBytes = remoteSizeBytes
                    }
                    if (bestPublishedAt.isBlank() && release.publishedAt.isNotBlank()) {
                        bestPublishedAt = release.publishedAt
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = "Could not reach GitHub Releases (${e.message ?: "network error"})."
            }
        }

        val isUpdateAvailable = compareVersions(bestVersion, normalizedCurrent) > 0 ||
            (bestCode > currentVersionCode)

        return AppUpdateInfo(
            currentVersionName = normalizedCurrent,
            currentVersionCode = currentVersionCode,
            latestVersionName = bestVersion.ifBlank { normalizedCurrent },
            latestVersionCode = bestCode.coerceAtLeast(currentVersionCode),
            downloadUrl = bestUrl,
            releaseNotes = bestNotes,
            apkFileSizeBytes = bestSizeBytes,
            publishedAt = bestPublishedAt,
            githubRepoSlug = effectiveSlug,
            isUpdateAvailable = isUpdateAvailable,
            isChecking = false,
            sourceLabel = sourceLabel,
            errorMessage = errorMessage
        )
    }

    companion object {
        const val LATEST_APK_ASSET_NAME = AppConstants.APK_ASSET_NAME
        private const val LEGACY_APK_ASSET_NAME = "binace-smart-earn-latest.apk"

        private fun createDefaultApi(): GitHubReleaseApi {
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl("https://api.github.com/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(GitHubReleaseApi::class.java)
        }

        fun normalizeRepoSlug(raw: String): Pair<String, String>? {
            val cleaned = raw.trim()
                .removePrefix("https://github.com/")
                .removePrefix("http://github.com/")
                .removeSuffix(".git")
                .trim('/')
            val parts = cleaned.split('/').filter { it.isNotBlank() }
            if (parts.size < 2) return null
            val owner = parts[0]
            val repo = parts[1]
            val validToken = Regex("^[A-Za-z0-9_.-]+$")
            if (!validToken.matches(owner) || !validToken.matches(repo)) return null
            return owner to repo
        }

        fun buildPublicLatestApkUrl(repoSlug: String): String {
            val pair = normalizeRepoSlug(repoSlug) ?: return ""
            return "https://github.com/${pair.first}/${pair.second}/releases/latest/download/$LATEST_APK_ASSET_NAME"
        }

        fun extractApkAsset(
            assets: List<GitHubAssetDto>,
            repoSlug: String,
            fallbackHtmlUrl: String = ""
        ): Pair<String, Long> {
            val exactLatest = assets.firstOrNull {
                (it.name.equals(LATEST_APK_ASSET_NAME, ignoreCase = true) ||
                    it.name.equals(LEGACY_APK_ASSET_NAME, ignoreCase = true)) &&
                    it.browserDownloadUrl.startsWith("https://")
            }
            if (exactLatest != null) return exactLatest.browserDownloadUrl to exactLatest.size

            val anyApk = assets.firstOrNull {
                it.name.endsWith(".apk", ignoreCase = true) &&
                    it.browserDownloadUrl.startsWith("https://")
            }
            if (anyApk != null) return anyApk.browserDownloadUrl to anyApk.size

            val publicLatest = buildPublicLatestApkUrl(repoSlug)
            if (publicLatest.isNotBlank()) return publicLatest to 0L

            return (fallbackHtmlUrl.takeIf { it.startsWith("https://") }.orEmpty()) to 0L
        }

        fun extractApkDownloadUrl(
            assets: List<GitHubAssetDto>,
            repoSlug: String,
            fallbackHtmlUrl: String = ""
        ): String = extractApkAsset(assets, repoSlug, fallbackHtmlUrl).first

        fun normalizeVersion(raw: String): String {
            val trimmed = raw.trim().removePrefix("v").removePrefix("V")
            val match = Regex("""\d+(?:\.\d+)*""").find(trimmed)
            return match?.value.orEmpty()
        }

        fun compareVersions(remote: String, current: String): Int {
            val remoteParts = normalizeVersion(remote)
                .split('.')
                .filter { it.isNotBlank() }
                .map { it.toIntOrNull() ?: 0 }
            val currentParts = normalizeVersion(current)
                .split('.')
                .filter { it.isNotBlank() }
                .map { it.toIntOrNull() ?: 0 }

            if (remoteParts.isEmpty() && currentParts.isEmpty()) return 0
            if (remoteParts.isEmpty()) return -1
            if (currentParts.isEmpty()) return 1

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r != c) return r.compareTo(c)
            }
            return 0
        }
    }
}
