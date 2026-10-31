# 包职责与依赖安排

本记录先确定主题脚本功能的代码归属。已对照当前工作分支的源码目录，以及此前 `chore/package-layout` 中的职责分析和迁移记录；以下记录保留实施前确定的包职责，实际实现沿用这些位置。

原先临时创建的 `data/themescript/ThemeScriptContract.kt` 已撤出工作树，原稿仅保存在仓库外的调研目录。路径确定后，从合入所有 open PR 的 dev 新建工作分支继续实施。

## 归属决策

源码根目录为 `app/src/main/java/com/ai/assistance/operit/`。

```text
data/
	model/
		theme/
			ThemeScriptDefinition.kt
			ThemeScriptAppearance.kt
			ThemeAppearanceStyle.kt
			ThemeStudioMetadata.kt
			ThemeScriptContract.kt
	resources/
		theme/
			ThemeScriptBundleStore.kt
	preferences/
		ThemePreferenceSnapshot.kt
		UserPreferencesManager.kt
	themesharing/
		ThemeArchive.kt
		ThemeTransferSchema.kt
		ThemeTransferRepository.kt
		ThemeResourcePaths.kt
ui/
	theme/
		script/
			ThemeScriptResolver.kt
			ThemeScriptHost.kt
			ThemeScriptDecoration.kt
			ThemeScriptAssets.kt
			ThemeAppearanceRuntime.kt
			ThemeAppearanceWidgets.kt
	features/
		management/settings/screens/theme/
			script/
				ThemeAppearanceLabels.kt
			studio/
				ThemeStudioScreen.kt
				ThemeStudioState.kt
				ThemeStudioCatalog.kt
				ThemeStudioPreview.kt
				ThemeStudioProperties.kt
				ThemeStudioResources.kt
				ThemeStudioSource.kt
				ThemeStudioColorPicker.kt
				ThemeStudioNineSliceEditor.kt
				ThemeStudioComponents.kt
```

`data/model/theme`、`data/resources/theme`、`ui/theme/script` 和设置页的 `script/studio` 是本次新增的下级包；其余列出的目录与主题偏好、分享文件均已存在。

## 各部分职责

### 纯模型与协议约束：data/model/theme

这里定义脚本声明、执行输入、外观输出及其纯数据约束。它们需要同时供文件导入、草稿、脚本解析与 UI 使用，不能挂在设置页或主题分享实现中。

模型允许依赖 Kotlin 标准库与序列化库；不依赖 Compose、QuickJS、Android Context、文件系统或具体设置页面。字段范围、合法部件标识、外观数据结构的校验与模型一起维护；ZIP 路径、文件读取等导入规则由分享或素材层负责。

### 文件和素材存储：data/resources/theme

这里负责脚本及关联素材的内部存放、读取、完整性和生命周期。该归属与现有 `resources/models`、`resources/extensions`、`resources/ocr` 的资源职责一致。

它不执行 JS，不绘制界面，不创建全局活动主题选择。角色主题继续通过现有偏好记录引用素材；资源清理由现有资源引用和草稿暂存规则协调。

### 主题解析和绘制：ui/theme/script

这里是脚本外观功能的主要入口，与现有 `ThemeColorSchemeResolver`、主题局部状态及玻璃渲染同属主题表现域。

- `ThemeScriptResolver`：将主题输入交给受约束的 JS 执行，校验并生成原生外观结果
- `ThemeScriptHost`：将当前角色或草稿的主题快照接到 Compose，管理加载、成功与错误状态
- `ThemeScriptDecoration`：使用原生绘制能力显示装饰，原生内容继续负责交互和业务
- `ThemeScriptAssets`：将已验证素材解码为绘制所需对象，负责相关绘制缓存

QuickJS 的通用执行资源限制放在现有 `quickjs` 模块内；主题模块只使用它，不将主题专用行为加入 `core/tools/javascript/JsEngine` 或工具权限桥。

### 草稿与角色作用域：现有 data/preferences

新增脚本引用仍走现有角色主题读写、复制、保存、放弃和重置流程。偏好层保存数据与引用，不依赖主题解析器或 Compose。

这里不再建立一个 `themescript` 一级功能包，也不引入第二个全局主题管理器。

### 分享格式：现有 data/themesharing

`.oktheme` 的协议扩展、归档导入导出、资源枚举和旧文件兼容在现有主题分享包内实现。该包引用共享模型和资源存储，不引用 `ui/theme/script`。

如果后续导入文件数量增长到确实存在独立职责，再在该包内部归组；当前五个分享实现文件不因新增功能而提前整体搬迁。

### 编辑入口：现有主题设置的下级包

代码编辑、资源目录、取色、预览、工程草稿与导入导出位于 `ui/features/management/settings/screens/theme/studio`；`script` 仅保留共享部件名称映射。设置页使用主题解析器验证和预览，不持有文件协议或 QuickJS 底层实现。

## 依赖约束

- 设置编辑入口依赖主题表现域和现有草稿会话
- 主题表现域依赖共享模型、主题资源存储和通用 QuickJS 模块
- 主题资源存储和 `.oktheme` 分享依赖共享模型
- 主题偏好保留角色作用域；数据层不反向依赖 UI
- `quickjs` 不依赖 app 的主题模型或主题业务
- 外部 JS/TS API 和文件格式使用稳定的显式标识，不使用 Kotlin 包全名作为协议标识

## 本轮范围

只确定新增功能的位置与依赖边界，不搬迁现有主题偏好、玻璃、透明度、主题分享和聊天组件，也不重做上一轮包整理。

[DONE] 路径与职责核对、布局记录及临时一级包撤回；实现已按此布局完成，原编辑弹窗已由设置页独立 studio 下级包替代，部件名称映射仍可复用。

## 预览工具栏与菜单复用

工作室场景入口单独放在既有 studio 包的 ThemeStudioPreviewToolbar.kt。可复用的锚定菜单位于 ui/components/menu/WorkbenchDropdownMenu.kt，与已有 ActionSheetItem 选项行组合；数值与颜色滑杆复用 ui/components/slider/WorkbenchSlider.kt。新增菜单组件没有依赖主题工程模型，其他工作区保留原职责。
