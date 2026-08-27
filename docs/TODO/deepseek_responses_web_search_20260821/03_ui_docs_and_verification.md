# 界面、文档与验证

## 界面

- DeepSeek API 端点列表显示 Chat Completions 与 Responses 两个完整端点。
- 仅在 Responses 端点显示服务器搜索开关。
- 旧配置保存的 Chat Completions 端点保持不变。

## 文档

在 feature protocol 中记录配置兼容、端点解析、搜索事件和历史恢复契约，供后续 provider 协作者复用。

## 验证计划

- 已发布 Chat Completions 端点保持原协议
- 官方 Responses 端点选择 Responses 协议
- 搜索工具开关和函数工具共存
- 非流式与流式 `web_search_call` 保存、恢复
- 搜索开始/完成事件不进入最终正文
- Chat Completions 请求保持原状

用户报告编译错误并明确要求检查打包后，使用项目级 mise JDK 21 执行编译和 Debug 打包验证。

## 完成记录

- 已静态确认旧配置保存的 Chat Completions 端点保持原协议，搜索开关默认开启但只在 Responses 端点生效。
- 已静态确认 DeepSeek Chat 与 Responses 使用同一 provider identity，协议由保存的端点直接判定。
- 已静态确认搜索工具与函数工具并列、开关关闭时不添加搜索工具。
- 已静态确认流式搜索状态使用 `output_index` 维护并行调用，隐藏元数据在下一轮恢复为原始 `web_search_call`。
- 已静态确认隐藏元数据不会进入普通请求、摘要、渲染或复制文本。
- `git diff --check` 通过。
- `mise exec -- ./gradlew :app:compileDebugKotlin` 通过。
- `mise exec -- ./gradlew :app:testDebugUnitTest` 通过。
- `mise exec -- ./gradlew assembleDebug` 通过，APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

以上 Gradle 结果产生于端点驱动协议调整之前。本轮调整遵守工程执行准则，未重新运行 Gradle 编译、构建或测试。

[DONE]
