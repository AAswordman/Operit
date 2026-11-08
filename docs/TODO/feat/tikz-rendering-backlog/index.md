---
repo: https://github.com/3316891527/Operit
status: deferred
release_blocker: false
---

# TikZ 绘图后续待办

用户决定等当前版本稳定、后续有空再评估。本轮不实施 TikZ，不为此延迟个人发行版发布。

期望模型直接输出 tikz 代码块，由应用自动编译显示，无需生成工具或内置长引导。

当前公式由 JLaTeXMath 渲染，未包含完整 TeX/PGF 环境。候选路线是 Kotlin 宿主接入 TikZJax 的 TeX/WebAssembly 引擎并生成 SVG。先验证 Android System WebView、离线运行、字体与宏包资源体积、编译超时和聊天滚动缓存，再决定实际兼容范围。

暂不承诺 PGFPlots、Circuitikz 或完整桌面 TeX 扩展兼容。

参考：[TikZJax 官方说明](https://tikzjax.com/)。
