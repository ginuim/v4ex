# V4EX 分享卡片

把 Android 客户端（`android/`）做成的一组社交分享图。视觉系统：**Swiss International × IKB 克莱因蓝**，
和 App 本身「浅色、无卡片、靠字重和留白分区」的排版语言保持一致。

## 产物

| 文件 | 尺寸 | 用途 |
|---|---|---|
| `output/xhs-01-cover.png` | 1080×1440 | 小红书封面 |
| `output/xhs-02-why.png` | 1080×1440 | 为什么删掉 AI 摘要 |
| `output/xhs-03-thread.png` | 1080×1440 | 对话视图（含 App 截图） |
| `output/xhs-04-browse.png` | 1080×1440 | 刷帖体验（矩阵 + 1379 节点） |
| `output/xhs-05-home.png` | 1080×1440 | 首页缓存（含 App 截图） |
| `output/xhs-06-traps.png` | 1080×1440 | 分享面板踩过的坑 |
| `output/xhs-07-end.png` | 1080×1440 | 收尾（深色页） |
| `output/wechat-21x9.png` | 2100×900 | 公众号头图 |
| `output/wechat-1x1.png` | 1080×1080 | 公众号分享卡 |

小红书按 `xhs-01` → `xhs-07` 顺序发；公众号用 `21:9` 做头图、`1:1` 做分享卡。

## 素材来源

`assets/` 里的截图全部来自本仓库，无外部图源：

| 文件 | 来源 |
|---|---|
| `assets/app-*.png` | `android/docs/android-*.png`，去掉状态栏与手势条 |
| `assets/shot-home.png` | 上述 `app-home` 的 3:4 取景（顶部） |
| `assets/shot-thread.png` | 上述 `app-thread` 的 3:4 取景（楼层/对话切换 + 缩进回复） |

重新裁剪的方式见本文件末尾的命令。

## 改文案 / 重出图

所有版面在一个文件里：`index.html`（Swiss 种子模板 + 本任务的少量补充 CSS，见文件内
`TASK-SCOPED (V4EX deck)` 注释块）。改完文案后重渲染：

```bash
# 出图（写进 output/）
~/.agents/skills/guizang-social-card-skill/run.sh "$PWD"

# 版式校验（R1–R9，溢出 / 密度 / 字号 / 标题间距）
~/.agents/skills/guizang-social-card-skill/run.sh "$PWD" --validate
```

渲染走 ego-browser，不需要装 Playwright 或 Chromium。当前校验结果：**9 张 0 fail**，
剩 7 条 R5 密度建议（Swiss 版面本身留白多，属 advisory）。

换主题色只需改 `index.html` 第 2 行的 `data-accent`：
`ikb` / `lemon-yellow` / `lemon-green` / `safety-orange`。

## 重新裁图

```bash
python3 - <<'EOF'
from PIL import Image
TOP, BOT = 100, 86                      # 状态栏 / 手势条
for n in ['android-home','android-thread','android-share','android-me']:
    im = Image.open(f'docs/{n}.png').convert('RGB')
    im.crop((0, TOP, im.width, im.height - BOT)).save(f'share-cards/assets/{n.replace("android-","app-")}.png')

# 3:4 取景
Image.open('share-cards/assets/app-home.png').crop((0, 0, 1080, 1440)).save('share-cards/assets/shot-home.png')
Image.open('share-cards/assets/app-thread.png').crop((0, 700, 1080, 2140)).save('share-cards/assets/shot-thread.png')
EOF
```

## 内容口径

文案取自 `android/README.md` 里记录的真实决策与踩坑，没有编造数据：

- 「AI 摘要试过，去掉了」——README「刻意不做」一节
- 170 楼 / 1379 节点 / 800 条已读 ——README 功能表与实际截图
- `#楼层` / `@提及` 推断 parent、包可见性、`FLAG_ACTIVITY_NEW_TASK`、
  Activity label 不可靠 ——README「已踩过的坑」一节

与 V2EX 官方无关，未获其背书。
