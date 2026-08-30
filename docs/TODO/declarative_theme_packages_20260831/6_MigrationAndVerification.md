# 迁移批次与验证

## 批次

1. 删除旧 Theme Studio 和目标级视觉主题基础
2. 建立全局主题选择、内置基底主题和固定安全主题
3. 建立 Scene DSL、资源模型、校验器和运行时表
4. 实现 `app.shell.v1` 与 `chat.main.v1` 的宿主适配器
5. 建立三个主题包样例与安装、预览、激活流程
6. 迁移通用组件页面
7. 迁移业务完整场景和独立宿主外壳

## 每批次要求

- 更新本目录中的语义清单和迁移状态
- 同时删除被替代路径，不保留两套视觉来源
- 为纯数据、链接与资源校验补充 JVM 测试
- 为主场景、输入、消息、导航和无障碍补充 Compose 测试
- 对参考主题执行手机、平板、深浅色、字体缩放、减弱动效和空/加载/错误/流式截图矩阵
- 包格式、场景 API 和示例主题在对外发布前由同一 schema、SDK 文档和 CI 校验生成

## 发布门槛

- 全局主题切换不依赖 `ActivePrompt`
- 所有已迁场景只有一个视觉主题来源
- 场景链接、资源校验、参数校验和能力校验都有稳定错误码
- 固定安全面在主题包损坏或删除时始终可用
- 主题包样例证明相同聊天语义能生成赛博、奇幻和像素三种完整界面

## 构建记录

### 2026-08-31：批次 C（旧存储清除 + 元数据搬前缀）release 构建

- 分支：`feat/plugin-interface`
- 提交：`a5547df0`（删旧存储/API + 迁移）、`5d6c20c9`（键常量与模板字符串修复）
- 构建服务动作：`build_current_release`
- 构建服务状态：`success`
- 产物：`operit-release-feat_plugin-interface-5d6c20c9.apk`
- 产物大小：`402880159` 字节
- SHA-256：`787b005ea7f07695aa803c2ed126f81dafc80d7ec97efa2c2c4751534f73776c`
- 落地：删 `ThemePreferenceSnapshot`/`NativeThemePreferenceSchemaV1`/`RulesV1`/`OptionsV1`/`ThemeScopeMigrationPolicy`/`ThemeTargetOperationCoordinator`/`ThemeColorSchemeResolver` 及 UPM 全部 per-target 主题 API、死键常量、recentColors；`ActivePromptManager` 移除主题协调器；两个管理器移除主题生命周期调用；AI 头像/聊天标题存储搬到 `character_{card,group}_metadata_{id}_` 前缀（`migrateLegacyThemeStorage` 一次性迁移，启动执行，旧视觉键全清，默认卡用户头像并入全局）；WebChat `/theme` 与 structured render 改读全局呈现（AI 头像按聊天绑定保留）。
- `a5547df0` 首轮失败：迁移函数模板字符串转义损坏与两个元数据键常量被误删；`5d6c20c9` 修复。

### 2026-08-31：批次 A/B（编辑器删除 + 全局呈现运行时）release 构建

- 分支：`feat/plugin-interface`
- 提交：`1af02486`（删编辑器 51 文件 -11595 行）、`124c4385`（全局呈现设置 + 设置页 + WebChat bootstrap）、`c2608005`（运行时切全局源 50 文件 -4011 行）、`5150a08e`/`c1b60f11`（编译修复）
- 构建服务动作：`build_current_release`
- 构建服务状态：`success`
- 产物：`operit-release-feat_plugin-interface-c1b60f11.apk`
- 产物大小：`402908831` 字节
- SHA-256：`27bb291eeb76b811101ed3df19d84b45f97eaa6e7266377632ecabcb355df373`
- 期间失败：`1af02486` import 拼接语法错误；`c2608005` 子代理三处残留（ChatScreenContent import 拼接、BubbleUser 重复 import、ClassicChatInputSection containerShape）与 `NATIVE_THEME_V1_DEFINITION_ID` 误删；均已修复。
- 落地：`LocalThemePreferenceSnapshot`/`rememberActiveThemePreferenceSnapshot`/`activeThemePreferenceSnapshotFlow` 及快照驱动解析全部移除；`resolveGlobalThemeV1` + `LocalGlobalPresentation`/`LocalResolvedGlobalTheme` 成为唯一主题源；背景媒体/自定义颜色/玻璃/贴图/自定义字体/chrome 定制从运行时删除；AppContent/抽屉/聊天组件/浮屏/离屏/Glance 全部切全局；WebChat bootstrap 改读全局呈现。JVM/Compose 测试未在本动作执行。

### 2026-08-31：批次 1 清理 + 批次 2/3 基础 release 构建

- 分支：`feat/plugin-interface`
- 提交：`57646123`（移除 Theme Studio 试点）、`9b24f6f5`（stat 合同字段修复）、`db2e928b`（全局选择与 Scene 契约）
- 构建服务动作：`build_current_release`
- 构建服务状态：`success`
- 产物：`operit-release-feat_plugin-interface-db2e928b.apk`
- 产物大小：`403527327` 字节
- SHA-256：`a0dfaf5538a206fad06e938db79c46655e4ae6e46a19ac56691226076a0089dd`
- `57646123` 首轮编译失败：stat 合同回退 1.0 时误删 `styleFamily`/`styleParts`/`styleStateAxes` 必填参数；`9b24f6f5` 恢复最小 surface+content 声明后通过。
- 本轮 release 汇编验证：Theme Studio 试点移除后生产源集编译通过；`theme_package_selection` DataStore、`ThemeInstanceV1` 模型、Scene DSL v1 契约/校验/目录与配套 JVM 测试源集纳入构建。JVM 测试未在本动作执行。
