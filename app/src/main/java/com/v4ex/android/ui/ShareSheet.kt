package com.v4ex.android.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.v4ex.android.data.SharePayload
import com.v4ex.android.ui.theme.Ink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 已知主流 App，按这个顺序排在网格前面；没装的自然会被跳过。 */
private val KnownApps = listOf(
    "com.tencent.mm",
    "com.tencent.mobileqq",
    "com.sina.weibo",
    "com.alibaba.android.rimet",
    "com.ss.android.lark",
    "com.tencent.wework",
    "org.telegram.messenger",
    "com.xingin.xhs",
    "com.ss.android.ugc.aweme",
    "com.twitter.android",
    "com.google.android.gm",
    "com.google.android.apps.messaging",
    "com.google.android.bluetooth",
    "com.android.chrome",
    "com.google.android.apps.docs",
)

private const val MaxApps = 14
private const val GridColumns = 5

/** 网格里的一格：具体 App，或固定末尾的「更多」。 */
private data class ShareTarget(
    val pkg: String,
    val label: String,
    val icon: ImageBitmap? = null,
    val isMore: Boolean = false,
)

/**
 * 分享面板：主流 App 网格 + 复制链接 + 在浏览器打开。
 * 全程不依赖任何第三方库；图标来自 PackageManager。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    payload: SharePayload,
    onDismiss: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var targets by remember(payload) { mutableStateOf<List<ShareTarget>?>(null) }
    var copyTick by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(payload) {
        targets = withContext(Dispatchers.IO) { loadShareTargets(context) }
    }
    // 每次点复制 tick+1，effect 重来；连点也只会以最后一次为准（旧 delay 被取消）
    LaunchedEffect(copyTick) {
        if (copyTick == 0) return@LaunchedEffect
        copied = true
        delay(1600)
        copied = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Color.White,
        contentColor = Ink.Primary,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = { SheetHandle() },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        // 内容比面板高时（小屏/横屏）可以滚，避免底部操作被裁掉
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("分享", fontSize = 15.5.sp, fontWeight = FontWeight.Medium, color = Ink.Primary)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = Ink.Tertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = payload.title,
                modifier = Modifier.padding(horizontal = 22.dp),
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                color = Ink.Secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            if (!payload.excerpt.isNullOrBlank()) {
                // 楼层分享时这里就是「该楼说了什么」：和「更多」发出去的正文一致，
                // 不然用户只能看到标题 + #replyN 链接，不知道自己在分享哪一楼。
                Text(
                    text = "「${payload.excerpt}」",
                    modifier = Modifier.padding(horizontal = 22.dp),
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = Ink.Secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
            }
            Text(
                text = payload.link,
                modifier = Modifier.padding(horizontal = 22.dp),
                fontSize = 11.5.sp,
                color = Ink.Tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))

            val loaded = targets
            if (loaded == null) {
                LoadingTargets()
            } else {
                ShareGrid(
                    targets = loaded + ShareTarget(pkg = "", label = "更多", isMore = true),
                    onSelect = { target ->
                        if (target.isMore) {
                            launchChooser(context, payload)
                        } else if (!launchShare(context, payload, target.pkg)) {
                            launchChooser(context, payload)
                        }
                        onDismiss()
                    },
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Ink.Line, thickness = 1.dp)

            ActionRow(
                text = if (copied) "已复制链接 ✓" else "复制链接",
                tint = if (copied) Ink.Blue else Ink.Primary,
            ) {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText(payload.title, payload.link))
                // 不再弹 Toast：Android 13+ 复制时系统自带剪贴板预览浮层，两个叠在一起会互相遮挡。
                // 行内「已复制链接 ✓」已经足够明确。
                copyTick++
            }
            ActionRow(text = "在浏览器打开", tint = Ink.Primary) {
                onOpenInBrowser()
                onDismiss()
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun SheetHandle() {
    Box(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(34.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Ink.Line),
        )
    }
}

@Composable
private fun LoadingTargets() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        PixelLoader(size = 6.dp, gap = 3.dp, color = Ink.Secondary)
        Text("正在查找可分享的应用…", fontSize = 12.5.sp, color = Ink.Tertiary)
    }
}

@Composable
private fun ShareGrid(targets: List<ShareTarget>, onSelect: (ShareTarget) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        targets.chunked(GridColumns).forEach { row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                row.forEach { target ->
                    ShareTile(target, Modifier.weight(1f), onSelect)
                }
                repeat(GridColumns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ShareTile(
    target: ShareTarget,
    modifier: Modifier = Modifier,
    onSelect: (ShareTarget) -> Unit,
) {
    val iconShape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onSelect(target) }
            .padding(vertical = 7.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val icon = target.icon
        when {
            target.isMore -> TileFallbackBox {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                    tint = Ink.Secondary,
                    modifier = Modifier.size(21.dp),
                )
            }
            icon != null -> Image(
                bitmap = icon,
                contentDescription = target.label,
                modifier = Modifier.size(48.dp).clip(iconShape),
                contentScale = ContentScale.Crop,
            )
            else -> TileFallbackBox {
                Text(
                    text = target.label.take(1).uppercase(),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                    color = Ink.Secondary,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = target.label,
            modifier = Modifier.fillMaxWidth(),
            fontSize = 11.5.sp,
            color = Ink.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TileFallbackBox(content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.ChipBg),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ActionRow(text: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 14.sp, color = tint, fontWeight = FontWeight.Medium)
    }
}

// ---- 目标发现与分享动作 ----------------------------------------------------

/**
 * 查设备上能接收 text/plain 的 App：按包名去重、排除自己，
 * 已知主流 App 按固定顺序优先，其余按应用名排序，最多 [MaxApps] 个。
 * 图标在这里（IO 线程）就转成 ImageBitmap，Compose 侧只负责画。
 */
