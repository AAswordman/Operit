# 3. 用户数据聚合

## 旧实现情况

- `TokenStatsQueryService` 提供范围汇总、模型规范化、花费计算和每日活跃度；但 `rangeData` 遇到跨度超过两天的范围会按天分桶，一年的范围要逐桶查询三百多次
- `ChatDao` 已有按角色卡汇总的查询，不带时间窗口
- `messages` 只有 `chatId` 和 `chatId, timestamp` 两个索引，按时间范围扫全表时用不上索引
- 工具调用只存在于 AI 消息正文的工具标签里，`ChatMarkupRegex` 已有标签与工具名的匹配规则
- 超大消息必须经 `ChatContentDao` 分段读取，直接 `SELECT content` 会触发 `SQLiteBlobTooBigException`
- 角色卡的 `createdAt` 在缺少记录时会被当前时间代替，不能直接用来判断"今年新建"
- 市场的 `getPublisherEntries` 在作者不在发布者分片里时会抛异常，而这种情况的含义其实是"还没发布过作品"

## 意图

新增 `data/annualreport/`：

- `AnnualReportDao`：只读聚合查询，注册到 `AppDatabase`；只新增 DAO，不改表结构，不需要数据库迁移
- `AnnualReportCollector`：按契约组装 `UserReport`，另提供 `collectPersonalMarket`
- `AnnualReportModels`：`@Serializable` 数据类，与前端类型同名

配套的小改动：

- `TokenStatsQueryService.annualSummary`：复用模型规范化和花费计算，只做全年、按月、按日三类汇总
- `CharacterCardManager.getCharacterCardCreatedAtRecords`：读取创建时间的原始记录，没有记录的角色卡为 null
- `MarketStatsApiService.findPublisherEntries`：分片文件或分片里没有这个作者时返回空列表，网络或解析失败仍然失败

取数方式：

- 对话与消息：在 SQL 内用 `COUNT`、`GROUP BY` 与 `strftime(..., 'localtime')` 聚合，不把正文读进内存；年度第一句话只取正文前 600 个字符再清理
- 工具调用：先在 SQL 内用 `LIKE` 筛出含工具标签的回复和变体，再经 `ChatContentDao` 逐条读取正文，用 `ChatMarkupRegex.namePattern` 计数
- 记忆：遍历 `UserPreferencesManager.memorySpaceListFlow` 的每个记忆空间，在 ObjectBox 里按 `createdAt` 计数
- 安装时间：`PackageManager.getPackageInfo` 返回的 `firstInstallTime`
- GitHub 身份：`GitHubAuthPreferences.getCurrentUserInfo`

约束：

- 全程只读，运行在 `Dispatchers.IO`
- 任一数据源读取失败，整份报告生成失败，异常原因交给前端的错误页展示，不输出缺字段的报告
- 是否给 `messages` 新增时间索引，先在大库上测量再决定；新增索引需要数据库迁移，届时单独开步骤

## 期待的新实现情况

[DONE] `AnnualReportCollector.collect(year)` 返回完整的 `UserReport`；token 部分与 Token 统计页在同一日期范围内一致。编译已在发布版构建中验证过，数据比对的验收范围见 [验证](7_verification.md)。
