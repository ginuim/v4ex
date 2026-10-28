# V4EX

一个以阅读为主的 V2EX 客户端。Kotlin + Jetpack Compose，无第三方 UI 库，亮暗主题「无卡片」排版。

不做 AI 摘要、不做内容过滤——**帖子原样呈现**，水贴、跑题、抬杠都留着。

![首页](docs/android-home.png)

## 功能

| 页面 | 内容 |
|---|---|
| 首页 | 问候语（内联头像 + 用户名）、数据概览、**热门 / 最新**两段列表；下拉刷新、上滑自动加载更多、已读主题标题置灰、**只看未读**开关、**本地缓存秒开** |
| 节点 | 1379 个节点，本地搜索（名称 / 标题），点进节点看主题列表 |
| 通知 | 未读提醒（回复 / 提及），底部标签带红色角标 |
| 我的 | 头像、会员 id、令牌有效期、注册时间；填写 / 更换 / 清空令牌；浏览历史入口 |
| 帖子 | 标题 + 正文 + 全部回复；**楼层 / 对话** 两种回复视图；右上角分享（分享到本机 App / 复制链接 / 浏览器打开） |

## 刷新 / 翻页 / 已读

| 能力 | 实现 |
|---|---|
| 下拉刷新 | Material3 `PullToRefreshBox`，刷新时保留列表（`refreshing` 与 `loading` 分开，不闪白） |
| 上滑加载更多 | 首页和节点主题页都是 `LazyColumn` + 距底部 4 项触发；数据走 v2 `/api/v2/topics/latest?p=N`、`/api/v2/nodes/:name/topics?p=N`（20 条/页） |
| 已读标记 | 打开主题即写入 DataStore（最多保留 800 条 id），列表里已读标题从 `#111114` 变 `#B4B4B9` |
| 只看未读 | 首页右上角开关，按 `readIds` 过滤，**只在渲染层过滤**不改原始数据，关掉立刻恢复；偏好存 DataStore，重启仍生效；过滤数量会在头部提示 |
| 首页缓存 | 打开 App 先读本地缓存渲染（列表字段，不存正文，约十几 KB），再后台**静默**拉新数据覆盖；`refreshing`（下拉指示器）与 `updating`（静默同步）分开，启动时不会突然弹转圈；拉取失败保留旧缓存 |

v1 的 `topics/latest.json` 没有翻页（`p` 参数被忽略），所以**没有令牌时只能刷到第一页**，底部会提示「填写令牌后可继续翻页」。填了令牌就是无限流。

## 分享

![分享面板](docs/android-share.png)

| 能力 | 实现 |
|---|---|
| 分享到本机 App | `PackageManager.queryIntentActivities(ACTION_SEND + text/plain)` 列出设备上真正能接分享的 App，按包名去重；微信 / QQ / 微博 / 钉钉 / 飞书 / Telegram / 小红书… 这些常见 App 固定排前面，其余按应用名排，最多 14 个；末尾「更多」走系统分享面板 |
| 复制链接 | `ClipboardManager` 只写纯链接，面板内「已复制链接 ✓」1.6s，**不关面板**，方便接着点某个 App；不弹 Toast，避免和 Android 13+ 系统自带剪贴板预览叠在一起 |
| 楼层分享 | 长按任意回复分享该楼：面板预览区显示该楼摘录，链接锚到 `#reply{floor}`，摘录同时写进分享正文（Markdown 清洗、压成一行、90 字截断）。帖子分享则带标题 + 正文摘录 |
| 包可见性 | Android 11+ 必须在 manifest 声明 `<queries>` 才能查到别的 App；不写的话网格永远是空的 |

## 按对话看回复

![对话视图](docs/android-thread.png)

V2EX 的回复接口（v1 / v2）都**没有 parent 字段**，所以「谁在回复谁」只能从正文推断：

| 依据 | 规则 |
|---|---|
| 楼层引用优先 | 正文里的 `#12`、`#12 楼`、`12 楼`，目标必须存在且比当前楼层更早 |
| `@某人` 次之 | 取正文里第一个「已经发过言」的用户，挂到该用户最近的一条更早回复 |
| 都不匹配 | 留在顶层。**宁可少连，不要乱连**：解析不出、前向引用、目标楼层已删 → 一律顶层，绝不丢回复 |
| 渲染 | 子回复缩进一级 + 1.5dp 竖线（对齐父楼头像中线）+ 「回复 @x · N 楼」；祖孙再深也压到同一级（手机屏宽有限）；根回复下显示「↳ N 条回复」，N 是整组后代总数，和实际排出来的行数对得上 |
| 切换 | 「楼层 / 对话」只换顺序，回复条数不变、item key 不变，滚动位置不跳；选择存 DataStore，下次打开还是上次的视图 |

