package com.v4ex.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.v4ex.android.data.VNode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.v4ex.android.ui.theme.Ink

/** 发帖与回帖共用编辑器；只有主动提交才会调用写接口。 */
@Composable
fun PublishDialog(
    replyTopicId: Int?,
    nodes: List<VNode>,
    nodesLoading: Boolean,
    nodesError: String?,
    onReloadNodes: () -> Unit,
    sending: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onPublish: (String, String, String, String) -> Unit,
) {
    var node by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var markdown by rememberSaveable { mutableStateOf(true) }
    var discard by remember { mutableStateOf(false) }
    val replying = replyTopicId != null
    val contentLimit = if (replying) 10000 else 20000
    val contentLength = content.codePointCount(0, content.length)
    val titleLength = title.trim().codePointCount(0, title.trim().length)
    val valid = contentLength <= contentLimit && if (replying) content.isNotBlank() else
        node.trim().matches(Regex("[A-Za-z0-9_-]+")) && titleLength in 1..120
    val close = {
        if (!sending) {
            if (node.isNotBlank() || title.isNotBlank() || content.isNotBlank()) discard = true
            else onDismiss()
        }
    }
    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Column(Modifier.fillMaxSize().background(Ink.Surface).systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = close, enabled = !sending) { Text("取消") }
                TextButton(
                    enabled = valid && !sending,
                    onClick = { onPublish(node.trim(), title.trim(), content, if (markdown) "markdown" else "default") },
                ) { Text(if (sending) "正在提交…" else if (replying) "发布回复" else "发布主题") }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(if (replying) "回复 #$replyTopicId" else "发布主题", fontSize = 24.sp, color = Ink.Primary)
                Text(
                    "需要 V2EX PRO。发布仍受节点权限和频率限制，并按站点规则消耗铜币。",
                    fontSize = 13.sp, lineHeight = 20.sp, color = Ink.Secondary,
                )
                if (!replying) {
                    NodeDropdown(
                        selected = node,
                        nodes = nodes,
                        loading = nodesLoading,
                        error = nodesError,
                        enabled = !sending,
                        onSelect = { node = it },
                        onReload = onReloadNodes,
                    )
                    OutlinedTextField(
                        value = title, onValueChange = { title = it }, enabled = !sending,
                        label = { Text("标题 · $titleLength / 120") }, singleLine = true,
                        isError = titleLength > 120, modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { markdown = !markdown }, enabled = !sending) {
                        Text(if (markdown) "正文格式：Markdown · 点击切换" else "正文格式：默认 · 点击切换")
                    }
                }
                OutlinedTextField(
                    value = content, onValueChange = { content = it }, enabled = !sending,
                    label = { Text(if (replying) "回复内容" else "正文（可选）") },
                    minLines = 8, isError = contentLength > contentLimit,
                    supportingText = { Text("$contentLength / $contentLimit") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) Text(error, color = Ink.Badge, fontSize = 13.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(20.dp))
            }
        }
        if (discard) {
            AlertDialog(
                onDismissRequest = { discard = false },
                title = { Text("放弃编辑？") },
                text = { Text("关闭后会丢弃本次未发布的内容。") },
                confirmButton = { TextButton(onClick = { discard = false; onDismiss() }) { Text("放弃") } },
                dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } },
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodeDropdown(
    selected: String,
    nodes: List<VNode>,
    loading: Boolean,
    error: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onReload: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val matches = remember(nodes, query, selected) {
        val term = if (selected.isNotBlank()) "" else query.trim()
        nodes.filter { term.isEmpty() || it.title.contains(term, ignoreCase = true) || it.name.contains(term, ignoreCase = true) }
    }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onSelect("")
                expanded = true
            },
            enabled = enabled,
            label = { Text("发布节点") },
            placeholder = { Text("搜索节点名称或中文标题") },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            supportingText = { Text(if (selected.isNotBlank()) "/go/$selected" else "请从下拉列表选择节点") },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable, enabled = enabled),
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 280.dp),
        ) {
            when {
                loading && nodes.isEmpty() -> DropdownMenuItem(text = { Text("正在加载节点…") }, onClick = {}, enabled = false)
                error != null && nodes.isEmpty() -> DropdownMenuItem(text = { Text("加载失败，点击重试") }, onClick = onReload)
                matches.isEmpty() -> DropdownMenuItem(text = { Text("没有匹配的节点") }, onClick = {}, enabled = false)
                else -> {
                    matches.take(50).forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(item.title, color = Ink.Primary)
                                    Text(item.name, fontSize = 12.sp, color = Ink.Secondary)
                                }
                            },
                            onClick = {
                                query = item.title
                                onSelect(item.name)
                                expanded = false
                                keyboard?.hide()
                            },
                        )
                    }
                    if (matches.size > 50) DropdownMenuItem(
                        text = { Text("共 ${matches.size} 个匹配，输入关键词继续筛选") },
                        onClick = {}, enabled = false,
                    )
                }
            }
        }
    }
}
