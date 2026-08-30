# 应用壳与主聊天场景

## `app.shell.v1`

应用壳在手机抽屉、平板展开侧栏和平板折叠导航之间响应式切换。主题包可重排已声明的导航分区并决定外框、背景和装饰，但不能隐藏可达设置、返回、主题修复或当前路由内容。

必需槽位：

- `app_bar.navigation`
- `app_bar.title`
- `app_bar.actions`
- `navigation.identity_status`
- `navigation.quick`
- `navigation.primary`
- `navigation.plugins`
- `navigation.system`
- `route.content`
- `announcement`

稳定状态：布局模式、当前 route ID、选中导航项、返回可用性、抽屉状态、网络状态、转场状态、主题修订版本。

封闭事件：导航项激活、返回、抽屉开关、公告确认。路由注册、插件动作、返回守卫和转场时序始终由宿主执行。

## `chat.main.v1`

主聊天场景须支持空会话、流式回复、消息选择、附件、队列、Classic/Agent 输入模式、工作区和 Computer 覆盖层。

根槽位：

- `configuration_gate`
- `header`
- `transcript`
- `composer`
- `classic_settings_rail`
- `overlay_stack`

消息视口顺序固定为：加载旧消息、重复消息项、加载新消息、等待生成、滚动导航。主题不能改变消息顺序、虚拟化、分页、自动跟随或消息身份。

消息项槽位：

- `avatar`
- `identity_meta`
- `reply_reference`
- `message_attachment`
- `message_body`
- `message_footer`
- `message_actions`

输入器槽位：

- `reply_context`
- `pending_queue`
- `processing_status`
- `draft_attachments`
- `draft_editor`
- `attachment_trigger`
- `primary_action`
- `fullscreen_editor_trigger`
- Agent 模式的 `model_trigger`、`settings_trigger`、`model_menu`

关键状态包括会话模式、聊天样式、输入样式、消息流式状态、消息选择、草稿文本与 selection、附件数、回复目标、队列、处理阶段、Token 限制、工作区和 IME 策略。

主题可调整 Header、消息 chrome、头像、元信息、输入器外壳和控制项的相对位置。宿主必须保留文本输入、提交 hook、流式解析、消息正文 part 顺序、选择、队列、语音、附件、权限、滚动和所有模态 Overlay 生命周期。

## 参考主题验收

首批参考主题必须分别证明：

- 科幻主题的多层背景、边缘发光、异形框与输入区布局
- 奇幻主题的完整场景背景、装饰性九宫格边框、主题字体和徽章
- 像素主题的整数像素边界、像素字体、最近邻素材与离散控件状态

三者必须使用相同的 `app.shell.v1` 与 `chat.main.v1` 语义输入和封闭事件。
