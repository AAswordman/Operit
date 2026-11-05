# 1. 数据契约与口径

## 旧实现情况

没有统一的契约。相关数据分别服务于聊天、Token 统计页和插件市场，口径各不相同。

## 意图

报告消费两份 JSON，外加两份按需请求的数据：

- `ProjectReport`：项目级，发版前由采集脚本生成，随前端打包
- `StargazerSnapshot`：stargazer 名单快照，随前端打包，用到时才加载
- `UserReport`：用户级，打开报告时由 App 生成
- `PersonalMarket`：登录 GitHub 的用户在插件市场的作品，用户点开对应卡片时由 App 联网查询

字段定义在 `annual-report/src/data/types.ts` 和 `data/annualreport/AnnualReportModels.kt`，两侧同名，增删必须同步。页面加载时逐字段校验，字段漂移直接报错。

每个指标只有一个来源。拿不到来源的指标不进入契约，不用其他数据推算后替代。可空字段表示今年没有这项数据，对应的场景不出现。

## 统计窗口

- 项目级窗口按 `Asia/Shanghai` 时区，从 1 月 1 日零点到快照时刻；快照晚于次年 1 月 1 日时截止到次年 1 月 1 日
- 用户级窗口按设备时区，从 1 月 1 日零点到生成时刻与次年 1 月 1 日零点中较早的一个
- 窗口左闭右开，与 Token 统计页的范围查询一致

## 项目级指标

| 指标 | 口径 | 来源 |
| --- | --- | --- |
| 提交数 | 默认分支 `main` 上作者时间落在窗口内的非合并提交 | GitHub Commits API |
| 提交分布 | 按月计数，星期与小时的二维计数，0 点到 5 点的深夜提交数，提交最多的一天 | 同上 |
| 增删行数 | GitHub 对默认分支统计的每周增删行数，取周起点落在窗口内的周求和 | `stats/code_frequency` |
| 正式版 | 窗口内发布的正式 Release，不含草稿和预发布 | `AAswordman/Operit` Releases |
| 补丁版本 | 补丁仓库里 tag 为 `vX.Y.Z+N` 且发布在窗口内的 Release | `AAswordman/OperitNightlyRelease` Releases |
| 贡献者 | 窗口内在 `main` 上有提交、且提交关联到 GitHub 账号的人，不含机器人账号 | Commits API 的 `author.login` |
| 新贡献者 | 上述账号中，首次提交落在窗口内的账号，按字母序排列 | 同上 |
| 合并 PR 与 Issue | 窗口内合并的 PR 数、新建与关闭的 Issue 数 | Search API |
| Stars | 年初数、快照数与按月新增 | Stargazers API 的 `starred_at` |
| 市场新上架 | `createdAt` 落在窗口内的公开条目，按类型计数 | 市场静态列表 |
| 原创作品 | 新上架条目中 `authorId` 等于 `publisherId` 的条目 | 同上 |
| 新发布者 | 首个公开条目落在窗口内的发布者 | 同上 |
| 插件下载 | 快照时全部公开条目 `stats.downloads` 之和 | 同上 |
| 人气插件 | 窗口内新上架条目中下载最多的 3 个 | 同上 |

说明：

- 发版时 `dev` 会同步到 `main`，`main` 上的非合并提交都在 `dev` 里，快照放在同步之后，两者的提交数一致
- Stars 只能统计快照时仍在 star 的账号，期间取消 star 的账号不可见，按月新增是净留存口径
- 没有关联 GitHub 账号的提交不计入贡献者，这与仓库 Contributors 页面的口径相同；采集脚本会单独列出这些提交的作者
- 市场静态列表只包含审核通过的公开条目，待审、驳回和撤回的条目不计入
- 下载量是累计计数器，不能按年切分，报告中标注为累计
- 脚本和工具包由作者本人上传，`authorId` 恒等于 `publisherId`；Skill 和 MCP 的作者取自源仓库所有者，只有发布者就是仓库所有者时才算原创

## 用户级指标

