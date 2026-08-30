# Theme Studio 设置试点

## 旧实现

普通 Theme Settings 只编辑 111 项已发布 `native_v1` 偏好，草稿、保存、重置和角色卡/群组目标切换均绑定 `ThemePreferenceValues`。高级样式契约已有值模型、链接器和组件部件校验，但没有存储、设置入口、运行时预览或生产消费点。

## 修改意图

新增独立 Theme Studio 路由，作为普通 Theme Settings 的作者级补充。首发只对 `operit.data_display.stat` 建立端到端闭环：

1. 按当前活动角色卡或群组持久化样式实例层
2. 生成与目标 `native_v1` 基础视觉一致的完整样式级联
3. 经现有 Style API v1 链接器校验后解析 `EDITOR_PREVIEW` 场景
4. 在设置页面使用同一部件和样式解析结果绘制统计预览
5. 编辑表面色、文字色、圆角/胶囊、内侧边框、不透明度、内边距和图标容器

普通 Theme Settings、111 项偏好、角色卡 JSON 导入导出、WebChat、Glance、ToolPkg 和主题包安装器不在本单元修改范围内。

## 存储边界

- 新建版本化 `theme_style_instances` Preferences DataStore
- 以 `ActivePrompt` 的角色卡或群组 ID 作为实例键
- 记录 Style API 版本、`native_v1` 定义 ID 和实例层 `NativeThemeStyleLayerV1`
- 基础层由运行时编译器重建，存储不复制完整样式级联
- 保存、重置、复制与删除复用主题目标操作协调器；目标已删除时，实例写入事务被拒绝
- 原始快照备份自然覆盖新 DataStore；角色卡和群组逻辑导入导出格式保持不变

## 设置体验

- Settings > Personalization 新增 Theme Studio 入口
- 页面进入时固定编辑当前活动目标；外部聊天切换不覆盖草稿或改变正在保存的实例
- 编辑区使用颜色选择、分段形状、开关和滑块，不使用自由 JSON 文本输入
- 预览区显示统计组件、图标容器、文字和值，并使用 `EDITOR_PREVIEW` 表面
- 保存、重置和离开提示遵循普通主题设置的目标级草稿语义；Reset 只恢复草稿，Save 才清除已保存实例
- 链接问题显示稳定错误码和受影响部件，非法实例不进入预览

## 运行时边界

- `NativeThemeNativeV1StyleCompilerV1` 仅产出 Stat 试点所需的完整样式层
- `NativeThemeStyledStatPreviewV1` 只实现试点实际生成的颜色、形状、内侧单层边框、不透明度、内边距和图标容器
- `EDITOR_PREVIEW` 使用明确的基线 Compose 能力档案；首发实例不生成背景采样、液态、水材质、内容模糊、内阴影或外侧边框
- 未登记能力的属性由链接器拒绝，预览不改写实例声明

## 验收

- 新建、保存、重开和重置 Theme Studio 实例均针对正确角色卡或群组
- 普通 Theme Settings 的 111 项字段、保存、重置、备份和目标切换行为不变
- Stat 已保存实例同时作用于 `MAIN` 的备份与聊天历史统计调用，Theme Studio 草稿通过同一 renderer 在 `EDITOR_PREVIEW` 显示；两个表面共享 `operit.data_display.stat@1.1` 部件契约
- 表面、文字、圆角/胶囊、边框、透明度、内边距和图标容器编辑立即反映在预览
- 无效层、未知部件、未允许属性和非公开宿主表面不会进入激活实例

## 后续批次

1. 将同一运行时扩展到 Section、Action Button、Choice Item、Operation Status 和 Navigation Drawer Item
2. 实现 `ThemeSurface` 的多层边框、阴影、模糊和完整材质
3. 增加组件族目录、状态编辑器、菜单与瞬态表面
4. 把 Theme Studio 预览从 Stat 试点扩展为冻结组件目录

## 当前进展

- [DONE] 新增 Settings > Personalization 的 Theme Studio 路由，并固定进入页面时的角色卡或群组目标
- [DONE] 新增版本化 `theme_style_instances` DataStore，记录 Style API `1.0`、`native_v1` 定义和实例层；原始快照备份自动覆盖该存储
- [DONE] 新增 Stat 的 `native_v1` 基线编译器、渲染计划校验和 `MAIN`/`EDITOR_PREVIEW` 双表面解析
- [DONE] Theme Studio 可编辑 Stat 的表面、数值与标签颜色、圆角/胶囊、内侧边框、不透明度、内边距和图标容器，并经同一 Stat renderer 实时预览
- [DONE] 已保存 Stat 实例作用于备份和聊天历史设置的实际 `MAIN` 调用；`native_v1` 字体族、字体缩放和原有排版指标继续从当前主题基线生成
- [DONE] 角色卡/群组新建、复制、删除及角色卡 JSON 导入均维护对应实例生命周期；保存与删除通过同一目标事务串行化
- [DONE] 补充 JVM 编译器/能力拒绝测试源码和 Android Compose 的 `MAIN`、`EDITOR_PREVIEW` Stat 语义测试源码

本单元已通过多轮静态审查和 `git diff --check`。按照仓库执行规则，未运行 Gradle、JVM 或 Android Compose 测试。
