package com.v4ex.android.update

import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.v4ex.android.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal const val RELEASES = "https://github.com/ginuim/v4ex/releases/latest"
data class AppRelease(val code: Int, val version: String, val notes: String, val url: String, val sha256: String)
data class UpdateState(
    val checking: Boolean = false, val release: AppRelease? = null,
    val downloading: Boolean = false, val progress: Int = 0, val verifying: Boolean = false,
    val apk: File? = null, val message: String? = null,
)

class AppUpdater(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("app_updates", 0)
    private val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.MINUTES)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val mutable = MutableStateFlow(UpdateState())
    val state = mutable.asStateFlow()

    fun check(manual: Boolean = false) {
        if (mutable.value.checking || mutable.value.downloading || mutable.value.verifying || mutable.value.release != null) return
        val now = System.currentTimeMillis()
        val last = prefs.getLong("checked", 0)
        if (!manual && now >= last && now - last < 86_400_000) return
        mutable.value = UpdateState(checking = true)
        viewModelScope.launch {
            try {
                val release = withContext(Dispatchers.IO) {
                    val json = JSONObject(get("https://api.github.com/repos/ginuim/v4ex/releases/latest"))
                    val assets = json.getJSONArray("assets")
                    val metadata = (0 until assets.length()).map { assets.getJSONObject(it) }
                        .firstOrNull { it.getString("name") == "update.json" }
                        ?: error("新版本尚未准备完成，请稍后重试")
                    parseGithubRelease(json, get(metadata.getString("browser_download_url")))
                }
                prefs.edit().putLong("checked", now).apply()
                val newer = release.code > BuildConfig.VERSION_CODE
                mutable.value = UpdateState(
                    release = release.takeIf { newer && (manual || prefs.getInt("skipped", 0) != it.code) },
                    message = if (manual && !newer) "当前已是最新版本" else null,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.value = UpdateState(message = if (manual) "检查失败：${e.message}" else null)
            }
        }
    }

    private fun get(url: String): String = client.newCall(Request.Builder().url(url)
        .header("Accept", "application/vnd.github+json")
        .header("Cache-Control", "no-cache").build()).execute().use {
        check(it.isSuccessful) { if (it.code == 404) "暂无可用的正式版本" else "GitHub 返回 ${it.code}" }
        it.body?.string() ?: error("响应为空")
    }

    fun dismiss(skip: Boolean = false) {
        if (mutable.value.downloading || mutable.value.verifying) return
        if (skip) mutable.value.release?.let { prefs.edit().putInt("skipped", it.code).apply() }
        mutable.value = UpdateState()
    }

    fun download() {
        val release = mutable.value.release ?: return
        if (mutable.value.downloading || mutable.value.verifying) return
        mutable.value = mutable.value.copy(downloading = true, progress = 0, apk = null, message = null)
        viewModelScope.launch {
            try {
                val apk = withContext(Dispatchers.IO) {
                    val dir = File(getApplication<Application>().cacheDir, "updates").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val part = File(dir, "${release.apkFileName()}.part")
                    try {
                        client.newCall(Request.Builder().url(release.url).header("Cache-Control", "no-cache").build()).execute().use { response ->
                            check(response.isSuccessful) { "下载失败：${response.code}" }
                            val body = response.body ?: error("安装包为空")
                            val digest = MessageDigest.getInstance("SHA-256")
                            var received = 0L
                            body.byteStream().use { input -> part.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                    digest.update(buffer, 0, count)
                                    received += count
                                    check(received <= 250L * 1024 * 1024) { "安装包过大" }
                                    val progress = if (body.contentLength() > 0) (received * 100 / body.contentLength()).toInt() else 0
                                    mutable.value = mutable.value.copy(progress = progress.coerceIn(0, 100))
                                }
                            } }
                            check(digest.digest().joinToString("") { "%02x".format(it) }.equals(release.sha256, true)) { "安装包校验失败，请重新下载" }
                        }
                        val target = File(dir, release.apkFileName())
                        check(part.renameTo(target)) { "无法保存安装包" }
                        validateApk(target, release)
                        target
                    } finally { part.delete() }
                }
                mutable.value = mutable.value.copy(downloading = false, apk = apk)
                install()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.value = mutable.value.copy(downloading = false, apk = null, message = e.message ?: "下载失败，请重试")
            }
        }
    }

    /** Revalidate on each install attempt, including after granting unknown-source access. */
    private fun validateApk(apk: File, release: AppRelease) {
        check(apk.isFile) { "安装包已失效，请重新下载" }
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        check(digest.digest().joinToString("") { "%02x".format(it) }.equals(release.sha256, true)) {
            "安装包校验失败，请重新下载"
        }
        val pm = getApplication<Application>().packageManager
        @Suppress("DEPRECATION")
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, android.content.pm.PackageManager.GET_SIGNATURES)
            ?: error("安装包无法解析")
        @Suppress("DEPRECATION")
        val installed = pm.getPackageInfo(BuildConfig.APPLICATION_ID, android.content.pm.PackageManager.GET_SIGNATURES)
        @Suppress("DEPRECATION")
        check(archive.packageName == BuildConfig.APPLICATION_ID &&
            archive.longVersionCodeCompat() == release.code.toLong() &&
            archive.versionName == release.version &&
            archive.longVersionCodeCompat() > installed.longVersionCodeCompat() &&
            !archive.signatures.isNullOrEmpty() &&
            archive.signatures?.toSet() == installed.signatures?.toSet()) { "安装包版本或签名不匹配" }
    }

    fun install() {
        val app = getApplication<Application>()
        val snapshot = mutable.value
        val apk = snapshot.apk ?: return
        val release = snapshot.release ?: return
        if (snapshot.downloading || snapshot.verifying) return
        mutable.value = snapshot.copy(verifying = true, message = "正在校验安装包…")
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { validateApk(apk, release) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(verifying = false, apk = null, message = e.message)
                return@launch
            }
            try {
                mutable.value = mutable.value.copy(verifying = false, message = null)
                if (!app.packageManager.canRequestPackageInstalls()) {
                    mutable.value = mutable.value.copy(message = "请允许安装应用，返回后点击「安装 ${release.version}」")
                    app.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return@launch
                }
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.share", apk)
                app.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .apply { clipData = ClipData.newRawUri("V4EX ${release.version}", uri) }
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(verifying = false, message = "无法打开安装程序：${e.message}")
            }
        }
    }

}

