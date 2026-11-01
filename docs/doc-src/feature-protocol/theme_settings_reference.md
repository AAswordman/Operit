# 主题设置只读参数目录

本目录与 [制作手册](theme_authoring_guide.md) 配套，依据 `3005a7d7` 的主题快照、默认值、玻璃参数与分享白名单核对。它列出当前主题设置中可供脚本读取的已知键，不给 JS 增加写配置能力。

`context.settings` 分为 `strings/booleans/ints/floats` 四张递归冻结的映射。下面的键写在对应映射中读取，例如 `context.settings.strings.input_style`；不要把这些键直接加进 `theme.json`。改变这些选项使用原主题页，脚本只根据它们计算并返回 Appearance。

默认值是重置主题时的原始默认值，当前角色或草稿可能已有不同值。标记“未设置”的键在 JS 中可能是 `undefined`，不保证存在；读取前判断是否存在。传统主题完整包的 `config` 对这些可选值使用 JSON `null`，不等于脚本上下文也会包含 null。

宿主按主题快照传入映射，未将所有类型再次裁剪为分享白名单；本目录覆盖已定义的主题分享字段，不能据此推断任何未列出的业务数据也可读。所有 `_uri`、`_path` 后缀的字符串和 `custom_chat_title` 在宿主处被明确排除。

## strings：枚举与字体来源

| 键 | 默认值 | 有效值 | 含义 |
| --- | --- | --- | --- |
| `avatar_shape` | `"circle"` | `circle/square` | 原有头像形状 |
| `background_media_type` | `"image"` | `image/video` | 原主题媒体背景类型 |
| `bubble_ai_font_type` | `"system"` | `system/file` | 助手消息原有字体来源 |
| `bubble_ai_system_font_name` | `"default"` | `default/serif/sans-serif/monospace/cursive` | 助手消息原有系统字体 |
| `bubble_image_render_mode` | `"tiled_nine_slice"` | `tiled_nine_slice/nine_patch` | 原有图片气泡绘制模式，与 Layer.type 的 nineSlice 分开配置 |
| `bubble_user_font_type` | `"system"` | `system/file` | 发送方消息原有字体来源 |
| `bubble_user_system_font_name` | `"default"` | `default/serif/sans-serif/monospace/cursive` | 发送方消息原有系统字体 |
| `chat_style` | `"cursor"` | `cursor/bubble` | 兼容单一消息风格字段；分侧风格分别读 user_chat_style 和 ai_chat_style |
| `user_chat_style` | `"cursor"` | `cursor/bubble` | 发送方消息风格 |
| `ai_chat_style` | `"cursor"` | `cursor/bubble` | 助手消息风格 |
| `font_type` | `"system"` | `system/file` | 原主题全局字体来源 |
| `input_style` | `"agent"` | `classic/agent` | 原生输入容器风格 |
| `on_color_mode` | `"auto"` | `auto/light/dark` | 原有前景明暗选择 |
| `system_font_name` | `"default"` | `default/serif/sans-serif/monospace/cursive` | 原主题全局系统字体 |
| `theme_mode` | `"light"` | `light/dark` | 手动主题模式；跟随系统时应读取 context.mode 判断实际模式 |

## booleans：原主题开关

全部为布尔值。显示名称、统计等开关可以作为外观条件读取，但 JS 不因此得到这些名称或统计的实际内容。玻璃区域独立参数开关关闭时，原生玻璃效果读取全局参数；脚本映射中仍可存在各区域的数值。

