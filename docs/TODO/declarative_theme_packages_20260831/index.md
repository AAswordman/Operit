---
title: 声明式全局主题包
fork: https://github.com/luojiaping/Operit.git
branch: feat/plugin-interface
status: superseded_by_v2
---

# 声明式全局主题包

## 背景

此前的统一主题工作把用户可见的外观拆成大量颜色、圆角、边框和组件级设置，并用 `Theme Studio` 试点验证单个统计组件。这个方向不适合普通用户，也不能表达异形壳层、场景背景、九宫格边框、像素风或完整应用布局。

本计划以当前开发基线为唯一兼容范围。旧的目标级视觉主题数据、编辑器和试验性组件样式实例不形成接口边界，可以直接清理。

## 产品决策

- 主题包是全局单主题，不随角色卡或群组切换
- 用户只安装、预览、启用主题包，并编辑主题作者公开的少量参数
- 角色名称、头像、聊天标题和其他业务数据仍属于角色卡或群组，不属于主题
- 首版主题包不执行作者代码，只使用受限的声明式场景、资源和状态规则
- 主题包可编排已登记的宿主语义槽位，不能获取业务对象、导航器、权限、平台生命周期或任意回调
- 主题包可使用本地素材、字体、路径、图片、九宫格和受限变换实现异形 UI
- 宿主始终控制输入、IME、滚动、消息身份、流式内容、焦点、无障碍、触控下限、权限和系统栏实际调用

## 目标

- 用一个全局主题选择记录替换目标级视觉主题记录
- 建立可验证、可安装、不可变发布的声明式主题包格式
- 用 `app.shell` 和 `chat.main` 证明完整壳层与异形输入区可以由主题包构成
- 盘点所有 Operit 控制的 UI 表面，并为每个表面指定主题责任边界
- 将通用组件和页面逐步迁入场景与语义组件协议
- 为后续 Kotlin + Compose 受信任主题渲染器保留同一组语义槽位，不在首版执行任何作者代码

## 非目标

- 保留旧 Theme Studio、目标级视觉字段或其 DataStore 迁移
- 将 ToolPkg、WebChat、WebView 页面、Android View 内部、Canvas 业务图形或平台界面交给主题包重绘
- DEX、JAR、APK、原生库、WASM、HTML、JavaScript、Kotlin 或 Compose 作者代码
- 任意 Canvas 命令流、任意 shader、网络资源、文件系统访问或主题控制器
- 将崩溃恢复、数据恢复、主题修复、关键权限恢复和诊断面交给第三方主题

## 目录

1. [产品边界与旧实现清理](./1_ProductBoundaryAndLegacyRemoval.md)
2. [全局主题包与场景契约](./2_GlobalThemePackageAndSceneContract.md)
3. [全应用 UI 语义目录](./3_ApplicationUiSemanticInventory.md)
4. [应用壳与主聊天场景](./4_AppShellAndChatScenes.md)
5. [包安装、资源与运行时链接](./5_PackageInstallAndRuntimeLinking.md)
6. [迁移批次与验证](./6_MigrationAndVerification.md)

## 阶段顺序

1. 清理旧 Theme Studio 与目标级视觉主题路径
2. 建立全局主题选择、包身份与基础参考主题
3. 实现纯声明式场景、token 与资源模型
4. 实现 `.otheme` 格式、不可变安装、固定 Release 摘要的默认主题和主题设置页
5. 固化 `app.shell`、`chat.main` 的语义与参考渲染器
6. 用赛博参考 `.otheme` 验证聊天主场景
7. 按 UI 目录迁移剩余 Operit 原生页面
8. 单列批次接入插件市场 `theme` artifact 类型、发布和安装链路

## 接替说明

批次 G 及后续迁移由 [global_theme_ownership_20260831](../global_theme_ownership_20260831/index.md) 接管：V1 场景叠加式实现已删除，V2 以 surface 覆盖与组件皮肤契约重建。
