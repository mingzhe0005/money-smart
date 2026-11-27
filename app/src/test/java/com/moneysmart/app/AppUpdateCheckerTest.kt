package com.moneysmart.app

import com.moneysmart.app.data.remote.AppUpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {

    @Test
    fun comparesSemanticVersionSegmentsNumerically() {
        assertTrue(AppUpdateChecker.isNewerVersion("v1.0.10", "1.0.9"))
        assertTrue(AppUpdateChecker.isNewerVersion("1.10", "1.9"))
        assertTrue(AppUpdateChecker.isNewerVersion("v1.0.20", "1.0.19"))
        assertFalse(AppUpdateChecker.isNewerVersion("1.0.10", "1.0.10"))
        assertFalse(AppUpdateChecker.isNewerVersion("v1.0.8", "1.0.10"))
        assertTrue(AppUpdateChecker.isNewerVersion("v1.1.0", "1.0.10"))
        assertTrue(AppUpdateChecker.isNewerVersion("v2.0.0", "1.9.9"))
    }

    @Test
    fun skipsAutoCheckOnlyWithinSixtySecondsOfSuccessfulStart() {
        assertFalse(AppUpdateChecker.shouldSkipAutoCheck(0L, 1_000_000L))
        assertTrue(AppUpdateChecker.shouldSkipAutoCheck(1_000_000L, 1_000_000L))
        assertTrue(AppUpdateChecker.shouldSkipAutoCheck(1_000_000L, 1_059_999L))
        assertFalse(AppUpdateChecker.shouldSkipAutoCheck(1_000_000L, 1_060_000L))
        assertFalse(AppUpdateChecker.shouldSkipAutoCheck(1_000_000L, 999_000L))
    }

    @Test
    fun suppressesAutoPromptOnlyForDismissedOrOlderVersion() {
        assertFalse(AppUpdateChecker.shouldSuppressAutoPrompt("1.0.20", null))
        assertTrue(AppUpdateChecker.shouldSuppressAutoPrompt("1.0.20", "1.0.20"))
        assertTrue(AppUpdateChecker.shouldSuppressAutoPrompt("1.0.19", "1.0.20"))
        assertFalse(AppUpdateChecker.shouldSuppressAutoPrompt("1.0.21", "1.0.20"))
    }

    @Test
    fun parsesGitHubLatestReleaseJsonAndFallsBackWhenNoApkAsset() {
        val sampleJson = """
            {
              "tag_name": "v1.0.11",
              "html_url": "https://github.com/mingzhe0005/money-smart/releases/tag/v1.0.11",
              "body": "- Added in-app update checker\n- Bug fixes",
              "assets": [
                {
                  "name": "MoneySmart.apk",
                  "browser_download_url": "https://github.com/mingzhe0005/money-smart/releases/download/v1.0.11/MoneySmart.apk"
                }
              ]
            }
        """.trimIndent()

        val info = AppUpdateChecker.parseGitHubReleaseJson(sampleJson, "1.0.10")
        assertEquals("1.0.10", info.currentVersion)
        assertEquals("1.0.11", info.latestVersion)
        assertEquals("v1.0.11", info.tagName)
        assertEquals(
            "https://github.com/mingzhe0005/money-smart/releases/download/v1.0.11/MoneySmart.apk",
            info.apkDownloadUrl
        )
        assertEquals(
            "https://github.com/mingzhe0005/money-smart/releases/tag/v1.0.11",
            info.releasePageUrl
        )
        assertTrue(info.releaseNotes.contains("Added in-app update checker"))
        assertTrue(info.isUpdateAvailable)

        val noAssetsJson = """
            {
              "tag_name": "v1.0.10",
              "html_url": "",
              "body": "",
              "assets": []
            }
        """.trimIndent()
        val sameVersionInfo = AppUpdateChecker.parseGitHubReleaseJson(noAssetsJson, "1.0.10")
        assertFalse(sameVersionInfo.isUpdateAvailable)
        assertEquals(AppUpdateChecker.FALLBACK_APK_URL, sameVersionInfo.apkDownloadUrl)
        assertEquals(AppUpdateChecker.FALLBACK_RELEASE_URL, sameVersionInfo.releasePageUrl)
    }
}
