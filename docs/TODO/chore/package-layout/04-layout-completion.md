# 清除废弃工具箱命名并完成 Android 包名迁移

基线为 `5efa22eceb33738ca57dcca528eafb2b7c118797`。维护者明确接受当前阶段的插件 API 和桌面组件兼容性变化，要求有效功能使用合适名字，组件目录和包声明全部统一。本轮继续在 refactor/package-layout 与 PR #67 提交。

## 已实施内容

1. 将实际负责 ToolPkg 应用生命周期的 ToolboxPlugin 移入 plugins/toolpkg，并命名为 ToolPkgLifecyclePlugin，同步注册和运行标识。
2. 将仍由插件详情使用的 UI 模块注册统一为 registerUiModule / registerToolPkgUiModule，数据类型、字段、注册桶、内置插件、预编译脚本与 SDK 声明一并更新。导航挂载面使用 package_details 表示包详情入口，按维护者最新要求保留旧工具箱 UI 注册 API 的空操作兼容入口，不返回内容、不注册任何界面或导航；配套旧导航声明忽略，新界面默认仍使用 Compose DSL。
3. 将 FFmpeg 页面、原生路由及用户协议入口按真实功能改名，删除无引用的旧工具箱资源，同步有效页面标题。
4. 将三个 DocumentsProvider、五个 Widget/Glance/配置 Activity 和 OfflineResourcesActivity 的包声明全部对齐物理目录，更新 Manifest、Widget XML、完整名称和同包隐式绑定。
5. 对固定基线执行逐文件受控变换核对、完整源码集合与范围外摘要检查，核对 Manifest/Widget 目标、桥接/API/示例一致性，并更新文档。
6. 提交推送到原分支，更新原 PR 并派发仓库 Android Build 和 Android Tests；不运行本地应用构建或测试，不轮询 CI。

## 兼容影响

插件注册的是宿主内部的布局、路由和小组件定义；Android 桌面绑定的是 Manifest 中的原生 AppWidget 接收器，两者处于不同层。原生接收器完整名称变化后，已有桌面小组件可能需要删除并重新添加。旧插件调用工具箱 UI 注册 API 或旧导航挂载面不会生成内容，也不会因这些旧声明中止加载；需要实际界面时使用新的 registerUiModule / registerUiRoute，省略 runtime 仍按 compose_dsl 处理。桌面组件不增加旧类转发。

authority、applicationId、存储字段、桌面插件 registerDesktopWidget API 与未纳入本轮的 Java 桥/JNI 入口保持现有定义。

## 静态验证结果

68 个运行代码、资源、内置插件与辅助文件的受控变换核对通过，其中 32 个 Kotlin/Java 文件仅发生批准的名称、包声明或注释调整，其他 2,390 个源码的 Git blob 摘要不变，全部 2,422 个源码集合无遗漏。九份此前保留的包声明已对齐物理路径，Manifest 七个组件入口和 Widget 配置目标一致。

九套语言资源中删除 45 份无调用的旧资源定义；5 个内置插件的源码和已提交脚本共 10 个界面注册调用同步完成。5 处宿主/SDK/脚本桥接声明一致，92 份 JNI/Binder 相关原生协议文件不变。扫描 3,504 个有效文本文件，除逐段核对的 11 处必要兼容代码与类型声明外，废弃模块、导航与路由标识无残留；图标库自带的检索关键词和历史迁移文档不属于旧功能代码。

当前第二轮 677 个文件的实际路径与包名见 [最终路径清单](ui-utilities-migration-final.csv)。之前的第二轮 JSON/CSV 是 `5efa22e` 的历史快照，不覆盖本轮收尾结果。完整静态证据和全部可选设备回归见 [收尾验证](verification-completion.md)。

[DONE] 命名、插件 API、Android 包声明与静态核对。最终提交后派发仓库构建与 JVM 测试，受理状态记录在 PR，执行结果另行确认。
