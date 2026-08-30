# 包安装、资源与运行时链接

## 导入流程

1. 宿主将 `.otheme` 文件复制到 `cacheDir/theme-package-staging/` 私有暂存目录
2. 校验专有扩展名、ZIP 容器、根 `operit-theme.json`、严格 schema、API 范围、基底主题、资源清单、摘要、大小和 Scene DSL
3. 解包到不可变的已安装版本目录，并为每个资源登记内容摘要
4. 链接全局主题参数、基础定义、组件皮肤、场景节点和宿主能力
5. 生成不可变运行时场景表、资源索引和结构化诊断
6. 仅在完整校验通过后允许用户预览或激活该精确版本

已落地的发布目录为 `filesDir/theme-packages/installed/<packageId>/<version>/<archiveSha256>/`。同一内容摘要的导入幂等；发布先写隐藏目录后 rename，安装目录不会暴露半成品。

## 链接规则

- 单继承只允许精确版本和精确摘要
- 带 `basis` 的包必须在本地已安装该精确坐标后才能发布
- 令牌引用必须无环
- 场景只允许注册的 scene ID、slot ID、状态名、节点类型和资源用途
- 每个必需槽位必须被准确放置一次，除非契约明确允许可选或重复
- 场景节点不得跨越宿主声明的安全区、焦点和模态边界
- 资源用途必须匹配类型和宿主能力
- 链接完成后运行时不解析 JSON、不搜索字符串键、不扫描目录

## 全局选择

应用级选择记录保存 `package_id`、精确版本、内容摘要、主题变体和参数值。主题切换作为一个事务更新；所有主题 Compose 宿主观察同一已解析运行时结果。

已落地基础：

- 存储：独立版本化 DataStore `theme_package_selection`（schema 1），单键 JSON `theme_instance_json`，序列化模型 `ThemeInstanceV1`；记录缺失或非法直接失败，不静默换主题。
- 内置参考主题：`operit.reference@1.0.0`（`ThemePackageReferenceV1.BuiltIn`），不伪造归档摘要；已安装包使用 `ThemePackageCoordinateV1(package_id, version, archive_sha256)` 精确坐标。
- Scene DSL v1 契约与校验已落地：`ui/theme/scene/` 的 `ThemeSceneContractV1` 节点集（stage/layer/row/column/grid/frame/host_slot/surface/image/nine_slice/text/path/transform）与 `ThemeSceneValidationV1` 结构化问题清单；场景目录注册 `app.shell@1.0` 与 `chat.main@1.0` 槽位契约。
- `.otheme` v1 已落地：`ThemePackageManifestV1`、严格归档校验器、内容寻址发布器和本地导入器。校验覆盖路径遏制、重复条目、条目/解压大小、压缩比、SHA-256、资源魔数、位图上限、token/场景契约及基底坐标。
- 内置 `operit.reference@1.0.0` 已公开 `primary_color` 与 `background_image` 两个参数；前者驱动受限全局配色派生，后者通过 SAF URI 提供静态背景图。

## 主题参数

主题作者可公开颜色、变体、背景、字体或有限枚举参数。参数模式控制名称、本地化、范围和依赖。普通设置只根据该模式生成控件，不包含组件级样式工作台。

## 安全面

包资源和场景只读、无执行代码、无网络和无平台句柄。安装失败、主题链接失败和激活失败由固定安全 UI 呈现，不能被待安装主题影响。
