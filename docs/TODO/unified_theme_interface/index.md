---
title: Operit 统一主题接口
fork: https://github.com/luojiaping/Operit.git
branch: feat/plugin-interface
status: implementation_in_progress
---

# Operit 统一主题接口

## 当前状态

Operit 的已发布主题以角色卡和群组为作用域，使用一组固定偏好字段控制颜色、背景、聊天气泡、输入区和部分应用外壳。主题运行时仍由各功能直接读取快照、`MaterialTheme` 或硬编码视觉参数，内置界面不是统一主题契约的实现，第三方作者也无法替换完整的视觉表达。

## 意图

把 Operit 控制的原生 UI 改造成统一、版本化且高度可定制的主题渲染平台。宿主持有业务状态、导航、动作执行和无障碍约束，主题实现持有组件内部布局、视觉层级、素材、图标和动效。内置主题与第三方主题使用相同的组件契约。

第三方主题使用独立主题包。作者通过类型化 SDK 编写主题，构建工具将源码编译为宿主可校验的 UI IR。主题包显式锁定基底主题版本并覆盖任意组件，安装时链接为完整的渲染实现。首版不执行主题作者代码，但保留隔离控制器协议的版本空间。

## 预期结果

- 已发布主题字段、默认值、作用域、迁移、保存、重置和备份行为保持兼容
- 所有正常原生业务界面通过统一主题上下文和组件契约渲染
- 内置 `native_v1` 成为主题契约的参考实现
- 主题设置由固定标签页升级为模式驱动的主题编辑与组件预览界面
- 独立主题包可以按角色卡或群组选择，并能覆盖任意已注册组件
- 一个固定的最小诊断面可以检查、切换和修复主题包
- 契约、SDK、校验器、示例主题、作者文档和 CI 约束来自同一份模式定义

## 作用域

1. [兼容基线 [DONE]](./1_CompatibilityBaseline.md)
2. [原生主题契约](./2_NativeThemeContract.md)
3. [主题编辑器基础](./3_ThemeEditorFoundation.md)
   - [主题设置重构](./3a_ThemeSettingsRedesign.md)
   - [Conversation 编辑器迁移](./3b_ConversationEditorMigration.md)
4. [渲染契约与组件目录](./4_RendererContractAndCatalog.md)
5. [主题包与 UI IR](./5_ThemePackageAndIr.md)
6. [原生 UI 迁移](./6_NativeUiMigration.md)
7. [独立渲染面适配](./7_AlternateSurfaceAdapters.md)
8. [验证与接口发布](./8_VerificationAndPublication.md)

## 明确排除

- 独立 React WebChat 及其 HTTP 主题协议
- 外部网页、系统界面、输入法界面和第三方页面内容
- ToolPkg 格式、注册 API 和执行模型变更
- 首版主题包中的第三方可执行代码
- 主题对业务数据、导航、权限和宿主生命周期的直接访问

原生 WebView、Android View、Canvas、Glance 和悬浮窗的宿主外壳仍属于改造范围，其内部外部内容不属于主题接口。
