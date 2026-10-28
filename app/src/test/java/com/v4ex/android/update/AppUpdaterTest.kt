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
}
