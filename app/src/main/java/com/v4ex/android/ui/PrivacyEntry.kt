package com.v4ex.android.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun PrivacyEntry(content: @Composable (onClick: () -> Unit) -> Unit = { onClick ->
    TextButton(onClick = onClick) { Text("隐私政策与数据说明") }
}) {
    var visible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    content { visible = true }
    if (visible) {
        val policy = remember { context.assets.open("privacy.txt").bufferedReader().use { it.readText() } }
        AlertDialog(
            onDismissRequest = { visible = false },
            title = { Text("隐私政策") },
            text = { MarkdownContent(text = policy, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { visible = false }) { Text("关闭") } },
        )
    }
}
