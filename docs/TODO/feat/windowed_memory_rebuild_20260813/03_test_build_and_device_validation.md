# 测试与设备验证

验收：

- 窗口规划器覆盖空消息、摘要排除、窗口边界和多窗口场景
- 记忆图谱协议覆盖具名对象、无 `main` 的独立操作、旧位置数组和缺失必填字段
- Android JVM 单元测试通过
- Debug APK 成功构建、安装到已连接设备，并能进入聊天记录管理页面

验证记录：

- `ChatMemoryWindowPlannerTest` 覆盖空消息、摘要排除、窗口边界、导入聊天的 `assistant` 发送者和密集总结场景。其中 2,000 回合且每回合都有总结的会话仍只按 48 条原始消息切分为 84 个窗口，不会按总结数发起请求
- `:app:testDebugUnitTest --tests "com.ai.assistance.operit.api.chat.library.ChatMemoryWindowPlannerTest" :app:assembleDebug` 成功，最终 Debug APK 已生成
- 已覆盖安装到设备 `V2055A` 的隔离包 `com.ai.assistance.operit.debug`，安装更新时间为 2026-08-13 08:33:11。正式包 `com.ai.assistance.operit` 未修改
- 隔离包此前残留的 Room 21 数据无法由当前 Room 20 直接打开，已仅清除隔离包数据后继续验证；未修改数据库迁移逻辑
- 已进入“设置 - 聊天记录管理”。空聊天列表也会显示“分窗口构建记忆库”区域，默认窗口为 32 条消息，窗口大小下拉项为 16、24、32、48 条消息，且“为已选 0 条聊天构建记忆”按钮禁用
- 隔离包没有可选择的聊天记录，因此未能在设备上打开二次确认框。没有发起模型请求，也没有写入记忆库
- 已新增 `MemoryAnalysisProtocolTest`；本轮未运行 Gradle 测试或构建，遵循 `AGENTS.md` 的默认约束
- 最新设备日志显示长窗口曾因助手思考和重复历史放大到 8,198/14,273 输入 token，且短问题检索命中无关候选；已移除思考文本并把有限窗口上下文纳入候选检索，本轮未运行 Gradle 测试或构建
