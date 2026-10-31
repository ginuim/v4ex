package com.v4ex.android.data

data class HomeCategory(val key: String, val title: String, val nodes: List<NodeBrief>)

/** Editorial shortcuts from the website (2026-10-30); the API exposes no tab configuration. */
val HomeCategories: List<HomeCategory> = listOf(
    HomeCategory("tech", "技术", listOf(
        NodeBrief("programmer", "程序员"), NodeBrief("python", "Python"),
        NodeBrief("idev", "iDev"), NodeBrief("claude", "Claude"),
        NodeBrief("openai", "OpenAI"), NodeBrief("localllm", "Local LLM"),
        NodeBrief("cloud", "云计算"), NodeBrief("bb", "宽带症候群"),
    )),
    HomeCategory("creative", "创意", listOf(
        NodeBrief("create", "分享创造"), NodeBrief("design", "设计"), NodeBrief("ideas", "奇思妙想"),
    )),
    HomeCategory("play", "好玩", listOf(
        NodeBrief("share", "分享发现"), NodeBrief("crypto", "加密货币"),
        NodeBrief("games", "电子游戏"), NodeBrief("movie", "电影"), NodeBrief("tv", "剧集"),
        NodeBrief("music", "音乐"), NodeBrief("travel", "旅游"),
        NodeBrief("android", "Android"), NodeBrief("afterdark", "午夜俱乐部"),
    )),
    HomeCategory("apple", "Apple", listOf(
        NodeBrief("macos", "macOS"), NodeBrief("iphone", "iPhone"), NodeBrief("ipad", "iPad"),
        NodeBrief("macmini", "Mac mini"), NodeBrief("mbp", "MBP"), NodeBrief("imac", "iMac"),
        NodeBrief("watch", " WATCH"), NodeBrief("apple", "Apple"),
    )),
    HomeCategory("jobs", "酷工作", listOf(
        NodeBrief("jobs", "酷工作"), NodeBrief("cv", "求职"), NodeBrief("career", "职场话题"),
        NodeBrief("meet", "创业组队"), NodeBrief("outsourcing", "外包"),
    )),
    HomeCategory("deals", "交易", listOf(
        NodeBrief("all4all", "二手交易"), NodeBrief("exchange", "物物交换"),
        NodeBrief("free", "免费赠送"), NodeBrief("dn", "域名"), NodeBrief("tuan", "团购"),
    )),
    HomeCategory("city", "城市", listOf(
        NodeBrief("beijing", "北京"), NodeBrief("shanghai", "上海"), NodeBrief("shenzhen", "深圳"),
        NodeBrief("guangzhou", "广州"), NodeBrief("hangzhou", "杭州"), NodeBrief("chengdu", "成都"),
        NodeBrief("singapore", "新加坡"), NodeBrief("nyc", "纽约"), NodeBrief("la", "洛杉矶"),
    )),
    HomeCategory("qna", "问与答", listOf(NodeBrief("qna", "问与答"))),
)
