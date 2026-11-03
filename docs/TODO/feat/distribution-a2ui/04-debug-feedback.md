# 调试反馈修正

目标分支：`feat/distribution-a2ui`，个人仓库 https://github.com/3316891527/Operit 。这是现有 Debug 方案的迭代。

当前工作台终端入口直接打开并创建会话，缺少资源检查；终端父容器透明，工作台切换没有动画。功能模型保存提示位于列表底部，内置包的默认启用范围也过大。

本轮范围：

1. 统一检查终端运行资源。已安装环境直接使用；已有校验通过的归档允许初始化；缺失资源时打开离线资源页的 Ubuntu 分组。
2. 工作台使用不透明背景，并以保留组合状态的水平滑动转场覆盖聊天。
3. 功能模型绑定、思考设置和重置完成后显示 Android Toast。
4. 内置插件容器默认关闭，内置沙盒包仅默认开启指定八项，保留已保存的用户开关；外部导入包继续使用自身元数据。
5. 修正官方 A2UI 源码中当前 Compose 不支持的带键 SideEffect，保留原始来源哈希并补充本地适配哈希。

验证范围：静态差异、默认值作用域和源码来源记录；云端派发 assembleDebug 与 Android Tests。界面验收按完整回归清单填写，不执行本地构建或测试。

实现位置：

- `WorkspaceHubScreen.kt` 的统一入口覆盖主页、加号和会话页签，先检查已安装环境或已验证归档，再导航终端；缺少资源时显式进入离线资源页。
- `WorkspaceHubOverlay.kt` 使用 240ms 水平滑动和不透明背景。退出期间保留最后页面，隐藏后保留工作区组合状态，支持动画中反向切换。
- `FunctionalConfigScreen.kt` 在绑定、思考设置、重置的保存协程完成后直接显示短 Toast，移除列表末尾成功提示及定时状态。
- `BuiltInPackageDefaults.kt` 在资产扫描时收敛默认值。八个默认沙盒包为 `system_tools`、`super_admin`、`various_search`、`extended_http_tools`、`extended_memory_tools`、`extended_file_tools`、`code_runner`、`operit_editor`；所有内置 ToolPkg 容器关闭。只影响首次默认值，保留已保存开关和外部导入语义。后续原生入口迭代已移除内置生成包，A2UI 由原生工具提供，后续 +21 移除可选引导脚本，说明改由 `a2ui_catalog` 按需返回。
- 两处官方 A2UI Compose 文件使用带键 `LaunchedEffect` 报告错误；来源清单分别记录上游和适配后 SHA-256。

源码实现完成。[DONE]

验证状态：差异检查、8 个 Kotlin 文件的静态结构、105 项来源哈希及默认值作用域核对已完成。随提交派发 Debug 构建及 Android Tests；云端通过、APK 包体变化和真机交互仍需实际结果确认。
