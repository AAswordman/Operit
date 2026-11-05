# 年度报告开发说明

年度报告是一本可以翻页的手绘手账：项目篇讲 Operit 这一年，个人篇讲用户自己的这一年。页面是打包进 APK 的本地 HTML，图形和手写字全部由代码绘制，不使用位图素材和字体文件。入口是侧边栏头部品牌名右侧的手绘徽章，到解锁时刻才会出现，在那之前从关于页的开发测试入口进入。

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
- `ui/features/annualreport/`：全屏宿主 Activity、WebView 配置和原生桥；`entry/` 是侧边栏的入口徽章和关于页的开发测试入口

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

## 上线时间与开发测试入口

侧边栏的徽章在 `AnnualReportEdition.UNLOCK_AT` 之前不出现，当前是 2027-01-03 00:00 UTC，此前抽屉头部的版面和没有年度报告时一致。

- 取 UTC 的绝对时刻，各地用户同一时刻解锁，此时最晚进入新年的 UTC-12 也已是 1 月 2 日中午，用户侧统计的是完整的上一年
- 判断只用设备的系统时钟，不联网，所以只拦得住普通用户，改系统时间可以提前看到入口
- 每次抽屉开合重新判断一次，应用常驻着跨过解锁时刻时，下一次打开抽屉入口就会出现
- 判断逻辑是 `AnnualReportEdition.isUnlocked`，使用处是 `DrawerContent.kt` 的 `SidebarInfoCard`

解锁之前用开发测试入口验收：在关于页连续点击顶部的 Operit 图标 10 次，第 10 次有一次长按触感，随后全屏打开报告。

- 相邻两次点击间隔超过 1 秒就从头计数
- 不受解锁时刻限制，所有构建都带，解锁之后依然可用
- 没有提示和水波纹，也不产生点击语义，读屏软件不会把图标读成可点击控件
- 实现在 `entry/AnnualReportDevEntry.kt`，关于页只挂了一个修饰符
- 解锁之前打开的报告里，用户数据的窗口到当前时刻为止，项目数据是随包的快照

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
3. 把 `AnnualReportEdition.YEAR` 改成新的年份，`AnnualReportEdition.UNLOCK_AT` 改成新一年计划的解锁时刻
4. 重新生成入口徽章，确认徽章上的年份
5. 检查各页文案里与年份相关的描述

## 1 月上线接手清单

2027 年 1 月上线时按这个顺序做，细节见上文对应章节。

1. 等 `dev` 同步到 `main`，运行采集脚本更新项目快照，提交 `annual-report/data/` 下的两个文件。快照时刻晚于 2027-01-01 00:00+08:00 时，窗口会截止到年底
2. 处理下面的已知问题，即完整年份窗口的右端点
3. 运行 `npm --prefix annual-report run typecheck`，GitHub 的 PR 检查也会运行该步骤；类型检查与生产构建分别执行
4. 在真机上用开发测试入口走一遍，重点看相识天数，日历是否满 365 格，以及版本之路和星空上的数字
5. 发版，让带着最终快照的安装包在 `UNLOCK_AT` 之前到达用户手里。已经装了含入口的旧版而没有更新的用户，到点同样会解锁，看到的是旧版自带的快照
6. 到点之后入口自动出现，不需要再发版或改配置

### 已知问题：完整年份窗口的右端点

窗口左闭右开。年还没过完时，右端点是生成时刻，当天算在窗口内；过了年底，右端点被截到次年 1 月 1 日 0 点，这一天不在窗口内。前端有几处把右端点的日期当成最后一天用，完整年份时会差一天：

- `scenes/meet.ts` 和 `scenes/poster.ts` 的相识天数多算了 1 天
- `scenes/github.ts` 的「截至 M 月 D 日」会显示成 1 月 1 日，应当是 12 月 31 日

`scenes/calendar.ts` 按完整日期比较，`scenes/releases.ts` 的进度比例有上限，这两处不受影响。

用户侧的右端点在 `AnnualReportCollector.windowOf`，项目侧的在 `collector/collect-project.ts` 里生成窗口的地方，两边口径一致。修的时候可以让前端取右端点前一刻的日期，也可以让采集端直接写最后一秒，改完同步 [数据契约](../../TODO/annual_report_20261105/1_data_contract.md) 里的窗口描述。
