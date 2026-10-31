package com.v4ex.android.update

import org.junit.Assert.assertEquals
import org.junit.Test

class AppUpdaterTest {
    private fun metadata(url: String = "https://github.com/ginuim/v4ex/releases/download/v1/V4EX.apk", hash: String = "a".repeat(64)) =
        """{"versionCode":10,"versionName":"0.6.0","notes":"更新说明","apkUrl":"$url","sha256":"$hash"}"""

    @Test fun readsBuildVersionAndNotes() {
        val result = parseRelease(metadata())
        assertEquals(10, result.code)
        assertEquals("更新说明", result.notes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOtherRepository() { parseRelease(metadata(url = "https://github.com/other/app/releases/download/v1/app.apk")) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidHash() { parseRelease(metadata(hash = "invalid")) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHttpDownload() { parseRelease(metadata(url = "http://github.com/ginuim/v4ex/releases/download/v1/V4EX.apk")) }
    @Test fun downloadNamesDistinguishVersionsAndBuilds() {
        val release = parseRelease(metadata())
        assertEquals("V4EX-0.6.0-10.apk", release.apkFileName())
        org.junit.Assert.assertNotEquals(release.apkFileName(), release.copy(code = 11).apkFileName())
        org.junit.Assert.assertNotEquals(release.apkFileName(), release.copy(version = "0.6.1").apkFileName())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsafeVersionInFilename() {
        parseRelease(metadata().replace("0.6.0", "../app"))
    }
    private fun github(tag: String = "v0.6.0", apk: String = "https://github.com/ginuim/v4ex/releases/download/v0.6.0/V4EX-0.6.0-10.apk") =
        org.json.JSONObject().put("tag_name", tag).put("assets", org.json.JSONArray().put(org.json.JSONObject().put("browser_download_url", apk)))

    @Test fun acceptsConsistentVersionedReleaseAndRetainsLegacyMetadataSupport() {
        val url = "https://github.com/ginuim/v4ex/releases/download/v0.6.0/V4EX-0.6.0-10.apk"
        val json = org.json.JSONObject(metadata(url = url)).put("apkFileName", "V4EX-0.6.0-10.apk")
        assertEquals("V4EX-0.6.0-10.apk", parseGithubRelease(github(), json.toString()).apkFileName())
        assertEquals(10, parseRelease(metadata()).code)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMetadataFromDifferentRelease() {
        parseGithubRelease(github(tag = "v0.6.1"), metadata())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingApkAsset() {
        val json = github().put("assets", org.json.JSONArray())
        parseGithubRelease(json, metadata(url = "https://github.com/ginuim/v4ex/releases/download/v0.6.0/V4EX-0.6.0-10.apk"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFileNameThatBelongsToDifferentBuild() {
        parseRelease(org.json.JSONObject(metadata()).put("apkFileName", "V4EX-0.6.0-9.apk").toString())
    }
}
