# 验证与接口发布

## 验证体系

- 模式测试冻结已发布字段、默认值、类型、作用域和重置分类
- 单元测试覆盖解析、迁移、目标隔离、草稿、资源和组件链接
- 契约套件同时验证 `native_v1` 与真实独立主题包夹具
- 组件目录覆盖状态、窗口、明暗模式、字体缩放和减少动态效果
- 视觉测试覆盖手机、平板、悬浮窗、Canvas、Glance 和固定诊断面
- 无障碍测试覆盖语义、焦点、触控区域、对比度和大字体
- 安全测试覆盖归档路径、重复条目、资源额度、内容类型和版本约束
- 样式测试覆盖全局令牌、组件族、单组件状态的解析优先级，以及边框、圆角、透明度、文字、菜单、图标容器、磨砂、液态和水材质
- 能力测试覆盖声明、表面范围、版本和安装激活拒绝；运行时不得改写主题要求的材质

## CI 与协作

- 从单一模式生成 Kotlin 契约、作者 SDK、校验器和参考文档
- 维护只减不增的旧主题直接访问清单
- 主题契约变化同时触发模式、Android、作者 SDK 和示例包检查
- 提供最小示例、完整示例、组件目录和本地验证命令

## 发布门槛

- 原生 UI 表面清单全部分类并达到计划覆盖范围
- 内置主题和开发示例主题通过相同契约套件
- 42 个冻结组件契约均有生产调用点、完整目录状态和可样式化部件声明
- Theme Studio 可以编辑 Style API v1 的所有开放属性，并与生产使用同一链接器和 Compose 效果宿主
- `native_v1` 与独立样式包在手机、平板、`MAIN`、`FLOATING`、`OVERLAY`、`OFFSCREEN`、浅深色和字体缩放矩阵中通过视觉、交互和无障碍验证
- 升级、备份恢复、角色卡与群组切换、进程重建和资源更新完成验证
- 固定诊断面可以处理不可用、损坏和不兼容主题
- 独立主题包规范、作者指南、版本策略和安全限制完成评审

公开主题 API v1 只在上述门槛满足后冻结。此前的开发主题包使用草案版本，不形成稳定外部契约。

## 验证记录

### 2026-08-28：兼容基线与首个编辑器试点

- 分支：`feat/plugin-interface`
- 提交：`0694396e63b9fdebcc8769973ac92e3b6d0ba280`
- 构建服务动作：`build_release`
- 构建服务状态：`success`
- 开始时间：`2026-08-28 14:05:41`
- 完成时间：`2026-08-28 14:33:05`
- 耗时：`1644.47` 秒
- 产物：`operit-release-feat_plugin-interface-0694396e.apk`
- 产物大小：`403297951` 字节
- SHA-256：`adc3b730f3be2ead9288649ff9a8443a4b7a9a83d39b52b77e98e8e1108cd364`

构建服务检出目录报告四个受构建环境管理的修改文件：`app/build.gradle.kts`、`cmake/operit_git_source.cmake`、`gradle/wrapper/gradle-wrapper.properties` 和 `llm/mnn/CMakeLists.txt`。产物来源提交仍由构建服务记录为 `0694396e`。

用户完成了原安装版本上的覆盖安装。安装过程无报错，启动和基础使用未发现明显问题。本记录属于 release 构建与设备烟雾验证，不代表主题契约单元测试、完整主题组合、角色卡与群组切换矩阵或视觉对比测试已经执行。

### 2026-08-29：编辑器分类与隔离预览收口

- App Chrome 已接入 `native_v1` 定义和通用编辑控件，旧手写颜色 Section 已删除。
- 聊天目标编辑增加目标聊天标题覆盖和 AI 头像；全局用户身份保留在 Profile & Identity。
- 聊天与背景设置预览使用草稿主题解析器，保存完成阶段使用不可取消上下文保护暂存素材。
- 静态检查：`git diff --check` 通过，删除文件和新接口引用已核对。
- 按仓库执行准则，本批次未执行构建、单元测试或 Android Compose 测试。

### 2026-08-29：Conversation 定义驱动迁移

