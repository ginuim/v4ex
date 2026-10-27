package com.v4ex.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NodeGroupsTest {
    private fun node(name: String, parent: String? = null, root: Boolean = false) =
        VNode(name, name, 1, "", null, parent, root)

    @Test
    fun nestedNodesStayWithTheirAncestorIncludingTheParentItself() {
        val groups = groupNodes(listOf(node("apple"), node("ios", "apple"), node("swift", "ios")))
        assertEquals(listOf("apple"), groups.map { it.key })
        assertEquals(setOf("apple", "ios", "swift"), groups.single().nodes.map { it.name }.toSet())
    }

    @Test
    fun incompleteAndCyclicRelationsDoNotLoseOrDuplicateNodes() {
        val input = listOf(node("v2ex", "v2ex", true), node("babel", "v2ex"),
            node("orphan", "missing"), node("alone"), node("a", "b"), node("b", "a"))
        val groups = groupNodes(input)
        assertEquals(setOf("v2ex", "__other__"), groups.map { it.key }.toSet())
        assertEquals(input.map { it.name }.sorted(), groups.flatMap { it.nodes }.map { it.name }.sorted())
        assertEquals(setOf("orphan", "alone", "a", "b"), groups.last().nodes.map { it.name }.toSet())
    }
}
