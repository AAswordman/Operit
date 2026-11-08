---
repo: https://github.com/3316891527/Operit
---

# 取消消息发送导致进程退出

`1.12.2+12` 在取消普通文字、图片或语音请求时可直接退出。设备 crash buffer 显示 Android 16 ART 因挂起的 `JobCancellationException` 触发 `SIGABRT`，业务帧位于发送协程。普通 Java 异常页无法接管这种原生终止。

[实现与验证](1-implementation.md)记录本次代码边界和完整回归项。图片、音频标签及语音转写独立处理；版本与正式发行在修复汇总后准备。
