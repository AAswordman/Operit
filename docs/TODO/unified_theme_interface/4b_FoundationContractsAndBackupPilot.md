# 基础组件契约与备份设置试点

## 兼容边界

本单元继续以 `upstream/main@f323d6c50fa661837fad06d4618462861779b562` 为对照基线。组件契约与主题包接口尚未对外发布，因此新增契约属于内部草案，不保留被替代的内部 Composable。

- 保留备份页面、对话框、标题、说明、图标、顺序、可见条件和现有视觉层级
- 保留导入、导出、删除、恢复、扫描、文件选择、确认和错误处理流程
- 保留 `ChatHistoryOperation` 等业务状态、持久化格式、目录规则和本地化资源
- 不修改主题偏好字段、角色卡与群组作用域、应用路由或已发布 Android 接口

## 修改意图

在导航项试点之后，为操作、输入、容器、反馈和数据展示分别建立首个基础契约。备份设置作为单一生产试点，只把原始值、受控插槽和封闭事件交给主题，业务枚举、领域对象和动作执行继续由宿主持有。

契约元数据同时增加可选状态字段、有限枚举值、分区标题、状态消息和展示值描述。目录可以据此检查基础组件边界，不依赖 Kotlin 运行时类型名称推断字段语义。

## 契约草案

### `operit.action.button@1.0`

- 状态：`label`、`enabled`、`emphasis`
- 枚举：`emphasis` 为 `standard`、`caution` 或 `destructive`
- 事件：`activate`
- 必需插槽：`leading`
- 场景：正常、禁用、警告、破坏性操作
- 语义：操作按钮、可访问标签、禁用态和至少 48dp 触控区域

### `operit.input.choice_item@1.0`

- 状态：`label`、可选 `supporting_text`、`selected`、`enabled`
- 事件：`select`
- 场景：正常、选中、禁用、长说明
- 语义：单选项、可访问标签、选择态、禁用态和至少 48dp 触控区域

### `operit.container.section@1.0`

- 状态：`title`、`description`
- 必需插槽：`leading`、`content`
- 场景：正常
- 语义：标题与内容区域；宿主内容保持受控，不向主题暴露业务对象

### `operit.feedback.operation_status@1.0`

- 状态：可选 `title`、`message`、`kind`
- 枚举：`kind` 为 `loading`、`success` 或 `error`
- 可选插槽：`leading`
- 场景：成功、加载、错误
- 语义：礼貌播报状态；加载态提供不确定进度语义

### `operit.data_display.stat@1.1`

- 状态：`label`、`value`
- 必需插槽：`leading`
- 场景：正常
- 语义：合并标签与展示值，图标不重复朗读

## 最小范围

- 扩展纯契约模型和校验，不加入 Android 类型、业务对象或任意动作回调
- 将五个必需契约登记到 `NativeThemeComponentContractsV1` 和 `NativeThemeComponentCatalogV1`
- 迁移备份管理按钮及其标准、警告、破坏性和禁用状态
- 将备份策略、导入格式、导出格式和配置空间单选行收敛为同一单选项实现
- 迁移四个管理卡及 Room DB、原始快照两个分区容器
- 合并备份操作加载、成功、错误反馈，以及两处重复统计项
- 删除 `ManagementButton`、`StrategyOption`、`ImportFormatOption`、`FormatOption`、`OperationProgressView`、`OperationResultCard`、共享 `SectionHeader` 和两处 `StatChip`

## 不在本单元

- 文本框、开关、滑块、多选项、图标按钮、对话框或列表契约
- FAQ、备份文件统计、Room DB 文件项或聊天历史领域组件迁移
- 备份业务状态收敛、文件 I/O 重构或本地化文案修改
- Theme Studio、独立样式包、作者 SDK 或外部 API 冻结

## 验收标准

- 每个基础类别至少有一个稳定 ID、类型化 Key 和 `native_v1` 实现；`operit.data_display.stat` 在 Theme Studio 试点中升至 `1.1`，新增 `EDITOR_PREVIEW` 宿主表面
- 有限枚举字段拒绝空值域和重复值；非枚举字段拒绝携带枚举值
- 目录覆盖五个新契约的全部声明状态和语义角色
- 操作按钮与单选项在禁用时不派发事件，并保持至少 48dp 触控区域
- 单选项只暴露一个可点击的 RadioButton 语义节点，宿主列表声明单选组
- 分区标题、状态播报、加载进度及统计标签和值具有明确 Compose 语义
- 备份页面继续执行原有回调，且被替代的重复实现及无用领域参数全部删除
- JVM 与 Android Compose 测试覆盖契约、目录、事件、插槽和语义；静态检查通过

## 实施记录

- `NativeThemeComponentKeyV1` 通过非反射状态编码器将类型化状态映射到契约值；目录校验必需与可选字段、值类型、有限枚举、语义角色和未知字段
- 目录场景改用可组合状态标签，校验正常态互斥、选择与禁用字段、动态状态枚举映射、枚举值域完整覆盖及可选字段有值与无值场景
- 语义元数据登记语义角色到平台可访问角色的映射、标题、状态消息、展示值、礼貌播报、不确定进度和装饰插槽
- `native_v1` 登记 `operit.action.button@1.0`、`operit.input.choice_item@1.0`、`operit.container.section@1.0`、`operit.feedback.operation_status@1.0` 和 `operit.data_display.stat@1.1`
- 操作按钮和单选项在内置渲染器与宿主事件适配器两层执行禁用约束；单选项整行只保留一个 RadioButton 动作语义并维持原 72dp 内容高度
- 四个备份管理卡、Room DB 和原始快照分区已接入基础目录；策略、格式、配置空间、操作状态和两处统计展示已迁移
- 旧管理按钮、三套格式单选项、操作进度与结果、共享分区标题及两处统计项实现已删除，业务枚举、回调、可见条件和文件流程未改变
- JVM 测试覆盖契约冻结、状态编码、目录状态、版本、枚举、可选字段和禁用事件；Android Compose 测试覆盖角色、触控、标题、状态播报、进度和受控插槽
- 三轮静态复审与最终符号审查无剩余发现，`git diff --check` 已通过；按仓库执行准则未在本机运行 Gradle、JVM 或 Android Compose 测试

[DONE]
