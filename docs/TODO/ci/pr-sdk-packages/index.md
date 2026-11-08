---
fork: https://github.com/3316891527/Operit
status: implemented
---

# PR Check Android SDK 初始化

PR #72 与 #73 的 Fast checks 成功，Android JVM job 在 Set up Android SDK 失败，尚未运行 Gradle。setup-android 默认尝试安装已移除的 tools 包，sdkmanager 报 Failed to find package tools；Candidate checks 因所需 JVM job 失败而正确标红，汇总 job 的秒数不是测试实际耗时。

在 pr-check.yml 的资源编译、JVM、完整 Android 三处 setup-android 显式设置 packages 为空；后续既有 sdkmanager --install 仍安装本 lane 需要的平台、构建工具和 NDK。与 android-build.yml、android-tests.yml 已有配置一致，不降低 Candidate checks 要求，不把上游失败改为跳过或成功。

本轮按维护者要求仅本地修改、提交和静态核对，不推送、创建 PR 或派发工作流；等待下一个复杂问题一起整合，之后使用 Debug，不签名发行。

验证：YAML 与 actionlint 静态核对；确认三处 setup 输入一致，后续工具链安装和汇总判断保留。CI 环境实际修复结果待后续运行。
