# 角色主题的原生外观接口

从零制作与查完整字段，请读 [JSON 与 JavaScript 制作手册](theme_authoring_guide.md)、[只读设置参数目录](theme_settings_reference.md) 和 [无外部素材入门工程](../../../examples/theme-script/starter/README.md)。本页保留协议概览。

`.oktheme` 同时携带原有主题参数、声明式外观、JavaScript 和绘图素材。主题属于角色卡或角色组，沿用草稿、预览、保存、放弃和重置；Android Compose 绘制原生界面，独立 QuickJS 实例计算外观数据。

主题页内的原生工作室提供配色、输入按钮素材、消息与头像和常用原生组件制作。没有提供任意页面重排、业务控件创建或场景树布局；素材替换保留原控件的点击、输入和无障碍行为。

## 制作流程

1. 在主题设置中选择角色卡或角色组，打开“基础 → 主题工作室 → 创作主题”。工作室使用原生全屏界面，编辑器外壳跟随当前应用的浅色、深色或系统模式，文字与图标使用配套内容色
2. 顶部保留“设计／源码／资源”三个工作区。“设计”区使用一条预览工具栏：左侧下拉选择聊天、常用组件、侧栏与顶栏场景，右侧切换浅深模式，并保留全屏与展开／收起。场景和部件选择相互联动。聊天标题位于顶部，消息靠下排列，输入区贴底；小预览满宽等比显示并可上下滚动，选中菜单及已接入的部件后按实际边界定位；全屏完整适配同一场景。预览可收起，没有自由拖拽分隔条。预览模式与编辑器外壳独立，聊天场景可选择经典或智能体，复用正式消息、顶栏、输入与经典设置条；侧栏共用展示布局与历史行，常用组件可打开真实弹窗和面板。预览使用固定数据且点击不触发会话业务，实际内容和窗口尺寸仍会影响画面
3. 打开部件选择页，通过分组侧栏和搜索选择 22 个区域、13 个图标或全局排版与配色。只有容器提供图层；按钮、图标按钮及导航项提供其实际交互状态。全局字体、全局配色和图标不显示图层或状态页签。全局和区域排版均可设置浅深文字颜色，普通主题页字体窗口编辑同一份全局文字色；颜色跟随角色草稿保存和放弃。形状、字体等属性选项等分整行，最小触控高度 52 dp，较多选项换行展示
4. 所有颜色入口打开统一选色器，包含饱和度与明度、色相、透明度、ARGB、RGBA、预设颜色和素材图片点按取色。浅色与深色列独立保存。图标只有已绑定素材的模式才能编辑固定着色
5. “资源”区导入 PNG、JPEG、WebP、静态 SVG、TTF、OTF；支持网格和列表、搜索、图片/图标/字体分类、嵌套文件夹、重命名与移动。素材标识保持不变，路径改动不破坏静态外观绑定；被静态外观引用的资源不能删除，文件夹只能在空目录时删除
6. “源码”区单独铺满工作区，编辑 `theme.json`、`manifest.json` 和清单指定的 JS 入口。支持行号、查找、JSON 字符串高亮、JavaScript 轻量语法高亮、JSON 格式化及脚本文件导入。切换工作区或源文件前先校验并同步；解析失败保留未同步文本，不能应用或导出
7. 工程菜单编辑名称、ID、作者、版本、简介和许可，或载入森林/信纸模板。模板替换当前工程，撤销可恢复。撤销/重做最多保留 24 步，连续滑杆和数值调整合为一步，资源对象随历史恢复。场景、工程操作、源码文件、添加资源和添加图层使用统一的自定义锚定菜单，当前单选项显示选中标记；滑杆沿用项目的细轨圆形样式
8. “保存工程草稿”只保存所选角色的制作进度，下次进入继续编辑；“应用到角色草稿”验证浅深两套脚本结果后返回主题页，仍需点击主题页保存。关闭时可保存制作草稿或放弃本次修改
9. 工程菜单直接导出当前工程为 `.oktheme`，包括当前角色参数、布局、源码、资源和工程信息，不要求先应用。工作室“导入工程”只载入包内的脚本工程；完整主题的角色参数应用继续使用原有完整主题导入页。没有脚本工程的包在工作室中明确拒绝

