package com.moneysmart.app.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val tagName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val releasePageUrl: String,
    val isUpdateAvailable: Boolean
)

class AppUpdateChecker(
    private val httpClient: OkHttpClient = defaultHttpClient()
) {
    suspend fun checkLatestRelease(currentVersion: String): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_RELEASE_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "MoneySmart-Android")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("GitHub check failed (HTTP ${response.code})")
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) error("Empty response from GitHub")
                parseGitHubReleaseJson(body = body, currentVersion = currentVersion)
            }
        }
    }

    companion object {
        const val LATEST_RELEASE_API_URL = "https://api.github.com/repos/mingzhe0005/money-smart/releases/latest"
        const val FALLBACK_APK_URL = "https://github.com/mingzhe0005/money-smart/releases/latest/download/MoneySmart.apk"
        const val FALLBACK_RELEASE_URL = "https://github.com/mingzhe0005/money-smart/releases/latest"

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        fun normalizeVersion(version: String): String {
            return version.trim().removePrefix("v").removePrefix("V").trim()
        }

        fun isNewerVersion(latestVersion: String, currentVersion: String): Boolean {
            val latestParts = parseVersionParts(latestVersion)
            val currentParts = parseVersionParts(currentVersion)
            val maxLen = maxOf(latestParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l != c) return l > c
            }
            return false
        }

        private fun parseVersionParts(version: String): List<Int> {
            val norm = normalizeVersion(version)
            if (norm.isBlank()) return emptyList()
            return norm.split('.').map { seg ->
                seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            }
        }

        fun parseGitHubReleaseJson(body: String, currentVersion: String): AppUpdateInfo {
            val root = json.parseToJsonElement(body).jsonObject
            val tagName = root["tag_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (tagName.isBlank()) error("Missing tag_name in release response")

            val latestVersion = normalizeVersion(tagName)
            val cleanCurrent = normalizeVersion(currentVersion)
            val releaseNotes = root["body"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val releasePageUrl = root["html_url"]?.jsonPrimitive?.contentOrNull?.trim()
                ?.takeIf { it.isNotBlank() } ?: FALLBACK_RELEASE_URL

            val apkDownloadUrl = root["assets"]?.jsonArray
                ?.asSequence()
                ?.mapNotNull { element ->
                    val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val url = obj["browser_download_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) url else null
                }
                ?.firstOrNull()
                ?: FALLBACK_APK_URL

            return AppUpdateInfo(
                currentVersion = cleanCurrent,
                latestVersion = latestVersion,
                tagName = tagName,
                releaseNotes = releaseNotes,
                apkDownloadUrl = apkDownloadUrl,
                releasePageUrl = releasePageUrl,
                isUpdateAvailable = isNewerVersion(latestVersion, cleanCurrent)
            )
        }

        fun openUrl(context: Context, url: String) {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }
}
