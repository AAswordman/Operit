# 02 Sidebar Surface

## 原状

ToolPkg 已支持 `main_sidebar_plugins` 路由和 JavaScript WebView。

## 修改

- 注册 DeepSeek Harness 侧边栏路由与导航入口。
- 页面显示 Runtime 初始化和 WebView 加载状态，并在启动失败时展示诊断信息。
- 不向 DSH 页面注入 Android 或 ToolPkg 原生能力。

## 预期结果

用户从 Operit 主侧边栏进入 DeepSeek Harness，并使用其原生 Web UI。

