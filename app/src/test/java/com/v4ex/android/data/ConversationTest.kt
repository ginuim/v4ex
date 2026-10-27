package com.v4ex.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 纯函数测试：parent 推断 + 对话树/渲染行。不碰 Android API。 */
class ConversationTest {

    private fun reply(
        floor: Int,
        user: String = "u$floor",
        content: String = "",
        id: Int = floor,
    ): Reply = Reply(
        id = id,
        floor = floor,
        content = content,
        created = 1_700_000_000_000L + floor,
        member = UserBrief(user, null),
    )

    /** 前序遍历整棵树的所有 depth，用来验证「真实层级」没被压平。 */
    private fun depths(node: ThreadNode): List<Int> =
        listOf(node.depth) + node.children.flatMap { depths(it) }

    private fun walkFloors(nodes: List<ThreadNode>): List<Int> =
        nodes.flatMap { listOf(it.reply.floor) + walkFloors(it.children) }

    @Test
    fun emptyInput() {
        assertTrue(Conversation.build(emptyList()).isEmpty())
        assertTrue(Conversation.rows(emptyList(), threaded = true).isEmpty())
        assertTrue(Conversation.rows(emptyList(), threaded = false).isEmpty())
    }

    @Test
    fun noReferencesAllRoots() {
        val replies = listOf(
            reply(1, "alice", "第一楼"),
            reply(2, "bob", "第二楼"),
            reply(3, "carol", "第三楼"),
        )
        val tree = Conversation.build(replies)
        assertEquals(3, tree.size)
        assertTrue(tree.all { it.depth == 0 && it.replyTo == null && it.children.isEmpty() })

        val flat = Conversation.rows(replies, threaded = false)
        assertEquals(listOf(1, 2, 3), flat.map { it.reply.floor })
        assertTrue(flat.all { it.depth == 0 && it.replyTo == null && it.childCount == 0 })

        val threaded = Conversation.rows(replies, threaded = true)
        assertEquals(listOf(1, 2, 3), threaded.map { it.reply.floor })
        assertTrue(threaded.all { it.depth == 0 && it.replyTo == null && it.childCount == 0 })
    }

    @Test
    fun flatModeKeepsOriginalOrder() {
        // 输入乱序也照原样输出，平铺模式不重新排序（API 一般已经按楼层排好）。
        val replies = listOf(
            reply(2, "bob", "@alice 看到了"),
            reply(1, "alice", "发个帖"),
            reply(3, "carol", "三"),
        )
        val flat = Conversation.rows(replies, threaded = false)
        assertEquals(listOf(2, 1, 3), flat.map { it.reply.floor })
        assertTrue(flat.all { it.depth == 0 && it.replyTo == null })
    }

    @Test
    fun mentionAttachesToAuthorsLatestEarlierReply() {
        val replies = listOf(
            reply(1, "Alice", "楼主发帖"),     // 用户名大小写混用
            reply(2, "bob", "附议"),
            reply(3, "alice", "我也来"),
            reply(4, "bob", "@alice 你说得对"),
        )
        // alice 更早的发言有 1、3 楼，取最近的一条 = 3 楼
        val rows = Conversation.rows(replies, threaded = true)
        assertEquals(listOf(1, 2, 3, 4), rows.map { it.reply.floor })
        val row4 = rows.single { it.reply.floor == 4 }
        assertEquals(1, row4.depth)
        assertEquals(3, row4.replyTo?.floor)
        val row3 = rows.single { it.reply.floor == 3 }
        assertEquals(0, row3.depth)
        assertEquals(1, row3.childCount)

        // 树里：3 楼是根，4 楼挂在 3 楼下面
        val tree = Conversation.build(replies)
        assertEquals(3, tree.size)
        val node3 = tree.single { it.reply.floor == 3 }
        assertEquals(0, node3.depth)
        val child = node3.children.single()
        assertEquals(4, child.reply.floor)
        assertEquals(1, child.depth)
        assertEquals(3, child.replyTo?.floor)
    }

    @Test
    fun mentionWithoutEarlierAuthorStaysRoot() {
        val replies = listOf(
            reply(1, "alice", "@ghost 你上次说的那个"),
            reply(2, "bob", "@ghost @alice 我猜"),
        )
        val rows = Conversation.rows(replies, threaded = true)
        assertEquals(2, rows.size)
        // 1 楼 @ghost：ghost 没发过言 → 根
        assertNull(rows.single { it.reply.floor == 1 }.replyTo)
        // 2 楼第一个能解析的是 @alice（1 楼）
        assertEquals(1, rows.single { it.reply.floor == 2 }.replyTo?.floor)
    }

