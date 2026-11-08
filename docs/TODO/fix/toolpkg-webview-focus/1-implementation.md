# 实现与证据

## 场景与调用链

反馈来自其他用户，证据范围为维护者提供的三个附件。插件 com.operit.voice_bar 0.5.5 的 say/say_fav 合成后返回 voice_tag，由 AI 在助手回复中输出 `<voice>`。onXmlRender 将其映射到 Compose DSL，内部为 64dp 高的 WebView 播放器，点击可播放或切换字幕。该路径与原生用户语音消息 `<voice_bar>` 不同。

助手气泡、游标和时间线分块通过 StreamMarkdownRenderer/CustomXmlRenderer 进入 XML 插件注册表，再由 RenderToolPkgComposeDslNode 进入 renderWebViewNode。流式转完整消息、条目重新挂载或插件界面重建时，播放器可能再次进入 WebView 挂载效果。

## 修改

ToolPkgComposeDslWebView.kt 的 DisposableEffect(webView) 原来无条件向 WebView 投递 requestFocus。语音条作为助手输出出现时就会主动转移焦点；Android View 与 Compose 焦点互操作可能进入惰性列表的额外子组合测量，与报告中 BoxWithConstraints、forceMeasureTheSubtree 和 LazyListMeasuredItemProvider 的路径相符。

移除挂载时这一次无条件焦点请求，保留创建、状态回调、页面加载、播放设置、网页可聚焦能力和销毁流程。焦点仍可由用户交互或明确请求取得，不通过禁用 WebView 全部焦点能力处理问题。

插件原文件继续可用，修复由宿主统一生效。原生用户语音条继续采用独立播放器；服务端合成、音频协议和语音收藏的行为保持原有实现。

## 回归

新增三项 Android 界面用例，使用助手播放器的 DSL WebView 挂载路径与已有输入焦点：首次显示不抢焦点；HTML 更新和隐藏后重新显示不抢焦点；显式焦点请求仍有效。夹具使用本地 HTML，不调用语音服务或插件凭据。

代码和用例未在本地编译或执行；本次仓库 assembleDebug 验证产品源码构建，设备用例需要单独运行。原始设备的完整焦点调用尾栈未保留，因此不把源码定位或构建受理等同于原始事件已重放通过。

同类焦点互操作问题可参考 [WebView 位于惰性列表时的同类报错记录](https://github.com/ChurchPresenter/ChurchPresenter-Mobile/pull/19)。该记录仅用于辅助理解触发路径，不作为 Operit 本次实机验证证据。

[DONE] 宿主焦点修复、针对性回归代码与完整回归材料已写入，验证结果待确认。
