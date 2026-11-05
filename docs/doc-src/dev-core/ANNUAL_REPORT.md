# 年度报告开发说明

年度报告是一本可以翻页的手绘手账：项目篇讲 Operit 这一年，个人篇讲用户自己的这一年。页面是打包进 APK 的本地 HTML，图形和手写字全部由代码绘制，不使用位图素材和字体文件。入口在侧边栏头部，品牌名右侧的手绘徽章。

数据口径和计划见 [年度报告计划](../../TODO/annual_report_20261105/index.md)，字段以 [数据契约](../../TODO/annual_report_20261105/1_data_contract.md) 为准。

## 目录

```text
annual-report/
	collector/
	data/
	fixtures/
	scripts/
	src/
		data/
		deck/
		interact/
		motion/
		scenes/
		sketch/
		styles/
		ui/
app/src/main/java/com/ai/assistance/operit/
	data/annualreport/
	ui/features/annualreport/
		entry/
```

- `annual-report/collector/`：项目数据采集脚本，输出年度快照
- `annual-report/data/`：提交进仓库的年度快照，构建时打包
- `annual-report/fixtures/`：只给开发服务器用的模拟用户数据和模拟原生桥，不进入构建产物
- `annual-report/scripts/`：同步构建产物到 assets、生成入口徽章
- `annual-report/src/sketch/`：手绘渲染层，包括 rough.js 画笔、手写字形、涂鸦和纸张
- `annual-report/src/deck/`：翻页本，负责翻页手势、揭晓门槛和进度
- `annual-report/src/scenes/`：每一页一个模块
- `data/annualreport/`：在本机只读聚合用户数据
- `ui/features/annualreport/`：全屏宿主 Activity、WebView 配置和原生桥；`entry/` 是侧边栏的入口徽章

## 本地预览

```bash
npm --prefix annual-report install
npm --prefix annual-report run dev
```

开发服务器默认监听 `http://127.0.0.1:5180/`。页面地址必须带上 `theme` 和 `year`，App 内由宿主 Activity 传入：

```text
http://127.0.0.1:5180/?theme=light&year=2026
```

调试用的查询参数只在开发服务器里生效：

- `theme=dark`：深色手账
- `fixture=empty`：今年没有任何对话的用户
- `fixture=guest`：没有登录 GitHub 的用户，不出现缘分页
- `fixture=error`：用户数据生成失败，检查过渡页的错误提示和重试
- `market=error`：个人插件市场数据请求失败
- `delay=3000`：用户数据延迟返回的毫秒数

建议用手机尺寸的视口预览，例如 390 × 844。每一页的数据都要先完成交互才会出现，上滑翻页在揭晓之前会被拦下。

## 更新项目快照

项目数据只取线上来源：GitHub API 和插件市场的静态接口，不读本地仓库。快照时刻放在发版时 `dev` 同步到 `main` 之后：

```bash
GH_TOKEN=$(gh auth token) npm --prefix annual-report run collect:project -- --year 2026 --snapshot 2026-12-10T00:00:00+08:00
```

产物是 `annual-report/data/project-2026.json` 和 `annual-report/data/stargazers-2026.json`，提交进仓库，数字可以在 PR 里审阅。脚本会打印窗口内没有关联 GitHub 账号的提交作者，这些提交不计入贡献者，需要时由维护者补充邮箱关联后重新采集。

脚本只对传输层错误重试同一个请求，HTTP 错误、分页不完整或数据校验失败都会直接退出，不写出不完整的文件。

## 入口徽章

徽章由脚本绘制：rough.js 画纸片、胶带和涂鸦，手写字形与报告页共用 `src/sketch/glyphPath.ts`。同一张图用三组随机种子各画一遍，App 里低帧率轮换，形成线条微微颤动的手绘动画。

```bash
npm --prefix annual-report run generate:badge -- --year 2026
```

产物是 `ui/features/annualreport/entry/AnnualReportBadgeArt.kt`，不要手改。加上 `-- --preview <html 路径>` 可以同时输出一份预览页面。徽章只在侧边栏打开时播放动画。

## 数据如何进入页面

- 页面通过 `https://appassets.androidplatform.net/annual-report/index.html` 加载，只开放 `assets/annual-report/` 目录
- `/report-data/user.json` 返回 `AnnualReportCollector` 生成的用户数据；Activity 创建时就开始采集，页面读到个人篇时通常已经完成
- `/report-data/market.json` 返回登录用户在插件市场的作品，需要联网，只在用户点开对应卡片时请求
- 其他任何请求都会被拦截并返回 403，页面没有联网能力
- 原生桥 `OperitAnnualReport` 只有三个方法：关闭、触感反馈、截取海报

开发服务器里，这些路径由 `vite.config.ts` 的中间件和 `fixtures/dev-bridge.ts` 模拟，页面代码只有一条调用路径。

数据契约在三处保持一致：`annual-report/src/data/types.ts`、`data/annualreport/AnnualReportModels.kt` 和契约文档。页面加载数据时会逐字段校验，字段漂移会直接报错并显示在错误页上。

## 手写字形

数字、少量汉字和 `Operit` 的笔画骨架定义在 `src/sketch/glyphs.ts`。数字都按 `src/data/format.ts` 的规则格式化成字形表里有的字符，例如千分位逗号、小数点、万和亿。新增字形时按书写顺序列出每一笔的控制点，坐标范围是 0 到 100，基线在 100。

其余文字使用系统字体，加上逐字微倾斜和墨迹滤镜做成艺术字，所以动态文本，比如对话标题和模型名，不受字形表限制。

## 新增一年

1. 用采集脚本生成新一年的快照，提交到 `annual-report/data/`
2. 在 `src/data/load.ts` 的两个加载表里加上新的年份
3. 把 `AnnualReportEdition.YEAR` 改成新的年份
4. 重新生成入口徽章，确认徽章上的年份
5. 检查各页文案里与年份相关的描述