## 导航层级

页面栈是 `首页/节点列表 → 节点主题列表 → 帖子 → 个人页`。返回键的实现必须**和显示顺序相反**逐层退，
否则会出现「从节点点开帖子，返回时直接掉回首页」——节点栈被提前清掉了。

```kotlin
// MainActivity.BackHandler
openMember != null -> openMember = null   // 个人页叠在帖子之上
openHistory  -> openHistory = false
openTopic    -> openTopic = null          // 帖子 → 回到它来自的节点列表
openNode     -> openNode = null
```

## 登录

![我的](docs/android-me.png)

V2EX 没有 OAuth，只有 Personal Access Token：

1. 网页版 <https://www.v2ex.com/settings/token> 创建令牌（最长 360 天）
2. App 里「我的 → 填写令牌」粘贴

令牌用 DataStore 存在本机，不上传任何地方。**不填也能用**（首页/节点/帖子全部是 v1 免令牌接口）。

「我的」页显示会员等级、令牌剩余有效期与权限范围、注册时间，以及更换 / 清空令牌、浏览历史入口。

## API 覆盖

| 类型 | 接口 | 需要 |
|---|---|---|
| 免令牌 v1 | 热门 / 最新、主题详情、全部回复、节点列表 / 节点主题、成员资料 | 无需令牌 |
| 令牌 v2 | 提醒（分页）、自己的资料、令牌详情、创建新令牌、节点详情、节点主题、主题详情、回复分页（20 条/页） | 令牌 |
| PRO 写接口 | 发布主题、发布回复、置顶自己的主题、把自己的主题放到首页 | 令牌 + PRO（放到首页也可用 5 万 token 或 1 万铜币替代） |

完整接口映射、响应结构、错误处理见 [docs/api.md](docs/api.md)。

PRO 写接口（发帖 / 回帖 / 置顶 / 放到首页）已接入。首页右上角「发帖」，帖子顶栏「回复」，自己的帖子可在「⋯」菜单置顶或放到首页。会员和费用由服务端校验；作者暂无 PRO，等待开通后验收。

## 构建 / 安装

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# 或者直接：
./gradlew installDebug
```

要求：JDK 17+、Android SDK 35（compileSdk 35 / minSdk 26）。

release 包需要签名。在仓库根目录放一份 `keystore.properties`（**已在 .gitignore 里，不会被提交**）：

```properties
storeFile=keystore/release.jks
storePassword=***
keyAlias=***
keyPassword=***
```

没有这个文件时 `release` 构建会跳过签名，`debug` 包不受影响——直接 `assembleDebug` 就能用。

### GitHub Release 自动打包

[Release APK](.github/workflows/release-apk.yml) 在发布 Release（含预发布）后检出对应 tag，使用 JDK 17 / Android SDK 35 运行 Release 单测、构建并验证签名，随后将 `V4EX.apk` 和 `V4EX.apk.sha256` 上传到该 Release。仅保存草稿或推送 tag 不触发。

先在仓库 **Settings → Secrets and variables → Actions** 添加以下 Repository secrets：

| Secret | 内容 |
|---|---|
| `V4EX_KEYSTORE_BASE64` | 正式签名 keystore 文件的 Base64 内容 |
| `V4EX_STORE_PASSWORD` | keystore 密码 |
| `V4EX_KEY_ALIAS` | 签名 key 的 alias |
| `V4EX_KEY_PASSWORD` | 签名 key 的密码 |

macOS 可用 `base64 -i /你的路径/release.jks | pbcopy` 将编码复制到剪贴板，再粘贴到 Secret；不要提交 keystore 或编码。应复用现有正式签名密钥，否则无法覆盖安装旧版本。

发布前先修改 `app/build.gradle.kts` 的 `versionName` / `versionCode`（升级安装要求 versionCode 递增），提交并推送代码和 tag，再在 GitHub 发布该 tag 的 Release。tag 名只用于选取源码，不会自动覆盖应用版本。首次发布需确保 tag 内已包含此 workflow。

签名 Secret 缺失或签名无效时任务失败，不上传未签名包。修复后可在 Actions 重跑，同名附件会被替换。仓库须允许 Actions 运行；此流程需要向 Release 写入附件，不能用于已经锁定附件的不可变 Release。构建及上传状态在 Actions 的 **Release APK** 查看。

如需用修复后的工作流重新构建已有 Release，可在 Actions 中手动运行 **Release APK**，填写已有的 release tag；源码仍从该 tag 检出。

纯函数单测（分享文案 / 对话归组，不需要模拟器）：

```bash
./gradlew testDebugUnitTest
```

## 结构

```
app/src/main/java/com/v4ex/android/
├── MainActivity.kt           # 导航（4 个 tab + 详情页）、令牌弹窗
├── data/
│   ├── Api.kt                # OkHttp + org.json，v1 免令牌 / v2 带 token
│   ├── Conversation.kt       # 回复 → 对话树（#楼层 / @提及 推断 parent）
│   ├── Models.kt             # Topic / Reply / VNode / Me / Notice
│   ├── Prefs.kt              # DataStore 存令牌 / 已读 / 历史 / 视图偏好
│   └── SharePayload.kt       # 分享文案：标题 + 摘录 + 链接
└── ui/
    ├── AppViewModel.kt       # 全部状态（StateFlow），无 DI
    ├── components.kt         # Avatar / 话题行 / 行内胶囊 / 悬浮底栏
    ├── ReplyList.kt          # 楼层列表：平铺 / 对话两种顺序 + 长按分享
    ├── ShareSheet.kt         # 底部分享面板：App 网格 + 复制链接
    ├── theme/Theme.kt        # 唯一的颜色和字体来源
    └── screens/              # Home / Nodes / Notices / Me / Topic
