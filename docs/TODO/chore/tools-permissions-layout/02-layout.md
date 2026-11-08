# 02 工具目录迁移

权限收口提交完成后读取工具源码，再以 [files.json](files.json) 作为本批逐文件迁移依据。清单记录当前路径、目标路径、源包名、目标包名和源文件摘要；执行前核对摘要，执行后核对文件与包名。

## 目标结构

以下仅展示本批涉及的目录，其余已有职责目录保持原有归属。

```text
core/tools/
	builtin/
		filesystem/
		ui/
		device/
		system/
		shell/
		terminal/
		web/
			session/
		calculator/
		workflow/
		chat/
		settings/
		media/
		memory/
		a2ui/
	packTool/
		PackageDebugRefreshReceiver.kt
		ToolPkgDebugInstallReceiver.kt
		ToolPkgComposeDslDebugDumpReceiver.kt
		parser/
		loader/
		runtime/
		install/
		minify/
		uidsl/
		metadata/
	runtime/
	permissions/
		authorization/
	system/
		shell/
		capture/
		terminal/
```

## 迁移规则

- 内置工具按功能组织，标准、管理员、Root 实现放在同一功能目录
- 插件包按解析、加载、运行、安装、压缩、UI DSL 和元数据归类
- 工具执行与注册入口集中到 runtime，授权安装与检测集中到 permissions/authorization
- 同步 Kotlin 引用、同包隐式依赖、Manifest 组件、脚本显式组件及测试资源路径
- 目录迁移保持具体操作体，既有逻辑完善分别记录在权限收口提交中

- 安装、刷新与 UI DSL 调试广播接收器保留既有公开组件名，现有 ADB 与编辑器调用继续使用原入口

## 文件清单

共 117 个文件，迁移 111 个，保留已合理分区或已有公开入口的文件 6 个。以下路径均相对于仓库根目录。

- 迁移：`app/src/androidTest/js/com/ai/assistance/operit/core/tools/defaultTool/standard/ffmpeg/ffmpeg_probe.js`
  - 目标：`app/src/androidTest/js/com/ai/assistance/operit/core/tools/builtin/media/ffmpeg/ffmpeg_probe.js`
- 迁移：`app/src/androidTest/js/com/ai/assistance/operit/core/tools/defaultTool/standard/browser/main.js`
  - 目标：`app/src/androidTest/js/com/ai/assistance/operit/core/tools/builtin/web/browser/main.js`
