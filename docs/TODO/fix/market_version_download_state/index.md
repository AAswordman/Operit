---
Fork: https://github.com/AAswordman/Operit.git
---

# 市场版本范围下载状态

插件市场已经展示发布者声明的应用版本范围，但列表下载入口不会根据该范围改变状态，详情页的禁用下载按钮也没有明确的风险视觉提示。

本次改动会使用应用当前版本和最新插件版本的范围进行判定。超出范围时，列表入口展示警告不可用状态，详情页入口展示警告色禁用状态。安装流程、市场接口和范围规则保持不变。

作用域：

- `ui/features/packages/market/`
- `ui/features/packages/screens/UnifiedMarketDetailEntryScreen.kt`

关联实现：[版本范围按钮状态](1_VersionRangeButtonState.md)