| 键 | 默认值 | 含义 |
| --- | --- | --- |
| `bubble_ai_bubble_liquid_glass` | `false` | Bubble 助手液态玻璃 |
| `bubble_ai_bubble_water_glass` | `false` | Bubble 助手水玻璃 |
| `bubble_ai_rounded_corners_enabled` | `true` | 助手 Bubble 圆角开关 |
| `bubble_ai_use_custom_font` | `false` | 启用助手消息自定义字体 |
| `bubble_ai_use_image` | `false` | 助手使用原有图片气泡 |
| `bubble_rounded_corners_enabled` | `true` | 发送方 Bubble 圆角开关 |
| `bubble_show_avatar` | `true` | 显示消息头像 |
| `bubble_user_bubble_liquid_glass` | `false` | Bubble 发送方液态玻璃 |
| `bubble_user_bubble_water_glass` | `false` | Bubble 发送方水玻璃 |
| `bubble_user_use_custom_font` | `false` | 启用发送方消息自定义字体 |
| `bubble_user_use_image` | `false` | 发送方使用原有图片气泡 |
| `bubble_wide_layout_enabled` | `false` | 启用 Bubble 宽布局 |
| `chat_header_overlay_mode` | `false` | 聊天标题覆盖模式 |
| `chat_header_transparent` | `false` | 原聊天标题透明背景选择 |
| `chat_input_floating` | `false` | 输入容器悬浮 |
| `chat_input_liquid_glass` | `false` | 输入容器液态玻璃 |
| `chat_input_transparent` | `false` | 原输入背景透明开关；有显式 chat_input_opacity 时按数值计算 |
| `chat_input_water_glass` | `false` | 输入容器水玻璃 |
| `cursor_user_bubble_follow_theme` | `true` | Cursor 发送方背景跟随主题 |
| `cursor_user_bubble_liquid_glass` | `false` | Cursor 发送方液态玻璃 |
| `cursor_user_bubble_water_glass` | `false` | Cursor 发送方水玻璃 |
| `glass_ai_message_custom_parameters` | `false` | 助手消息使用独立玻璃参数 |
| `glass_conversation_list` | `false` | 对话列表玻璃开关 |
| `glass_conversation_region_custom_parameters` | `false` | 对话列表区域使用独立玻璃参数 |
| `glass_input_custom_parameters` | `false` | 输入区使用独立玻璃参数 |
| `glass_package_cards` | `false` | 工具包卡片玻璃开关 |
| `glass_package_region_custom_parameters` | `false` | 工具包卡片区域使用独立玻璃参数 |
| `glass_settings_cards` | `false` | 设置卡片玻璃开关 |
| `glass_settings_region_custom_parameters` | `false` | 设置卡片区域使用独立玻璃参数 |
| `glass_sidebar_buttons_custom_parameters` | `false` | 侧栏按钮使用独立玻璃参数 |
| `glass_sidebar_custom_parameters` | `false` | 侧栏使用独立玻璃参数 |
| `glass_user_message_custom_parameters` | `false` | 发送方消息使用独立玻璃参数 |
| `navigation_drawer_button_liquid_glass` | `false` | 侧栏按钮液态玻璃 |
| `navigation_drawer_water_glass` | `false` | 侧栏水玻璃 |
| `show_chat_floating_dots_animation` | `true` | 显示聊天浮点动画 |
| `show_input_processing_status` | `true` | 显示输入处理状态 |
| `show_message_timestamp` | `false` | 显示消息时间 |
| `show_message_timing_stats` | `false` | 显示消息耗时 |
| `show_message_token_speed` | `false` | 显示 token 速度 |
| `show_message_token_stats` | `false` | 显示消息 token 统计 |
| `show_model_name` | `false` | 显示模型名称 |
| `show_model_provider` | `false` | 显示模型提供方 |
| `show_role_name` | `true` | 显示角色名称 |
| `show_status_tags` | `true` | 显示状态标签 |
| `show_thinking_process` | `true` | 显示思考过程 |
| `show_user_name` | `true` | 显示发送方名称 |
| `use_background_blur` | `false` | 启用媒体背景模糊 |
| `use_background_image` | `false` | 启用原主题媒体背景 |
| `use_custom_colors` | `false` | 启用原有自定义主次色 |
| `use_custom_font` | `false` | 启用原主题自定义字体 |
| `use_custom_navigation_drawer_accent_color` | `false` | 侧栏使用自定义强调色 |
| `use_custom_navigation_drawer_background_color` | `false` | 侧栏使用自定义背景色 |
| `use_system_theme` | `true` | 跟随系统浅深模式 |
| `video_background_loop` | `true` | 背景视频循环 |
| `video_background_muted` | `true` | 背景视频静音 |

## ints：原主题整数颜色

这些键均为 Android 32 位有符号 ARGB 整数；`thinking_particle_color` 默认 `-1`，对应 `#FFFFFFFF`，其他键默认未设置。颜色是否启用还受相应自定义颜色开关和原生入口控制。

| 键 | 默认值 | 含义 |
| --- | --- | --- |
| `bubble_ai_bubble_color` | 未设置 | Bubble 助手背景色 |
| `bubble_ai_text_color` | 未设置 | 助手消息文字色 |
| `bubble_user_bubble_color` | 未设置 | Bubble 发送方背景色 |
| `bubble_user_text_color` | 未设置 | 发送方消息文字色 |
| `cursor_user_bubble_color` | 未设置 | Cursor 发送方背景色 |
| `custom_navigation_drawer_accent_color` | 未设置 | 自定义侧栏强调色 |
| `custom_navigation_drawer_background_color` | 未设置 | 自定义侧栏背景色 |
| `custom_primary_color` | 未设置 | 原有自定义主色 |
| `custom_secondary_color` | 未设置 | 原有自定义次色 |
| `package_add_button_color` | 未设置 | 添加工具包按钮颜色 |
| `package_market_button_color` | 未设置 | 工具包市场按钮颜色 |
| `thinking_particle_color` | `-1`，白色 | 思考粒子颜色 |

