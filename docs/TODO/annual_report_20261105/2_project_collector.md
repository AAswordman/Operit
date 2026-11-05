# 2. 项目数据采集脚本

## 旧实现情况

仓库内没有采集脚本。本地仓库也不适合作为数据源：协作者的 remote 配置各不相同，本地分支可能包含尚未提交上游的改动，部分克隆也未必具备完整历史。

## 意图

在 `annual-report/collector/collect-project.ts` 用 TypeScript 编写采集脚本，类型直接来自前端的 `src/data/types.ts`。脚本靠 Node 的类型擦除直接运行，不需要编译。

数据全部取自线上，任何人用同一个快照时刻运行都能得到相同的结果：

- GitHub REST API：Commits、Releases、Stargazers、Search 与 `stats/code_frequency`
- 补丁仓库 `AAswordman/OperitNightlyRelease` 的 Releases
- 插件市场静态接口：`https://static.operit.app/market/v2/lists/type/<type>/updated/page-<n>.json`，按首页给出的总数算出页数逐页拉取，拉到的条数必须等于总数

运行方式：

```bash
GH_TOKEN=$(gh auth token) npm --prefix annual-report run collect:project -- --year 2026 --snapshot 2026-12-10T00:00:00+08:00
```

产物：

- `annual-report/data/project-<year>.json`：`ProjectReport`
- `annual-report/data/stargazers-<year>.json`：按 star 时间排序的 stargazer 名单，日期存为距离仓库创建日的天数

实现约束：

- 必须提供 `GH_TOKEN`，未认证的每小时 60 次额度不够拉完 Stargazers
- 只对传输层错误重试同一个请求；HTTP 错误、分页不完整或数据校验失败时直接退出并打印原因，不写出不完整的文件
- `stats/code_frequency` 在 GitHub 后台计算完成前返回 202，按文档轮询直到 200
- 机器人账号不计入贡献者；未关联 GitHub 账号的提交作者单独打印，供维护者核对
- 输出按固定顺序排序并格式化，重复运行产生的 diff 只反映数据变化

## 2026-11-05 试运行结果

快照时刻 2026-11-05 05:52，共 120 次 GitHub 请求：

- 非合并提交 696 个，8 月最多，提交最多的一天是 9 月 8 日，深夜提交 134 个
- 新增约 105 万行，删除约 52 万行
- 正式版 10 个，补丁版本 106 个
- 贡献者 25 位，其中 24 位今年首次提交；合并 PR 201 个，新建 Issue 654 个，关闭 542 个
- Stars 年初 2,699，快照时 9,246
- 市场今年新上架 1,524 个，原创 532 个，发布者 310 人，其中 300 人今年首次发布，累计下载约 105 万次

10 月提交只有 2 个，与本地 `upstream/main` 的统计一致：这段时间的工作还在 `dev` 上，发版同步后会进入 `main`。这也是快照要放在发版同步之后的原因。

## 期待的新实现情况

[DONE] 一条命令生成指定年份的快照，产物与 `ProjectReport` 类型一致，前端构建时直接导入。
