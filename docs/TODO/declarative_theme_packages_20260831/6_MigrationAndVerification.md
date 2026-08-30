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

### 2026-08-31：批次 F（chat.main 接线 + 赛博 `.otheme` 样例）release 构建

此记录中的 bundled 赛博样例已被未发布阶段的外置主题仓库方案替换，不再代表当前 APK 内容。

- 分支：`feat/plugin-interface`
- 提交：`b88dee68`（chat.main 六槽 host 接线）、`033d60f1`/`7c2d8313`（赛博样例归档与根节点编码修复）、`ee2eaf40`（bundled 样例保留）、`433337cd`（九宫格源像素/目标 dp 语义修复）
- 构建服务动作：`build_current_release`
- 构建服务状态：`success`
- 产物：`operit-release-feat_plugin-interface-433337cd.apk`
- 产物大小：`403585491` 字节
- SHA-256：`7d8776cda424f0f39ecec35f622e9cb9d39497b4c246490867651f591535b4cd`
- 容器验证：`:app:compileReleaseKotlin` 与主题包/场景/全局主题 JVM 测试通过；当时的 bundled `cyber-grid.otheme` 使用同一归档校验器通过。
- 落地：`ChatMainSceneHost` 将 configuration/header/transcript/composer/classic_settings_rail/overlay_stack 六个稳定语义槽映射到激活场景；reference 场景维持现有 Compose 行为；赛博包使用 NASA Hubble Ring Nebula 衍生背景（来源、归属和链接写入 manifest 与 `ATTRIBUTION.md`）及项目自生成九宫格框体。背景图已可透过主内容壳层。
- 尚需设备验证：默认包首次安装、主题页列表刷新、从赛博 Release 导入并启用、主色/背景参数、输入/IME、聊天流式、选择器、TalkBack 与不同密度下九宫格框体。

### 2026-08-31：批次 E（`.otheme` 格式、安装器、内置参数和主题页）release 构建

- 分支：`feat/plugin-interface`
- 提交：`f437db2f`（manifest/校验器/发布器/安装器/设置页）、`7e3b49de`/`c52e6239`/`df6da944`（集成、可见性和原子发布修复）、`f03762f8`（基底/token 链接和背景穿透）、`f6a09d50`（格式文档与模型约束）
- 构建服务动作：`build_current_release`
- 构建服务状态：`success`
- 产物：`operit-release-feat_plugin-interface-f6a09d50.apk`
- 产物大小：`402953887` 字节
- SHA-256：`1b0dd00bb83b551de832a6a903ad700029878522dc97a1b45bab62e63f460a31`
- 容器验证：`:app:compileReleaseKotlin` 与 `:app:testReleaseUnitTest --tests "com.ai.assistance.operit.data.theme.packages.*" --tests "com.ai.assistance.operit.ui.theme.scene.*"` 通过。
- 落地：专有 `.otheme` 扩展名、根 `operit-theme.json` 严格 manifest、资源 SHA-256/MIME/路径/压缩限制、内容寻址私有安装目录、精确基底依赖、默认 `operit.default` 的主色/静态背景图参数、设置页本地导入/启用/卸载和参数编辑；场景 token/资源链接在安装阶段校验。默认包源与赛博包源均在独立 GitHub 仓库维护，后者不随 APK 交付。尚未做设备端主题页操作和聊天场景换肤验证，后者属于批次 F。

### 2026-08-31：批次 D（Token 池 + Scene 渲染器 + 资产仓库）release 构建

- 分支：`feat/plugin-interface`
- 提交：`5a2cb156`（Token 模型/渲染器/资产仓库 + 测试）、`77cc96b2`/`8e999f75`（编译修复：路径解析器引用、IntOffset/IntSize、when-else）
- 构建服务动作：`build_current_release` + 容器内 `:app:testReleaseUnitTest --tests "com.ai.assistance.operit.ui.theme.scene.*"`
- 构建服务状态：`success`；JVM 测试通过
- 产物：`operit-release-feat_plugin-interface-8e999f75.apk`
- 产物大小：`402904735` 字节
- SHA-256：`77754653710eda4e81d62fa2ee497bec5aef25dd19eae406e8f04ea6af41b2b3`
- 落地：`ThemeSceneTokenSetV1`（颜色/尺寸/文字样式 token，fail-fast 解析器 + 引用校验，文字样式支持包内字体）；`ThemeSceneAssetRepositoryV1`（位图解码带 4096² 像素上限、字体 FontFamily、归一化 M/L/Q/C/Z 路径解析与像素投影）；`ThemeSceneRendererV1`（13 种节点 Compose 渲染：stage/layer/row/column/grid/frame 锚定布局/host_slot 注入/surface 填充描边/位图三种 fit/九宫格九区拉伸/文字/路径填充描边/graphicsLayer 变换）。纯增量，未接线任何页面。

### 2026-08-31：stat 目录版本修复 release 构建（含目录校验 JVM 测试）

- 分支：`feat/plugin-interface`
- 提交：`07e47f87`（stat implementedVersion 回 1.0）、`f6e0ae63`（测试编译修复：`NativeThemeRuntimeTest` 缺省参数 + 删分支既有坏测试 `XaiProviderReasoningTest`，其实现 `XaiReasoningMapper` 已在 `b9a0ce01` 后续重构中移除而测试残留）
- 设备事故：`5d6c20c9` APK 启动即崩——`NativeThemeComponentCatalogV1.<clinit>` 校验"stat 合同 1.0 vs 实现 1.1"抛 `IllegalArgumentException` → `ExceptionInInitializerError`。release 汇编不执行 object 初始化，故历轮构建未拦截。
- 修复验证：`07e47f87` 全量核对六组件合同/实现版本一致；`f6e0ae63` 在构建容器内运行 `:app:testReleaseUnitTest`（`*CatalogV1*`、`ui.theme.*`、`GlobalPresentationPreferencesTest`、`data.theme.packages.*`、`ui.theme.scene.*`）全部通过——目录 `<clinit>` 校验自此被测试覆盖。
- 构建服务动作：`build_current_release`
- 构建服务状态：`success`
- 产物：`operit-release-feat_plugin-interface-f6e0ae63.apk`
- 产物大小：`402880159` 字节
- SHA-256：`87c45ce74f7ab6d2275da93cc5dfdf3b62f60a4c00235c54de8e8c1dcb9b6132`

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
