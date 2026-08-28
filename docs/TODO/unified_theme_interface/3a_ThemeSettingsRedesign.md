# 主题设置重构

## 问题基线

现有五个标签分别承载 11、8、68、5 和 19 个设置。聊天样式主 Composable 接近千行并包含 65 个参数，基础与界面重复读取同一批字段，聊天页还携带被隐藏的输入设置分支。普通视觉设置、目标身份、全局身份、开发级九宫格参数和不一致的预览混在同一长滚动页面中。

## 产品决策

- 选择角色或群组只改变编辑目标，不切换全局活动会话
- 普通 Theme Settings 与作者 Theme Studio 分离
- 手机使用分类列表进入详情，宽屏使用导航、编辑和预览三栏
- 全局用户名和头像迁入 `Profile & Identity`
- 角色与群组名称、头像保留在各自编辑器
- 主题设置只保留目标级用户头像视觉覆盖、头像形状和显示规则
- Save、Reset、脏状态和错误固定在命令栏，不再位于长列表底部
- 设置区域使用平面分组、设置行和分隔线，不使用嵌套卡片
- 草稿只影响隔离预览，编辑器外壳继续使用活动目标已提交的主题

## 普通设置分类

1. Themes
2. Colors & Mode
3. Typography
4. Background
5. Conversation
6. Composer
7. App Chrome
8. Message Details & Motion

每个分类通过声明式条件展示相关控件。气泡图片切片、独立消息字体、精确内边距、图标颜色和诊断指标进入分类内的 Advanced 分组。组件契约、包资源、状态矩阵、性能和无障碍诊断只进入 Theme Studio。

## 代码边界

- `theme.storage`：V1 字段、快照与持久化兼容
- `theme.editor.contract`：稳定 ID、文本键、分类、控件、谓词、动作和校验
- `theme.editor.state`：文档 reducer、草稿、脏状态、重置、保存和素材会话
- `theme.editor.port`：目标、素材、颜色历史和预览环境端口
- `ui.features.settings.theme`：响应式壳、通用控件和状态渲染
- `ui.features.settings.theme.android`：资源文本、Activity Result、裁剪、字体和颜色选择器
- `ui.features.settings.theme.preview`：隔离主题宿主与生产组件场景

这些边界先在 `:app` 内建立，旧实现删除完成后再评估独立模块。

## 迁移顺序

1. 建立纯编辑文档 reducer 和 Android 无关的定义模型
2. 迁移 Composer 试点并改为平面分组 UI
3. 迁移 Colors & Mode、Typography 和 Background
4. 迁移 Conversation、App Chrome 和 Message Details & Motion
5. 移走全局身份控件，并让主题保存只提交视觉字段
6. 所有字段进入定义后替换五标签，接入响应式分类壳和固定命令栏
7. 提取系统栏与背景媒体宿主，接入真实隔离预览
8. 建立 Theme Studio 和组件目录

一个字段组迁移完成时，同一改动删除对应旧读取、回调和渲染分支。已发布的 111 个字段、默认值、作用域前缀、素材格式和重置语义保持不变。

## 当前实施批次

- [DONE] 将 `ThemeEditorSession` 的文档状态改为单一 `StateFlow` 驱动的纯 reducer
- [DONE] 编辑定义改用稳定文本键、通用谓词、有序分组和 Advanced 元数据
- [DONE] Composer 试点使用通用平面分组渲染器
- [DONE] 分段选项在窄宽度和大字体环境使用单选列表布局
- [DONE] 保持现有保存、重置、目标切换和素材会话行为，并修复保存快照与 Reset 元数据竞态
- [DONE] 增加 reducer、定义完整性、发布选项字面量和 Composer Compose UI 契约测试
- [DONE] 修正葡萄牙语折叠状态的错误翻译
- [DONE] Colors & Mode 使用独立分类定义，旧 BASIC 模式与配色实现已删除
- [DONE] 颜色目标覆盖全部 13 个已发布整数颜色字段，目录校验 ID、选项和字段覆盖
- [DONE] 颜色对话框由 13 路可空回调收敛为单颜色值，并增加精确 ARGB Android 测试
- [DONE] 删除旧 BASIC 手工预览及其八种语言的无引用资源
- [DONE] Typography 使用独立分类定义，素材动作与字段绑定，旧字体 Section 已删除
- [DONE] 通用渲染器支持可校验滑块、响应式素材操作和控件无障碍语义
- [DONE] Background 使用定义驱动设置与独立媒体预览，保留草稿覆盖和滑块提交策略
- [DONE] 主题设置目标选择与活动会话解耦，保存目标不会切换当前聊天
- [DONE] 五标签外壳替换为分类导航，手机列表/详情与宽屏导航/编辑响应式布局已接入
- [DONE] Save/Reset 从滚动内容移至固定命令区，分类切换会重置内容滚动位置
- [DONE] 目标读取加入代次校验、重试错误态和禁用期间关闭选择菜单
- [DONE] 全局身份控件迁入 Profile & Identity，主题页删除全局身份编辑入口
- [DONE] Message Details & Motion 从 Conversation 中拆出，诊断项集中到 Advanced 分组
- [DONE] 角色和群组的目标头像、聊天标题覆盖进入目标编辑器，角色/群组编辑器继续同步名称与 `custom_chat_title`
- [DONE] App Chrome 使用统一定义和通用控件渲染，旧手写界面 Section 已删除
- [DONE] 聊天与背景预览使用编辑草稿解析出的隔离 Material 主题，包含明暗模式、动态配色和字体缩放

本批次已经通过多轮独立静态审查和 `git diff --check`。按照仓库执行准则，未执行新增单元测试、Android Compose 测试或构建。