internal fun parseRelease(raw: String): AppRelease {
    val json = JSONObject(raw)
    val url = json.getString("apkUrl")
    require(url.startsWith("https://github.com/ginuim/v4ex/releases/download/")) { "安装包地址无效" }
    val sha = json.getString("sha256")
    require(sha.matches(Regex("[a-fA-F0-9]{64}"))) { "校验信息无效" }
    val code = json.getInt("versionCode")
    val version = json.getString("versionName")
    require(code > 0 && version.matches(Regex("[A-Za-z0-9][A-Za-z0-9._+-]*"))) { "版本信息无效" }
    val release = AppRelease(code, version, json.optString("notes"), url, sha)
    if (json.has("apkFileName")) {
        require(json.getString("apkFileName") == release.apkFileName() && url.endsWith("/" + release.apkFileName())) {
            "安装包文件名与版本不匹配"
        }
    }
    return release
}

internal fun parseGithubRelease(github: JSONObject, metadata: String): AppRelease {
    val release = parseRelease(metadata)
    val tag = github.getString("tag_name")
    require(tag == "v${release.version}" && release.url.startsWith("https://github.com/ginuim/v4ex/releases/download/$tag/")) {
        "更新元数据与 Release 不匹配，请稍后重试"
    }
    val assets = github.getJSONArray("assets")
    require((0 until assets.length()).any { assets.getJSONObject(it).optString("browser_download_url") == release.url }) {
        "新版本安装包尚未准备完成，请稍后重试"
    }
    return release
}

internal fun AppRelease.apkFileName(): String = "V4EX-$version-$code.apk"

@Suppress("DEPRECATION")
private fun android.content.pm.PackageInfo.longVersionCodeCompat(): Long =
    if (android.os.Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()
