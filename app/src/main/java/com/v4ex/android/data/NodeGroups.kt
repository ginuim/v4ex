package com.v4ex.android.data

data class NodeGroup(val parent: VNode?, val nodes: List<VNode>) {
    val key: String get() = parent?.name ?: "__other__"
    val title: String get() = parent?.title ?: "其他节点"
}

/** Follow official parent links; incomplete links and cycles remain discoverable. */
fun groupNodes(nodes: List<VNode>): List<NodeGroup> {
    val byName = nodes.associateBy { it.name }
    fun ancestor(node: VNode): String? {
        val seen = mutableSetOf<String>()
        var current = node
        while (seen.add(current.name)) {
            val parent = current.parentNodeName
            if (current.root || parent == current.name || parent.isNullOrBlank()) return current.name
            current = byName[parent] ?: return null
        }
        return null
    }
    val grouped = nodes.groupBy { ancestor(it) }
    val categories = grouped.filter { (name, members) -> name != null && members.size > 1 }
    val other = grouped.filterKeys { it !in categories }.values.flatten()
    return categories.map { (name, members) -> NodeGroup(byName[name], members.sortedByDescending { it.topics }) }
        .sortedByDescending { group -> group.nodes.sumOf { it.topics.toLong() } }
        .let { groups -> if (other.isEmpty()) groups else groups + NodeGroup(null, other.sortedByDescending { it.topics }) }
}
