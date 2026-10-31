package com.v4ex.android.data

import org.junit.Assert.*
import org.junit.Test

class HotAuthorsTest {
    private fun topic(id: Int, author: String) = Topic(
        id, "title", "", 0, 0, 0, "", NodeBrief("test", "Test"), UserBrief(author, null), "",
    )

    @Test fun unreadAuthorsComeFirstAndMixedAuthorsAreDeduplicated() {
        val result = hotAuthors(
            listOf(topic(1, "read"), topic(2, "mixed"), topic(3, "unread"), topic(4, "mixed")),
            setOf("1", "2"), false,
        )
        assertEquals(listOf("mixed", "unread", "read"), result.map { it.member.username })
        assertEquals(listOf(true, true, false), result.map { it.unread })
    }

    @Test fun filteringRemovesOnlyFullyReadAuthors() {
        val topics = listOf(topic(1, "read"), topic(2, "mixed"), topic(3, "mixed"))
        assertEquals(listOf("mixed"), hotAuthors(topics, setOf("1", "2"), true).map { it.member.username })
        assertTrue(hotAuthors(topics, setOf("1", "2", "3"), true).isEmpty())
        assertEquals(2, hotAuthors(topics, setOf("1", "2", "3"), false).size)
    }
}