app/src/test/java/com/v4ex/android/data/
├── ConversationTest.kt       # 归组规则（楼层 / @ / 前向引用 / 长链）
└── SharePayloadTest.kt       # 分享文案清洗与截断
```

## 已踩过的坑（踩之前不知道）

| 坑 | 处理 |
|---|---|
| `/api/topics/latest.json` 的 `p` 参数无效（p=1 与 p=2 返回同一批） | 首页只展示一页最新，翻页交给「节点」 |
| 主题和楼层是两个接口，串行要等 4s | 先渲染标题正文，回复到了再补；`loadTopic` 分两段更新状态 |
| Compose 里 `ActionRow("文字") { ... }` 尾随 lambda 会落到最后一个 Boolean 参数上 | 显式写 `onClick =` |
| 悬浮底栏会盖住列表最后一行 | 列表底部预留 110dp |
| 未登录时问候语出现「[占位头像] v 友」很怪 | 未登录不显示内联头像 |
| **底部角标被裁成水滴形** | 角标放在 `clip(CircleShape)` 的父容器里，被圆角裁掉了。改成父容器不裁、只裁背景层，角标用白描边 + 最小 16dp，压在铃铛右上角 |
| token 异步读盘，首帧误判「没令牌」→ 翻页被禁 | `init` 里 `_token.filterNotNull().first()` 之后再拉首页 |
| **分享面板点 App 崩**：`requires FLAG_ACTIVITY_NEW_TASK` | `ModalBottomSheet` 内容跑在 Dialog 窗口里，`LocalContext` 是 `ContextThemeWrapper` 而不是 Activity；沿 `ContextWrapper` 链找宿主 Activity，找不到才补 `FLAG_ACTIVITY_NEW_TASK` |
| Android 11+ 分享目标一个都查不到 | manifest 没写 `<queries>`，包可见性把别的 App 全挡住；声明 `ACTION_SEND + text/plain` 之后才看得到微信 / Gmail 等 |
| 长按楼层里 `detectTapGestures` 收不到手势 | 头像 / 用户名 / 正文自己的 `clickable` 在 Main pass 先吃掉了 down；改成 Initial pass 自己计时判长按，命中后消费剩余事件（松手时内层点击自动取消） |
| 回复没有 parent 字段，`@` 与 `#楼层` 会「打架」 | 楼层引用优先于 @；只认更早且存在的楼层。比如「@javen73 #10」会挂到 10 楼而不是 javen73 的最新发言 |
| 切对话视图列表跳动 | 两种模式回复条数不变，item key 固定 `reply-{id}`，只换顺序；不要按模式改 key，否则 LazyColumn 会整段重建 |
| 分享网格里「Gmail 图标 + Chat 文案」 | `queryIntentActivities` 回来的 Activity label 不可靠（Gmail 的分享 Activity 自己叫 Chat）；改用 `getApplicationLabel` 读应用名 |
| 复制链接的 Toast 被系统剪贴板预览盖住 | Android 13+ 复制时会自己弹预览浮层；去掉自己的 Toast，只留面板内「已复制链接 ✓」 |

## 刻意不做

- **感谢 / 收藏 / 私信**：API 根本没有这些端点。
- **AI 摘要 / 内容过滤**：试过，去掉了。筛完的「要点」读起来像被嚼过的饭——V2EX 的意思一半在楼层的语气、跑题和互怼里，滤掉就没了。只做纯粹的客户端。
- **无限层级盖楼**：移动端一级缩进已经够表达「在回谁」，再深只是把正文挤窄，所以祖孙统一压到第二级。
- **账号密码登录**：只支持 PAT。V2EX 没有 OAuth，密码不该经过第三方 App。

