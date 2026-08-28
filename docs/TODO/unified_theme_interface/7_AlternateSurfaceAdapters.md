# 独立渲染面适配

## 修改意图

为不能直接复用主 Compose 根的界面提供明确宿主适配器。所有适配器消费同一 `ResolvedTheme`，同时遵守各自平台能力。

## 渲染面

- 悬浮窗与 WindowManager Overlay 使用独立 Compose 主题宿主
- Canvas 保留宿主命中测试、几何和语义，主题提供绘制规格
- Android View、WebView、ExoPlayer 和 GL 由宿主创建，主题控制可支持的外壳参数
- Glance 使用受限的静态组件与明暗投影
- 消息图片和导出使用显式目标及不可交互环境
- 启动与普通错误界面使用内置主题契约
- 最小主题诊断面使用固定宿主实现

## 明确边界

- 不修改独立 React WebChat 或 HTTP 主题模型
- 不修改浏览器访问页面、工作区 HTML、文档页面和第三方 WebView 内容
- 不主题化系统通知布局、输入法和外部 Activity
- ToolPkg 自绘内容继续使用其现有 API，宿主提供兼容的 Material 颜色投影

## 预期结果

- 每个独立 Compose 根都有显式主题目标和宿主类型
- 不再通过不完整的颜色与字体序列化模拟主主题
- Canvas、Glance 和 Android View 的能力差异在契约中可查询
- 主题包不能实例化任意平台 View 或取得平台生命周期对象