    @Test
    fun floorReferenceAttachesToThatFloor() {
        val replies = listOf(
            reply(1, "alice", "问个问题"),
            reply(2, "bob", "回答"),
            reply(3, "carol", "#2 楼说得好"),
            reply(4, "dave", "回复 #1"),
            reply(5, "erin", "1 楼你忘了贴链接"),
            reply(6, "frank", "#2楼补充"),
        )
        val rows = Conversation.rows(replies, threaded = true)
        assertEquals(2, rows.single { it.reply.floor == 3 }.replyTo?.floor)
        assertEquals(1, rows.single { it.reply.floor == 4 }.replyTo?.floor)
        assertEquals(1, rows.single { it.reply.floor == 5 }.replyTo?.floor)
        assertEquals(2, rows.single { it.reply.floor == 6 }.replyTo?.floor)
        assertTrue(rows.filter { it.reply.floor in 3..6 }.all { it.depth == 1 })
    }

    @Test
    fun explicitFloorWinsOverMention() {
        val replies = listOf(
            reply(1, "alice", "一"),
            reply(2, "bob", "二"),
            reply(3, "carol", "@alice 但 #2 楼才是重点"),
        )
        // @alice 指向 1 楼，但显式 #2 楼优先
        assertEquals(2, Conversation.rows(replies, threaded = true).single { it.reply.floor == 3 }.replyTo?.floor)
    }

    @Test
    fun firstResolvableMentionWins() {
        val replies = listOf(
            reply(1, "alice", "一"),
            reply(2, "bob", "二"),
            reply(3, "carol", "@ghost @bob @alice 你们看"),
        )
        // @ghost 没发过言被跳过，@bob 最近一条更早回复在 2 楼
        assertEquals(2, Conversation.rows(replies, threaded = true).single { it.reply.floor == 3 }.replyTo?.floor)
    }

    @Test
    fun forwardReferencesAreIgnored() {
        val replies = listOf(
            reply(1, "alice", "#3 楼见"),
            reply(2, "bob", "@carol 还没发言的人"),
            reply(3, "carol", "我来了"),
        )
        val rows = Conversation.rows(replies, threaded = true)
        assertNull(rows.single { it.reply.floor == 1 }.replyTo)
        assertNull(rows.single { it.reply.floor == 2 }.replyTo)
        assertTrue(rows.all { it.depth == 0 })
        assertEquals(3, Conversation.build(replies).size)
    }

    @Test
    fun selfReferencesDoNotCrashOrAttach() {
        val replies = listOf(
            reply(1, "alice", "一"),
            reply(2, "bob", "#2 楼我自己"),
            reply(3, "carol", "@carol 自言自语"),
        )
        val rows = Conversation.rows(replies, threaded = true)
        assertEquals(3, rows.size)
        assertNull(rows.single { it.reply.floor == 2 }.replyTo)
        assertNull(rows.single { it.reply.floor == 3 }.replyTo)
        assertEquals(3, Conversation.build(replies).size)
        // 每条恰好出现一次
        assertEquals(listOf(1, 2, 3), rows.map { it.reply.floor })
    }

    @Test
    fun numbersThatAreNotFloorsAreIgnored() {
        val replies = listOf(
            reply(1, "alice", "一"),
            reply(2, "bob", "#123456 是 id 不是楼层"),
            reply(3, "carol", "2024 10 楼 这个数字不是楼层"),
        )
        val rows = Conversation.rows(replies, threaded = true)
        assertNull(rows.single { it.reply.floor == 2 }.replyTo)
        assertNull(rows.single { it.reply.floor == 3 }.replyTo)
        assertTrue(rows.all { it.depth == 0 })
    }

    @Test
    fun floorReferenceToMissingFloorStaysRoot() {
        val replies = listOf(
            reply(1, "alice", "#9 楼在哪"),
            reply(2, "bob", "#0 楼"),
        )
        val rows = Conversation.rows(replies, threaded = true)
        assertTrue(rows.all { it.replyTo == null && it.depth == 0 })
    }

