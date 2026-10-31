package com.v4ex.android.data

import java.io.File

/** Only first-page list fields are persisted, using the same format as the home cache. */
class NodeTopicCache(private val directory: File) {
    private val memory = mutableMapOf<String, List<Topic>>()

    @Synchronized
    fun peek(node: String): List<Topic>? = memory[node]

    @Synchronized
    fun read(node: String): List<Topic>? {
        require(node.matches(Regex("[A-Za-z0-9_-]+")))
        return memory[node] ?: runCatching {
            HomeCacheCodec.decode(File(directory, "$node.json").readText())?.second
        }.getOrNull()?.also { memory[node] = it }
    }

    @Synchronized
    fun write(node: String, topics: List<Topic>) {
        require(node.matches(Regex("[A-Za-z0-9_-]+")))
        memory[node] = topics
        // A failed disk write must not turn a successful fetch into a UI error.
        runCatching {
            directory.mkdirs()
            val temporary = File(directory, "$node.tmp")
            temporary.writeText(HomeCacheCodec.encode(emptyList(), topics))
            check(temporary.renameTo(File(directory, "$node.json")))
        }
    }
}
