package com.moneysmart.app.data.remote

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.moneysmart.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
        const val PREFS_NAME = "updates"
        const val KEY_LAST_SUCCESSFUL_START_MS = "last_successful_start_ms"
        const val KEY_DISMISSED_VERSION = "dismissed_version"
        const val CHECK_GUARD_MS = 60_000L

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

        fun shouldSkipAutoCheck(
            lastSuccessfulStartMs: Long,
            nowMs: Long,
            guardMs: Long = CHECK_GUARD_MS
        ): Boolean {
            return lastSuccessfulStartMs > 0L &&
                nowMs >= lastSuccessfulStartMs &&
                (nowMs - lastSuccessfulStartMs) < guardMs
        }

        fun shouldSuppressAutoPrompt(latestVersion: String, dismissedVersion: String?): Boolean {
            if (dismissedVersion.isNullOrBlank()) return false
            return !isNewerVersion(latestVersion, dismissedVersion)
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

        fun openUrl(context: Context, url: String): Boolean {
            return runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        }

        fun openApkOrReleasePage(context: Context, info: AppUpdateInfo): Boolean {
            return openUrl(context, info.apkDownloadUrl) ||
                openUrl(context, info.releasePageUrl) ||
                openUrl(context, FALLBACK_RELEASE_URL)
        }
    }
}

/**
 * Coordinates automatic foreground GitHub release checks (with a 60-second cooldown
 * and per-version "Later" dismissal) as well as manual update checks that bypass both.
 */
class AppUpdateCoordinator(
    context: Context,
    private val checker: AppUpdateChecker,
    private val currentVersionProvider: () -> String = { BuildConfig.VERSION_NAME },
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    private val prefs = context.applicationContext
        .getSharedPreferences(AppUpdateChecker.PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var inFlightCheck: Deferred<Result<AppUpdateInfo>>? = null

    private val _autoUpdatePrompt = MutableStateFlow<AppUpdateInfo?>(null)
    val autoUpdatePrompt: StateFlow<AppUpdateInfo?> = _autoUpdatePrompt.asStateFlow()

    fun onAppForegrounded() {
        if (_autoUpdatePrompt.value != null) return
        val checkDeferred: Deferred<Result<AppUpdateInfo>> = synchronized(lock) {
            inFlightCheck?.let { return@synchronized it }
            val now = clockMs()
            val last = prefs.getLong(AppUpdateChecker.KEY_LAST_SUCCESSFUL_START_MS, 0L)
            if (AppUpdateChecker.shouldSkipAutoCheck(last, now)) {
                return
            }
            startCheckLocked(now)
        }
        scope.launch {
            val info = checkDeferred.await().getOrNull() ?: return@launch
            if (!info.isUpdateAvailable) return@launch
            val dismissed = prefs.getString(AppUpdateChecker.KEY_DISMISSED_VERSION, null)
            if (!AppUpdateChecker.shouldSuppressAutoPrompt(info.latestVersion, dismissed)) {
                _autoUpdatePrompt.value = info
            }
        }
    }

    suspend fun checkNowManual(): Result<AppUpdateInfo> {
        val checkDeferred: Deferred<Result<AppUpdateInfo>> = synchronized(lock) {
            inFlightCheck ?: startCheckLocked(clockMs())
        }
        return checkDeferred.await()
    }

    private fun startCheckLocked(startMs: Long): Deferred<Result<AppUpdateInfo>> {
        val deferred = scope.async {
            val result = checker.checkLatestRelease(currentVersionProvider())
            prefs.edit().apply {
                if (result.isSuccess) {
                    putLong(AppUpdateChecker.KEY_LAST_SUCCESSFUL_START_MS, startMs)
                } else {
                    remove(AppUpdateChecker.KEY_LAST_SUCCESSFUL_START_MS)
                }
            }.apply()
            result
        }
        inFlightCheck = deferred
        deferred.invokeOnCompletion {
            synchronized(lock) {
                if (inFlightCheck === deferred) {
                    inFlightCheck = null
                }
            }
        }
        return deferred
    }

    fun dismissForLater(version: String) {
        _autoUpdatePrompt.value = null
        val normalized = AppUpdateChecker.normalizeVersion(version)
        if (normalized.isBlank()) return
        val dismissed = prefs.getString(AppUpdateChecker.KEY_DISMISSED_VERSION, null)
        if (dismissed == null || AppUpdateChecker.isNewerVersion(normalized, dismissed)) {
            prefs.edit().putString(AppUpdateChecker.KEY_DISMISSED_VERSION, normalized).apply()
        }
    }

    fun clearAutoPrompt() {
        _autoUpdatePrompt.value = null
    }
}
