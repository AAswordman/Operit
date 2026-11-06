# 03 回归测试与构建验证

## 测试位置

`terminal/src/test/java/com/ai/assistance/operit/terminal/SessionInputRouterTest.kt`

`terminal` 模块此前没有 JVM 测试源集，本次一并启用：

```kotlin
// terminal/build.gradle.kts
testOptions {
    unitTests.isReturnDefaultValues = true
}
dependencies {
    testImplementation(libs.junit)
}
```

`isReturnDefaultValues` 让 `android.util.Log` 在 JVM 上返回默认值，测试无需 Robolectric。

## 用例

| 用例 | 断言 |
| --- | --- |
| `interruptIsWrittenToTheNamedSessionAsASingleCtrlCByte` | 只写目标会话，字节恰为 `0x03`，另一会话零字节 |
| `interruptNeverFallsBackToAnotherSessionWhenTheIdIsUnknown` | 返回 `SESSION_NOT_FOUND`，在场会话零字节 |
| `interruptOnASessionWithoutAWriterReportsNotReadyAndWritesNothing` | 返回 `SESSION_NOT_READY` |
| `failingWriterIsReportedAsWriteFailed` | writer 抛错时返回 `WRITE_FAILED` |
| `literalInputReachesOnlyTheNamedSession` | 普通输入同样只到目标会话 |
| `interruptSequenceIsTheSingleControlByteTheLineDisciplineTurnsIntoSigint` | 序列仍是单个 `0x03` |
| `logEscapingKeepsControlBytesDistinguishableFromLiteralText` | 日志转义不吞控制字节 |

## 旧行为复现

用一次性测试复现修改前的顺序（先 `switchToSession`，再在写入时解析"当前会话"，中途插入一次前台切换）：中断落到了前台会话，后台会话零字节，确认这组断言能区分修复前后。复现脚本验证后删除，不进入提交历史。

## 构建验证

```text
:terminal:testDebugUnitTest  7 tests, 0 failures
:app:compileDebugKotlin      BUILD SUCCESSFUL
```

`verifyExternallyBuiltNativeLibraries` 需要预置 `liboperit_ripgrep.so` 与 `ffmpeg-kit-local.aar`，两者都在 `.gitignore` 内，从本机已有产物复制，不进入提交。

本机另有会话占用 Gradle 共享构建缓存锁，验证时使用 `--no-build-cache` 规避，不影响产物正确性。
