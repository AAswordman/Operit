# 废弃工具箱首页与孤立文件管理器清理

维护者在路径整理期间要求核对原 ui/features/toolbox/screens，确认不用的页面直接删除。基线为 b419768cd9fa166b56031bcb38bf5fe957d69e98。

## 调用核对

此前移除的是工具箱导航入口和卡片，Screen.Toolbox、hidden.toolbox 与空的 TOOLBOX 导航面仍存在。工具箱没有可发布的卡片，旧首页只剩空网格。内置文件管理器只由没有调用者的 FileManagerToolScreen 包装函数引用，没有注册为独立 Screen，也没有其他调用者。

日志页面由 DataAndBackupScreen 使用，UI 调试由 UIDebuggerService 与浮窗使用，HTML 导出资源对话框由聊天导出使用。SqlTableView 仍由 SqlViewerScreen 的 AndroidView 创建，不能作为孤立文件删除。其余原生工具页面保留在路由目录，供已有内部入口和插件调用，不能仅因没有侧边栏卡片就整包删除。

## 已实施

- 删除旧工具箱首页、卡片模型和卡片组件，删除 Screen.Toolbox、NavItem.Toolbox、hidden.toolbox、TOOLBOX 界面枚举及空的 toolboxEntries 聚合。
- 删除 filemanager 子目录的 13 个 Kotlin 文件及对应无调用包装函数，连同原 ToolboxScreen.kt 共删除 14 份旧文件。删除没有调用者的 FFmpegToolboxToolScreen 和 LogcatToolScreen 包装函数，底层有效页面保留。
- 将仍有路由调用的 TerminalAutoConfigToolScreen、UIDebuggerToolScreen、ShellExecutorToolScreen 原样移出旧首页文件。终端页面归属 Terminal 导航项，其他工具页面归属 Settings，保留各自原始路由 ID。
- 插件的 toolbox 声明仍被解析为既有模块，包详情中的模块入口继续保留；既有过滤不再发布工具箱卡片。此处是有效插件协议处理，不属于空首页代码。

已核对源码、示例、工具脚本：旧首页、空导航面、文件管理器目录和三个无调用包装函数均无残留绑定引用。git diff --check 通过。未运行本地应用构建或测试，随本轮最终工作分支派发仓库验证。

后续路径迁移把保留的日志、诊断、导出和语音页面分别放回对应功能，原 toolbox 目录整体退出布局。

[DONE] 源码清理与静态引用核对。