    @Test
    fun descendantsAreFlattenedToOneLevelButTreeKeepsRealDepth() {
        val replies = listOf(
            reply(1, "alice", "一"),
            reply(2, "bob", "@alice 二"),
            reply(3, "carol", "@bob 三"),
            reply(4, "dave", "@carol 四"),
        )
        val tree = Conversation.build(replies)
        assertEquals(1, tree.size)
        assertEquals(listOf(0, 1, 2, 3), depths(tree.single()))

        val rows = Conversation.rows(replies, threaded = true)
        assertEquals(listOf(1, 2, 3, 4), rows.map { it.reply.floor })
        assertEquals(listOf(0, 1, 1, 1), rows.map { it.depth })
        // replyTo 永远是「直接上一层」，哪怕那一层在 UI 里也被压到 depth=1
        assertEquals(listOf(null, 1, 2, 3), rows.map { it.replyTo?.floor })
        assertEquals(1, rows[0].childCount)
        assertTrue(rows.drop(1).all { it.childCount == 0 })
    }

    @Test
    fun childrenOfSameParentAreFloorAscending() {
        val replies = listOf(
            reply(1, "alice", "一"),
            reply(2, "bob", "@alice 二"),
            reply(3, "carol", "@alice 三"),
            reply(4, "dave", "@bob 四"),
            reply(5, "erin", "#1 楼五"),
        )
        val tree = Conversation.build(replies)
        assertEquals(1, tree.size)
        val root = tree.single()
        assertEquals(listOf(2, 3, 5), root.children.map { it.reply.floor })
        assertEquals(listOf(4), root.children[0].children.map { it.reply.floor })

        // 渲染顺序：根的子树按「根、子节点升序、孙辈紧跟在父节点后」的深度优先展开
        val rows = Conversation.rows(replies, threaded = true)
        assertEquals(listOf(1, 2, 4, 3, 5), rows.map { it.reply.floor })
        assertEquals(listOf(0, 1, 1, 1, 1), rows.map { it.depth })
    }

    @Test
    fun randomRepliesAppearExactlyOnceWithCappedDepth() {
        val random = Random(20241027)
        val replies = (1..200).map { floor ->
            val user = "u${random.nextInt(4)}"
            val content = when (random.nextInt(6)) {
                0 -> "@u${random.nextInt(4)} 看这个"
                1 -> "#${random.nextInt(1, floor + 3)} 楼说得对"
                2 -> "${random.nextInt(1, 250)} 楼"
                3 -> "@u${random.nextInt(4)} @u${random.nextInt(4)} 你们俩"
                4 -> "既没有楼层也没有引用"
                else -> ""
            }
            Reply(
                id = 1000 + floor,
                floor = floor,
                content = content,
                created = 1_700_000_000_000L + floor,
                member = UserBrief(user, null),
            )
        }

        val rows = Conversation.rows(replies, threaded = true)
        assertEquals(200, rows.size)
        assertEquals(200, rows.map { it.reply.id }.toSet().size)
        assertEquals((1..200).toSet(), rows.map { it.reply.floor }.toSet())
        assertTrue(rows.all { it.depth == 0 || it.depth == 1 })
        assertTrue(rows.all { (it.replyTo?.floor ?: 0) < it.reply.floor })
        assertTrue(rows.all { it.childCount >= 0 })
        assertTrue(rows.all { it.depth == 0 || it.replyTo != null })

        // 根按楼层升序，且每个根后面紧跟着它自己的后代（后代都是 depth=1）
        val rootIndices = rows.indices.filter { rows[it].depth == 0 }
        assertTrue(rootIndices.zipWithNext().all { (a, b) -> rows[a].reply.floor < rows[b].reply.floor })
        for ((k, start) in rootIndices.withIndex()) {
            val end = rootIndices.getOrNull(k + 1) ?: rows.size
            assertTrue(rows.subList(start + 1, end).all { it.depth == 1 })
        }

        // 平铺模式：原顺序、每条一次
        val flat = Conversation.rows(replies, threaded = false)
        assertEquals(replies.map { it.floor }, flat.map { it.reply.floor })
        assertTrue(flat.all { it.depth == 0 && it.replyTo == null && it.childCount == 0 })

        // 树里同样每条恰好一次
        val treeFloors = walkFloors(Conversation.build(replies))
        assertEquals(200, treeFloors.size)
        assertEquals((1..200).toSet(), treeFloors.toSet())
    }

    @Test
    fun deepChainHasNoCycle() {
        val replies = (1..40).map { floor ->
            reply(floor, "u$floor", if (floor == 1) "从头开始" else "#${floor - 1} 楼接着聊")
        }
        val tree = Conversation.build(replies)
        assertEquals(1, tree.size)
        assertEquals((0 until 40).toList(), depths(tree.single()))
        val rows = Conversation.rows(replies, threaded = true)
        assertEquals(40, rows.size)
        assertEquals(40, rows.map { it.reply.id }.toSet().size)
        assertEquals(listOf(0) + List(39) { 1 }, rows.map { it.depth })
    }
}