- 新增 `native_v1` Conversation 分区，覆盖聊天样式、气泡颜色、字体、图片参数、头像、圆角和目标聊天标题。
- 资源选择继续使用编辑会话的暂存登记、代次校验、裁剪和 `.9.png` 参数解析。
- Conversation 预览接入生产 `CursorStyleChatMessage` 和 `BubbleStyleChatMessage`，删除旧手写聊天与头像预览。
- 静态检查：`git diff --check` 通过，旧 Conversation UI 符号和生产引用已核对。
- 按仓库执行准则，本批次未执行单元测试、Android Compose 测试或构建。

### 2026-08-29：Conversation release 构建

- 分支：`feat/plugin-interface`
- 代码提交：`41c45ef7`
- 构建服务动作：`build_current_release`
- 构建服务状态：`success`
- 产物：`operit-release-feat_plugin-interface-41c45ef7.apk`
- 产物大小：`403269279` 字节
- SHA-256：`62280222022a28f623298f00a5ce5ca9b4eeede87de7d7bc32f70f59a1286667`
- 首次构建在 Kotlin 编译阶段发现文本键残留和默认参数类型问题，修复提交为 `41c45ef7` 后重试成功。

### 2026-08-29：主界面主题宿主适配器

- 主根的窗口系统栏副作用已移至 `NativeThemeMainWindowChromeHostAdapter`，保留已有状态栏和导航栏决策。
- 主根与消息图片离屏导出共用 `NativeThemeBackgroundMediaHostAdapter`；前者只会禁用当前仍引用失败 URI 和媒体类型的活动目标背景，后者仅记录失败。
- 新增纯窗口和资源写回决策测试，覆盖隐藏状态栏、背景透明、自定义状态栏颜色、默认主题颜色、失败资源匹配和离屏透明度裁剪。
- 静态检查：`git diff --check` 通过，主题根和离屏导出不再直接创建播放器或操作窗口系统栏。
- 按仓库执行准则，本批次未运行单元测试、Android Compose 测试或构建。

### 2026-08-29：主界面主题宿主适配器 release 构建

- 分支：`feat/plugin-interface`
- 代码提交：`a7345720`
- 构建服务动作：`build_current_release`
- 构建服务状态：`success`
- 产物：`operit-release-feat_plugin-interface-a7345720.apk`
- 产物大小：`403252895` 字节
- SHA-256：`66b208733ec9444181895fd23cff07d101d07117d475bb9e0295077d2fab8411`
- release 汇编成功；本动作不执行单元测试或 Android Compose 测试。

### 2026-08-29：OFFSCREEN 消息图片导出宿主

- 后续阶段的唯一兼容边界已记录为 `upstream/main@f323d6c50fa661837fad06d4618462861779b562`；功能分支中间实现不形成额外接口边界。
- `NativeThemeOffscreenHost` 为编辑器预览和消息图片导出提供统一的 `OFFSCREEN` 解析、主题 Local、自定义 Typography 与 Material 投影。
- 消息图片导出保留已发布函数签名、单次目标快照、软件位图、透明外层、立即视频播放和 PNG 输出，删除独立系统配色路径。
- 临时 `ScrollView` 的附加、测量、等待、捕获和移除统一在同一释放范围，取消或异常不会保留离屏视图。
- 纯测试覆盖 OFFSCREEN 环境、自定义颜色、字体、背景和注入基底配色；静态检查：`git diff --check` 通过。
- release 构建和设备级图片、动态配色、视频背景验证待本次提交推送后由构建服务执行。

### 2026-08-29：OFFSCREEN 消息图片导出 release 构建

- 分支：`feat/plugin-interface`
- 代码提交：`70a6bcce`
- 构建服务动作：`build_current_release`
- 构建服务状态：`success`
- 产物：`operit-release-feat_plugin-interface-70a6bcce.apk`
- 产物大小：`403252895` 字节
- SHA-256：`0571913c9bdf59235eaa65d6abb6cdd94ba19b729ae5f0d672d908b7e282acad`
- 首次构建提交 `f3178ba4` 在 `NativeThemeOffscreenHost.kt` 发现多余闭合括号；修复提交 `70a6bcce` 后 release 汇编成功。
- release 汇编不执行单元测试或 Android Compose 测试；设备级图片、动态配色和视频背景验证仍待执行。

### 2026-08-29：FLOATING 与权限 Overlay 宿主

