package com.v4ex.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.v4ex.android.data.saveBitmapToGallery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 保存到相册的三种结局，界面各自决定提示文案。 */
enum class GallerySaveResult { Saved, Failed, PermissionDenied }

/**
 * 统一的「保存到相册」入口，大图预览和海报共用。
 *
 * bitmap 用挂起 lambda 传入而不直接传值：API 28 及以下要先弹权限框，
 * 拿到授权再去取图/编码，权限被拒时一点多余工作都不做。
 */
@Composable
fun rememberGallerySaveAction(onResult: (GallerySaveResult) -> Unit): (String, suspend () -> Bitmap?) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 回调包在 State 里，避免下面 lambda 捕获到重组前的旧闭包。
    val resultState = rememberUpdatedState(onResult)
    // 权限框是异步的，中间落盘的图先挂在这里，等回调回来再接着写。
    val pending = remember { mutableStateOf<Pair<String, suspend () -> Bitmap?>?>(null) }

    fun write(fileName: String, load: suspend () -> Bitmap?) {
        scope.launch {
            val saved = try {
                val bitmap = withContext(Dispatchers.IO) { load() }
                    ?: return@launch resultState.value(GallerySaveResult.Failed)
                saveBitmapToGallery(context, bitmap, fileName)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            resultState.value(if (saved) GallerySaveResult.Saved else GallerySaveResult.Failed)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val request = pending.value
        pending.value = null
        if (request == null) return@rememberLauncherForActivityResult
        if (granted) write(request.first, request.second) else resultState.value(GallerySaveResult.PermissionDenied)
    }

    return { fileName, load ->
        val granted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            write(fileName, load)
        } else {
            pending.value = fileName to load
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}
