# Conversation 编辑器迁移

## 修改意图

将 Conversation 分类从旧的手写聊天设置迁移到 `native_v1` 定义驱动编辑器。当前版本尚未对外发布，因此迁移完成后删除旧的 Conversation UI 和重复预览实现，只保留可复用的存储、素材解析和编辑会话基础设施。

## 字段边界

### 视觉字段

- `chat_style`
- `bubble_show_avatar`
- `bubble_wide_layout_enabled`
- `cursor_user_bubble_follow_theme`
- `cursor_user_bubble_liquid_glass`
- `cursor_user_bubble_water_glass`
- `bubble_user_bubble_liquid_glass`
- `bubble_user_bubble_water_glass`
- `bubble_ai_bubble_liquid_glass`
- `bubble_ai_bubble_water_glass`
- `bubble_user_use_image`
- `bubble_ai_use_image`
- `bubble_user_rounded_corners_enabled`
- `bubble_ai_rounded_corners_enabled`
- `bubble_user_use_custom_font`
- `bubble_ai_use_custom_font`
- 所有 Conversation 颜色字段
- 所有气泡字体、图片裁剪、重复区间、缩放和内容内边距字段
- `custom_user_avatar_uri`
- `avatar_shape`
- `avatar_corner_radius`

### 目标元数据

- `custom_ai_avatar_uri`
- `custom_chat_title`

目标元数据仍由目标保存流程提交，视觉 Reset 不得清除目标元数据。全局用户身份继续由 `Profile & Identity` 管理。

## 实施顺序

1. 扩展编辑器文本、文本输入和资源动作契约。
2. 新增 `NativeThemeEditorConversationDefinitionV1`，登记稳定分区、分组、控件、选项、可见条件、Advanced 分类和字段覆盖。
3. 迁移气泡字体、普通图片裁剪、`.9.png` 参数读取、头像裁剪和暂存资源操作。
4. 使用通用平面分组渲染器替换旧聊天样式和头像 Composable。
5. 将聊天预览切换到真实生产消息组件的隔离草稿宿主，删除手写聊天预览。
6. 删除旧 Conversation 设置入口、重复回调和无引用的视觉辅助函数。
7. 补充定义完整性、可见条件、资产动作、目标元数据和 Reset 语义测试。

## 不在本批次

- 主聊天运行时从 `ResolvedNativeThemeV1` 读取完整 Conversation 结果
- 悬浮窗、Canvas、Glance 和导出适配器
- 独立主题包和 Theme Studio
- 已发布外部 API 的兼容层

## 验收标准

- Conversation 的视觉字段只有一个定义驱动编辑入口。
- Cursor 和 Bubble 的条件控件与旧字段语义一致。
- 气泡图片和字体选择仍通过编辑会话登记、保存和清理暂存资源。
- AI 头像和聊天标题可编辑，视觉 Reset 保留两者。
- 草稿预览不改变活动聊天和已提交主题。
- 旧 Conversation UI 和手写预览无生产引用。
- 静态检查通过；新增测试覆盖定义字段、条件显示、资源动作和元数据边界。

[DONE]
