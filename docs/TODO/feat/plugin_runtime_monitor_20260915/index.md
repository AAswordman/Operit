---
For_Agent: 插件运行监测功能计划
---

# 插件运行监测

## 现状
- ToolPkg 执行统一经过 `PackageManagerToolPkgFacade.runToolPkgMainHook` 和 `JsEngine.executeScriptFunction`
- JS `console.log/info/warn/error` 已经进入 Native 日志，但插件详情弹窗里没有按包展示
- 宿主当前已有按容器复用的 QuickJS 实例，但此前监测错误地读取了 Android 进程的 JVM、Native Heap 和 PSS

## 意图
- 在插件详情弹窗里新增“性能监测”入口
- 按 ToolPkg 容器包名聚合调用次数、耗时、失败数、运行中调用数
- 记录 QuickJS 实际分配量、每次调用期间的最大瞬时分配量和近期 console/错误日志

## 作用域
- 新增 ToolPkg 运行监测状态对象
- 在 JS 执行入口和 console/error 回调记录指标与日志
- 在 QuickJS JNI 层读取运行时分配量，并在每次插件调用开始时重置峰值计数
- 在插件详情弹窗中展示运行指标和插件日志
- 补充中英文字符串资源

[DONE]
