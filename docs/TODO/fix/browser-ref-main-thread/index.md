# 浏览器 ref 点击的 WebView 主线程访问

参考上游 [PR #1420](https://github.com/AAswordman/Operit/pull/1420) 和 [Issue #1318](https://github.com/AAswordman/Operit/issues/1318)。

browser_click(ref) 在工具线程解析元素后，原实现于主线程切换之前读取 WebView.scale、width、height，可能触发 WebView 线程检查，导致原生点击失败。本次将这些读取与坐标计算移入已有 runOnMainSync，与触摸派发使用同一线程。中心点、缩放和边界约束的计算原样保留，不为此引入额外坐标转换层。

源码及差异空白检查通过。未本地构建、调用浏览器或运行设备测试；未推送、创建 PR 或派发工作流。

验证：从工具执行线程用 ref 点击普通页面、缩放页面、边缘元素，确认无 WebView 线程异常，点击落点正确；失效 ref 仍按原逻辑报错。各项均未执行。
