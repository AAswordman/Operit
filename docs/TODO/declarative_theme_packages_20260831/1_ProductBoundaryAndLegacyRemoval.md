# 产品边界与旧实现清理

## 旧实现

`unified_theme_interface` 已在开发分支中建立 `native_v1` 偏好兼容层、主题编辑器、宿主解析器、组件契约和 Stat 专用 Theme Studio 试点。该实现以角色卡或群组为主题作用域，并把视觉设计建模为用户可调的字段与组件样式层。

## 修正意图

主题设计属于主题作者。普通用户不应在设置中调节单个组件的圆角、边框、颜色和材质。主题包必须能表达完整场景与异形 UI，而不只是覆写 Material 风格。

当前仅兼容开发基线，因此旧视觉主题数据不迁移。业务元数据与视觉字段必须分离，避免删除主题系统时损伤角色、群组、聊天、备份和导入逻辑。

## 必须删除

- `ThemeStudioScreen`、Theme Studio 路由、Settings 入口及全部本地化字符串
- `ThemeStyleInstancePreferences`、`theme_style_instances`、Stat 实例克隆和删除钩子
- Stat 专用编译器、运行时样式计划、预览 renderer 与试点测试
- `4e_ThemeStudioSettingsPilot.md` 及其仅服务于试点的验证记录
- 旧 Theme Settings 中的目标选择、目标级视觉草稿、目标级视觉保存、重置和素材暂存
- 已发布开发基线之外新增的目标级视觉字段、资源关系和对 `ActivePrompt` 的主题依赖

## 需要保留后重接

- 角色卡与群组的名称、头像、聊天标题和业务配置
- 主界面、浮窗、权限 Overlay、离屏导出、Glance 等宿主边界
- 背景媒体、图片、字体、九宫格、路径和 Compose 图层的可复用渲染能力
- 组件状态、受控插槽、封闭事件、语义和触控约束的建模思想
- 固定恢复与诊断界面使用独立、安全的内置 UI

## 删除原则

- 每个被删除的视觉路径必须同时删除其设置入口、持久化、读写、运行时消费、生命周期钩子、字符串、测试和文档
- 不保留兼容读取、旧主题回退或未引用的数据模型
- `stat`、`section`、`action.button` 等名称可保留为未来语义组件 ID；它们的旧 renderer 与样式编译器不构成新协议
- 一次迁移一个完整边界，避免主题来源在同一 UI 上并存

## 验收

- 应用不再出现 Theme Studio 或目标级视觉主题设置
- 角色卡、群组、聊天历史、导入导出和备份不再维护主题视觉数据
- 所有剩余视觉主题读取均来自新的全局主题上下文或固定安全 UI

## 已确认决策（2026-08-31）

1. **全局单主题**：主题选择全局唯一，不随角色卡/群组切换；旧目标级视觉数据按开发状态直接清理。
2. **基础设置保留范围**：19 个 A 类字段（主题模式、字体缩放、聊天样式、输入样式、消息展示开关组、气泡头像/宽布局、光标跟随主题）保留为全局基础设置，入口并入现有"全局显示设置"页；其余约 70 个纯视觉字段的设置项与运行时消费全部删除，等主题包接手。
3. **业务元数据搬前缀保数据**：AI 头像、聊天标题、用户头像的 API 签名不变，存储键从 `character_card_theme_{id}_`/`character_group_theme_{id}_` 搬到独立 metadata 前缀并一次性搬运存量数据；`customUserAvatarUri` 的 VISUAL 错标随迁移修正。
4. **WebChat 零兼容投入**：per-chat 主题语义不保留；bootstrap 字段改读全局设置；`GET /api/web/chats/{id}/theme` 路由改为返回同一份全局 payload；`resolveThemePreferenceSnapshot` per-chat 解析与 `ThemeColorSchemeResolver` 在旧链清除批次删除。

## 撤链批次

- **批次 A（编辑器删除）**：整删旧主题编辑器（shim + `screens/theme/` 13 文件 + `theme/editor/` 10 文件 + `ColorPickerDialog` + `ThemeSettingsBackgroundPreview` + `ThemeSettingsComponents` + 死代码 `ChatStyleOption`）及测试、路由、设置入口、死传参链、`EDITOR_PREVIEW` 表面、`commitThemeDraft`；清理 8 locale 编辑器专用字符串。运行时快照链本批不动。
- **批次 B（全局化）**：19 字段进全局展示设置；`resolveNativeThemeV1` 重写为全局解析（背景恒关、chrome 默认）；`OperitTheme`、浮窗/Overlay/离屏/Glance、小组件刷新、截图导出全部脱离 `activeThemePreferenceSnapshotFlow`；`LocalThemePreferenceSnapshot` 消亡，消费方回默认；WebChat bootstrap 改全局。
- **批次 C（清除与搬前缀）**：删 `UserPreferencesManager` per-target 主题 API 与死键常量、`ActivePromptManager` 主题流与 transition 协调、管理器主题生命周期调用、`ThemePreferenceSnapshot`/`ThemePreferenceValues`/`NativeThemePreferenceSchemaV1`/`ThemePreferenceLocals`/`ThemeColorSchemeResolver`；头像/标题搬 metadata 前缀；WebChat 主题管线改读全局。
