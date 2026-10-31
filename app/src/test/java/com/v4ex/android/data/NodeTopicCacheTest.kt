package com.v4ex.android.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NodeTopicCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun topic(id: Int, node: String) = Topic(
        id, "title$id", "https://v2ex.com/t/$id", 3, 1, 2, "body excluded",
        NodeBrief(node, node), UserBrief("author", "https://avatar"), "reply-author",
    )

    @Test fun restoresEachNodeAcrossInstancesAndKeepsListFields() {
        val directory = temporary.newFolder()
        val cache = NodeTopicCache(directory)
        cache.write("python", listOf(topic(1, "python")))
        cache.write("create", listOf(topic(2, "create")))
        val restarted = NodeTopicCache(directory)
        assertEquals(topic(1, "python").copy(content = ""), restarted.read("python")!!.single())
        assertEquals(2, restarted.read("create")!!.single().id)
        cache.write("python", listOf(topic(3, "python")))
        assertEquals(3, NodeTopicCache(directory).read("python")!!.single().id)
    }

    @Test fun missingAndCorruptCacheAreMissesAndEmptyResultCanReplaceOldData() {
        val directory = temporary.newFolder()
        val cache = NodeTopicCache(directory)
        assertNull(cache.read("missing"))
        File(directory, "broken.json").writeText("{")
        assertNull(cache.read("broken"))
        cache.write("python", listOf(topic(1, "python")))
        cache.write("python", emptyList())
        assertEquals(emptyList<Topic>(), NodeTopicCache(directory).read("python"))
    }
}