## 许可证

[V4EX Non-Commercial License 1.0](LICENSE) © 2026 ginuim

允许非商业使用、修改和再分发；**禁止商用**（包括售卖、付费分发、广告或订阅变现、商业产品 / 服务及企业业务用途），商业使用须先取得著作权人的书面授权。再分发须保留协议及版权声明，修改版须标明改动并遵守同样的非商业限制。这是源码可用的非商业协议，不属于 OSI 定义的开源协议；第三方依赖及用户内容仍遵守各自的许可。此前已按 MIT 发布的版本保留原有授权。

与 V2EX 官方无关，未获其背书。界面中的用户内容版权归各自作者所有。


## 外观与设计 token

「我的 → 外观」支持跟随系统（默认）、浅色、深色，选择通过 DataStore 保存在本机。切换立即作用于所有页面、分享面板、弹窗、富文本和系统栏。

- `ui/theme/Tokens.kt` 定义亮暗两套语义颜色：正文、次级文字、画布、浮层、胶囊、边线、强调色、反色文字和空状态插画。
- `ui/theme/Theme.kt` 通过 CompositionLocal 提供 `Ink` 入口，并映射 Material 3 颜色；字体定义不绑定固定颜色。新增界面应使用 `Ink`，避免直接写白色背景或白色选中态文字。
- Markdown 解析保持纯函数，渲染时传入当前链接色与代码背景色；切换主题会重建带颜色的文本 span。
- 原生启动背景随系统主题变化；手动选择的主题在 DataStore 读取后应用。

验证（2026-10-28）：Debug 构建及 44 项单元测试通过；Android 35 手机模拟器已检查手动亮暗切换、重启保留选择、跟随系统亮暗切换、通知登录态反色按钮和系统栏。实体设备断开，尚未完成真机验收；在线帖子及分享面板未在本轮做完整视觉验收。

### PRO 发布接入（2026-10-28，待验收）

按 [V2EX 官方 API 文档](https://edge.v2ex.com/help/api) 新增原生发帖 / 回帖编辑器。发帖支持节点名、标题、可选正文及 Markdown / default 格式；回帖输入正文。仅在当前令牌身份已确认且是 PRO 时显示发帖 / 回复；身份未知、刷新中、校验失败或普通会员均隐藏。最终节点权限、费用仍由服务端判断。输入校验、提交期间防重复点击、错误时保留编辑内容、关闭前确认丢弃均已接入。写操作关闭自动网络重试和重定向，网络中断会提示先核实发布结果。

本次按要求未运行构建、单元测试、模拟器或真实接口测试，未连接手机。后续开通 PRO 后，用模拟器验收成功发布、403 / 429、费用、断网结果不确定及刷新后的帖子 / 回复。

发帖节点使用可搜索的下拉列表，支持按节点英文名称或中文标题筛选；选择后提交节点 name，输入修改后需重新选择。列表复用节点页数据，支持加载失败重试，最多展示前 50 个匹配以避免长列表卡顿。「我的」页分区顺序为账号、阅读、外观、操作。

### 权限入口（2026-10-28）

入口统一由 `MemberPermissions` 判定：发帖 / 回帖需身份确认且 PRO；置顶 / 放到首页还需主题作者与当前账号一致。放到首页虽有非 PRO 资产资格，当前响应模型没有可核验余额，因此只对已确认的 PRO 作者显示，资产资格未知时隐藏。创建令牌需已确认身份及 `everything` scope，`regular` 或 scope 未知时隐藏。切换令牌立即清空旧身份，旧请求返回不会覆盖新账号身份；身份刷新中亦暂时隐藏入口。ViewModel 提交时再次校验资格。此次按要求未构建、未测试、未连接设备。


### 应用内更新

App 启动时每天最多检查一次 GitHub 最新正式 Release；“我的 → 检查更新”可随时手动检查，也会显示已跳过的版本。预发布不参与自动更新。发布流程会从 APK 构建产物生成 `update.json`，与 APK 和 SHA-256 一起上传；首次启用需要发布包含该流程的新 tag。

更新弹窗提供日志和跳过版本选项。下载完成后验证 SHA-256、包名、版本号与已安装应用签名，再打开系统安装程序。首次需允许安装应用，授权返回后点击“安装更新”。发布包需保持相同签名且递增 versionCode；Debug 签名不能覆盖 Release 安装。检查或下载失败可重试，不影响正常浏览。
