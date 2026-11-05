---
fork: https://github.com/luojiaping/Operit
branch: feat/annual-report
status: accepted, sidebar entry unlocks 2027-01-03T00:00:00Z
For_Agent: 指标口径以 1_data_contract.md 为准；开发方式见 docs/doc-src/dev-core/ANNUAL_REPORT.md
---

# 年度报告

## 原本状况

App 内没有年度回顾。用户侧数据分散在 Room 的 `chats`、`messages`、`message_variants`、`token_usage_records`，ObjectBox 记忆库，以及角色卡和工作流各自的存储里。设置中的 Token 统计页能按日期范围查看 token，但不涉及对话、工具和记忆。

项目侧数据分散在 GitHub 仓库、Release、Stargazers 和插件市场的静态接口里，仓库内没有采集脚本。

## 修改意图

做一个可交互的年度报告，分项目和用户两部分：

- 页面用 HTML 承载，手绘风格，所有图形和手写字由代码在运行时绘制，不使用位图素材和字体文件
- 前端是独立的 Vite 子项目，产物内嵌到 APK 的 assets，打开时完全离线
- 项目数据在发版前由脚本采集为年度快照，随前端一起打包
- 用户数据在打开报告时从本机数据只读聚合，只在 App 进程与本地 WebView 之间传递
- 入口是侧边栏头部品牌名右侧的手绘徽章，2027-01-03 00:00 UTC 解锁，在那之前从关于页的开发测试入口进入
- 每一项数据都要用户完成点按、长按、拖动、刮开、涂抹、拨动或撕开之后才出现

音乐不在本计划内。

## 已确认的决定

- 首发只支持中文
- 不打包字体：数字、少量汉字和 `Operit` 用代码定义的手写字形绘制，其余文字用系统字体加代码效果做成艺术字
- 使用时长展示 AI 工作时长，也就是 AI 回复的等待与输出耗时，附活跃天数；不采用系统使用情况统计
- 原创插件按市场数据中 `authorId` 等于 `publisherId` 判定
- 项目数据随包快照，快照时刻放在发版同步之后
- 版本数包含正式版和补丁仓库里 `vX.Y.Z+N` 形式的补丁版本
- 贡献者名单只公开 GitHub 账号，按字母序排列，不做排名
- 做个人化数据：stargazer 名次来自快照，插件市场作品来自市场发布者接口
- 分享海报可以出现对话标题和角色名
- 2027 年 1 月上线：侧边栏入口在 2027-01-03 00:00 UTC 解锁，开发测试入口是关于页连续点击 Operit 图标 10 次

## 期待结果

- 侧边栏徽章和任意入口都调用同一个启动方法，全屏打开指定年份的报告
- 报告里每个数字都能在 [数据契约](1_data_contract.md) 中找到唯一的口径与来源
- 数据不完整的指标标明统计起点，不用推算值替代
- 开发者可以在桌面浏览器用模拟数据预览全部场景
- 明年复用同一套代码，只需新增年度快照、更新年份并重新生成徽章

## 作用域

```text
annual-report/
	collector/
	data/
	fixtures/
	scripts/
	src/
app/src/main/assets/annual-report/
app/src/main/java/com/ai/assistance/operit/
	data/annualreport/
	ui/features/annualreport/
		entry/
.github/workflows/
ci/script/
docs/doc-src/dev-core/
```

- `annual-report/`：前端子项目、采集脚本、年度快照和徽章生成脚本
- `app/src/main/assets/annual-report/`：前端构建产物，不进入 Git
- `data/annualreport/`：用户数据只读聚合
- `ui/features/annualreport/`：宿主 Activity、WebView 配置、原生桥和入口徽章
- `.github/workflows/`、`ci/script/`：构建与预审接入
- `docs/doc-src/dev-core/`：开发者文档

## 步骤

1. [DONE] [数据契约与口径](1_data_contract.md)
2. [DONE] [项目数据采集脚本](2_project_collector.md)
3. [DONE] [用户数据聚合](3_user_collector.md)
4. [DONE] [前端子项目与手绘渲染](4_web_frontend.md)
5. [DONE] [原生宿主、数据注入与入口](5_android_host.md)
6. [DONE] [CI 与文档](6_ci_and_docs.md)
7. [验证](7_verification.md)

## 待办

- 在真机上检查 [验证](7_verification.md) 里「上线开关」一节
- 1 月上线按 [开发说明](../../doc-src/dev-core/ANNUAL_REPORT.md) 里的「1 月上线接手清单」处理
- 发版前按快照时刻重新运行采集脚本并提交快照
- 前台使用时长记录尚未实现；如果明年的报告需要使用时长，要在今年发版时上线记录