需要转换已存在的整数颜色时，可用下面的纯函数。先判断目标键存在，再调用它；不要把缺键转换成颜色。

```js
function argbIntToColor(value) {
  return "#" + (value >>> 0).toString(16).padStart(8, "0");
}
```

## floats：尺寸、比例与透明度

下表范围来自传统主题分享校验，不是 Appearance 对象的可写字段范围。例如原主题 `font_scale` 是 `0.8..1.5`，Appearance 的 `typography.scale` 是 `0.5..2`。所有数值必须有限。

| 键 | 默认值 | 分享合法范围 | 含义 |
| --- | --- | --- | --- |
| `avatar_corner_radius` | `8` | `0..16` | 原有头像圆角半径，dp |
| `background_blur_radius` | `10` | `0..30` | 原有媒体背景模糊半径，dp |
| `background_image_opacity` | `0.3` | `0..1` | 媒体背景及 background 装饰使用的不透明度 |
| `bubble_ai_bubble_opacity` | 未设置 | `0..1` | Bubble 助手背景不透明度；未设置时原有值为 1 |
| `bubble_ai_content_padding_left` | `12` | `0..96` | 助手左侧原有气泡正文内距，dp；与 Surface.padding 叠加 |
| `bubble_ai_content_padding_right` | `12` | `0..96` | 助手右侧原有气泡正文内距，dp；与 Surface.padding 叠加 |
| `bubble_ai_image_crop_bottom` | `0` | `0..0.45` | 助手原有气泡图下侧裁切比例 |
| `bubble_ai_image_crop_left` | `0` | `0..0.45` | 助手原有气泡图左侧裁切比例 |
| `bubble_ai_image_crop_right` | `0` | `0..0.45` | 助手原有气泡图右侧裁切比例 |
| `bubble_ai_image_crop_top` | `0` | `0..0.45` | 助手原有气泡图上侧裁切比例 |
| `bubble_ai_image_repeat_end` | `0.65` | `0..1` | 助手原有气泡图横向重复终点；起点必须小于终点 |
| `bubble_ai_image_repeat_start` | `0.35` | `0..1` | 助手原有气泡图横向重复起点；起点必须小于终点 |
| `bubble_ai_image_repeat_y_end` | `0.65` | `0..1` | 助手原有气泡图纵向重复终点；起点必须小于终点 |
| `bubble_ai_image_repeat_y_start` | `0.35` | `0..1` | 助手原有气泡图纵向重复起点；起点必须小于终点 |
| `bubble_ai_image_scale` | `1` | `0.2..3` | 助手原有气泡图缩放比例 |
| `bubble_content_padding_left` | `12` | `0..96` | 发送方左侧原有气泡正文内距，dp；与 Surface.padding 叠加 |
| `bubble_content_padding_right` | `12` | `0..96` | 发送方右侧原有气泡正文内距，dp；与 Surface.padding 叠加 |
| `bubble_user_bubble_opacity` | 未设置 | `0..1` | Bubble 发送方背景不透明度；未设置时原有值为 1 |
| `bubble_user_image_crop_bottom` | `0` | `0..0.45` | 发送方原有气泡图下侧裁切比例 |
| `bubble_user_image_crop_left` | `0` | `0..0.45` | 发送方原有气泡图左侧裁切比例 |
| `bubble_user_image_crop_right` | `0` | `0..0.45` | 发送方原有气泡图右侧裁切比例 |
| `bubble_user_image_crop_top` | `0` | `0..0.45` | 发送方原有气泡图上侧裁切比例 |
| `bubble_user_image_repeat_end` | `0.65` | `0..1` | 发送方原有气泡图横向重复终点；起点必须小于终点 |
| `bubble_user_image_repeat_start` | `0.35` | `0..1` | 发送方原有气泡图横向重复起点；起点必须小于终点 |
| `bubble_user_image_repeat_y_end` | `0.65` | `0..1` | 发送方原有气泡图纵向重复终点；起点必须小于终点 |
| `bubble_user_image_repeat_y_start` | `0.35` | `0..1` | 发送方原有气泡图纵向重复起点；起点必须小于终点 |
| `bubble_user_image_scale` | `1` | `0.2..3` | 发送方原有气泡图缩放比例 |
| `chat_input_opacity` | 未设置 | `0..1` | 输入容器背景不透明度；未设置时按 chat_input_transparent 解释 |
| `cursor_ai_bubble_opacity` | 未设置 | `0..1` | Cursor 助手背景不透明度；未设置时原有值为 0 |
| `cursor_user_bubble_opacity` | 未设置 | `0..1` | Cursor 发送方背景不透明度；未设置时原有值为 1 |
| `font_scale` | `1` | `0.8..1.5` | 原主题字号比例，与 Appearance 排版 scale 范围不同 |
| `glass_ai_message_blur_radius` | `12` | `0..40` | 助手消息模糊半径，dp |
| `glass_ai_message_edge_width` | `0.4` | `0..2` | 助手消息边缘宽度，dp |
| `glass_ai_message_highlight_alpha` | `0.18` | `0..0.8` | 助手消息高光不透明度 |
| `glass_ai_message_shadow_radius` | `4` | `0..20` | 助手消息阴影半径，dp |
| `glass_ai_message_tint_opacity` | `0.24` | `0..1` | 助手消息染色不透明度 |
| `glass_blur_radius` | `12` | `0..40` | 全局玻璃模糊半径，dp |
| `glass_conversation_region_blur_radius` | `12` | `0..40` | 对话列表区域模糊半径，dp |
| `glass_conversation_region_edge_width` | `0.4` | `0..2` | 对话列表区域边缘宽度，dp |
| `glass_conversation_region_highlight_alpha` | `0.18` | `0..0.8` | 对话列表区域高光不透明度 |
| `glass_conversation_region_shadow_radius` | `4` | `0..20` | 对话列表区域阴影半径，dp |
| `glass_conversation_region_tint_opacity` | `0.24` | `0..1` | 对话列表区域染色不透明度 |
| `glass_edge_width` | `0.4` | `0..2` | 全局玻璃边缘宽度，dp |
| `glass_highlight_alpha` | `0.18` | `0..0.8` | 全局玻璃高光不透明度 |
| `glass_input_blur_radius` | `12` | `0..40` | 输入区模糊半径，dp |
| `glass_input_edge_width` | `0.4` | `0..2` | 输入区边缘宽度，dp |
| `glass_input_highlight_alpha` | `0.18` | `0..0.8` | 输入区高光不透明度 |
| `glass_input_shadow_radius` | `4` | `0..20` | 输入区阴影半径，dp |
| `glass_input_tint_opacity` | `0.24` | `0..1` | 输入区染色不透明度 |
| `glass_package_region_blur_radius` | `12` | `0..40` | 工具包卡片区域模糊半径，dp |
| `glass_package_region_edge_width` | `0.4` | `0..2` | 工具包卡片区域边缘宽度，dp |
| `glass_package_region_highlight_alpha` | `0.18` | `0..0.8` | 工具包卡片区域高光不透明度 |
| `glass_package_region_shadow_radius` | `4` | `0..20` | 工具包卡片区域阴影半径，dp |
| `glass_package_region_tint_opacity` | `0.24` | `0..1` | 工具包卡片区域染色不透明度 |
| `glass_settings_region_blur_radius` | `12` | `0..40` | 设置卡片区域模糊半径，dp |
| `glass_settings_region_edge_width` | `0.4` | `0..2` | 设置卡片区域边缘宽度，dp |
| `glass_settings_region_highlight_alpha` | `0.18` | `0..0.8` | 设置卡片区域高光不透明度 |
| `glass_settings_region_shadow_radius` | `4` | `0..20` | 设置卡片区域阴影半径，dp |
| `glass_settings_region_tint_opacity` | `0.24` | `0..1` | 设置卡片区域染色不透明度 |
| `glass_shadow_radius` | `4` | `0..20` | 全局玻璃阴影半径，dp |
| `glass_sidebar_blur_radius` | `12` | `0..40` | 侧栏模糊半径，dp |
| `glass_sidebar_buttons_blur_radius` | `12` | `0..40` | 侧栏按钮模糊半径，dp |
| `glass_sidebar_buttons_edge_width` | `0.4` | `0..2` | 侧栏按钮边缘宽度，dp |
| `glass_sidebar_buttons_highlight_alpha` | `0.18` | `0..0.8` | 侧栏按钮高光不透明度 |
| `glass_sidebar_buttons_shadow_radius` | `4` | `0..20` | 侧栏按钮阴影半径，dp |
| `glass_sidebar_buttons_tint_opacity` | `0.24` | `0..1` | 侧栏按钮染色不透明度 |
| `glass_sidebar_edge_width` | `0.4` | `0..2` | 侧栏边缘宽度，dp |
| `glass_sidebar_highlight_alpha` | `0.18` | `0..0.8` | 侧栏高光不透明度 |
| `glass_sidebar_shadow_radius` | `4` | `0..20` | 侧栏阴影半径，dp |
| `glass_sidebar_tint_opacity` | `0.24` | `0..1` | 侧栏染色不透明度 |
| `glass_tint_opacity` | `0.24` | `0..1` | 全局玻璃染色不透明度 |
| `glass_user_message_blur_radius` | `12` | `0..40` | 发送方消息模糊半径，dp |
| `glass_user_message_edge_width` | `0.4` | `0..2` | 发送方消息边缘宽度，dp |
| `glass_user_message_highlight_alpha` | `0.18` | `0..0.8` | 发送方消息高光不透明度 |
| `glass_user_message_shadow_radius` | `4` | `0..20` | 发送方消息阴影半径，dp |
| `glass_user_message_tint_opacity` | `0.24` | `0..1` | 发送方消息染色不透明度 |

