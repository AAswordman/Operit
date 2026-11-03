---
fork: https://github.com/3316891527/Operit
branch: refactor/model-domain-layout
status: implemented
---

# 持久化模型与业务领域归属整理

`data.model` 当前同时保存数据库实体、聊天存储结构、服务配置、工具协议、主题模型和运行时实现。此次按职责迁移，使该包保留持久化实体与必须稳定保留的数据结构，其余声明归入配置、工具、聊天、记忆、主题、角色和工作流对应目录。

## 实施边界

- 保持 Room 与 ObjectBox 实体的包名、字段、表名、关系、UID、数据库版本和 schema 文件不变；数据库转换器迁移只调整编译期引用
- 保留聊天消息、片段、历史及数据库查询结果等直接关联数据结构；保留现有 Parcelable 主题传输类，以及已分发脚本按完整类名访问的 FunctionType 与 InputProcessingState，单独迁出 Compose 转换函数
- 业务模型与实现按照实际依赖迁移，不以预估类数决定范围；原路径不增加转发包装或类型别名
- 为迁出的 Kotlin 序列化类显式固定原有 serial name，尤其保留工作流节点与嵌套参数值已有的多态类型标识；存储字段、枚举值、默认值和协议版本不变
- 同步主源码、JVM 和 Android 测试、通配导入、全限定名、混淆规则和有效文档引用；迁移清单记录每个声明的原位置与新位置

## 实施步骤

1. 完成逐文件归属清单，核对持久化、序列化和运行时引用
2. 迁移独立业务模型与实现，拆出混合文件中的角色交换模型、转换函数和运行状态
3. 复核声明内容、实体结构、schema、序列化标识与全部引用，补充工作流旧格式回归源码
4. 提交并推送当前分支，先派发 `assembleDebug`，再创建目标为个人仓库 `dev` 的 PR；不等待或轮询 CI

## 验证约定

本地只执行静态检查，不运行编译、构建或测试。现有完整 JVM 回归与新增旧格式用例交由仓库检查；Debug 构建派发受理与构建通过分开记录。设备回归覆盖配置、密钥池、角色导入导出、聊天与历史、记忆、工具、主题和工作流，结果单独填写，未执行不标记通过。

## 实际归属与兼容性

42 个文件整体迁出，另外拆出角色卡交换格式、主题转换函数和工具计时运行状态；原目录由 67 个 Kotlin 文件收敛至 25 个。完整对应关系见[迁移清单](migration.json)。清单中的 138 项为顶层声明名称，包含数据类、枚举、对象和函数，不等于 138 个实体类。

- 模型服务配置与参数归入 `data.config.model`；密钥相关模型与校验归入既有 `api.chat.keypool`；供应商身份与计费归入 `data.stats`
- 工具调用与结果归入 `core.tools.protocol`，提示词归入 `core.tools.prompt`；工作流定义与运行报告归入 `core.workflow.model`
- 聊天解析归入 `core.chat.protocol`，时间戳、引用与附件归入对应聊天目录；归档模型与序列化器归入 `data.chat.archive`，片段存储实现归入 `data.chat.serialization`
- 记忆配置、搜索与导出归入 `data.memory` 对应子包，队列统计归入既有记忆提取模块；角色交换模型归入 `data.character`，表情归入 `data.emoji`
- 主题协议与模型归入 `data.theme.script`，Compose 适配归入 `ui.theme.serialization`，ObjectBox 属性转换器归入 `data.db.converter`

迁出的 67 个序列化类型显式使用迁移前的 serial name。工作流继续读写原有 `__type`，包括五种节点和两种嵌套参数值；数据库版本、ObjectBox UID、实体字段和 schema 不变。默认值、字段名称、枚举值及原始实现逐项进行静态对照。

`FunctionType` 与 `InputProcessingState` 的原类由已有插件的 Java 桥接调用；Parcelable 主题类也有类身份要求，因此保留这些纯数据声明。原包不添加转发类或类型别名，迁出的声明只有一个实现位置。

## 验证资料

新增 `WorkflowModelCompatibilityTest` 两个 JVM 用例，分别覆盖旧工作流文件读取和继续输出旧类型标识，未在本地运行。现有全部测试仅更新导入和全限定名，回归范围及独立结果填写见[完整验证记录](verification.md)。

[DONE] 领域迁移、混合文件拆分、引用接续、原序列化名称固定与兼容回归源码已完成。静态检查、提交和工作流受理分别记录，设备与 CI 未完成项不标记通过。