可视化编辑保存在 `script.appearance`，默认源码同步返回它：

```js
module.exports = {
  apiVersion: 1,
  render(context) {
    return context.appearance;
  }
};
```

需要条件样式时在“源码”工作区编辑 JS 或导入源码。自定义 `render` 应显式合并需要保留的 `context.appearance`；可视化编辑不覆写自定义源码。TypeScript 由制作环境编译为 CommonJS JS，Android 不执行 TypeScript。

类型声明位于仓库根目录 `examples/theme-script/oktheme.d.ts`，合并基础外观的示例为 `examples/theme-script/minimal.ts`。在已安装 TypeScript 的制作环境中运行：

```sh
tsc --strict --target ES2020 --module commonjs --outDir dist examples/theme-script/minimal.ts
```

导入生成的 `dist/minimal.js`；分享的完整主题仍使用 `.oktheme`。`schema` 和 `apiVersion` 仅供机器校验，不是主题或产品的版本名称。

## 已接入的原生区域

- `background`：主界面与草稿预览的装饰，位于媒体背景上方、业务界面下方；声明填色或图层后普通内容承载面透明，工作区保持独立实色底面
- `userMessage`、`assistantMessage`：双方 Cursor/Bubble 消息正文、宽度比例、文字样式和原生容器形状；包括现有宽窄布局和图片气泡路径。已有消息分片仍逐片绘制
- `userAvatar`、`assistantAvatar`：消息头像尺寸、外形和装饰；不修改头像身份数据
- `composer`：Classic/Agent 输入容器，支持形状、填色、描边、尺寸、留白、图层和字体
- `input`：Classic/Agent 真实输入外框的填色、形状、描边、留白与局部排版；其他业务表单与工作室自身表单不自动接入该部件
- `appBar`：主顶栏和聊天标题栏；不同入口保留各自的原生结构
- `navigation`、`navigationItem`：手机抽屉、平板侧边栏的内容及导航项，导航项传入选中状态
- `button`、`iconButton`：已接入适配器的常用 Material3 按钮、描边按钮、文字按钮、色调按钮及图标按钮；业务事件和交互源继续交给原生按钮
- `settingsCard`、`listItem`：设置首页的卡片和列表行、主题工作台卡片
- `dialog`：已接入适配器的常用 AlertDialog，支持容器形状、颜色、阴影、装饰和文字样式；自定义内容弹窗仍保留其自身内容布局
- `sheet`、`menu`：消息复制底部面板与消息菜单；不代表所有业务面板和菜单都已接入
- `attachmentMenu`、`modelMenu`、`chatSettingsMenu`：附件、Agent 模型选择及 Agent 聊天设置弹起菜单；三个区域分别编辑形状、描边、填色、图层和内容留白，并通过位置偏移移动整个菜单
- `status`、`toolCard`：消息状态行与工具调用条

通用形状、颜色、尺寸和装饰由原生消费入口决定；明确设有颜色或自定义字体的业务内容仍保留其自身优先级。局部字体、文字色和形状以接入的实际内容区域为界。未接入的页面仍可继承全局 Material 色板和排版，不宣称任意布局能力。

## 外观数据

`Appearance` 包含数字 `apiVersion: 1` 和以下可选字段：

- `palette.light/dark`：36 个 Material 颜色 token。包括 primary/secondary/tertiary 的前景与容器、background/surface、surfaceVariant、inverse、error、outline、scrim、surfaceTint，以及 surfaceBright/surfaceDim 和五档 surfaceContainer；完整 token 名称以类型声明为准
- `typography`：全局字体类别 `default/sans-serif/serif/monospace/cursive` 或 `asset:<素材标识>` 的 TTF/OTF 字体，字号比例 `scale`、行高倍数 `lineHeight` 、字间距 `letterSpacing` 和可选文字色 `color`
- `surfaces`：上述 22 个部件的局部外观
- `icons`：原生按钮图像绑定，只替换图像
- `modes.light/dark`：按模式覆盖 `surfaces/icons/typography`。未声明的部件保留公共外观；声明的单个部件以完整局部对象替换公共部件，局部对象中未指定的字段使用模型默认值。模式展开后才传入脚本，脚本返回的模式覆盖也按当前模式展开

