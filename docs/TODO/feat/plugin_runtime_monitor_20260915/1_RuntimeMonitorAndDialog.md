---
For_Agent: 插件运行监测实现记录
---

# 运行监测与弹窗入口

## 旧实现
- 插件详情弹窗只展示元数据、配置入口、模板和子包工具
- ToolPkg console 日志会进入全局日志和 packageLogs 文件
- JS 执行入口没有按插件聚合调用耗时和内存快照

## 新实现
- 新增 `ToolPkgRuntimeMonitor` 按包名记录插件执行统计
- `JsEngine.executeScriptFunction` 在调用开始、完成、异常和取消时写入运行统计
- `console.log/info/warn/error` 和 JS 错误进入当前插件的近期日志列表
- 插件详情弹窗新增“性能监测”按钮，可查看运行概览、QuickJS 内存快照、执行峰值和插件日志
- 监测弹窗每秒刷新一次，清空日志只影响当前插件的近期日志列表
- QuickJS JNI 提供运行时当前分配量和调用期间峰值分配量，宿主 App 的 JVM、Native Heap 和 PSS 不再作为插件内存展示

## 验收点
- 打开插件详情弹窗后可以点击“性能监测”
- 执行插件后能看到调用次数、耗时、失败次数、QuickJS 当前分配量、调用峰值和内存增量
- 插件 console 输出按 info、warn、error 展示在该插件日志中
- 旧的包详情、子包开关和配置入口仍保留原有交互

[DONE]
