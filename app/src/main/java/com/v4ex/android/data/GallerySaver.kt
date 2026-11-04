package com.v4ex.android.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val ALBUM = "V4EX"
private const val MIME_PNG = "image/png"

/**
 * 把 bitmap 落进系统相册的 Pictures/V4EX，两套系统版本共用一条调用路径。
 *
 * API 29+ 走 MediaStore 插入：先置 IS_PENDING=1，写完再置 0，
 * 否则相册扫描会在文件还没写完时先看到半张图。
 * API 26–28 没有 RELATIVE_PATH 也没有 scoped storage 兜底，只能直接写公共图片目录，
 * 再让 MediaScanner 扫一次，不然相册里根本看不到（调用方须先拿到 WRITE_EXTERNAL_STORAGE）。
 *
 * 写入失败只会返回 false：磁盘满、相册被禁用、权限被拒这些对调用方没有区别，
 * 界面统一提示「保存失败，请重试」就够了。
 */
suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, bitmap, fileName)
            } else {
                saveViaPublicDirectory(context, bitmap, fileName)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

@RequiresApi(Build.VERSION_CODES.Q)
private fun saveViaMediaStore(context: Context, bitmap: Bitmap, fileName: String): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, MIME_PNG)
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    val written = try {
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } == true
    } catch (e: Exception) {
        // 留着半成品比保存失败更糟：相册里会多出一张打不开的图。
        resolver.delete(uri, null, null)
        throw e
    }
    if (!written) {
        resolver.delete(uri, null, null)
        return false
    }
    values.clear()
    values.put(MediaStore.Images.Media.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    return true
}

// API 28 及以下没有别的方式写公共相册，getExternalStoragePublicDirectory 是唯一选择。
@Suppress("DEPRECATION")
private fun saveViaPublicDirectory(context: Context, bitmap: Bitmap, fileName: String): Boolean {
    val album = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
    if (!album.isDirectory && !album.mkdirs()) return false
    val file = File(album, fileName)
    val written = file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    if (!written) {
        file.delete()
        return false
    }
    MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(MIME_PNG), null)
    return true
}
