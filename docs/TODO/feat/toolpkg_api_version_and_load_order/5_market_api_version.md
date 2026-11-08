---
title: ToolPkg 市场 API 版本
status: completed
---

# ToolPkg 市场 API 版本

## 目标

让 ToolPkg manifest 中的 `api_version` 沿发布链路进入市场版本对象，并在发布界面、市场列表、详情和历史版本中保持一致展示。

## 约定

- manifest 字段使用 `api_version`。
- 市场发布请求和响应的版本对象使用 `apiVersion`。
- `apiVersion` 描述宿主 ToolPkg API，不代表包自身的 `version` 或归档格式 `formatVer`。
- 非 ToolPkg 版本不设置 `apiVersion`。
- 市场响应中的 `apiVersion` 必须可选；`PACKAGE` 类型已有数据缺少该字段时，客户端按 `1.0.0` 解释，但不改写市场数据。

## 完成情况

- 本地 ToolPkg 发布源读取 manifest API 版本。
- 新发布和发布新版本请求携带 `version.apiVersion`。
- 发布界面以只读字段展示 API 版本。
- 市场列表卡片、详情、版本历史和发布预览展示 API 版本。

[DONE]
