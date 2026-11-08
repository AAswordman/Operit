# +17 统一 Debug 测试

当前基线 dev 已包含插件升级安装保留及 use_package 结构化失败修复（#72、#73）。本分支整合后续七项独立修复与外置扩展的真实分发产物，供维护者统一测试，不发布签名 APK 或应用 Release。

## 已上传的独立 PR

- [#76](https://github.com/3316891527/Operit/pull/76)：`ci/pr-sdk-packages`
- [#77](https://github.com/3316891527/Operit/pull/77)：`fix/github-terminal-result`
- [#78](https://github.com/3316891527/Operit/pull/78)：`ci/resource-publication-idempotence`
- [#79](https://github.com/3316891527/Operit/pull/79)：`fix/history-scroll-placement`
- [#80](https://github.com/3316891527/Operit/pull/80)：`fix/scroll-state-preservation`
- [#81](https://github.com/3316891527/Operit/pull/81)：`fix/copy-preview-state`
- [#82](https://github.com/3316891527/Operit/pull/82)：`fix/browser-ref-main-thread`

## 外置扩展

GitHub 包和世界书源码及分发文件已实际构建。固定资源清单只变更 sandbox-github、plugin-worldbook 两个资源组的内容版本，其他 49 个资源组逐项验证后复用原归档。V1 和旧 V2 不改写、不删除。

本次 V2：`resources-v2-d16730007f8d94095264`；此前 V2：`resources-v2-f67fc0082a8e638c6cc2`。

Debug 安装后，在离线资源中更新或首次下载 GitHub 与世界书，再验证对应终端输出和世界书状态保留；只更新 APK 不会自动替换已安装旧扩展。统一版本保持 1.12.2+17 / 73。

本仓库 Debug applicationId 为 com.ai.assistance.operit.debug，和正式安装分开。界面、工具与新安装行为可在本版测试；正式 +16 的原位升级数据验证需要相同应用 ID、签名的升级链，不能用新建 Debug 数据替代。

## 已执行验证

- GitHub esbuild 分发构建通过。
- GitHub TypeScript 编译和世界书 TypeScript 编译通过。
- 固定资源目录验证通过：51 个资源组，193 个源码文件。
- 修改 Kotlin 的 Tree-sitter 语法、Python AST、XML/JSON 解析、YAML/actionlint 和 git diff --check 通过。
- 未执行本地 Android 构建或新增回归。后续 Debug 与 JVM 工作流仅派发，不等待或轮询结果。

## 测试范围

| 编号 | 操作 | 预期 |
| --- | --- | --- |
| D01 | 非存在的普通包、内置包、Skill/MCP 名称执行 use_package | 返回失败；包不存在有 PACKAGE_NOT_FOUND |
| D02 | 对有效包执行 use_package | 正常成功，不将成功文字误判为失败 |
| D03 | 已有旧版扩展时安装新版应用并打开离线资源页 | 旧安装保留可用；真实内容变化才显示更新 |
| D04 | 更新 GitHub 与世界书，下载失败时重试 | 完整校验后安装，失败不先删旧安装 |
| D05 | 两个终端分别输出 stdout/stderr 后退出 7 | 均保留完整结果且 success=false、exitCode=7 |
| D06 | 终端长输出及超时 | 保留原生输出/文件路径、会话、超时与退出状态 |
| D07 | 至少 100 条分组/文件夹对话，反复快慢上下滑动及反向滑动 | 无卡片叠加、半截文字或分组错位 |
| D08 | 展开收起、拖拽、侧滑、搜索、多选与归档往返 | 位置和操作正确，玻璃样式仍可用 |
| D09 | 世界书列表滚动后开关条目，长表单点击/输入/粘贴 | 不异常回顶，光标和内容对应正确 |
| D10 | 进入不同条目、分组及复制页面，再返回原对象 | 不同对象位置隔离，同对象可恢复 |
| D11 | 三个设置页签分别滚动并切换，角色卡/标签长字段弹出键盘 | 页签恢复位置，焦点可见，不异常回顶 |
| D12 | 消息复制中段长按并拖动手柄到边缘，格式往返后复制 | 选区与滚动稳定，各格式独立，复制内容正确 |
| D13 | 浏览器工具用 ref 点击普通、缩放及边缘元素 | 无 WebView 线程异常，点击位置正确 |
| D14 | 流式回复中交替工具调用、切页返回及状态刷新 | 正文持续刷新，不等回复结束才出现 |
| D15 | 同一固定资源版本再次执行发布核对，未公开草稿续传 | 完整公开版复用；草稿按 ID 处理，不重复创建 |

D03 在旧 Debug 升级链上测试；正式安装升级链仍另行验证。更细的回归场景见本分支各修复目录，不将静态检查算作设备通过。

## 结果填写

设备/系统：
Debug 构建提交：
旧安装来源：

| 编号 | 结果 | 备注 |
| --- | --- | --- |
| D01 | 待填写 | |
| D02 | 待填写 | |
| D03 | 待填写 | |
| D04 | 待填写 | |
| D05 | 待填写 | |
| D06 | 待填写 | |
| D07 | 待填写 | |
| D08 | 待填写 | |
| D09 | 待填写 | |
| D10 | 待填写 | |
| D11 | 待填写 | |
| D12 | 待填写 | |
| D13 | 待填写 | |
| D14 | 待填写 | |
| D15 | 待填写 | |
