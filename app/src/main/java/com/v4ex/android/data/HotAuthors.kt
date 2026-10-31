package com.v4ex.android.data

data class HotAuthor(val member: UserBrief, val unread: Boolean)

/** One avatar per author; an author with any unread topic belongs in the unread group. */
fun hotAuthors(topics: List<Topic>, readIds: Set<String>, unreadOnly: Boolean): List<HotAuthor> =
    topics.groupBy { it.member.username }.values.map { authored ->
        HotAuthor(authored.first().member, authored.any { it.id.toString() !in readIds })
    }.filter { !unreadOnly || it.unread }.sortedBy { !it.unread }