private fun loadShareTargets(context: Context): List<ShareTarget> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain" }
    val resolved = runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
    val priority = KnownApps.withIndex().associate { (index, pkg) -> pkg to index }

    val byPkg = LinkedHashMap<String, String>()
    for (info in resolved) {
        val pkg = info.activityInfo?.packageName ?: continue
        if (pkg == context.packageName || byPkg.containsKey(pkg)) continue
        // 用 Application label，不用 Activity label：Gmail 的分享 Activity 自己叫 "Chat"，
        // 读 Activity 标签会出现「Gmail 图标 + Chat 文案」这种前后不一致。
        val label = runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString().trim()
        }.getOrDefault("")
        byPkg[pkg] = label.ifBlank { pkg }
    }

    return byPkg.entries
        .sortedWith(
            compareBy<Map.Entry<String, String>>(
                { priority[it.key] ?: Int.MAX_VALUE },
                { it.value.lowercase() },
                { it.key },
            ),
        )
        .take(MaxApps)
        .map { (pkg, label) ->
            val icon = runCatching {
                pm.getApplicationIcon(pkg).toBitmap(48, 48).asImageBitmap()
            }.getOrNull()
            ShareTarget(pkg = pkg, label = label, icon = icon)
        }
}

private fun baseShareIntent(payload: SharePayload): Intent =
    Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, payload.text())
        putExtra(Intent.EXTRA_TITLE, payload.title)
    }

/**
 * ModalBottomSheet 的内容跑在 Dialog 窗口里，LocalContext 往往是 ContextThemeWrapper 而不是
 * Activity；用非 Activity context 直接 startActivity 会抛
 * "Calling startActivity() from outside of an Activity context requires FLAG_ACTIVITY_NEW_TASK"。
 * 所以沿 ContextWrapper 链找宿主 Activity；实在找不到再补 NEW_TASK。
 */
private fun Context.findHostActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** 启动外部界面：成功返回 true；目标不存在 / 无 Activity 返回 false（由调用方回退）。 */
private fun Context.startShareIntent(intent: Intent): Boolean = try {
    val host = findHostActivity()
    if (host != null) {
        host.startActivity(intent)
    } else {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: Exception) {
    false
}

/** 直接指定包名分享；目标不存在时返回 false，由调用方回退系统面板。 */
private fun launchShare(context: Context, payload: SharePayload, pkg: String): Boolean =
    context.startShareIntent(baseShareIntent(payload).apply { setPackage(pkg) })

private fun launchChooser(context: Context, payload: SharePayload) {
    // createChooser 一般都能解析；失败（设备上真没有任何可分享目标）就静默
    context.startShareIntent(Intent.createChooser(baseShareIntent(payload), "分享到"))
}
