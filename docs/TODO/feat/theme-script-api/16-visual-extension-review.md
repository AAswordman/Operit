# 主题视觉扩展方向评估

本页依据当前主题模型、Compose 绘制、AndroidSVG 素材准备和脚本上下文评估后续建议。它是待讨论的演进方案，不代表已实现，不扩大 PR #99 的运行能力，也不作为当前合入 dev 的前置条件。

当前基础已经包含 19 个区域、原生状态覆盖、形状内侧描边、图片/九宫格、8 dp 图片模糊、全局及局部排版、固定/内容图标着色和浅深模式。普通 Surface 的材质高度与任意图层发光是不同能力，不能把已有 elevation 描述成图层阴影。

## 建议顺序

1. 优先增加原生渐变，整理原生状态过渡与涟漪配置；这几项能直接改善常见按钮、卡片和输入外观
2. 单独实现受控投影/发光，再评估共享离屏绘制阶段的图层遮罩；明确裁剪边界与缓存后再开放参数
3. 增加可选 Material You 色板和明确的窗口环境上下文，继续复用已存在的玻璃参数
4. SVG 分路径动态色与 Shader 分别做独立方案；Shader 先考虑受控预设，不先承诺任意源码入口

合入 dev 用于收敛现有功能；不能用新增特效替代已确认的背景、保存或预览接入问题修复。

## 渐变：推荐，独立字段保持现有 color 语义

当前 `ThemeScriptLayer.color` 为可选字符串，模型、序列化、校验、编辑器和 TypeScript 声明均依赖它；静态 SVG 素材已经可以携带自身的渐变，但工作室没有原生渐变参数。

建议增加独立、类型明确的渐变字段或填充结构，保留现有颜色字符串。在线性/径向类型中定义颜色停点、归一化起终点或中心/半径、透明度及与纯色的互斥规则；角度需要规定坐标方向，不能只给一个无定义的 45。

Compose 的 Brush 与 Android 的 LinearGradient/RadialGradient 可以承担绘制，但还需接入模式、状态、导出、编辑器及边界校验。不要直接把现有 `color` 改成“字符串或任意对象”，使已导入工程或严格字段解析失去明确语义。

## 投影与发光：视觉收益高，渲染改动大于几个字段

`shadowColor/radius/offset` 的方向合理，单位和范围必须统一。阴影需要保留原图 alpha，发光需要定义扩散与颜色；当前区域先裁剪再画图层，直接加模糊会把外发光截掉。

不能将 `Paint.setShadowLayer` 视为所有 Android 版本和绘制路径都通用的一行实现。Operit 最低 API 为 26，非文字绘制的硬件支持与低版本限制必须核对；当前矩形/椭圆走 Compose DrawScope，图片/SVG 又使用不同画布入口，需要统一离屏层或合适的原生渲染阶段。

第一版应明确效果作用于图层还是整个部件、是否允许超出区域、扩展像素如何计入预算，以及与正文/边框的顺序。采用按素材、尺寸、密度和参数变化更新的缓存，避免滑杆拖动时重复生成大位图。建议先限定效果类型，再以实测确定半径与总像素限制。

## 状态过渡：推荐，保持原生状态与一次求值

当前已经有 pressed/focused/selected/disabled/error 覆盖，按钮、图标按钮和装饰共用交互状态。过渡应发生在原生绘制阶段，不逐帧执行 JS。

`animateColorAsState` 只能覆盖颜色；边框宽度、透明度等需要相应动画值，并让原生容器、正文和装饰读取同一进度。需要规定主题/角色切换、禁用、快速连续点击和初次加载的行为，遵循系统动画时长设置。第一版限制为明确列出的属性，形状或不同素材切换不能默认视为可直接插值。

## 涟漪：合理，配置现有点击部件的反馈

当前按钮仍由 Material3 持有点击、启用状态、交互源和无障碍。`ripple()` 是原生 indication 工厂，不是给任意容器直接追加的 `Modifier.ripple()`。

应在已接入按钮/图标按钮/导航入口的正确作用域配置 Material3 的 ripple 颜色，例如核对当前版本的 LocalRippleConfiguration/RippleConfiguration。不要再包一层 clickable，以免出现双涟漪、重复点击或丢失禁用/读屏行为；不具备点击语义的 Surface 不显示该属性。

## Material You：有价值，但要区分系统色和主题色

当前 `context.colors` 已有 36 个实际主题 token，来源是应用基础色与角色外观；当前 `ThemeColorSchemeResolver` 使用应用浅深色板及自定义主/副色，没有读取 Material You 动态色板。不能把现有 colors 直接称为壁纸提取色。

