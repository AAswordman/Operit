# 浏览器跳转与 UIAutomator 旋转状态保护

分支继续使用 `feat/chat-batch-and-settings`，更新同一个 PR #18，目标为 `dev`。

## 问题与证据

内置浏览器原先为每次新窗口请求创建 WebView，并为每次外部协议请求生成新的确认状态。持续探测、重复开窗、取消后自动重试会累积窗口并反复刷新界面。本轮修复这两个通用机制，QQ 特定网站的卡死仍缺少可复现页面与真机日志。

Root 授权入口只检查授权、刷新状态，未找到直接开启自动旋转的代码。Root 与 Shell 页面读取、按元素点击会执行 `uiautomator dump`；Android 的 `UiAutomationConnection` 在连接与断开时保存、恢复旋转状态。本轮保护这条已定位路径，尚不能认定“仅授权即触发”的全部根因已经解决。对应系统实现见 [UiAutomationConnection](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/UiAutomationConnection.java) 与 [DisplayRotation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/wm/DisplayRotation.java)。

虚拟屏幕开启后整体降到几帧的反馈等待补充资料。本轮不修改视频、截图、输入、悬浮聊天窗或虚拟显示配置。

## 浏览器实现

- `websession/browser/BrowserNavigationPolicy.kt` 独立管理开窗与外部跳转；每个文档允许一次无手势自动开窗，同时存活的弹出标签最多八个。关闭弹出标签后释放总量名额，后台或已关闭页面不能抢占当前页面
- `BrowserWebViewSupport.kt` 识别短点击，保留两秒内一次可消费的操作记录，以支持没有 WebView 手势标记的异步登录脚本。滚动、长按和多指动作不会生成该记录
- 外部跳转只接受主框架或用户操作，无手势子框架探测被拦截。`intent://` 与其他应用协议共用策略；待确认期间不生成新请求，确认或取消后自动请求保持抑制，页面自动重载和重定向也不解除抑制
- 用户重新点击可以重试；地址导航、前进、后退、手动刷新和桌面模式切换重置导航尝试。普通 HTTP(S)、Cookie、Blob 和 Data 下载仍走既有实现
- `PendingExternalOpenRequest` 记录来源会话。页面导航、切换标签或关闭来源标签时清理请求；确认与取消保留请求 ID 校验，过期按钮不能启动旧请求

## UIAutomator 实现

- `system/uiautomator/UiAutomatorDumpGuard.kt` 使用互斥锁串行执行本应用的 dump，在同一次调用中读取当前用户的 `accelerometer_rotation` 和 `user_rotation`
- 保护命令显式启动 `sh -c`，完整脚本作为单个参数传递，兼容 Root 的 Shell 身份启动器、libsu、exec 与 Shizuku 的现有命令交接方式；实际设备后端兼容性仍待验证
- 读取失败或快照不合法时停止，不执行未受保护的 dump。退出处理分别恢复方向与自动旋转开关，仅写回发生变化的字段；原本未设置的字段恢复为未设置状态，写入后回读确认
- 普通退出、dump 失败与可捕获的终止信号都进入恢复处理；方向恢复失败仍继续尝试恢复开关。dump 的非零退出码优先保留，dump 成功但恢复失败时返回失败
- `DebuggerUITools.executeUiShellCommand()` 集中接入保护器，Root 继承该入口，覆盖读取页面和按元素点击的 dump。显式 display 读取失败时直接报告错误，不再读取默认屏幕，避免返回错误页面

## 兼容性与限制

网页后台自动拉起应用及连续自动开窗现在会受到限制。需要用户再次点击、主动导航或关闭多余弹出标签才能继续相应操作；正常登录是否符合目标网站的实际流程，需要随构建产物验收。

旋转保护会增加系统设置读取操作；执行期间用户主动修改旋转设置也可能被调用前的快照覆盖。该机制只保护本应用经过入口的 dump，无法处理系统强制杀进程、其他应用同时修改设置或尚未定位的授权路径副作用。

## 验证记录

新增 13 个浏览器策略 JVM 用例，覆盖自动开窗限额、后台页面、待确认去重、确认取消后抑制、重定向、主动导航、点击时效及异步子框架登录。

新增 12 个旋转保护 JVM 用例，在临时目录模拟 `settings`、`uiautomator` 和 argv 启动器，覆盖开关与方向恢复、未设置字段、dump 失败、快照读取和格式错误、恢复写入失败、写入无效果、外层 errexit、启动器传参及并发串行化。用例不访问设备系统设置。

本地仅完成差异空白、调用点、Shell 传参及清单完整性的静态核对，未执行编译、构建或测试。Android Build 使用 `assembleDebug`，Android Tests 执行 JVM 回归；本次提交对应运行链接与实际结果记录在 PR。尚无 QQ 登录真机复现或 Root 设备验证结论。

[完整验收清单](02-完整验收清单.md) 保留此前 318 项，新增 18 项浏览器与 12 项 Root/Shell 场景及独立空白结果行，共 348 项。工作流状态与真机结果分别记录。

## 实现状态

[DONE] 代码、JVM 回归用例及验收文档已完成。CI 与真机结果以各自记录为准。
