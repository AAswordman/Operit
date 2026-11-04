---
fork: https://github.com/mvanhorn/Operit
issue: https://github.com/AAswordman/Operit/issues/1431
status: implemented-awaiting-verification
---

# BLE 写入属性与完整传输

## 原有行为与范围

Android 33 及以上写入固定使用 `WRITE_TYPE_DEFAULT`，旧版没有设置特征的 `writeType`，因此仅支持 `write_no_response` 的打印机特征无法使用正确的写入模式。大数据直接单次提交，也没有按 MTU 分段或保护同一会话的并发写入。

本次只修改 `BluetoothSessionManager.kt`、对应 JVM 回归测试、本记录和 [工具返回类型说明](../../doc-src/toolpkg_developer_documentation/04_modules/tool-types.md)。独立写入和写后读取保持现有参数与结果结构，不主动请求更大的 MTU，不增加重试。

## 实施步骤

1. 写入属性修正 [DONE]

   两个 Android API 分支均使用特征声明的写入模式。支持 `write_no_response` 时使用无响应写入，仅支持 `write` 时保留有响应写入，两者都不支持时在提交前拒绝。

2. MTU 与写入时序 [DONE]

   每个会话初始 MTU 为 23，仅成功的 MTU 回调更新状态。无响应写入每块最多 MTU 减 3 字节，每块使用独立完成等待器，成功回调后间隔 15 ms 再发送下一块。会话互斥锁覆盖整个传输；提交失败、回调失败、超时或取消会终止传输，并清理等待器和释放锁。空数据仍执行一次写入，有响应写入保留单次提交行为。

3. 回归与验收

   已添加使用 Mockito 与协程测试调度器的 JVM 回归测试，通过测试内反射设置私有会话及完成回调。覆盖 11 字节命令的模式与完整内容，以及初始 MTU 下 41 字节的 20/20/1 顺序分块、逐块等待器、回调前不继续提交、完整重组和最终 `bytesWritten`。

   已实现成功 MTU 247 后的 244 字节上限、失败 MTU 回调保留状态、无效 MTU 拒绝提交、整块边界、单字节与空数据，以及有响应、双属性和不可写特征的测试。失败路径覆盖提交失败、状态 3、超时、等待回调或块间取消后的停止、等待器清理与互斥锁释放。同会话并发测试检查分块不交错、等待器不被替换；不同会话并发测试使用相同服务与特征 UUID，检查一个会话等待回调时另一个会话可完成全部分块，且分块内容、等待器与清理相互独立。

   静态检查：`git diff --check` 通过。已检查两种 API 分支使用相同的写入模式，等待器在 `finally` 中清理，互斥锁覆盖分块与间隔；现有依赖已包含 Mockito 5 和 `kotlinx-coroutines-test`，未修改测试配置。尚未运行编译、构建或测试。待授权后运行：

   ```bash
   ./gradlew :app:testDebugUnitTest --tests com.ai.assistance.operit.core.tools.defaultTool.standard.BluetoothSessionManagerTest
   ```

   上述 JVM 场景已编写测试，执行结果仍待验证。

   设备验收尚未执行：在 Android 33 及以上检查新重载的 `writeType` 和分块内容，并尽可能验证旧版 Android。使用受控外设确认 AE01 的 11 字节自检命令和数 KB 数据完整接收，再通过 `StandardSystemOperationTools` 验证独立写入与写后读取。Android 写入回调只表示蓝牙栈完成，不证明打印机应用已经消费数据。

## 协作说明

当前分支为 `fix/1431-ble-write-properties-and-mtu`，未创建提交或 PR。计划记录的基线为 `main`，后续贡献 PR 按仓库贡献指南以 `dev` 为目标并填写现有 PR 模板。