| 指标 | 口径 | 来源 | 数据起点 |
| --- | --- | --- | --- |
| 相识天数 | 本次安装日期到窗口结束日期，两端都算 | `PackageInfo.firstInstallTime` | 本次安装 |
| Token 总量 | 窗口内的输入、输出与缓存 token | `token_usage_records` | 见下文 |
| 调用次数 | 窗口内 `requestCount` 之和 | 同上 | 见下文 |
| 模型排行 | 按规范化模型名汇总的 token 总量，取前 3 | 同上 | 见下文 |
| 花费 | 按用户当前价格与汇率设置算出的已知金额，附未定价的调用数 | 同上与 `token_stats_models` | 见下文 |
| 每月与每日 token | 按本地月份与本地日期汇总 | `token_usage_records` | 见下文 |
| 新建对话 | `chats.createdAt` 落在窗口内 | `chats` | 全年 |
| 消息数 | 窗口内 `sender` 为 `user` 与 `ai` 的消息分别计数 | `messages` | 全年 |
| 活跃天数与最长连续 | 有用户消息的本地日期 | 同上 | 全年 |
| 最忙的一天 | 用户消息最多的本地日期 | 同上 | 全年 |
| 时段分布 | 用户消息按本地小时计数，以及 0 点到 5 点之间时刻最晚的一条 | 同上 | 全年 |
| 年度第一句话 | 窗口内最早一条用户消息的时间与开头 120 字，去掉附件、记忆、引用等由 App 拼入的结构化内容 | 同上 | 全年 |
| 最长的对话 | 窗口内消息最多的对话 | `messages` 与 `chats` | 全年 |
| 最常聊的角色卡 | 按对话绑定的角色卡汇总窗口内的消息数，没有绑定角色卡的对话和群聊不参与 | `chats.characterCardName` | 全年 |
| 重新生成 | 窗口内的消息变体数，`messages` 保存原始回复，每次重新生成另存一行变体 | `message_variants` | 全年 |
| 收藏 | 窗口内被收藏的消息数 | `messages.isFavorite` | 全年 |
| AI 工作时长 | AI 回复与重新生成版本的 `waitDurationMs` 与 `outputDurationMs` 之和 | `messages` 与 `message_variants` | v1.10.1 |
| 最长的一次回复 | 单条回复的最大 `outputTokens` | 同上 | v1.10.1 |
| 工具调用 | 采用的回复和重新生成版本里的工具标签计数与前 5 个工具 | 两表的正文 | 全年 |
| 新增记忆 | 所有记忆空间中 `createdAt` 落在窗口内的记忆 | ObjectBox | 全年 |
| 新建角色卡 | 有创建时间记录且落在窗口内的角色卡，没有记录的角色卡不计入 | 角色卡 DataStore | 全年 |
| 新建工作流 | `createdAt` 落在窗口内的工作流 | 工作流存储 | 全年 |
| GitHub 身份 | App 内登录的 GitHub 账号和数字 id；未登录为 null | GitHub 登录信息 | 当前 |

## 个人化数据

- 第几颗星：在 stargazer 快照里按 star 时间排序找到当前登录账号的位置，快照之后才 star 的用户显示没有找到
- 贡献者身份：当前登录账号在窗口贡献者名单里
- 插件市场作品：用 `gh_<GitHub 数字 id>` 查询市场发布者分片，统计关系为 owner 且审核通过的条目，下载和点赞取各条目的 `stats`；分片里没有这个作者表示还没发布过作品

## Token 数据的起点

`token_usage_records` 能覆盖到哪一天，取决于用户升级经历的三个版本：

- v1.6.3 起，消息带 `provider` 与 `modelName`
- v1.10.1 起，每条消息带 token 与耗时字段
- v1.12.2 引入 `token_usage_records`；升级迁移时把已有的 AI 消息和变体各复制为一行，`requestCount` 记为 1，之后每次成功请求单独记一行

用户升级到 v1.10.1 之前的 token 没有逐条记录，只存在于没有时间戳的累计计数中。累计计数无法归属到年份，报告不使用。token 页显示窗口内最早一条记录的日期，作为统计起点。

调用次数同理：迁移复制的行按每条 AI 回复记 1 次，升级到 v1.12.2 之后才是逐次请求。这与 Token 统计页的口径一致，报告里的数字可以在统计页用同一日期范围核对。

## 使用时长

现有数据里没有 App 前台时长。今年展示 AI 工作时长和活跃天数；系统 `UsageStatsManager` 需要用户手动授权，不同用户口径会不一致，不采用。前台时长记录如需为明年准备，要单独开步骤实现。

## 期待的新实现情况

[DONE] `ProjectReport`、`StargazerSnapshot`、`UserReport`、`PersonalMarket` 的字段与本文一致，采集脚本、Kotlin 聚合与前端三处实现都以本文为准。