Android 12/API 31 起可用 dynamicLightColorScheme/dynamicDarkColorScheme 取得系统动态色板；如果确实需要壁纸提示色，则核对 WallpaperManager.getWallpaperColors 的版本、空值和含义。Palette 负责从提供的位图提色，不负责直接取得手机壁纸；不能把 UiModeManager.getSystemAppearance 当作壁纸取色接口。

建议分别定义原始系统色板与最终主题色，提供明确可用性和版本要求，再决定角色是否选择跟随。壁纸返回色并不保证总有五个有独立语义的颜色，不能承诺固定五色而不定义生成规则。预览需要明确系统色样本与应用最终色的差别。

## 环境感知：按窗口提供，避免重复 mode

当前 `context.mode` 已经给出实际 light/dark，也适用于工作室样例明暗；再提供同义 isDarkMode 会形成两个来源。若要暴露真实系统模式，名称应与当前主题模式区分。

屏幕像素宽高不等于聊天可用区域，分屏、旋转、侧栏和窗口尺寸都会改变布局。建议先定义 viewport 的宽高 dp、density 和 fontScale，说明来源是窗口还是具体预览区域。当前图层 bounds 已归一化，大多数铺放不需要 JS 获取物理屏幕尺寸。

上下文按稳定的窗口/配置变化更新并保持冻结，不引入逐帧回调。主题预览和正式页面都要使用各自实际容器，而不能在小预览中传整台手机的宽高。

## 玻璃联动：已有参数通道，优先补说明与有效值语义

`context.settings.floats.glass_blur_radius` 已经存在；当前上下文还提供玻璃相关开关及区域参数，过滤的是媒体位置与角色业务元数据。此项不是从零新增接口。

制作者现在就可以根据开关返回透明填色或半透明装饰。需要注意全局数值与区域覆盖的优先级；若后续提供“部件实际生效的材质参数”，应复用原生解析规则，不能让 JS 再猜一遍。背景能否透出仍取决于原生内容面、填色和区域透明度，不会因读取了 blur 字段就自动建立毛玻璃采样。

## SVG 分路径动态色：需求合理，建议中的实现不适用于当前资产管线

当前 SVG 经 AndroidSVG 解析并录制成 Picture，不是转换成 VectorDrawable；现有整图 tint 与保留多色原图已经可用。VectorDrawable 的命名路径也不是通用的完整 SVG DOM 编辑接口。

如果以后支持 pathColors，应先定义允许的 SVG 子集、稳定 ID、fill/stroke/渐变的覆盖规则和每份素材的缓存键。更适合在安全解析后的受控结构中应用语义色，再生成记录；避免绘制时反复改 XML，也避免不同角色共享可变 Picture。现阶段可用浅深分别绑定静态多色 SVG，先验证具体制作需求。

## 遮罩：可行，必须隔离合成区域

PNG alpha 遮罩可以补充当前 rounded/cut/circle 裁剪。DST_IN 只是合成步骤，还要把被遮罩图层与 mask 放进独立离屏层，定义其 bounds、采样、反相与透明度；直接在主画布上混合可能把正文或其他图层一起擦除。

建议第一版只作用于明确指定的图层，不改变原生正文或触控范围。素材解码、缓存和离屏像素预算要纳入现有资源限制，导入/导出与九宫格/模糊的先后顺序需要明确。

## Shader：长期可选路线，先做受控预设

RuntimeShader/AGSL 从 Android 13/API 33 起可用；Operit 最低 API 26，因此不能称为所有支持设备的必经能力。声明式外观、图片、SVG 和原生渐变已经能覆盖大量主题，不需要先开放 Shader 才能继续发展。

建议先实现噪点、静态纹理、极光等经过测量的内置预设，只开放明确的颜色、强度或速度参数。需要独立考虑编译缓存、GPU/离屏像素成本、可见性、后台暂停、帧率与系统动画设置；当前 QuickJS 的 100 ms/16 MiB 预算不能约束 GPU 工作量。

任意 AGSL 源码入口应另行评估兼容声明、编译诊断、复杂度与运行成本，不能只放宽一个字符串字段就认为完成。若将来开放，老设备对该能力的处理必须由协议明确规定。

## API 参考

- [Compose 图形与 Brush](https://developer.android.com/develop/ui/compose/graphics/draw/overview)
- [Android 硬件绘制支持表](https://developer.android.com/develop/ui/views/graphics/hardware-accel#drawing-support)
- [Material3 涟漪迁移与配置](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/migrate-indication-ripple)
- [Material3 动态色](https://developer.android.com/develop/ui/compose/designsystems/material3#dynamic_color)
- [WallpaperManager](https://developer.android.com/reference/android/app/WallpaperManager)
- [RuntimeShader](https://developer.android.com/reference/android/graphics/RuntimeShader)

现有字段与边界见[协议概览](../../../doc-src/feature-protocol/theme_script_api.md)，本轮背景修复与交付范围见[实施记录](15-background-and-acceptance.md)。