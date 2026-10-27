package com.v4ex.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("v4ex")

class Prefs(private val context: Context) {
    private val tokenKey = stringPreferencesKey("pat")
    private val readKey = stringPreferencesKey("read_ids")
    private val favNodesKey = stringPreferencesKey("fav_nodes")
    private val historyKey = stringPreferencesKey("history")
    private val unreadOnlyKey = booleanPreferencesKey("unread_only")
    private val replyThreadedKey = booleanPreferencesKey("reply_threaded")
    private val homeCacheKey = stringPreferencesKey("home_cache")

    val token: Flow<String> = context.store.data.map { it[tokenKey].orEmpty() }

    /** 首页「只看未读」。记住用户的选择，下次打开还是这个状态。 */
    val unreadOnly: Flow<Boolean> = context.store.data.map { it[unreadOnlyKey] ?: false }

    suspend fun setUnreadOnly(value: Boolean) {
        context.store.edit { it[unreadOnlyKey] = value }
    }

    /** 帖子回复「按对话查看」。记住选择，回到任何帖子都沿用。 */
    val replyThreaded: Flow<Boolean> = context.store.data.map { it[replyThreadedKey] ?: false }

    suspend fun setReplyThreaded(value: Boolean) {
        context.store.edit { it[replyThreadedKey] = value }
    }

    /** 首页缓存（只用于「打开就有内容」，拿到新数据后会覆盖）。 */
    val homeCache: Flow<String> = context.store.data.map { it[homeCacheKey].orEmpty() }

    suspend fun saveHomeCache(raw: String) {
        context.store.edit { it[homeCacheKey] = raw }
    }

    suspend fun clearHomeCache() {
        context.store.edit { it.remove(homeCacheKey) }
    }

    /** 已读主题 id，最新的排在后面，最多留 800 条。 */
    val readIds: Flow<Set<String>> = context.store.data.map {
        it[readKey].orEmpty().split('\n').filter(String::isNotBlank).toSet()
    }

    /**
     * 常用节点，纯本地（V2EX API 没有收藏/关注端点）。
     * 存 "name|title"，保持添加顺序即用户排的顺序。
     */
    val favNodes: Flow<List<Pair<String, String>>> = context.store.data.map { prefs ->
        prefs[favNodesKey].orEmpty()
            .split('\n')
            .filter(String::isNotBlank)
            .map { line ->
                val i = line.indexOf('|')
                if (i < 0) line to line else line.substring(0, i) to line.substring(i + 1)
            }
    }

    /**
     * 浏览历史，最近的排在最前。
     * 用 \u001F 分隔字段：标题里会出现 | 、换行等字符，用竖线当分隔符会解析错位。
     * 字段：id, viewedAt, nodeName, nodeTitle, author, replies, title
     */
    val history: Flow<List<HistoryEntry>> = context.store.data.map { prefs ->
        prefs[historyKey].orEmpty()
            .split('\n')
            .filter(String::isNotBlank)
            .mapNotNull { line ->
                val f = line.split(SEP.toString())
                if (f.size < 7) return@mapNotNull null
                val id = f[0].toIntOrNull() ?: return@mapNotNull null
                HistoryEntry(
                    id = id,
                    viewedAt = f[1].toLongOrNull() ?: 0L,
                    nodeName = f[2],
                    nodeTitle = f[3],
                    author = f[4],
                    replies = f[5].toIntOrNull() ?: 0,
                    // 标题可能含分隔符，剩下的重新拼回去
                    title = f.drop(6).joinToString(SEP.toString()),
                )
            }
    }

    /** 记一次浏览。同一帖子重复浏览只更新时间并置顶，不堆重复项。 */
    suspend fun recordHistory(entry: HistoryEntry) {
        if (entry.id == 0) return
        context.store.edit { prefs ->
            val list = prefs[historyKey].orEmpty()
                .split('\n').filter(String::isNotBlank).toMutableList()
            list.removeAll { it.substringBefore(SEP.toString()).toIntOrNull() == entry.id }
            list.add(0, entry.toLine())
            if (list.size > MAX_HISTORY) list.subList(MAX_HISTORY, list.size).clear()
            prefs[historyKey] = list.joinToString("\n")
        }
    }

    suspend fun removeHistory(id: Int) {
        context.store.edit { prefs ->
            val list = prefs[historyKey].orEmpty()
                .split('\n').filter(String::isNotBlank).toMutableList()
            list.removeAll { it.substringBefore(SEP.toString()).toIntOrNull() == id }
            prefs[historyKey] = list.joinToString("\n")
        }
    }

    suspend fun clearHistory() {
        context.store.edit { it.remove(historyKey) }
    }

    private fun HistoryEntry.toLine(): String = listOf(
        id.toString(),
        viewedAt.toString(),
        nodeName, nodeTitle, author, replies.toString(), title,
    ).joinToString(SEP.toString())

    suspend fun setToken(value: String) {
        context.store.edit { it[tokenKey] = value.trim() }
    }

    suspend fun toggleFavNode(name: String, title: String) {
        if (name.isBlank()) return
        context.store.edit { prefs ->
            val list = prefs[favNodesKey].orEmpty()
                .split('\n').filter(String::isNotBlank).toMutableList()
            val existing = list.indexOfFirst { it.substringBefore('|') == name }
            if (existing >= 0) {
                list.removeAt(existing)
            } else {
                list.add("$name|$title")
            }
            prefs[favNodesKey] = list.joinToString("\n")
        }
    }

    suspend fun markRead(id: Int) {
        val key = id.toString()
        context.store.edit { prefs ->
            val list = prefs[readKey].orEmpty()
                .split('\n').filter(String::isNotBlank).toMutableList()
            if (key !in list) {
                list.add(key)
                if (list.size > MAX_READ) list.subList(0, list.size - MAX_READ).clear()
                prefs[readKey] = list.joinToString("\n")
            }
        }
    }

    suspend fun clearRead() {
        context.store.edit { it.remove(readKey) }
    }

    private companion object {
        const val MAX_READ = 800
        const val MAX_HISTORY = 300
        const val SEP = '\u001F'
    }
}
