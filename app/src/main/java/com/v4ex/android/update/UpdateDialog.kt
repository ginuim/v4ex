package com.v4ex.android.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import com.v4ex.android.ui.MarkdownContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background

@Composable
fun UpdateDialog(state: UpdateState, updater: AppUpdater) {
    val release = state.release
    if (release != null) {
        AlertDialog(
            onDismissRequest = { updater.dismiss() },
            title = { Text("发现新版本 ${release.version}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    MarkdownContent(release.notes.ifBlank { "有新版本可供更新" })
                    if (state.downloading) {
                        Spacer(Modifier.height(16.dp))
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(4.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth((state.progress / 100f).coerceIn(0f, 1f)).height(4.dp).background(MaterialTheme.colorScheme.primary))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("正在下载 ${release.version} · ${state.progress}%")
                    }
                    state.message?.let { Text(it) }
                }
            },
            confirmButton = {
                TextButton(enabled = !state.downloading && !state.verifying, onClick = {
                    if (state.apk != null) updater.install() else updater.download()
                }) { Text(if (state.apk != null) "安装 ${release.version}" else "下载 ${release.version}") }
            },
            dismissButton = {
                TextButton(enabled = !state.downloading && !state.verifying, onClick = { updater.dismiss(skip = true) }) { Text("跳过此版本") }
            },
        )
    } else if (state.message != null) {
        AlertDialog(onDismissRequest = { updater.dismiss() }, title = { Text("检查更新") },
            text = { Text(state.message) }, confirmButton = {
                TextButton(onClick = { updater.dismiss() }) { Text("确定") }
            })
    }
}
