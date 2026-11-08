# 会话工作区迁移验证

本页记录工作区迁移阶段的检查结果。后续聊天包迁移及两轮合并核对见[聊天迁移验证](../chat-responsibility-layout/01-validation.md)。

迁移基线为 `9b9bb75d93b96b72204067fa576db30e5279d960`，逐文件来源、目标、包名与主源码声明见 [files.json](files.json)。本批实施阶段先保留本地，后续交付与 CI 记录由聊天目录整理 PR 衔接。

## 已完成的静态核对

- 清单内 82 个文件全部迁移，包含 72 个主源码文件、8 个 JVM 测试文件和 2 个 Android 测试文件，目标目录与包声明一致
- `main`、`test`、`androidTest` 中的旧 `chat/webview/` 与 `chat/workspace/` 目录均已移除，没有保留转发文件或包别名
- 应用源码、仓库工作流、终端目录和根构建配置中没有旧 `conversation.chat.webview` 或 `conversation.chat.workspace` 引用
- 核对 129 处相关显式导入和 17 处拆包后的原同包引用，覆盖工作区主页、网页服务、预览扩展属性、聊天提及面板和终端入口
- 对迁移前的 1938 个 Kotlin、Java 源码文件逐一对照，确认本批变化限于 82 个迁移文件和 16 个调用方文件
- 去除包声明及导入，并按完整符号映射替换旧包路径后，全部声明体与基线一致；声明体内仅 `WorkspaceManager.kt` 的 `NativeCodeEditor` 和 `FileUtils.kt` 的 `GitIgnoreFilter` 使用了需要同步的全限定路径
- 使用临时 Git 索引纳入未跟踪的新路径后执行 `git diff --cached --check`，空白检查通过，Git 识别出 82 处重命名；真实暂存区未变，HEAD 仍为迁移基线
- 更新相关开发文档中的当前路径，`chore/package-layout/` 下既往迁移清单保持原样

在仓库根目录可重新检查旧引用：

```sh
rg -n 'conversation[./]chat[./](webview|workspace)' app .github terminal build.gradle.kts settings.gradle.kts
```

预期无输出，`rg` 退出码为 1。源码声明体对照以清单中的 `source`、`target`、`oldPackage`、`newPackage` 和基线提交为依据，比较前仅移除包声明及导入、归一化全限定包路径。

## 后续验证范围

本地未执行编译、构建或测试，后续远端验证以对应 PR 的派发及结果记录为准。静态声明和引用核对不代替 Kotlin 编译及 Android 运行验证。

| 编号 | 做什么 | 预期行为 |
| --- | --- | --- |
| L1 | 编译应用与 Android 测试源码 | 新包路径、显式导入和原同包引用全部解析 |
| L2 | 执行迁移的 8 个 JVM 测试类及原流式复制测试 | 原回归用例继续通过，测试没有遗漏 |
| L3 | 打开工作区主页，切换工作区与终端 | 原导航、会话标签与终端入口正常 |
| L4 | 浏览文件，打开编辑器、图片、文档、Markdown 与网页预览 | 文件入口、预览服务与刷新行为一致 |
| L5 | 在聊天输入框使用提及候选面板 | 候选展示、样式和选择行为一致 |
| L6 | 复查完整文件操作回归范围 | 复制、剪切、删除及取消保持既有行为 |

L6 的全部自动用例、实机项与可填写结果表见[工作区复制与删除验证](../../fix/workspace-copy-streaming-progress/01-validation.md)，包含此前已实现的复制及删除覆盖范围。

## 结果记录

| 编号 | 结果与证据 |
| --- | --- |
| L1 | 未运行 |
| L2 | 未运行 |
| L3 | 未验证 |
| L4 | 未验证 |
| L5 | 未验证 |
| L6 | 未验证，完整用例沿用关联记录 |

[DONE] 静态核对；后续编译、测试和 Android 运行结果待填写。