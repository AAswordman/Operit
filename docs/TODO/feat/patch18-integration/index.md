---
作者仓库: https://github.com/3316891527/Operit
状态: 统一提交验证，工作流与设备验收结果待回填
---

# 补丁 18：统一集成与回归

本次将七项主体改动与插件管理修正统一提交到工作分支，PR 目标为 dev。主应用固定引用本次终端核心提交，构建时从子模块取得同一份终端代码。

此前各分项文档中的“不提交、不推送、不派发工作流”记录的是先前本地累积阶段。本次进入统一 PR 验证阶段，派发 Android Debug 构建和 JVM 测试工作流，再由维护者统一进行设备与云端验收；工作流受理不等于构建或测试通过。

## 实现范围

- [工作区属性](../patch18-workspace-properties/index.md)：12 项回归
- [双方消息风格](../patch18-independent-message-style/index.md)：12 项回归
- [云端 TTS 预取](../patch18-cloud-tts-prefetch/index.md)：16 项回归
- [自动朗读与通话](../patch18-voice-call-autoread/index.md)：24 项回归
- [请求错误时长](../patch18-request-error-duration/index.md)：8 项回归
- [工具调用计时](../patch18-tool-execution-timing/index.md)：22 项回归
- [终端显示与键盘](../patch18-terminal-input/index.md)：26 项回归
- [插件删除提示与旧版本清理](resource-plugin-management.md)：12 项回归

终端核心提交：[48ebcbab](https://github.com/3316891527/OperitTerminalCore/commit/48ebcbab46beb53b04aa7b36c4be870ad4c4b184)。终端修改保留主应用固定基线的资源外置、后台执行及环境维护实现，主应用 gitlink 更新到本次提交。

## 已执行验证

- 累计 78 个 Kotlin 文件通过 Tree-sitter Kotlin 语法解析，22 个 XML 文件通过 xml.etree.ElementTree 解析
- 新增与修改源码的 R.string 引用静态核对通过，生成的 Shell 配置函数与交互 rcfile 通过 bash -n
- 主应用与终端仓库的 git diff --check 通过；提交前核对全部功能源码与上一份累计快照一致
- 未运行本地 Android 编译、构建或 JVM 测试；设备、云端服务和真实 GitHub OAuth 尚未验收

上述语法与引用检查不包含 Android 类型检查，不能当作编译、设备运行或性能测量结果。

## 工作流与验收

工作分支派发 Android Build 的 assembleDebug，关闭发布 Release 和仓库迁移；Android Tests 执行 app 的 Debug JVM 单测。PR 创建后按既有 PR Check 规则检查 merge candidate。工作流结果留待实际回执，不预填通过。

[完整回归操作与预期](regression.md) 保留此前六项 94 项和终端 26 项，再追加插件管理 12 项，再追加首轮体验反馈 18 项，再追加第二轮反馈 16 项，再追加编译与错误归并 4 项，再追加流式完成与错误定位 12 项，共 182 项，没有删除已记录项目。[独立结果表](results.md) 记录旧候选已知失败，新候选保持未测或待复测，按实际结果填写设备、引擎、输入法与证据。

## 兼容性边界

- 旧统一主题样式在读取及分享导入边界展开为双方相同样式；已有混搭保留，HTTP Web 聊天仍沿用既有字段投影为 AI 样式
- 普通自动朗读继续使用 enable_auto_read，语音通话使用独立条件与播放实例；系统及本地 TTS 不改为云端并行合成策略
- 云端预取的实际并发受客户端与提供方额度约束；工具耗时为实际执行区间并集，不包含模型生成或人工权限等待
- 本地交互 Bash 使用应用管理的 rcfile 并读取登录配置，自定义 login_shell 和 .bash_logout 行为需要回归；已有会话重新创建后使用新提示符
- 旧消息没有计时记录时不补造耗时；新计时通过独立状态流交给界面，并保存在 tool_call 的 execution 字段中；正文、复制、搜索与后续模型请求不包含这些字段

单个工具卡片仅在 Markdown 节点索引存在时查询该节点的计时；没有索引的独立渲染保留原工具详情，不关联其他节点的耗时。


插件删除入口在资源使用中置灰，点击只显示关闭插件的警告。资源组独占租约保护跨版本替换，已确认当前内容完整后回收已知旧安装；已有残留在进入或刷新离线资源页时处理。

[首轮反馈修正](feedback-fixes.md) 记录权限预检查、安装入口、后台终端、无按压动效删除以及去正文计时实现。

[第二轮体验反馈](detail-feedback.md) 修正删除警告的点击与窗口、亚秒耗时显示、普通工具错误去重和图片读取失败状态。

[编译导入与失败结果归并](error-result-contract.md) 记录应用 R 导入、生成最终结果前的结构化错误归并及独立输出保留。

[5003 与流式收尾调查](stream-completion-feedback.md) 记录触发规则、协议完成后的尾部连接处理和请求进度字段，截图中的实际中止来源仍待对应错误证据确认。