共享结构包含以下字段，工作室按实际消费显示控件：背景不展示描边，头像不展示文字样式，固定尺寸部件不展示最小高度，圆形不展示半径。当前能力边界见[二十二个区域与工作室属性](theme_authoring_guide.md#surfaces)。原生边框与装饰只绘制一次，描边位于形状内侧。

共享字段：

- `shape`：`rounded/cut/circle` 和 `radius`；圆角/切角 `0..128 dp`
- `fillColor/contentColor/strokeColor/strokeWidth`：底色、前景色、描边；宽度 `0..16 dp`
- `elevation/minHeight`：阴影 `0..24 dp` 和最小高度 `0..256 dp`
- `padding`：`start/top/end/bottom` 各 `0..48 dp`，与原有内容内距叠加；背景必须保持零内距
- `popupOffset`：只用于三个聊天弹起菜单，`up/down/left` 各 `0..256 dp`，默认零；纵向按 `down - up` 计算，向左是物理方向，不提供 `right`。偏移移动完整容器及命中区域，按当前窗口和实际菜单尺寸限制范围；内容留白仍使用 `padding`。工作室的上下滑杆与数字输入互斥，归零后解锁另一方向；既有双向数值按净位移编辑
- `textStyle`：与全局排版同结构的局部排版；字号比例 `0.5..2`，行高倍数 `0.8..2`，字间距 `-2..8 sp`
- `size`：只用于头像，`16..96 dp`
- `messageWidth`：只用于消息，为现有最大宽度的 `0.3..1` 倍
- `layers`：绘图、纹理和九宫格图层
- `opacity`：整个部件的制作透明度 `0..1`，与区域背景不透明度独立
- `states`：`pressed/focused/selected/disabled/error` 的颜色、描边宽度及透明度覆盖。未指定的颜色和宽度继承默认外观；状态透明度与部件透明度相乘。按钮与图标按钮的颜色、正文和装饰共享原生交互源；工作室只为实际可预览的部件显示状态编辑

未配置的字段使用原有主题参数和组件默认值。现有自定义文件字体、玻璃、媒体背景、图片气泡与布局仍可组合使用；具体消息已有文件字体设置时保留它的行为。

## 按钮素材

`icons` 的键包括 `composer.settings/attachment/microphone/send/stop/fullscreen/queue/expand`、`appBar.back/menu/workspace/floating` 和 `navigation.item`，共 13 个绑定。

每个值要求 `asset` 引用已声明的素材标识，可设 `size: 8..64 dp`、`opacity: 0..1` 、`useContentTint` 和 `tintColor`。`tintColor` 为固定 ARGB 着色，显式设置时优先；未设置固定色时，`useContentTint: true` 使用原按钮当前前景色，适合单色图标；两者均未设置时保留素材本色。一个通用 `navigation.item` 绑定会统一替换所有导航项图像。

附件、录音、发送和停止接入两种输入风格；输入设置与展开菜单位于 Agent。队列、展开、全屏等绑定只在原本存在该操作的入口出现，不凭空增加按钮。

## 图层和透明度

图层支持 `rect/ellipse/line/image/nineSlice`，可设 `id`、归一化 `bounds`、`opacity`、`placement` 和 `state`。

`bounds.x/y/width/height` 默认 `0/0/1/1`，`x/y` 在 `-1..1`，宽高在 `(0,1]`。图层平移保持尺寸，超出容器的部分裁剪。`placement` 为 `background/foreground`，默认背景：原生材质底色、背景图层、原生正文、前景图层与描边依次绘制。前景层不要遮挡正文。

`state` 为 `always/pressed/focused/selected/disabled/error`。按下和聚焦由原生指针与焦点更新；导航项传入选中状态，按钮传入启用状态；只有明确传入错误状态的消费入口显示错误层。其余入口只显示它们实际提供的状态，不把这些值解释为任意业务条件，也不逐帧执行 JS。

绘图层至少设置 `color` 或 `strokeColor`，描边 `0..16 dp`、圆角 `0..128 dp`。`line` 连接区域左上与右下，最细为一个物理像素。图片层要求 `asset`，不能配置绘图颜色；`fit` 为 `fill/fit/crop/tile`，`alignment` 为 `center/top/bottom/start/end`，起始与结束侧随布局方向解释。`sampling` 为 `smooth/nearest`，最近邻关闭位图缩放滤波；`blur` 为 `0..8 dp`，非零值要求 Android 12 或更新系统，低版本明确拒绝。平铺与采样由原生绘制消费。九宫格仅接受位图，`slices.start/top/end/bottom` 是原图像素，切片必须留出非空中心；四角保留，边和中心按目标尺寸拉伸。

所有装饰都乘以区域原有背景透明度；背景使用 `background_image_opacity`。区域透明度为零时装饰不可见，正文保持原透明度行为。Cursor 助手背景默认透明，需要提高原有助手背景不透明度才能看到装饰。原生材质已经绘制的底色不在装饰层重复绘制。 `fillColor` 的 alpha 只影响容器填色，图层仍使用区域原有透明度；制作图片边框时，可以将底色设为 `#00000000`，并保持对应区域不透明度大于零。

六位颜色为 `#RRGGBB`，八位颜色为 Android `#AARRGGBB`。

## 像素主题制作

像素风消息框和输入框可以用位图九宫格保留边角与细边，再结合部件留白、宽度、透明度和图层。像素按钮通过图标绑定替换，风景背景沿用媒体背景或背景图层，像素字体可在工作室资源中导入 TTF/OTF，再绑定到全局或局部排版；原有文件字体设置仍可使用。

这些能力承载主题的外观，页面结构仍由当前 Android 原生布局决定。制作需要原始图片、字体文件及九宫格切片参数；工作室提供最近邻采样，九宫格与普通位图均使用该采样设置。像素图在非整数目标缩放与不同屏幕密度下的排布仍需确认，最近邻不会自动将所有部件尺寸对齐到整数像素。

## 脚本上下文与执行限制

同步 `render(context)` 返回可序列化外观对象。`context` 及内部对象在模块加载前冻结，包含：

- `apiVersion`、实际生效的 `mode: light/dark`
- `appearance`：制作工程或导入包按当前模式展开的基础外观，传入时不再保留 `modes`
- `colors`：将基础外观的当前模式色板应用到原生主题后生成的完整 36 个颜色 token
- `settings`：当前角色/草稿的 strings、booleans、ints、floats；排除 `_uri`、`_path` 资源位置和 `custom_chat_title`

聊天内容、角色资料和工具配置不进入上下文。不提供 `require`、DOM、网络、定时器、文件读写、工具调用或 Java 桥；加载源码前移除 `NativeInterface`。每次求值创建独立实例。

Promise、循环引用、未知字段、非法数值和不支持的机器版本产生诊断。脚本、参数和模式变化触发求值；草稿预览使用短暂防抖。错误记录日志并显示提示，不改写已保存参数。

容量与执行预算：

- JS 64 KiB，外观结果 128 KiB，独立脚本清单 64 KiB，JSON 深度最多 16 层
- QuickJS 16 MiB 内存、256 KiB 原生栈，模块加载和 render 分别最多 100 ms
- 最多 48 个图片与字体素材，单个最多 8 MiB，源码与素材总计最多 64 MiB
- 位图总预算 41,943,040 像素，单张也受同一上限约束，所有部件合计最多 128 层
- 单 SVG 最多 512 KiB、2048 节点、24 层；允许静态形状、路径、分组、渐变与裁切，禁用脚本、事件、动画、外部资源、样式表、DOCTYPE 和 ENTITY

完整 `.oktheme` 根清单还受 8 层 JSON 深度限制；模式内继续嵌套状态或图层可能超过。制作时可在 JS 中按模式生成较复杂外观，具体层级与资源限制见 [完整制作手册](theme_authoring_guide.md#limits)。

## 文件与分享码

新导出的完整主题根清单固定 `format: "oktheme"`、`schema: 1`，继续包含 `name/author/config/layout`；可选 `script` 保存如下对象：

```json
{
  "apiVersion": 1,
  "entry": "assets/scripts/main.js",
  "assets": { "send": "assets/icons/send.svg", "bodyFont": "assets/fonts/body.ttf" },
  "studio": {
    "id": "sample_theme",
    "name": "示例主题",
    "author": "作者",
    "version": "1.0.0",
    "description": "原生主题工程",
    "license": "CC BY 4.0",
    "folders": ["assets/icons", "assets/fonts", "assets/images/empty"]
  },
  "appearance": {
    "apiVersion": 1,
    "icons": { "composer.send": { "asset": "send", "useContentTint": true } },
    "surfaces": { "composer": { "shape": { "type": "rounded", "radius": 24 } } }
  }
}
```

资源路径保留 `assets/` 下的相对文件夹层级，源码入口必须是 JS。路径总长最多 160 字符、含根目录最多 10 段、单段最多 96 字符；允许中文、字母、数字、空格、下划线、连字符和扩展名用的点，首字符不能为空格或点，拒绝绝对路径、点路径、连续点、尾随空格/点、反斜杠和重复分隔符。文件不能同时成为其他资源或工程文件夹的父路径。

`studio` 保存制作元数据：可选 ID 最多 96 字符且以字母开头，仅用字母、数字、点、下划线与连字符；名称/作者各 128 字符、版本 32、简介 2048、许可 8192；最多 96 个不重复的合法文件夹。空目录依靠清单保存，不增加 ZIP 条目。

导出原样保留工程中的源码与资源路径，普通角色媒体使用独立分配的 `assets/role-media/` 文件名，避开工程已占用的文件/目录。重新导入仍使用同一层级。素材 ID 与路径分离，脚本和外观按 ID 引用素材，不分享设备私有位置。完整清单最多 192 KiB；参数分享码解压上限仍为 64 KiB。

参数码使用 `format: "oktheme-params"`、`schema: 1`，链接为 `oktheme://theme/import?schema=1&encoding=zlib-base64url&data=...`；仍接受裸编码正文。参数码不携带脚本和素材，也不删除接收角色现有外观。

已发布的 `operit-theme-v1`、`operit-theme-params-v1` 和 `operit://theme/import?v=1` 按各自明确格式读取，不再输出这些名称。新包不使用带品牌的版本名称。旧应用不认识新格式时会报告不支持。

完整包按完整替换语义应用，没有 `script` 就移除目标原有自定义外观。导入预览只使用缓存；确认后写私有资源束，再将引用保存到目标角色。取消和失败清理暂存，复制角色及应用到全部角色继续使用现有引用规则，最后一个持久引用移除后清理共享资源。

## 验证入口

JVM 用例覆盖格式与机器 schema、旧文件/链接兼容、模式/状态继承、字体声明与图像类型约束、部件页签能力、目录边界、资源归档与引用清理，由 `Android Tests` 工作流执行。`ThemeScriptAndroidTest` 另含模板、原生求值、冻结上下文、执行预算和 SVG 用例，需要 Android 条件；独立 Android Tests 工作流运行 JVM 回归；PR Check 按改动范围编译仪器代码，不执行设备用例。

手机操作先读 [16 场景验收指南](theme_acceptance_checklist.md)。完整操作与可填写结果见 [主题回归记录](../../TODO/feat/theme-script-api/03-verification.md)。实现和静态核对不等于 Android 界面验证通过。

当前聊天控件行与工作室共用展示入口，图标槽包含 `appBar.menu`、`appBar.floating` 和 `appBar.workspace`。重构前的 `appBar.history` 已从此开发协议移除；开发工程请更新对应绑定。
