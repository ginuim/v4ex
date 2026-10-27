package com.v4ex.android.data

/**
 * 一条回复在对话视图里的行。depth 已封顶（渲染最多一层缩进，0 或 1）。
 */
data class ReplyRow(
    val reply: Reply,
    val depth: Int,
    val replyTo: Reply?,      // 这条回复明确引用的上一层楼层；没有就是 null
    val childCount: Int = 0,  // 仅 depth==0 有意义：它下面直接挂了多少条回复
)

/**
 * 对话树里的节点。depth 是真实层级（0 = 顶层、1 = 直接回复、2 = 孙辈……），
 * children 按楼层升序；渲染时由 [Conversation.rows] 把 depth 压到 0/1。
 */
data class ThreadNode(
    val reply: Reply,
    val depth: Int,
    val replyTo: Reply?,
    val children: List<ThreadNode>,
)

/**
 * 从回复内容推断「谁在回复谁」。
 *
 * V2EX v1/v2 的回复接口都没有 parent 字段，只能从正文里的楼层引用（#12 / #12 楼 / 12 楼）
 * 和 @username 猜。原则是「宁可少连，不要乱连」：解析不出来的一律留在顶层；
 * 因为父楼层的楼层号严格小于子楼层，所以不可能丢回复，也不可能出现环。
 */
object Conversation {

    /** #12 / #12 楼 / 12 楼。两侧禁止更长数字串，避免把 #123456 截成 #123456 的半截当楼层。 */
    private val FLOOR_REF = Regex("""#(\d{1,6})(?!\d)|(?<!\d)(\d{1,6})\s*楼""")

    /** V2EX 用户名大小写混用，所以先按原样抓，再统一小写查人。 */
    private val MENTION = Regex("""@([A-Za-z0-9_-]+)""")

    /** 把回复列表组成对话树（根与子节点都按楼层升序），用于统计/测试。 */
    fun build(replies: List<Reply>): List<ThreadNode> {
        if (replies.isEmpty()) return emptyList()
        val t = threading(replies)
        val n = t.sorted.size
        val depth = IntArray(n)
        for (i in 0 until n) {
            val p = t.parents[i]
            depth[i] = if (p < 0) 0 else depth[p] + 1
        }
        // 父节点下标严格小于子节点下标，倒序建树即可，不需要递归（深链也不怕爆栈）。
        val nodes = arrayOfNulls<ThreadNode>(n)
        for (i in n - 1 downTo 0) {
            val p = t.parents[i]
            nodes[i] = ThreadNode(
                reply = t.sorted[i],
                depth = depth[i],
                replyTo = if (p < 0) null else t.sorted[p],
                children = t.children[i].map { nodes[it]!! },
            )
        }
        return t.roots.map { nodes[it]!! }
    }

    /** 渲染行：threaded=false 原顺序、全 depth=0；threaded=true 深度优先展开、depth 封顶 1。 */
    fun rows(replies: List<Reply>, threaded: Boolean): List<ReplyRow> {
        if (replies.isEmpty()) return emptyList()
        if (!threaded) {
            return replies.map { ReplyRow(reply = it, depth = 0, replyTo = null, childCount = 0) }
        }
        val t = threading(replies)
        val sorted = t.sorted
        val seen = BooleanArray(sorted.size)
        val out = ArrayList<ReplyRow>(sorted.size)
        // 先根遍历（显式栈）：根按楼层升序，每个根后面紧跟它自己的全部后代。
        val stack = ArrayDeque<Int>()
        for (i in t.roots.indices.reversed()) stack.addLast(t.roots[i])
        while (stack.isNotEmpty()) {
            val index = stack.removeLast()
            val parent = t.parents[index]
            val depth = if (parent < 0) 0 else 1
            out += ReplyRow(
                reply = sorted[index],
                depth = depth,
                replyTo = if (parent < 0) null else sorted[parent],
                childCount = if (depth == 0) t.children[index].size else 0,
            )
            seen[index] = true
            val kids = t.children[index]
            for (k in kids.indices.reversed()) stack.addLast(kids[k])
        }
        // 父下标恒小于子下标的构造保证这里是死代码；真出问题也不能丢回复。
        if (out.size != sorted.size) {
            for (i in sorted.indices) {
                if (!seen[i]) out += ReplyRow(reply = sorted[i], depth = 0, replyTo = null, childCount = 0)
            }
        }
        return out
    }

    private class Threading(
        val sorted: List<Reply>,
        val parents: IntArray,
        val children: List<List<Int>>,
        val roots: List<Int>,
    )

    /**
     * 一轮扫描算出每个回复的父节点下标（-1 = 顶层）。只把「已经扫过的更早楼层」放进索引里，
     * 所以天然满足「只认更早楼层」，也就不可能自引用/成环。
     */
    private fun threading(replies: List<Reply>): Threading {
        val sorted = replies.sortedBy { it.floor }
        val n = sorted.size
        val parents = IntArray(n) { -1 }
        // 楼层 -> 该楼层回复在 sorted 里的下标；用户名（小写）-> 最近一条回复的下标
        val byFloor = HashMap<Int, Int>(n * 2)
        val lastByName = HashMap<String, Int>(n * 2)
        for (i in 0 until n) {
            val cur = sorted[i]
            parents[i] = inferParent(cur, sorted, byFloor, lastByName)
            check(parents[i] < i) { "对话树父节点必须严格更早：floor=${cur.floor}" }
            byFloor[cur.floor] = i
            lastByName[cur.member.username.lowercase()] = i
        }
        val children = List(n) { ArrayList<Int>(2) }
        val roots = ArrayList<Int>()
        for (i in 0 until n) {
            val p = parents[i]
            if (p < 0) roots += i else children[p] += i
        }
        return Threading(sorted, parents, children, roots)
    }

    private fun inferParent(
        cur: Reply,
        sorted: List<Reply>,
        byFloor: Map<Int, Int>,
        lastByName: Map<String, Int>,
    ): Int {
        // 1) 显式楼层引用优先，按出现顺序取第一个能解析到更早楼层的。
        for (match in FLOOR_REF.findAll(cur.content)) {
            val raw = match.groupValues[1].ifEmpty { match.groupValues[2] }
            val floor = raw.toIntOrNull() ?: continue
            // 目标楼层必须存在（已出现）且真的更早；不存在的楼层号直接跳过。
            val target = byFloor[floor] ?: continue
            if (sorted[target].floor < cur.floor) return target
        }
        // 2) 再退到 @username：第一个「有更早发言」的被提及用户，
        //    parent = 该用户最近的一条更早回复；同一段里连续 @ 多人时第一个能解析的胜出。
        for (match in MENTION.findAll(cur.content)) {
            val user = match.groupValues[1].lowercase()
            val target = lastByName[user] ?: continue
            if (sorted[target].floor < cur.floor) return target
        }
        return -1
    }
}
