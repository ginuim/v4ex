# V2EX API 2.0 接入清单

本文档记录 V4EX 对 V2EX API 2.0 的接入情况：认证方式、限流、逐接口的映射关系、响应结构与错误处理。

V4EX 原本只使用 v1 免令牌接口（`/api/*.json`），现在接入 API 2.0 读接口，以及发帖、回帖、置顶和放到首页写接口。

**2026-10-28 新增发帖 / 回帖，仅实现与静态核对，按要求未运行构建或任何测试。** 旧接口的编译验证不能视为本次发布功能的验证。全部 v2 接口真实验收待后续开通 PRO 后进行。文档来源：[官方 API](https://edge.v2ex.com/help/api)。

## 认证与限流

- 接口前缀（官方文档）：`https://www.v2ex.com/api/v2/`。仓库里所有请求都拼在 `Api.kt` 的 `base` 常量后面（当前是 `https://edge.v2ex.com`），路径本身与文档一致。
- 认证：`Authorization: Bearer <token>`，令牌在「我的 → 填写令牌」粘贴，用 DataStore 存在本机，不上传。
- **所有 V2 接口都强制要求令牌**，包括 `nodes/:node_name` 这种看起来像公开数据的接口；匿名请求一律 401。所以无令牌用户全部走 v1，见下文。
- 限流：每 IP 每小时 600 次。响应头：
  - `X-Rate-Limit-Limit`：本小时配额上限
  - `X-Rate-Limit-Remaining`：本小时剩余次数
  - `X-Rate-Limit-Reset`：配额重置时间（Unix 秒）
- 令牌的 `scope` 有 `everything` / `regular` 两种；`regular` 令牌不能再创建新令牌。

## 接口清单

| 方法 | 路径 | 说明 | 需要令牌 | 需要 PRO | 实现位置 | UI 入口 | 状态 |
|---|---|---|---|---|---|---|---|
| GET | `notifications?p=N` | 最新提醒（分页，20 条/页） | 是 | 否 | `data/Api.kt` `notices(page)` | 通知页列表底部「加载更多」 | ⚠️ 已接入 · 未实测 |
| DELETE | `notifications/:id` | 删除提醒（等价于标记已读） | 是 | 否 | `data/Api.kt` `clearNotice(id)` | 通知页点击提醒 | ⚠️ 已接入 · 未实测 |
| GET | `member` | 自己的 Profile | 是 | 否 | `data/Api.kt` `me()` | 「我的」页 | ⚠️ 已接入 · 未实测 |
| GET | `token` | 当前令牌信息（scope、有效期、已用次数） | 是 | 否 | `data/Api.kt` `token()` | 「我的」页令牌详情；本小时配额另取响应头 | ⚠️ 已接入 · 未实测 |
| POST | `token`（文档写作 `tokens`） | 创建新令牌，body：`{"scope":"everything"\|"regular","expiration":2592000\|5184000\|7776000\|15552000}` | 是 | 否 | `data/Api.kt` `createToken(scope, expiration)` | 「我的」页创建新令牌 | ⚠️ 已接入 · 未实测 |
| GET | `nodes/:node_name` | 节点详情 | 是 | 否 | `data/Api.kt` `node(name)` | 「/go/:node」节点主题页顶部的节点说明 | ⚠️ 已接入 · 未实测 |
| GET | `nodes/:node_name/topics?p=N` | 节点主题（分页，20 条/页） | 是 | 否 | `data/Api.kt` `nodeTopicsPage(node, page)` | 节点主题页上滑加载（底部也有「加载更多」/「到底了」） | ⚠️ 已接入 · 未实测 |
| GET | `topics/:topic_id` | 主题详情 | 是 | 否 | `data/Api.kt` `topicV2(id)`（无令牌时退回 v1，见下） | 帖子页 | ⚠️ 已接入 · 未实测 |
| GET | `topics/:topic_id/replies?p=N` | 主题回复（分页，每页 20） | 是 | 否 | `data/Api.kt` `repliesPage(topicId, page)` | 帖子页先加载 20 条，底部「加载更多」/「到底了」 | ⚠️ 已接入 · 未实测 |
| POST | `topics` | 创建主题，`node_name` / `title` / `content` / `syntax` | 是 | **是** | `Api.createTopic` | 首页右上角「发帖」 | ⚠️ 已实现 · 未编译 / 未实测 |
| POST | `topics/:topic_id/replies` | 创建回复，`content` | 是 | **是** | `Api.createReply` | 帖子顶栏「回复」 | ⚠️ 已实现 · 未编译 / 未实测 |
| POST | `topics/:topic_id/set-sticky?duration=15min\|1hr\|8hr` | 置顶自己的主题 | 是 | **是** | `data/Api.kt` `setSticky(topicId, duration)` | 帖子页右上角「⋯」菜单（自己的主题且已填令牌才出现） | ⚠️ 未实测 |
| POST | `topics/:topic_id/boost` | 把自己的主题放到首页 | 是 | **是** | `data/Api.kt` `boost(topicId)` | 帖子页右上角「⋯」菜单，带费用与门槛确认弹窗 | ⚠️ 未实测 |

置顶 / 放到首页只对已确认 PRO 身份且自己发的主题显示；置顶需要 PRO，放到首页需要 PRO、持有 5 万以上 token 或 1 万以上铜币三者之一。

另外首页「最新」的翻页用 `GET /api/v2/topics/latest?p=N`（对应 `latestPage(page)`）。官方文档现已列出，需要令牌。

## v1 免令牌接口

无令牌时 App 仍然可用，靠的是下面这些 v1 接口；帖子详情与回复在 v2 请求失败时也会退回 v1。

| 方法 | 路径 | 实现位置 | 用途 |
|---|---|---|---|
| GET | `/api/topics/hot.json` | `data/Api.kt` `hot()` | 首页「热门」 |
| GET | `/api/topics/latest.json` | `data/Api.kt` `latest()` | 首页「最新」 |
| GET | `/api/topics/show.json?id=N` | `data/Api.kt` `topic(id)` | 无令牌时看帖子正文 |
| GET | `/api/topics/show.json?node_name=N` | `data/Api.kt` `nodeTopics(node)` | 无令牌时节点主题第一页 |
| GET | `/api/replies/show.json?topic_id=N` | `data/Api.kt` `replies(topicId)` | 无令牌时一次取回全部回复 |
| GET | `/api/nodes/all.json` | `data/Api.kt` `nodes()` | 节点列表 |
| GET | `/api/members/show.json?username=N` | `data/Api.kt` `member(username)` | 看别人的资料 |

保留 v1 的原因：

- **无令牌可用**：v2 全部接口都要令牌，不填令牌的新用户只能靠 v1。
- **v1 的 `topics/latest.json` 不支持翻页**：`p` 参数被忽略（p=1 与 p=2 返回同一批），所以无令牌时首页「最新」只有第一页，底部提示「填写令牌后可继续翻页」。有令牌时走 v2 `/topics/latest` 分页。
- **v1 的 replies 一次返回全部楼层**：不需要分页，但没有 `pagination`、也不能只取一页；有令牌时改用 v2 分页接口（20 条/页），首屏更快。

## 响应结构

所有 v2 接口统一包裹在下面的结构里：

```json
{
  "success": true,
  "message": "",
  "result": {},
  "pagination": {
    "per_page": 20,
    "total": 42,
    "pages": 3
  }
}
```

- `success`：业务是否成功；`false` 时看 `message`。
- `result`：单对象接口是对象，列表接口是数组。
- `pagination`：分页接口在响应顶层给 `{per_page, total, pages}`（`nodes/:name/topics`、`topics/:id/replies` 会带）。
- **没有 `current` 字段**：客户端自己记录当前页，不要从响应里读。
- `notifications` 不保证返回 `pagination`：App 的处理是「这一页满 20 条就当作还有下一页」，下一页空了自然停。
- v1 接口不套这层壳：列表接口直接返回数组，详情接口返回数组套对象。

回复条目的楼层号要注意：v2 回复没有 floor 字段，也没有楼层号，**楼层需要按 `(page - 1) * per_page + index + 1` 推算**（`per_page` 为 20，`index` 从 0 开始）；v1 一次返回全部回复，直接按数组下标 + 1 就是楼层。

## 错误处理

| HTTP 状态 | 含义 | App 内展示 |
|---|---|---|
| 401 | 令牌缺失或无效/过期 | 「我的」页头像旁的错误文案、帖子页 / 通知页的 `ErrorLine`：「令牌无效或已过期，请到「我的」重新填写」；未填令牌时直接提示先填令牌，不代发请求 |
| 403 | 权限不足：非本人主题、没有 PRO、铜币/token 不足、`regular` 令牌不能创建新令牌 | 置顶 / 放到首页的结果显示在帖子页顶栏下方的提示条；创建令牌失败显示在对话框错误行。服务端说明原样显示，不将铜币不足等情况统一误报为需要 PRO |
| 404 | 对象不存在（主题/节点/提醒已删） | 帖子页提示「这个主题不存在」等；删除提醒返回 404 视为已读，按成功处理（可重入） |
| 405 | 服务端不接受该方法 | 原样显示服务端 `message`，没有则「服务端不接受这个方法」 |
| 429 | 触发限流（每 IP 每小时 600 次） | 「请求太频繁，已触发限流（每 IP 每小时 600 次），过一会儿再试」 |
| 其它 5xx | V2EX 服务端错误 | 「V2EX 服务器出错了（<code>），稍后再试」 |
| 网络异常 | DNS 失败 / 超时 / 无网络 | `UnknownHostException` 显示「网络不可用」，其它异常显示异常信息；列表刷新失败保留旧数据 |
| 其它 | 未归类的 HTTP 错误 | 显示服务端 `message`，没有则「网络错误 <code>」 |

App 解析错误时优先用服务端返回的 `message`（中文说明原样展示，英文才换成自己的话），401 统一文案为「令牌无效或已过期」。

## 已知边界 / 待办

- **全部 v2 接口未实测**：接入时没有可用的 Personal Access Token，v2 读接口和 PRO 写接口都只做了编译与静态核对，没有真实请求。发帖 / 回帖需要 PRO；置顶与 boost 的权限和成功分支亦待实测。
- **boost 费用与门槛**：需要 PRO、5 万以上 token 或 1 万以上铜币三者之一；费用 100 铜币，创建于过往年度的老帖按年份加价。确认弹窗里已说明，实际扣费未验证。
- **创建令牌的路径差异**：官方文档写 `POST /api/v2/tokens`，实现里先打 `/api/v2/token`，返回 404/405 再退到 `/api/v2/tokens`，两条路径都保留。新令牌明文只在响应里出现一次。
- **V2 全部接口需要令牌**：没有令牌的用户仍走 v1；因此无令牌时节点主题、最新列表只能看第一页，回复虽然完整但一次全量返回。
- **分页数据会漂移**：翻页期间有新主题/回复时 `total` 和 `pages` 会变化，已加载的列表可能重复或跳过，不做去重补偿。
- **限流按 IP 计**：同一网络下多个客户端共享每小时 600 次配额；CDN 可缓存的请求只有第一次消耗配额。

## PRO 发帖 / 回帖交互与验收边界

- 主题标题 1–120 字符，正文可空且最多 20000 字符；支持 `markdown`（默认）和 `default`。回复必填且最多 10000 字符。计数按 Unicode code point。节点通过可筛选下拉列表选择，支持按英文名称或中文标题检索，提交时使用节点 name。
- 编辑器在请求期间禁用提交、编辑与关闭；错误不会清除草稿。关闭有内容的编辑器先确认丢弃，草稿不落盘。当前令牌身份确认且 PRO 才显示发帖 / 回帖入口；身份未知、加载中或失败时隐藏，并在提交时再次校验。
- 发帖成功打开返回的主题；回复成功刷新当前帖子。刷新失败不应重新提交已成功的回复；分页回复仍从第一页展示，新增回复可能位于后续页。
- 写请求禁用 OkHttp 自动重试与重定向；网络中断提示先到网站确认结果，避免重复发布。发帖响应缺少有效主题编号时也提示先确认。
- 发布需遵守正常节点权限、频率及铜币费用，编辑器中已说明。未实际调用写接口，没有产生发帖或扣费。
- 待开通 PRO 后仅在模拟器验收：主题 / 回复成功、超长与空内容、403、429、断网时保留内容、成功后详情刷新。按用户要求，本次不运行任何测试或构建。

## 权限显示规则（2026-10-28）

| 功能 | 客户端显示条件 |
|---|---|
| 发帖 / 回帖 | 当前令牌已确认身份，且 `me.pro` 为真 |
| 置顶 / 放到首页 | 上述条件，且主题作者等于当前用户名 |
| 创建新令牌 | 已确认身份，且 scope 为 `everything` |

未登录、身份加载中 / 失败、已知令牌过期、scope 未知时不授予对应权限；账号切换清空身份并丢弃旧身份请求结果。会员身份尚未确认时不再显示“普通会员”。boost 的非 PRO 资产门槛无法从当前接入字段核验，故暂不开放该入口；不将 Personal Access Token 的使用次数误当作资产余额。节点权限、频率、余额和实际扣费仍由服务端最终判断。本次仅静态核对，未运行构建或测试。