- 迁移：`app/src/androidTest/js/com/ai/assistance/operit/core/tools/defaultTool/standard/browser/probe.js`
  - 目标：`app/src/androidTest/js/com/ai/assistance/operit/core/tools/builtin/web/browser/probe.js`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/ToolGetter.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/ToolGetter.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/a2ui/A2uiToolDefinitions.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/a2ui/A2uiToolDefinitions.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/a2ui/A2uiToolRuntime.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/a2ui/A2uiToolRuntime.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/a2ui/A2uiTools.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/a2ui/A2uiTools.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardCalculator.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/calculator/StandardCalculator.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardChatManagerTool.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/chat/StandardChatManagerTool.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardDeviceInfoToolExecutor.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/device/StandardDeviceInfoToolExecutor.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/PathValidator.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/filesystem/PathValidator.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/admin/AdminFileSystemTools.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/filesystem/AdminFileSystemTools.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/LinuxFileSystemTools.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/filesystem/LinuxFileSystemTools.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/SafFileSystemTools.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/filesystem/SafFileSystemTools.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardFileSystemTools.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/filesystem/StandardFileSystemTools.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/BluetoothSessionManager.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/media/BluetoothSessionManager.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardFFmpegTool.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/media/StandardFFmpegTool.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardMusicPlaybackTools.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/media/StandardMusicPlaybackTools.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/MemoryQueryToolExecutor.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/memory/MemoryQueryToolExecutor.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardSoftwareSettingsModifyTools.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/settings/StandardSoftwareSettingsModifyTools.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/ToolPermissionModeSupport.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/settings/ToolPermissionModeSupport.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/ShellCommandSafety.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/shell/ShellCommandSafety.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardShellToolExecutor.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/shell/StandardShellToolExecutor.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/admin/AdminSystemOperationTools.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/system/AdminSystemOperationTools.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardIntentToolExecutor.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/system/StandardIntentToolExecutor.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardSendBroadcastToolExecutor.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/system/StandardSendBroadcastToolExecutor.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardSystemOperationTools.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/system/StandardSystemOperationTools.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardTerminalCommandExecutor.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/terminal/StandardTerminalCommandExecutor.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/admin/AdminUITools.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/ui/AdminUITools.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/root/RootUITools.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/ui/RootUITools.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardUITools.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/ui/StandardUITools.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/CookiePrivacyManager.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/CookiePrivacyManager.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardBrowserSessionTools.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/StandardBrowserSessionTools.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardHttpTools.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/StandardHttpTools.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardWebVisitTool.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/StandardWebVisitTool.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserDownloadSupport.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/browser/BrowserDownloadSupport.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserNavigationPolicy.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/browser/BrowserNavigationPolicy.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserPageExecutionSupport.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/browser/BrowserPageExecutionSupport.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserToolSupport.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/browser/BrowserToolSupport.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserWebViewSupport.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/browser/BrowserWebViewSupport.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/WebSessionBrowserHost.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/browser/WebSessionBrowserHost.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/WebSessionBrowserHostState.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/browser/WebSessionBrowserHostState.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/WebSessionHistoryStore.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/browser/WebSessionHistoryStore.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/WebSessionPermissionRequestActivity.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/browser/WebSessionPermissionRequestActivity.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/WebSessionWebViewHost.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/browser/WebSessionWebViewHost.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/userscript/UserscriptCapabilityRegistry.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/userscript/UserscriptCapabilityRegistry.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/userscript/UserscriptMatcher.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/userscript/UserscriptMatcher.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/userscript/UserscriptMetadataParser.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/userscript/UserscriptMetadataParser.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/userscript/UserscriptModels.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/userscript/UserscriptModels.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/userscript/install/UserscriptImportCoordinator.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/userscript/install/UserscriptImportCoordinator.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/userscript/install/UserscriptImportPickerActivity.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/userscript/install/UserscriptImportPickerActivity.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/userscript/runtime/UserscriptBootstrapScript.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/userscript/runtime/UserscriptBootstrapScript.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/userscript/runtime/UserscriptCookieService.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/userscript/runtime/UserscriptCookieService.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/userscript/runtime/UserscriptStorageNotifier.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/userscript/runtime/UserscriptStorageNotifier.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/userscript/runtime/UserscriptTabStateStore.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/userscript/runtime/UserscriptTabStateStore.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/userscript/runtime/UserscriptWebRequestEngine.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/userscript/runtime/UserscriptWebRequestEngine.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/userscript/runtime/WebSessionUserscriptManager.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/userscript/runtime/WebSessionUserscriptManager.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/userscript/storage/UserscriptEntities.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/userscript/storage/UserscriptEntities.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/userscript/storage/UserscriptJsonStore.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/userscript/storage/UserscriptJsonStore.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/userscript/storage/UserscriptRepository.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/userscript/storage/UserscriptRepository.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/userscript/ui/WebSessionUserscriptUiStateStore.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/session/userscript/ui/WebSessionUserscriptUiStateStore.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardWorkflowTools.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/builtin/workflow/StandardWorkflowTools.kt`
- 保留：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/PackageDebugRefreshReceiver.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/PackageDebugRefreshReceiver.kt`
- 保留：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgComposeDslDebugDumpReceiver.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgComposeDslDebugDumpReceiver.kt`
- 保留：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgDebugInstallReceiver.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgDebugInstallReceiver.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/PackageFileImportSupport.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/install/PackageFileImportSupport.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/PackageSourceDeletion.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/install/PackageSourceDeletion.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/PackageTemplateFactory.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/install/PackageTemplateFactory.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgLoadOrder.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/loader/ToolPkgLoadOrder.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgLoader.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/loader/ToolPkgLoader.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/BuiltInPackageDefaults.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/metadata/BuiltInPackageDefaults.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgApiVersion.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/metadata/ToolPkgApiVersion.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgCommonPluginConstants.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/metadata/ToolPkgCommonPluginConstants.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgManifestRequirement.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/metadata/ToolPkgManifestRequirement.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgMarketOrigin.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/metadata/ToolPkgMarketOrigin.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgTemplateModels.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/metadata/ToolPkgTemplateModels.kt`
- 保留：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/metadata/PluginActivityStore.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/metadata/PluginActivityStore.kt`
- 保留：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/minify/ToolPkgArtifactMinifier.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/minify/ToolPkgArtifactMinifier.kt`
- 保留：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/minify/ToolPkgJsAstMinifier.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/minify/ToolPkgJsAstMinifier.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgMainRegistrationScriptParser.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/parser/ToolPkgMainRegistrationScriptParser.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgParser.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/parser/ToolPkgParser.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/PackageActivationResult.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/runtime/PackageActivationResult.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/PackageManager.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/runtime/PackageManager.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/PackageManagerToolPkgFacade.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/runtime/PackageManagerToolPkgFacade.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgHookModels.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/runtime/ToolPkgHookModels.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgManager.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/runtime/ToolPkgManager.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgRuntimeMonitor.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/runtime/ToolPkgRuntimeMonitor.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgComposeDslParser.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/packTool/uidsl/ToolPkgComposeDslParser.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/system/AndroidPermissionLevel.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/permissions/AndroidPermissionLevel.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/system/AccessibilityProviderInstaller.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/permissions/authorization/AccessibilityProviderInstaller.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/system/RootAuthorizer.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/permissions/authorization/RootAuthorizer.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/system/ShizukuAuthorizer.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/permissions/authorization/ShizukuAuthorizer.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/system/ShizukuInstaller.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/permissions/authorization/ShizukuInstaller.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/AIToolHandler.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/runtime/AIToolHandler.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/AIToolHook.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/runtime/AIToolHook.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/ToolExecutionLimits.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/runtime/ToolExecutionLimits.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/ToolProgressBus.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/runtime/ToolProgressBus.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/ToolRegistration.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/runtime/ToolRegistration.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/system/MediaProjectionCaptureManager.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/system/capture/MediaProjectionCaptureManager.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/system/MediaProjectionHolder.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/system/capture/MediaProjectionHolder.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/system/ScreenCaptureActivity.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/system/capture/ScreenCaptureActivity.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/system/ScreenCaptureService.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/system/capture/ScreenCaptureService.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/system/AndroidShellExecutor.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/system/shell/AndroidShellExecutor.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/system/OperitTerminalManager.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/system/terminal/OperitTerminalManager.kt`
- 迁移：`app/src/main/java/com/ai/assistance/operit/core/tools/system/Terminal.kt`
  - 目标：`app/src/main/java/com/ai/assistance/operit/core/tools/system/terminal/Terminal.kt`
- 迁移：`app/src/test/java/com/ai/assistance/operit/core/tools/defaultTool/a2ui/A2uiToolsTest.kt`
  - 目标：`app/src/test/java/com/ai/assistance/operit/core/tools/builtin/a2ui/A2uiToolsTest.kt`
- 迁移：`app/src/test/java/com/ai/assistance/operit/core/tools/defaultTool/standard/ToolPermissionModeSupportTest.kt`
  - 目标：`app/src/test/java/com/ai/assistance/operit/core/tools/builtin/settings/ToolPermissionModeSupportTest.kt`
- 迁移：`app/src/test/java/com/ai/assistance/operit/core/tools/defaultTool/standard/ShellCommandSafetyTest.kt`
  - 目标：`app/src/test/java/com/ai/assistance/operit/core/tools/builtin/shell/ShellCommandSafetyTest.kt`
- 迁移：`app/src/test/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserNavigationPolicyTest.kt`
  - 目标：`app/src/test/java/com/ai/assistance/operit/core/tools/builtin/web/session/browser/BrowserNavigationPolicyTest.kt`
- 迁移：`app/src/test/java/com/ai/assistance/operit/core/tools/packTool/PackageFileImportSupportTest.kt`
  - 目标：`app/src/test/java/com/ai/assistance/operit/core/tools/packTool/install/PackageFileImportSupportTest.kt`
- 迁移：`app/src/test/java/com/ai/assistance/operit/core/tools/packTool/PackageSourceDeletionTest.kt`
  - 目标：`app/src/test/java/com/ai/assistance/operit/core/tools/packTool/install/PackageSourceDeletionTest.kt`
- 迁移：`app/src/test/java/com/ai/assistance/operit/core/tools/packTool/PackageTemplateFactoryTest.kt`
  - 目标：`app/src/test/java/com/ai/assistance/operit/core/tools/packTool/install/PackageTemplateFactoryTest.kt`
- 迁移：`app/src/test/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgApiDeclarationTest.kt`
  - 目标：`app/src/test/java/com/ai/assistance/operit/core/tools/packTool/metadata/ToolPkgApiDeclarationTest.kt`
- 迁移：`app/src/test/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgMarketOriginCodecTest.kt`
  - 目标：`app/src/test/java/com/ai/assistance/operit/core/tools/packTool/metadata/ToolPkgMarketOriginCodecTest.kt`
- 迁移：`app/src/test/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgManifestSelectionTest.kt`
  - 目标：`app/src/test/java/com/ai/assistance/operit/core/tools/packTool/parser/ToolPkgManifestSelectionTest.kt`
- 迁移：`app/src/test/java/com/ai/assistance/operit/core/tools/packTool/PackageActivationResultTest.kt`
  - 目标：`app/src/test/java/com/ai/assistance/operit/core/tools/packTool/runtime/PackageActivationResultTest.kt`
- 迁移：`app/src/test/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgManagerTest.kt`
  - 目标：`app/src/test/java/com/ai/assistance/operit/core/tools/packTool/runtime/ToolPkgManagerTest.kt`

## 验收

- [x] 清单中所有目标路径及包名一致
- [x] 旧引用、同包隐式依赖和显式组件调用已同步
- [x] 移动文件的操作体保持，静态语法与差异检查完成
- [x] 仓库验证入口与交付方式已记录，实际运行结果见 PR

[DONE] 清单中的目录迁移及静态核对完成，编译与 JVM 单测结果由仓库工作流确认。
