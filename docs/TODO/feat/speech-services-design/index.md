---
repo: https://github.com/3316891527/Operit
branch: docs/speech-services-design
status: DESIGN_ACCEPTED
---

# 语音服务配置交互设计

保留 Operit Android 的朗读、识别和唤醒能力，以紧凑工作区重新组织语音配置。顶部三个等宽标签直接进入对应设置，档案、引擎和当前参数在同页编辑，测试区紧随配置。

本次交付为可交互 HTML 设计稿。Operit 2 的白色截图仅作为参考，设计沿用现有工作区的深浅主题、紧凑行和折叠区，未修改 Android 语音页面、提供方、配置存储或迁移逻辑。

- [交互设计稿](design.html)
- [页面结构与现有能力对应](01-design.md)
- [完整核对步骤与独立结果表](02-verification.md)
- [可重复执行的交互检查](verify-design.cjs)

手机文件位于 Download/Operit/design/operit-speech-services.html，统一设计索引新增语音服务入口。示例只在当前页面内存中生效，刷新后恢复，不录音、不访问真实语音服务。

HTML 语法与二十八组 DOM 交互检查已通过；实际浏览器排版、手机触摸和软键盘效果待查看。仓库工作流只验证提交对应的 Android 基线构建，不能作为语音功能已经原生实现或服务已联通的证明。

设计已被选定用于 Android 原生实现；代码和完整回归见 [语音服务原生工作区](../speech-services-workbench/index.md)。上面的 HTML 验证记录仍只适用于设计稿。
