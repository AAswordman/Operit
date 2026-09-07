---
title: ToolPkg API 版本与加载顺序
status: completed
---

# ToolPkg API 版本与加载顺序

## 原本状况

ToolPkg manifest 目前只有包自身的 `version`，没有声明宿主 ToolPkg API 版本的字段。包加载时也没有统一的包间依赖与加载顺序模型；已安装包列表的顺序和启动加载顺序没有形成清晰的关系。

## 修改意图

为 ToolPkg 增加稳定、可识别的宿主 API 版本约定，并支持包声明必需包和加载顺序约束。缺少 `api_version` 的旧包按 `1.0.0` 解释；Operit 在 `1.12.1+4` 开始支持 `1.0.1`。

依赖与顺序只在宿主加载阶段解析，不把运行态或持久化状态引入 ToolPkg API。插件列表复用已有的拖动排序入口，排序结果参与没有 manifest 顺序约束时的加载顺序。

## 作用域

- manifest API 版本字段与兼容性校验
- Operit 版本对应的 ToolPkg API 支持声明
- `requires` 对象列表、包版本约束和依赖加载顺序
- ToolPkg 启动加载顺序解析与错误提示
- 插件列表顺序保存与加载顺序接入
- DTS 的 API 引入版本注解
- ToolPkg 格式和开发文档

## 分步记录

- [DONE] 1_manifest_and_compatibility.md
- [DONE] 2_package_dependencies_and_order.md
- [DONE] 3_plugin_order_ui.md
- [DONE] 4_dts_and_documentation.md
- [DONE] 5_market_api_version.md
