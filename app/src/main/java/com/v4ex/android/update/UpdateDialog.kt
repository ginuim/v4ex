package com.v4ex.android.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun UpdateDialog(state: UpdateState, updater: AppUpdater) {
    val release = state.release
    if (release != null) {
        AlertDialog(
            onDismissRequest = { updater.dismiss() },
            title = { Text("发现新版本 ${release.version}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(release.notes.ifBlank { "有新版本可供更新" })
                    if (state.downloading) {
                        LinearProgressIndicator(progress = { state.progress / 100f })
                        Text("正在下载 ${state.progress}%")
                    }
                    state.message?.let { Text(it) }
                }
            },
            confirmButton = {
                TextButton(enabled = !state.downloading, onClick = {
                    if (state.apk != null) updater.install() else updater.download()
                }) { Text(if (state.apk != null) "安装更新" else "立即更新") }
            },
            dismissButton = {
                TextButton(enabled = !state.downloading, onClick = { updater.dismiss(skip = true) }) { Text("跳过此版本") }
            },
        )
    } else if (state.message != null) {
        AlertDialog(onDismissRequest = { updater.dismiss() }, title = { Text("检查更新") },
            text = { Text(state.message) }, confirmButton = {
                TextButton(onClick = { updater.dismiss() }) { Text("确定") }
            })
    }
}