玻璃区域键包括发送方消息、助手消息、输入、侧栏、侧栏按钮、对话列表、设置卡片与工具包卡片。每个区域的五个原始默认值与全局相同：模糊 12、边宽 0.4、高光 0.18、阴影 4、染色 0.24。开启区域独立参数时，原生主题页会以当时有效的全局参数作为编辑起点，因此开启后的实际值未必等于原始默认值。

## 明确排除的字符串

以下八个字段属于完整主题 `config` 的媒体字段，但不会传给 JS。完整包中未绑定时可为 `null`，绑定时使用包内素材路径；角色设备上的私有位置不应手写进分享文件。

| config 键 | 用途 |
| --- | --- |
| `background_image_uri` | 原主题媒体背景 |
| `custom_user_avatar_uri` | 发送方头像 |
| `custom_ai_avatar_uri` | 助手头像 |
| `bubble_user_image_uri` | 发送方气泡图片 |
| `bubble_ai_image_uri` | 助手气泡图片 |
| `custom_font_path` | 原主题全局字体文件 |
| `bubble_user_custom_font_path` | 发送方消息字体文件 |
| `bubble_ai_custom_font_path` | 助手消息字体文件 |

`custom_chat_title` 也不进入 JS 上下文，且不属于完整主题的分享白名单。`custom_theme_script_uri` 是应用管理的脚本资源引用，会被 `_uri` 过滤，不是 `config` 中允许手工分享的字段。JS 使用自己的图片与字体时，通过工程 `assets` ID 绑定，详见制作手册。