- 独立 Compose 根按 `FLOATING` 或 `OVERLAY` 环境消费活动主题快照、解析结果、Typography 和 Material 投影。
- 浮窗聊天、状态指示器、WebSession 浏览器和最小化指示根已移除颜色字体序列化通道；权限请求 Overlay 不再从主聊天页接收颜色方案。
- 保留浮窗 Service、WindowManager 模式、外部 `show_floating`/`initial_mode`/自动退出和权限决策接口。
- 新增 FLOATING、OVERLAY 解析测试和独立主题宿主 Android Compose 测试；静态检查结果和 release 构建待本次提交推送后补充。

### 2026-08-30：FLOATING 与权限 Overlay release 构建

- 分支：`feat/plugin-interface`
- 代码提交：`943d9e28`
- 构建服务动作：`build_current_release`
- 构建服务状态：`success`
- 产物：`operit-release-feat_plugin-interface-943d9e28.apk`
- 产物大小：`403261087` 字节
- SHA-256：`88b591bb46411feff32659dda0e645f60bdf23686c4917b89784ccf4f37423a2`
- release 汇编验证了生产 Kotlin 源集和移除的浮窗主题传输引用；本动作不执行单元测试或 Android Compose 测试。
- 设备级验证仍需覆盖浮窗权限、模式、IME、活动主题切换、动态色、自定义字体、权限决策和 WebSession 最小化状态。

### 2026-08-30：GLANCE Widget 宿主 release 构建

- 分支：`feat/plugin-interface`
- 代码提交：`f9bfeda1`
- 构建服务动作：`build_current_release`
- 构建服务状态：`success`
- 产物：`operit-release-feat_plugin-interface-f9bfeda1.apk`
- 产物大小：`403248799` 字节
- SHA-256：`05961dab8dd39a657ab1b77754055943a67a055d32e104bb07023f96a1a66f3e`
- release 汇编验证了 GLANCE `ColorProvider`、`ColorFilter`、Widget 主题流、动态色监听、ToolPkg 颜色 token 和生产 Manifest 源集；本动作不执行 JVM 或 Android Compose 测试。
- 设备级验证仍需覆盖语音 Widget 与 ToolPkg Widget 的活动角色/群组切换、固定主题模式、Android 12+ 壁纸替换、应用未预先打开的 Widget 冷启动、ToolPkg 配置和运行时刷新，以及所有 DSL 语义颜色 token。

### 2026-08-30：组件契约核心与导航项试点 release 构建

- 分支：`feat/plugin-interface`
- 代码提交：`f3e975f7`
- 构建服务动作：`build_current_release`
- 构建服务状态：`success`
- 产物：`operit-release-feat_plugin-interface-f3e975f7.apk`
- 产物大小：`403297951` 字节
- SHA-256：`d7b5b869b09cc2f270f958ce02a73eccbbc2e2234f2349328801f3ecaa9314c4`
- release 汇编验证了组件契约、类型化目录 Key、`native_v1` 目录初始化、导航项参考渲染器和生产侧边栏调用；本动作不执行 JVM 或 Android Compose 测试。
- 设备级验证仍需覆盖手机抽屉、展开平板侧边栏、原生路由项、ToolPkg 路由项、ToolPkg 动作项、选中状态、液态玻璃阴影和 48dp 触控区域。

### 2026-08-30：基础组件契约与备份设置试点 release 构建

- 分支：`feat/plugin-interface`
- 代码提交：`8b975eec`
- 构建服务动作：`build_current_release`
- 构建服务状态：`success`
- 产物：`operit-release-feat_plugin-interface-8b975eec.apk`
- 产物大小：`403322527` 字节
- SHA-256：`4b907e38c0bad9b6f4423104c94650fcd8f7df4fafbb37c8bc3d00e742c70e52`
- release 汇编验证了类型化状态编码、目录场景校验、五类基础组件、导航禁用事件约束及备份设置生产调用；本动作不执行 JVM 或 Android Compose 测试。
- 设备级验证仍需覆盖备份概览与管理分区、策略/导入/导出格式及配置空间单选、Room DB 与原始快照操作、标准/警告/破坏性按钮、加载/成功/错误反馈、TalkBack 播报和 48dp 触控区域。
