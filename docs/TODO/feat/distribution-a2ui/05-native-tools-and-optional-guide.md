# 原生工具与外部可选引导

仓库：https://github.com/3316891527/Operit
分支：`feat/distribution-a2ui`

原状：Express 编译和 Compose 渲染已经是 Kotlin，但模型生成、修订和实时数据更新曾通过内置 JS 包调用 NativeInterface。单独移除脚本会同时移除这些工具入口。

目标：将生成、修订、数据更新、组件目录和图标查询注册为原生工具；外部引导包仅返回说明和示例，不执行生成操作，也不随 APK 内置。宿主、原生工具和测试统一使用 a2ui 包路径。

作用域：独立 `core/tools/defaultTool/a2ui/` 复用可信 ToolRuntimeContext、CardDelivery 与 CardLiveStore，并接入现有工具权限和角色卡可见性。JS 生成桥接在尚未正式发布的 A2UI 迭代中移除，完整卡片通过原生渲染器读取。

实现：

- A2uiToolRuntime、A2uiTools 和 A2uiToolDefinitions 直接注册五个原生工具；JsEngine 中的 A2UI 生成桥接与专用上下文捕获已经移除。
- 41 个宿主 Kotlin 文件、23 个宿主测试文件和 29 个测试资源位于 a2ui 路径；普通卡片与 live 快照的私有状态目录统一为 `files/a2ui/`，命名与目录整理见[后续步骤](06-a2ui-naming-and-storage.md)。
- 旧内置生成脚本已从 assets 和打包白名单移除。后续 +21 已移除外部示例引导脚本，语法通过原生 `a2ui_catalog` 获取；原始完整脚本的手机备份不属于仓库发行文件。
- 原生入口有 8 个 JVM 用例，覆盖无需引导包生成、可信上下文、请求结束、无效输入、引用隔离、修订和 live 版本控制。完整人工清单保留 40 项。

验证：原生入口的活动请求、输入校验、引用隔离与修订由现有用例覆盖。静态核对后派发 Debug 和 Android Tests，不执行本地构建或测试；正式发布等待用户 Debug 验收。

[DONE] 源码实现已完成；静态检查、云端检查与实机验收分别记录，源码完成不代表运行验证通过。