## layout：分享包中的五个布局值

以下字段在完整主题和参数码根 `layout` 中全部必填，由既有布局设置管理；它们属于全局聊天布局，不是 `context.settings` 的字段，也不是 Surface 的任意布局权限。分享/导入按原有布局流程处理。

| layout 键 | 默认值 | 合法范围 | 含义 |
| --- | --- | --- | --- |
| `chat_settings_button_end_padding` | `2` | `0..50` | 设置按钮末端内距，dp |
| `chat_area_horizontal_padding` | `16` | `0..50` | 聊天区域水平内距，dp |
| `global_text_line_height_multiplier` | `1` | `0.8..2` | 既有全局文字行高倍数 |
| `global_text_letter_spacing` | `0` | `-1..8` | 既有全局字间距，sp |
| `ai_markdown_paragraph_spacing` | `12` | `0..48` | 助手 Markdown 段落间距，dp |

## 核对入口

目录已按当前白名单逐键核对，不表示这些值都能由 JS 修改，也不表示每个开关都会增加原生外观接入范围。

<details><summary>参数与默认值源码</summary>

- [主题快照与默认值](../../../app/src/main/java/com/ai/assistance/operit/data/preferences/ThemePreferenceSnapshot.kt)
- [玻璃默认值](../../../app/src/main/java/com/ai/assistance/operit/data/preferences/glass/GlassThemePreferences.kt)、[区域参数](../../../app/src/main/java/com/ai/assistance/operit/data/preferences/glass/GlassRegion.kt)
- [透明度解释](../../../app/src/main/java/com/ai/assistance/operit/data/preferences/transparency/ThemeTransparencyPreferences.kt)
- [分享字段、枚举与数值范围](../../../app/src/main/java/com/ai/assistance/operit/data/themesharing/ThemeTransferSchema.kt)
- [布局模型与范围](../../../app/src/main/java/com/ai/assistance/operit/data/preferences/layout/ChatLayoutSettings.kt)
- [上下文过滤](../../../app/src/main/java/com/ai/assistance/operit/ui/theme/script/ThemeScriptHost.kt)

</details>
