# 清理 Vertex AI 登录

## 目标

按照用户指示，彻底删除 PR #1257 中引入的 Vertex AI 登录，仅保留 Antigravity。

## 修改范围

- [DONE] 删除 Vertex 独立类与资源文件：
  - `VertexProvider.kt`
  - `VertexAuthManager.kt`
  - `VertexOAuthClient.kt`
  - `VertexAuthPreferences.kt`
  - `VertexLoginDialog.kt`
  - `VertexOAuthCoordinator.kt`
  - `VertexOAuthLoopbackCallbackServer.kt`
  - `ModelConfigManagerVertexMigrationTest.kt`
  - `assets/model_logos/VERTEX_AI/vertex.svg`
- [DONE] 恢复通用配置与界面代码：
  - `ModelConfigData.kt`：移除 `VERTEX_AI` 枚举项及 `vertexProjectId` / `vertexLocation` 字段。
  - `ModelConfigManager.kt`：还原至基础版本，移除复合端点拆分与版本迁移。
  - `AIServiceFactory.kt`：移除 `ApiProviderType.VERTEX_AI` 分支。
  - `ChatConfigReadiness.kt`：移除 Vertex 认证判断与测试用例。
  - `EndpointCompleter.kt`、`ApiProviderConfigCollect.kt`、`ModelPricingDefaultsCollect.kt`、`ModelThinkingConfigDefaultsCollect.kt`、`ToolPkgJsAiProviderService.kt`、`ChatUtils.kt`：移除 Vertex AI。
  - `ModelConfigScreen.kt`、`ModelApiSettingsSection.kt`、`AdvancedSettingsSection.kt`：移除 Vertex 认证设置块、输入框及对话框。
  - 多语言 `strings.xml`：清理 Vertex 相关文案，将 Google OAuth 实验提示更新为仅针对 Antigravity。

[DONE]
