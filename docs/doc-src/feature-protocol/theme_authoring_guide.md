# 原生主题 JSON 与 JavaScript 制作手册

本文依据 `feat/oktheme-script-api` 分支源码编写，是当前实现的制作参考稿。机器协议为 `apiVersion: 1`，完整主题包为 `schema: 1`；这些数字不表示应用发布版本。字段经过源码核对，示例的验证状态见文末。

从零开始请先读“第一次制作”和“文件放在哪里”，再按需要查字段。只读主题设置的完整键名、默认值和范围见 [主题设置参数目录](theme_settings_reference.md)。接口概览见 [原生外观接口](theme_script_api.md)，可复制文件见 [入门工程](../../../examples/theme-script/starter/README.md)，编辑器类型提示见 [TypeScript 声明](../../../examples/theme-script/oktheme.d.ts)。

阅读导航：

- [第一次制作](#quickstart)、[文件放在哪里](#files)、[共同规则](#rules)
- [外观顶层](#appearance)、[22 个区域](#surfaces)、[区域字段](#surface-fields)、[配色](#palette)、[排版](#typography)
- [浅深模式](#modes)、[交互状态](#states)、[图标](#icons)、[图层](#layers)、[资源](#assets)
- [JS 契约](#javascript)、[执行与容量上限](#limits)、[保存和工程信息](#workflow)、[打包分享](#sharing)、[排错](#troubleshooting)
- [16 场景手机验收](theme_acceptance_checklist.md)，包含独立结果表并映射完整回归

<a id="capabilities"></a>
## 能制作什么

当前接口可以组合 19 个原生区域、13 个图标槽位、36 个 Material 配色 token、全局与局部排版，以及背景和前景装饰层。适合制作统一配色、纸张纹理、像素九宫格消息框、自定义输入按钮图像、字体主题、浅深两套外观，以及按钮按下或导航选中时的装饰。

JSON 保存静态外观；JS 根据当前浅深模式、原生配色和已有主题设置计算最终外观。例如，用一个函数给多个区域生成同款边框，根据 `input_style` 调整输入容器最小高度，或者按模式生成点阵背景。

接口的可配置范围由下文列出的原生接入点决定。没有任意页面坐标、Flex/Grid/CSS、业务组件树、聊天内容读取、模型调用、工具调用、按钮事件脚本或自定义业务表单。图层是容器内的绘制数据，不会创建可以单独点击的控件。原生布局、输入、滚动、发送和无障碍行为继续由 Android 界面持有。

JS 在计算和批量组织样式方面很灵活；能表达的最终结果仍是本文的 `Appearance`。制作完整皮肤有空间，做网页级交互、动态业务插件或连续动画需要另外的原生接口。执行预算见“JS 运行环境与上限”。

<a id="quickstart"></a>
## 第一次制作

1. 在主题页选定角色卡或角色组，进入“基础 → 主题工作室 → 创作主题”。
2. 顶部“设计／源码／资源”保持为工作区入口。在“设计”的预览工具栏左侧打开场景下拉菜单，选择聊天、常用组件或侧栏与顶栏；右侧切换浅色／深色，旁边保留全屏和展开／收起。选择部件后，先调颜色、形状和留白；全局字体与配色有独立入口。工作室外壳跟随应用模式；预览的浅深切换只改变样例。属性选项占满每行并提供至少 52 dp 的触控高度，字体等较多选项自动分行。
3. 想直接写配置时，打开“源码”，选择 `theme.json`，粘贴下面的完整 JSON，然后使用同步操作，或切换文件触发校验。保留原有 JS，确保它仍返回 `context.appearance`。
4. 检查两种模式，再点击“应用到角色草稿”。返回主题页后点击保存，才会持久应用到角色。
5. 要保留制作进度，使用“保存工程草稿”；要分享，使用工程菜单导出 `.oktheme`。

以下内容是可独立使用的 `theme.json`，不需要任何素材。它设置输入框、常用按钮和浅深色板；发送行为与页面结构沿用原生界面。

```json
{
  "apiVersion": 1,
  "palette": {
    "light": { "primary": "#386A4C", "onPrimary": "#FFFFFF" },
    "dark": { "primary": "#A0D5AE", "onPrimary": "#07381D" }
  },
  "surfaces": {
    "composer": {
      "shape": { "type": "rounded", "radius": 20 },
      "strokeColor": "#80808080",
      "strokeWidth": 1,
      "minHeight": 48,
      "padding": { "start": 4, "top": 2, "end": 4, "bottom": 2 }
    },
    "button": {
      "shape": { "type": "rounded", "radius": 12 },
      "states": { "disabled": { "opacity": 0.5 } }
    }
  }
}
```

默认 JS 的完整内容如下。只写 JSON 就够用时，保留它即可。

```js
module.exports = {
  apiVersion: 1,
  render(context) {
    return context.appearance;
  }
};
```

进阶的 [入门工程](../../../examples/theme-script/starter/README.md) 提供配套的 `theme.json`、`manifest.json` 与 `assets/main.js`，展示模式覆盖、状态、颜色计算、已有设置读取和批量生成装饰。按 README 的顺序复制；该 JS 会读取配套 JSON 中的 `background` 与 `composer`。

<a id="files"></a>
## 文件放在哪里

工作室有三个源码视图，它们对应同一份工程中的不同内容：

| 视图或文件 | 内容 | 何时编辑 |
| --- | --- | --- |
| 工作室 `theme.json` | 单独的 `Appearance`，即工程清单的 `appearance` | 改配色、部件、字体、图层、模式 |
| 工作室 `manifest.json` | `ScriptManifest`，包括入口、素材映射、外观、工程信息 | 改制作信息、已有素材路径和工程结构 |
| `entry` 指定的 JS，默认 `assets/main.js` | 同步的 `module.exports` 模块 | 计算或组合最终外观 |
| 完整 `.oktheme` 中的根 `manifest.json` | 完整主题清单，包含 `config`、`layout` 和可选 `script` | 通常交给主题导出器生成 |

`theme.json` 是工作室提供的外观编辑视图，导出时内容保存在 `script.appearance`，不会作为一个必需的独立 ZIP 文件导出。修改它也会反映在工作室清单的 `appearance` 中。工作室清单和完整主题包的根清单结构不同，不能互相整段替换。

一个制作目录可以这样组织。这里根 `manifest.json` 指脚本工程清单，完整主题包的根清单关系见“打包与分享”。

```text
my-theme/
	manifest.json
	theme.json
	assets/
		main.js
		icons/
			send.svg
		images/
			frame.png
		fonts/
			body.ttf
```

工作室清单的最小合法结构：

```json
{
  "apiVersion": 1,
  "entry": "assets/main.js"
}
```

完整字段：

| 字段 | 类型 | 省略时 | 规则与用途 |
| --- | --- | --- | --- |
| `apiVersion` | 整数，必填 | 无 | 只能为数字 `1` |
| `entry` | 字符串，必填 | 无 | `assets/` 下的合法相对文件路径，必须以小写 `.js` 结尾 |
| `assets` | 素材 ID 到路径的对象 | `{}` | 最多 48 个 ID；包含图片和字体，不包含 JS 入口 |
| `appearance` | `Appearance` | `{ "apiVersion": 1 }` | 可视化编辑和 JS 输入的基础外观 |
| `studio` | `StudioMetadata` | 默认制作信息 | 名称、版本、作者与空目录等 |

在工作室手改 `assets` 不能凭空增加文件。先在“资源”导入真实素材，再使用生成的素材 ID；清单同步只允许重用工程中已存在的 ID，并按 ID 保留原文件字节。新增一个文件路径字符串不等于导入了素材。

<a id="rules"></a>
## 所有字段共同遵守的规则

JSON 使用 UTF-8，不支持注释、尾随逗号、单引号或 `undefined`。数字写数值，布尔写 `true/false`，不能写带引号的数值。字段和枚举区分大小写，未知字段会直接拒绝，不能追加任意 CSS 属性。

下表中 `null` 表示模型允许空值，并继续使用该接入点原有选择；其他字段省略时采用表中默认值，不能一律写成 `null`。默认值描述的是外观模型，原生界面仍有自己的布局、字样和最低尺寸。

| 表达 | 含义 |
| --- | --- |
| `#RRGGBB` | 不透明 RGB，例如 `#386A4C` |
| `#AARRGGBB` | Android ARGB，例如 `#80386A4C` 是约一半透明的绿色 |
| `dp` | Android 布局尺寸，JSON 中只写数值，不加 `dp` 后缀 |
| `sp` | 字间距等文字尺寸，随系统文字缩放 |
| `0..1` 比例 | 归一化坐标、宽度或不透明度；`0` 透明、`1` 不透明 |
| 原图像素 | 九宫格切片的位置，与布局 `dp` 不同 |

颜色只接受六位或八位十六进制。`red`、`rgba(...)`、`#FFF`、`var(--primary)` 均不合法。网页常见的 `#RRGGBBAA` 不能原样作为本接口八位色；例如半透明红色应写 `#80FF0000`。

所有受限数值都必须有限，不能使用 `NaN` 或无穷大。JS 返回值也会经过 JSON 编码与同一份字段校验，JS 不会放宽范围。

<a id="appearance"></a>
## Appearance 顶层

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `apiVersion` | 整数，必填 | 无 | 必须为 `1` |
| `surfaces` | 区域 ID 到 `Surface` 的对象 | `{}` | 只接受下节的 22 个 ID |
| `icons` | 图标 ID 到 `IconAsset` 的对象 | `{}` | 只接受下文的 13 个槽位 |
| `palette` | `Palette` | 浅深两个空映射 | 部分覆盖 Material 色板 |
| `typography` | `Typography` | 默认排版 | 全局排版 |
| `modes` | `light/dark` 到 `AppearanceMode` 的对象 | `{}` | 按当前模式覆盖部件和排版 |

最小外观为 `{ "apiVersion": 1 }`。`apiVersion` 必须同时出现在工程清单、JS 导出对象和外观结果各自要求的位置。

<a id="surfaces"></a>
## 22 个原生区域

同一个区域 ID 可影响多个已接入的位置，不能按 CSS 选择器指定某一条消息或某一个业务按钮。区域对象通过校验，不代表每个消费位置都会使用其所有字段。

| ID | 原生接入位置 | 制作时需要知道的边界 |
| --- | --- | --- |
| `background` | 主界面、角色草稿预览的装饰 | 位于媒体背景上方、业务界面下方；`padding` 必须全为 0 |
| `userMessage` | 发送方 Cursor/Bubble 消息 | 形状、填色、文字样式、装饰及宽度；已有消息分片仍逐片绘制 |
| `assistantMessage` | 助手 Cursor/Bubble 消息 | 同上；Cursor 助手背景原有不透明度可能为 0 |
| `composer` | Classic/Agent 输入容器 | 形状、填色、描边、留白、最小高度、装饰；输入文字读取局部排版 |
| `userAvatar` | 消息中的发送方头像 | `size`、形状与装饰；实际头像图片由原主题设置提供 |
| `assistantAvatar` | 消息中的助手头像 | 同上；不会改变角色头像身份数据 |
| `appBar` | 主顶栏、聊天标题栏 | 填色、形状、内侧描边与装饰；当前聊天标题使用区域排版，控件行保留原有固定高度 |
| `navigation` | 手机抽屉、平板侧栏的内容容器 | 容器装饰及继承它的内容样式；保留导航结构 |
| `navigationItem` | 已接入的导航项 | 形状、底色、前景、文字、装饰；原生传入选中状态 |
| `button` | 已适配的 Material3 常用按钮 | 填充、描边、文字、色调按钮共用；支持原生按下、聚焦、禁用状态 |
| `iconButton` | 已适配的常用图标按钮 | 按钮容器形状、颜色、装饰和状态；按钮图像由 `icons` 单独绑定 |
| `input` | Classic/Agent 的实际文本输入区域 | 填色、形状、描边、留白与排版接入真实外框，正文和占位文字读取区域内容色；其他业务表单不自动接入 |
| `settingsCard` | 设置首页卡片、主题工作台卡片 | 容器填色、形状与单个原生边框；主题工作台标题和说明使用区域文字样式 |
| `dialog` | 已适配的常用 AlertDialog | 容器、排版、颜色、阴影、装饰；自定义内容保留原有内部布局 |
| `sheet` | 消息复制底部面板 | 容器形状、颜色及装饰；其他业务底部面板不自动接入 |
| `menu` | 消息操作菜单 | 容器形状、颜色及装饰；其他业务菜单不自动接入 |
| `attachmentMenu` | 聊天附件弹起菜单 | 独立形状、描边、填色、装饰、留白及上/下/左位置偏移；不作用于经典网格附件面板 |
| `modelMenu` | Agent 输入区模型选择弹起菜单 | 同上；配置分组、思考设置和选择动作保留原实现 |
| `chatSettingsMenu` | Agent 输入区聊天设置弹起菜单 | 同上；上下文、记忆、权限与插件展开后按实际尺寸重新定位 |
| `listItem` | 设置首页已接入的列表行 | 行填色、形状、描边和文字样式；不会改变所有业务列表项 |
| `status` | 消息变体状态行 | 容器描边与装饰、计数文字和图标颜色；文本内容仍由消息状态决定 |
| `toolCard` | 消息工具调用条 | 调用条容器装饰、描边、标题与摘要排版；不改变工具调用内容和执行行为 |

全局 Material 色板和排版还会影响继承它们的其他页面。业务内容显式指定的文字颜色、原有文件字体、富文本样式和自定义组件有自身优先级。局部 `textStyle` 仅对读取它或继承局部排版的内容生效；只接入绘制修饰器的位置不会因此自动接入文字样式。

### 工作室按实际消费显示属性

共享 `Surface` 结构不代表每个控件都使用全部字段。工作室按二十二个区域的实际接入筛选属性，隐藏项不会删除工程中已有的值；源码仍按完整协议校验。

- 填色、不透明度和图层：二十二个区域均提供
- 形状、描边和留白：除全屏 `background` 外的二十一个区域提供；背景不展示描边颜色或宽度
- 内容色与排版：头像、纯图标按钮以及仅接入容器的 `menu/sheet/attachmentMenu/modelMenu/chatSettingsMenu` 不提供；图标素材着色在对应图标槽编辑
- 最小高度：固定控件行 `appBar`、整屏 `navigation`、导航项、头像和图标按钮不提供；其余内容容器仍保留原生最低尺寸约束
- 阴影高度：工作室只为直接接入原生材质高度的 `button/dialog` 提供，避免在已被父层裁剪的部件上展示无效选项
- 头像尺寸只属于两个头像区域，消息宽度只属于双方消息区域
- 位置偏移只属于 `attachmentMenu/modelMenu/chatSettingsMenu`；工作室在“弹窗与面板”中分别提供三个区域和完整聊天场景预览，只有向上、向下、向左三个滑杆；上下滑杆与数字输入互斥，当前方向归零后才能调整另一方向

圆角半径只在明确选择圆角或切角时展示；圆形/胶囊由部件宽高决定，不使用 `radius`。九宫格只展示实际使用的切片、采样和边界，不展示普通图片的铺放与对齐。

自定义描边需要可见颜色与正数宽度，完全透明或零宽度不绘制自定义边线。继承颜色且原控件已有边框时，正数宽度调整原生边框宽度；没有原生边框的容器仍需指定描边色。清除自定义颜色恢复原生边框，想明确隐藏边线可使用完全透明颜色。填色、裁剪与描边共用同一形状，描边在轮廓内侧绘制；原生边框与装饰不重复绘制。

<a id="surface-fields"></a>
## Surface 全字段

| 字段 | 类型 | 默认值 | 合法范围与效果 |
| --- | --- | --- | --- |
| `padding` | `Insets` | 四边 0 | 每边 `0..48 dp`，叠加到原有内容内距；背景仅允许全 0 |
| `popupOffset` | `PopupOffset` | 三方向 0 | 仅三个聊天弹起菜单可设非零值，移动整个容器及命中区域 |
| `layers` | `Layer` 数组 | `[]` | 按数组顺序绘制装饰；所有区域合计最多 128 层 |
| `shape` | `Shape` 或 `null` | `null` | 原生形状覆盖；装饰也受指定形状裁切 |
| `fillColor` | 颜色或 `null` | `null` | 容器填色；`#00000000` 可清空填色而保留装饰 |
| `contentColor` | 颜色或 `null` | `null` | 已接入的原生内容前景；业务显式颜色有自身优先级 |
| `strokeColor` | 颜色或 `null` | `null` | 描边颜色，需搭配正数宽度 |
| `strokeWidth` | 数值 | `0` | `0..16 dp`；设置描边色且宽度为 0 时不画描边 |
| `elevation` | 数值或 `null` | `null` | `0..24 dp`，由装饰阴影或原生材质接入点消费 |
| `minHeight` | 数值 | `0` | `0..256 dp`，最小高度，不能抵消原控件的最低约束 |
| `size` | 数值或 `null` | `null` | 仅 `userAvatar/assistantAvatar`，`16..96 dp` |
| `messageWidth` | 数值 | `1` | 仅双方消息可为 `0.3..1`；其余区域必须保持 `1` |
| `textStyle` | `Typography` | 默认排版 | 读取该区域排版的内容使用；与全局排版可叠加 |
| `states` | 状态名到 `SurfaceState` 的对象 | `{}` | 颜色、描边宽度与透明度的条件覆盖 |
| `opacity` | 数值 | `1` | `0..1`，部件制作不透明度；与原区域背景不透明度不同 |

`Insets` 的四个字段为 `start/top/end/bottom`，全部为数值且默认 0。`start/end` 根据布局方向解释，不固定等同于左/右。`messageWidth: 0.8` 表示现有消息最大宽度的 80%，不是屏幕宽度的 80%。

`PopupOffset` 的字段为 `up/down/left`，每项 `0..256 dp` 且默认 0；纵向偏移为 `down - up`，相同数值相互抵消。`left` 始终向屏幕物理左侧移动，不接受 `right`、负值或非有限数值。运行时在保留原始菜单尺寸约束的基础上，按可用窗口重新测量并限制位置；触及边缘后继续增加数值不会把菜单移出窗口。零偏移保持原来的末端 12 dp、底部 44 dp 间距。

例如：

```json
{
  "apiVersion": 1,
  "surfaces": {
    "modelMenu": {
      "shape": { "type": "cut", "radius": 12 },
      "strokeColor": "#80D1B5FF",
      "strokeWidth": 2,
      "popupOffset": { "up": 24, "left": 16 }
    }
  }
}
```

工作室把既有 `up/down` 转成等效的单方向数值显示。例如上 24、下 8 显示为上 16、下 0；上下都为 24 则显示为全 0，两边均可编辑。仅在执行编辑时写回等效数值，不自动改写已保存工程，左偏移保持原值。

小预览与全屏均先按应用窗口扣除系统栏后的完整聊天尺寸测量。小预览按可用宽度等比放大，保持固定显示高度，上下滚动查看其余画布；选中菜单或已接入的部件并完成布局后，会按真实边界定位一次，之后可以自由滚动。全屏继续等比显示完整场景。两种方式共用原始菜单坐标，全屏标题、编辑面板和小窗口滚动只改变查看范围，不重新排版或横向拉伸部件。三个菜单叠在正式顶栏、示例消息和智能体输入区上，复用实际菜单的图标、行布局、模型分组、记忆标题和上下文统计入口；消息、统计和开关状态使用展示样例，配置列表只读获取。预览不执行菜单业务操作，正式聊天继续使用原生弹窗。

这三个区域分别保存外观；更改 `menu` 仍只影响原有消息操作菜单。旧主题缺省偏移为零，带有新增区域或 `popupOffset` 的主题需要支持本扩展的应用才能导入。

`Shape` 字段：

| 字段 | 类型与默认值 | 规则 |
| --- | --- | --- |
| `type` | 字符串，`"rounded"` | `rounded` 圆角、`cut` 切角、`circle` 圆形/胶囊形 |
| `radius` | 数值，`16` | `0..128 dp`；用于圆角和切角，`circle` 由原生形状决定 |

`shape` 整体省略会保留原形状，写 `"shape": {}` 则明确采用圆角 16。`circle` 放在长方形容器上通常形成胶囊外形，不能据此改变容器宽高。

<a id="palette"></a>
## 配色与 36 个 token

`Palette` 只有 `light` 和 `dark`，各自默认为 `{}`，都是 token 到颜色的部分映射。未指定的 token 继续使用当前角色原生色板。设置 `primary` 不会自动重新生成整套 `onPrimary` 或容器色，需要时成组指定。

| 组别 | 完整 token 名称 |
| --- | --- |
| 主色 | `primary`、`onPrimary`、`primaryContainer`、`onPrimaryContainer`、`inversePrimary` |
| 次色 | `secondary`、`onSecondary`、`secondaryContainer`、`onSecondaryContainer` |
| 第三色 | `tertiary`、`onTertiary`、`tertiaryContainer`、`onTertiaryContainer` |
| 页面背景 | `background`、`onBackground` |
| 表面 | `surface`、`onSurface`、`surfaceVariant`、`onSurfaceVariant`、`surfaceTint`、`inverseSurface`、`inverseOnSurface` |
| 错误 | `error`、`onError`、`errorContainer`、`onErrorContainer` |
| 边线与遮罩 | `outline`、`outlineVariant`、`scrim` |
| 明暗表面 | `surfaceBright`、`surfaceDim` |
| 容器层级 | `surfaceContainerLowest`、`surfaceContainerLow`、`surfaceContainer`、`surfaceContainerHigh`、`surfaceContainerHighest` |

`on...` 通常是对应底色上的前景；例如主色按钮使用 `primary` 底色和 `onPrimary` 前景。`surfaces.background` 是区域外观，`palette.light.background` 是 Material 颜色，二者位置和作用不同。

<a id="typography"></a>
## 全局与局部排版

`Appearance.typography`、`Surface.textStyle`、`AppearanceMode.typography` 使用同一结构：

| 字段 | 类型 | 默认值 | 范围和效果 |
| --- | --- | --- | --- |
| `family` | 字符串或 `null` | `null` | `default/sans-serif/serif/monospace/cursive` 或 `asset:<素材ID>` |
| `scale` | 数值 | `1` | `0.5..2`，乘在已有字号上 |
| `lineHeight` | 数值 | `1` | `0.8..2`，额外行高倍数 |
| `letterSpacing` | 数值 | `0` | `-2..8 sp`；当前实现中 0 表示保留原样式字距 |
| `color` | 颜色或 `null` | `null` | 全局或区域文字颜色，支持六位 RGB 和八位 ARGB；可在浅深模式分别配置 |

普通主题页的“字体设置”也可分别编辑浅深文字颜色，和工作室使用同一份 `typography.color`，不会新增另一套配色偏好。工作室必须先应用到角色草稿，主题页再保存；普通字体窗口的改动同样支持保存和放弃。区域或状态的 `contentColor` 优先于区域 `textStyle.color`，其次是全局 `typography.color`，没有声明时沿用入口原色。全局色进入 Material 排版、默认正文角色 onBackground/onSurface/onSurfaceVariant 和原生主题颜色消费入口；业务代码显式指定的独立语义颜色仍由相应入口控制。自定义 JS 需要保留要编辑的外观字段。

已指定的行高会乘以 `scale × lineHeight`；字号或行高原本未指定时，不会凭空创建固定值。全局排版影响 Material 字体样式，局部排版又以该入口已有样式为基础计算，缩放可能叠加。

`family: null` 保留已有字体，`family: "default"` 明确选择默认字体。`asset:bodyFont` 必须引用 `assets` 中已声明、已导入的 TTF/OTF 素材 ID。当前没有单独声明字体字重文件组、字体可变轴或 CSS `@font-face` 的接口。

<a id="modes"></a>
## 浅色与深色覆盖

`modes` 只允许 `light/dark`。每个 `AppearanceMode` 只有以下字段：

| 字段 | 类型 | 默认值 |
| --- | --- | --- |
| `surfaces` | 区域 ID 到 `Surface` 的对象 | `{}` |
| `icons` | 图标 ID 到 `IconAsset` 的对象 | `{}` |
| `typography` | `Typography` 或 `null` | `null`，继承公共排版 |

色板放在顶层的 `palette.light/dark`，不能写进 `modes.dark.palette`。

模式合并以部件为单位：公共区域映射加上模式区域映射；模式中声明的单个部件整体替换公共对象。局部对象省略的字段使用模型默认值，不会逐字段继承。下面在深色模式下，`composer` 只留下深色填色，公共圆角与内距不会继续保留。

```json
{
  "apiVersion": 1,
  "surfaces": {
    "composer": {
      "shape": { "radius": 24 },
      "padding": { "start": 8, "end": 8 }
    }
  },
  "modes": {
    "dark": {
      "surfaces": { "composer": { "fillColor": "#202823" } }
    }
  }
}
```

需要同款形状和留白时，在深色对象中重复写出这些字段，或在 JS 中根据 `context.mode` 创建并合并所需字段。图标也是整个 `IconAsset` 替换，仍需必填 `asset`；模式排版非空时也整体替换公共排版。

基础外观会先按当前模式展开，再作为 `context.appearance` 传入 JS，展开后的 `modes` 为空。脚本若返回含 `modes` 的外观，结果校验后还会按当前模式展开。通常直接在 JS 中读取 `context.mode` 计算有效外观更直观。

<a id="states"></a>
## 原生交互状态

`states` 的合法键是 `pressed/focused/selected/disabled/error`。默认外观直接写在 Surface 上，没有 `states.default` 或 `states.always`。

| SurfaceState 字段 | 类型 | 默认值 | 作用 |
| --- | --- | --- | --- |
| `fillColor` | 颜色或 `null` | `null` | 继承基础填色，或在该状态覆盖 |
| `contentColor` | 颜色或 `null` | `null` | 继承基础前景，或在该状态覆盖 |
| `strokeColor` | 颜色或 `null` | `null` | 继承基础描边色，或覆盖 |
| `strokeWidth` | 数值或 `null` | `null` | 继承基础宽度，或设 `0..16 dp` |
| `opacity` | 数值 | `1` | `0..1`，乘以基础 Surface 的 `opacity` |

颜色覆盖只选择一个活动状态，优先级为：禁用 → 错误 → 按下 → 聚焦 → 选中 → 默认。不会把五个颜色状态同时依次叠加。

工作室为 `button/iconButton` 提供按下、聚焦和禁用编辑，为 `navigationItem` 提供选中编辑。其他使用指针或焦点装饰的容器可以显示其实际收到的状态层，但不保证所有区域、所有状态都会由业务入口触发。`error` 需要消费入口明确传入错误状态，不能把它当作“任意异常时自动显示”的条件。

状态对象仅覆盖上表五个字段。要在按下时增加纹理或图案，将图层加入该区域的 `layers`，并设置该层 `state: "pressed"`。条件图层按各自可见条件绘制；`always` 层与当前满足的状态层可以叠加，颜色状态优先级不表示只允许画一个状态层。

<a id="icons"></a>
## 13 个图标槽位

| 图标 ID | 位置与显示条件 |
| --- | --- |
| `composer.settings` | Agent 输入设置入口 |
| `composer.attachment` | 已有附件入口，两种输入风格 |
| `composer.microphone` | 已有录音入口，两种输入风格 |
| `composer.send` | 发送入口，两种输入风格 |
| `composer.stop` | 停止入口，仅当前原生界面处于相应状态时 |
| `composer.fullscreen` | 已有全屏输入入口 |
| `composer.queue` | 已有消息队列入口 |
| `composer.expand` | Agent 已有展开入口 |
| `appBar.back` | 顶栏返回入口 |
| `appBar.menu` | 顶栏主菜单入口 |
| `appBar.workspace` | 当前控件行的工作区入口 |
| `appBar.floating` | 顶栏已有悬浮入口 |
| `navigation.item` | 已接入的所有导航项共用一份替换图像 |

槽位只在对应操作原本出现时绘制，不会通过绑定新增操作。图标替换保留原按钮点击、长按与无障碍说明。

| IconAsset 字段 | 类型 | 默认值 | 规则 |
| --- | --- | --- | --- |
| `asset` | 字符串，必填 | 无 | 已声明的图片素材 ID；PNG/JPEG/WebP/SVG |
| `size` | 数值 | `24` | `8..64 dp`，图像尺寸，不是按钮触摸区域大小 |
| `opacity` | 数值 | `1` | `0..1` |
| `useContentTint` | 布尔 | `false` | 使用原按钮当前前景色为单色图像着色 |
| `tintColor` | 颜色或 `null` | `null` | 固定着色；设置时优先于 `useContentTint` |

两种着色都未启用时保留素材原色。图像按 `fit` 方式完整放入尺寸区域。当前 IconAsset 没有 `sampling`、`blur` 或独立状态映射；这类字段属于图层或按钮 Surface，不能直接加到图标对象上。工作室只有已绑定素材的模式才能编辑固定图标色。

<a id="layers"></a>
## 五种图层与全部字段

每层都有必填 `type`。其余字段在共享结构中有默认值，但只由相应类型的绘制分支消费。

| Layer 字段 | 类型 | 默认值 | 规则及实际消费 |
| --- | --- | --- | --- |
| `type` | 字符串，必填 | 无 | `rect/ellipse/line/image/nineSlice` |
| `id` | 字符串或 `null` | `null` | 可选稳定标识，格式同素材 ID；用于编辑与绘制对象标识，不是业务选择器 |
| `bounds` | `Bounds` | `0,0,1,1` | 相对容器的归一化位置与尺寸 |
| `opacity` | 数值 | `1` | `0..1`，乘以区域原有背景不透明度 |
| `placement` | 字符串 | `"background"` | `background/foreground`，在原生正文前或后 |
| `state` | 字符串 | `"always"` | `always/pressed/focused/selected/disabled/error` |
| `color` | 颜色或 `null` | `null` | 绘图层填色；图片层必须为 `null` |
| `strokeColor` | 颜色或 `null` | `null` | 绘图层描边色；图片层必须为 `null` |
| `strokeWidth` | 数值 | `0` | `0..16 dp`，绘图描边或线宽 |
| `radius` | 数值 | `0` | `0..128 dp`，只用于 `rect` 的圆角 |
| `asset` | 字符串或 `null` | `null` | 图片/九宫格必需；绘图层不能绑定素材 |
| `fit` | 字符串 | `"fill"` | `fill/fit/crop/tile`；只影响 `image` |
| `slices` | `Insets` | 四边 0 | 每边 `0..2048` 原图像素；只影响 `nineSlice` |
| `sampling` | 字符串 | `"smooth"` | `smooth/nearest`；用于位图缩放与平铺的滤波选择 |
| `alignment` | 字符串 | `"center"` | `center/top/bottom/start/end`；只影响非平铺 `image` |
| `blur` | 数值 | `0` | `0..8 dp`；对单层成像做模糊，非零要求 Android 12/API 31 及以上 |

`Bounds` 字段均为数值：`x: 0`、`y: 0`、`width: 1`、`height: 1`。`x/y` 在 `-1..1`，宽高在 `(0,1]`。位置与尺寸独立：满幅图层也可以直接向左、右、上、下平移，超出部件的部分被裁剪，不因移动自动缩小图片。它们相对于当前部件绘制尺寸，不能定位到别的控件。

| 类型 | 要求 | 绘制方式与不使用的字段 |
| --- | --- | --- |
| `rect` | 至少有 `color/strokeColor` 之一 | 圆角矩形；使用 `radius`，描边正宽度才显示 |
| `ellipse` | 同上 | 内接椭圆；不使用 `radius` |
| `line` | 同上 | 从 bounds 左上连接到右下；优先用描边色，线宽至少 1 个物理像素 |
| `image` | 已声明图片 `asset`，没有绘图颜色 | 按 `fit/alignment/sampling` 绘制；不使用 `radius/slices` |
| `nineSlice` | 已声明位图 `asset`，没有绘图颜色 | 按 `slices/sampling` 分九块拉伸；不使用 `fit/alignment/radius` |

共享字段不等于功能可互换。例如 `image.radius` 不会把图片裁成圆角，需使用区域 `shape`；`ellipse.fit` 不会改变椭圆；`nineSlice.fit: "tile"` 不会平铺九宫格中心。未参与该类型绘制的字段仍需符合字段校验。

### 图片铺放

`fill` 拉伸到 bounds，可能改变纵横比；`fit` 等比完整显示并可能留白；`crop` 等比填满并裁掉超出部分；`tile` 以原生绘制密度重复素材并裁切到 bounds。`top/bottom` 决定垂直边，另一轴居中；`start/end` 决定水平边，另一轴居中。

`nearest` 关闭位图缩放过滤，适合像素素材；它不会自动把所有布局尺寸、屏幕密度和缩放比例变成整数像素。图标绑定没有单独的最近邻开关。当前 SVG 先渲染为 `512 × 512` 的 Picture，制作时应使用合适的方形 viewBox，并检查非方形图案的比例；字体图标、SVG 文本和外链图像不在支持列表中。

### 九宫格

先导入真实位图并取得素材 ID，再把该层设为 `nineSlice`。`slices.start/top/end/bottom` 是从原图四边切入的像素数，建议使用整数；实现取整后，左右和必须严格小于图宽，上下和必须严格小于图高。SVG 不能作九宫格素材。

例如一张真实的 `32 × 32` 位图，每边切入 6 像素，中心剩 `20 × 20`，可以保留四角并拉伸边与中心。切片数用于原图像素边界，绘制时边缘尺寸按对应 dp 换算，目标过小时会压缩边缘；不能据此承诺在所有密度下保持原图物理像素大小。

工作室提供位图切片编辑，预览仍需检查不同消息长度和多行输入。需要圆角裁切时使用部件 `shape`；边框素材可以配合透明底色和额外 `padding` 留出正文空间。

### 绘制顺序与透明度

顺序为容器底色 → `background` 图层 → 原生正文 → `foreground` 图层 → 部件描边。同一 placement 内按数组顺序绘制，后面的覆盖前面。图层限于所属容器，前景层可能遮住文字。

以下几种透明度分别作用于不同阶段：

- 填色自身的 ARGB alpha 只改变填色，不会顺带隐藏图层
- 图层 `opacity` 乘以该入口原有背景不透明度；`background` 使用 `background_image_opacity`
- Surface `opacity` 是部件制作透明度，状态 `opacity` 再与之相乘；原生适配器处理部件与内容合成
- 区域原有背景不透明度为 0 时，装饰也不可见；Cursor 助手背景原有值尤其需要检查

做透明图片边框时，把 `fillColor` 设为 `#00000000`，保留大于 0 的区域背景不透明度，再绑定图层。非零 `blur` 在低于 Android 12 的设备上会明确校验失败；该模糊作用于装饰层自身，不是读取页面后景的玻璃材质。

### 工作室背景与媒体背景

原主题页的媒体背景由图片/视频开关与 URI 提供，工作室的 `background` 是另一层声明式填色或绘图。添加工作室背景不会写入旧媒体字段，也不要求额外开启旧媒体背景；脚本求值结果包含背景填色或图层时，普通内容承载面透明，背景可以显示到正式聊天的消息间与页面空白。

合成顺序为应用底色、旧媒体背景、工作室背景和业务内容。消息气泡、输入及顶栏保留各自的填色与透明度，工作区页面保留独立实色底面。图层 `opacity: 1` 仍会乘以区域及 `background_image_opacity`，不能只看图层数值判断最终浓度。

先应用到角色草稿，再在主题页保存；只有保存工程草稿不会改变正式聊天。移除全部背景填色/图层或关闭脚本后，普通内容面重新依据原有媒体配置选择透明或原底色，现有媒体字段保持自身语义。

<a id="assets"></a>
## 图片、字体与资源路径

支持 PNG、JPEG、WebP、静态 SVG、TTF、OTF。GIF、视频和音频不能作脚本绘图素材；角色原有媒体背景仍由主题设置管理。图片素材和字体素材有不同绑定位置，不能混用。

资源映射示意如下，实际使用前必须先导入三个真实文件：

```json
{
  "apiVersion": 1,
  "entry": "assets/main.js",
  "assets": {
    "send": "assets/icons/send.svg",
    "frame": "assets/images/frame.png",
    "bodyFont": "assets/fonts/body.ttf"
  },
  "appearance": {
    "apiVersion": 1,
    "typography": { "family": "asset:bodyFont" },
    "icons": { "composer.send": { "asset": "send", "useContentTint": true } },
    "surfaces": {
      "composer": {
        "fillColor": "#00000000",
        "padding": { "start": 6, "top": 6, "end": 6, "bottom": 6 },
        "layers": [
          {
            "type": "nineSlice",
            "asset": "frame",
            "slices": { "start": 6, "top": 6, "end": 6, "bottom": 6 },
            "sampling": "nearest"
          }
        ]
      }
    }
  }
}
```

`send/frame/bodyFont` 是稳定 ID；右侧是包内路径。图层 `asset: "frame"`、图标 `asset: "send"`、字体 `family: "asset:bodyFont"` 的写法各不相同。资源页重命名或移动路径时保持 ID，静态绑定不变；手写 JS 也应按 ID 引用。

| 项目 | 完整规则 |
| --- | --- |
| 素材 ID、可选图层 ID | `[A-Za-z][A-Za-z0-9_-]{0,63}`，首字符英文字母，总长最多 64 |
| 文件路径 | `assets/` 下的相对路径，总长最多 160 字符，含根目录共 2..10 段 |
| 路径段 | 每段最多 96 字符，首字符为 Unicode 字母、数字、下划线或连字符；后续可含点和普通空格 |
| 禁止的路径形式 | 绝对路径、反斜杠、重复 `/`、连续点、尾随空格或点、点路径及路径穿越 |
| 文件扩展名 | 必须存在，1..10 位英文字母或数字；资源仍需属于支持的文件类型 |
| 文件与目录冲突 | 文件不能同时成为另一个文件或工程文件夹的父路径，文件路径也不能与目录相同 |
| 工程空目录 | 由 `studio.folders` 保存，不需要增加 ZIP 空目录条目；根 `assets` 不写入此列表 |

单独改扩展名不能转换文件类型；导入和加载还会检查实际内容、图片尺寸、字体头和资源绑定。

SVG 仅允许以下元素：`svg/g/path/rect/circle/ellipse/line/polyline/polygon/defs/linearGradient/radialGradient/stop/clipPath/title/desc`。不支持 `text/image/use/filter/foreignObject/style` 等未列出的元素，事件属性、`href/src/style` 属性、脚本、动画、外部资源、DOCTYPE 与 ENTITY 都会拒绝。渐变或裁切引用必须是合法的本地 `url(#标识)`，不能使用公网或文件 URL。文字请转路径，复杂效果请预先栅格化。

资源页可以删除未被静态外观引用的素材。删除保护检查公共与模式外观的图层、图标和字体，不静态分析 JS 动态生成的引用；JS 使用的素材 ID 需要制作者自行维护，删除后应用/导出校验会报告引用错误。

<a id="javascript"></a>
## JavaScript 输入、输出与写法

### 模块契约

入口必须提供 `module.exports = { apiVersion: 1, render(context) { ... } }`。只有一个入口文件，不提供模块加载器；辅助函数直接放在同一文件，或在外部制作环境先打包为单文件 CommonJS。

`render` 必须同步返回可序列化的 `Appearance` 对象，包含数字 `apiVersion: 1`。不能返回 `Promise`、字符串、完整工程清单或角色 `config`。循环引用、函数和不可序列化数据不适合作为外观输出；最终 JSON 必须符合本手册结构。

### context 全字段

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `apiVersion` | 数字 `1` | 当前上下文版本 |
| `mode` | `"light"` 或 `"dark"` | 实际生效模式；跟随系统时应读这里 |
| `colors` | 全部 36 个 token 到颜色字符串的只读对象 | 原生色板应用基础外观当前模式 palette 后的颜色 |
| `appearance` | 只读 `Appearance` | 基础外观按当前模式展开，`modes` 已清空 |
| `settings.strings` | 字符串到字符串的只读映射 | 当前角色/草稿主题设置，已过滤资源位置与标题 |
| `settings.booleans` | 字符串到布尔的只读映射 | 既有主题开关 |
| `settings.ints` | 字符串到整数的只读映射 | 既有颜色等整数配置，可能缺键 |
| `settings.floats` | 字符串到数值的只读映射 | 既有比例、玻璃和透明度参数，部分可选键可能缺失 |

`context` 及内部对象在加载模块前递归冻结。不得直接赋值或向 `context.appearance.surfaces...layers` 执行 `push`；用对象和数组展开创建新的结果。JS 可以引用未改动的冻结对象，不必复制整个工程。

`colors` 在调用前生成。本次 JS 再修改返回值的 palette，不会让同一调用中的 `context.colors` 重新计算；需要同步使用计算后的色值时，先保存在局部变量，再同时写入返回色板和相应部件。

主题设置目录见 [完整只读参数](theme_settings_reference.md)。所有 `_uri`、`_path` 后缀的字符串和 `custom_chat_title` 被排除；没有设备资源路径、聊天内容、角色资料、工具配置或布局快照。原有整数 ARGB 颜色可能是负数，与 Appearance 的十六进制字符串不同；缺键与数值 0 也不同。

### 保留可视化编辑的结果

JS 返回值就是最终外观，运行时不会把它与编辑器结果再做一次深合并。修改已存在的输入容器边框时，可以这样写：

```js
module.exports = {
  apiVersion: 1,
  render(context) {
    const base = context.appearance;
    const composer = base.surfaces.composer;
    return {
      ...base,
      apiVersion: 1,
      surfaces: {
        ...base.surfaces,
        composer: {
          ...composer,
          strokeColor: context.colors.primary,
          strokeWidth: 1
        }
      }
    };
  }
};
```

这个示例既保留其他顶层字段，也保留其他区域和输入容器已有字段。若还要修改 `states`、`padding` 或 `layers`，分别创建对应的嵌套对象/数组；只展开最外层无法保留被替换的内层字段。配套入门工程给出了保留状态和图层的写法。

可视化编辑只更新基础外观，不会改写自定义 JS。若 JS 每次都重新生成 `composer.layers`，编辑器新增的该区域图层会被返回值覆盖；应明确选择保留数组或完全由脚本生成。

### TypeScript

Android 运行的是 JS。TypeScript 的类型、`import/export` 和声明文件供外部制作环境使用，先编译为单文件 CommonJS，再导入生成的 `.js`。已有 TypeScript 环境可编译仓库示例：

```sh
tsc --strict --target ES2020 --module commonjs --outDir dist examples/theme-script/minimal.ts
```

导入 `dist/minimal.js`，不要导入 `.ts` 或 `.d.ts`。只依赖 `import type` 的示例会消除类型导入；含真正运行时依赖的工程还需在外部打包，不能期待 Android 提供 `require`。

<a id="limits"></a>
## JS 运行环境与上限

脚本使用独立 QuickJS 实例。可以使用该引擎支持的普通 ECMAScript 语法、函数、分支、循环、数组、对象、字符串和数学运算；主题接口没有单独添加 Web API。

没有 DOM、浏览器 Canvas、CSS、`require`、网络、定时器、文件读写、原生 Java 桥或 Operit 工具调用。模块加载前 `NativeInterface` 被固定为 `undefined`。每次重新求值创建独立实例，模块变量不保存为跨次持久状态。

源码、主题参数、颜色或模式变化会触发重新求值；宿主有约 75 ms 的防抖。JS 不按每帧运行，也不按每次按钮按下重跑；按钮状态由原生消费已有的 `states` 和条件图层。不要用重循环、Promise 或定时器制作动画。

| 资源或执行项目 | 当前上限 | 制作建议 |
| --- | --- | --- |
| JS 源码 | 65,536 字节，64 KiB | 单文件，辅助代码也计入 |
| JS 外观结果 | 131,072 字节，128 KiB | 统计序列化后的 UTF-8 字节 |
| 独立脚本工程清单 | 65,536 字节，64 KiB | 保存的静态外观、素材映射和工程信息合计 |
| 独立脚本 JSON 深度 | 16 层 | 对象与数组都计层 |
| QuickJS 内存 | 16 MiB | 包含执行与对象开销，不能全部用于最终数据 |
| 原生栈 | 256 KiB | 避免深递归 |
| 模块加载、render 调用 | 分别最多 100 ms | 包含对应阶段的计算，100 ms 是硬限制，不是目标帧时长 |
| 素材声明 | 最多 48 个 ID | 图片和字体共用额度 |
| 单素材文件 | 4 MiB | SVG 另有更小限制 |
| JS 与素材字节总计 | 16 MiB | 按实际工程文件统计 |
| 位图解码总量 | 4,194,304 像素 | 所有唯一位图文件合计，单张也不能超过；约等于一张 2048² 位图 |
| 图层总数 | 128 层 | 基础及每个模式展开后的有效外观分别校验；所有区域合计 |
| 单 SVG | 256 KiB、2048 节点、24 层 | 仍需符合静态元素和属性限制 |
| 工程文件夹 | 96 个 | 以清单元数据保存，不重复 |

QuickJS 计算预算和 Android 绘制成本分别受约束。即使没有超过 128 层，很多大面积模糊、复杂 SVG 或密集平铺仍会增加原生绘制开销；用少量可复用素材与图层表达效果，实际性能以设备表现为准。

### 完整主题包的额外限制

完整 `.oktheme` 最多 512 MiB、64 个 ZIP 条目，普通角色媒体单文件最多 256 MiB，根清单最多 192 KiB。脚本和绘图素材仍受上表较小的自身预算限制，不能使用整包额度绕过。

当前完整主题根清单的 JSON 深度另限 8 层；独立脚本 16 层不会覆盖这个外层限制。根清单包裹 `script.appearance` 会增加层级，例如 `modes.dark.surfaces.button.states.pressed` 的完整对象路径会超过 8 层；模式里的图层数组及层对象也可能超过。只在工作室同步成功，不代表完整包可以导出。

需要同时表达模式与状态/图层时，可以在公共静态外观保留基础字段，再在 JS 中按 `context.mode` 生成状态和图层，让包内静态 JSON 保持较浅结构。运行时返回值仍按自己的 16 层与大小限制校验。这是当前实现约束，制作时需要实际检查导出结果。

<a id="workflow"></a>
## 工作室同步、保存和工程信息

聊天场景下拉分别提供“聊天 · 经典”和“聊天 · 智能体”。消息直接使用双方 Cursor/Bubble 组件，顶栏与当前主聊天页共用 MainChatControlsContent，输入框和经典设置条与正式界面共用展示入口。侧栏共用正式布局、历史行、插件入口和导航项；常用组件复用设置卡片、列表、按钮和工具调用条，选择弹窗、底部面板或菜单后可以打开真实原生窗口查看。预览使用固定消息与历史样例，选择罩接收点击，不发送请求或改写会话；实际内容、运行状态、系统栏和可用空间仍影响最终画面，不能据此承诺所有页面逐像素一致。

新工程的 `assets/main.js` 默认使用多行与四空格缩进；不会自动重排导入的任意 JavaScript。颜色方块的棋盘格表达透明或继承，并裁剪在方块自身的圆角边界内。直角仍由 `rounded + radius: 0` 表达，切回圆角或切角会恢复正半径。

图片模糊的编辑器和协议上限统一为 8 dp，使用独立的原生渲染节点录制素材，并按图层、图片、尺寸、密度与透明度变化更新缓存，不复用当前正文绘制作用域。此前开发工程中大于 8 的数值须调整后再导入；降低上限和改变绘制路径仍需在实际 Android 设备上检查持续拖动与性能。

场景、工程操作、源码文件、添加资源和图层共用自定义锚定菜单。场景与源码文件会标记当前选项，选择后关闭菜单；返回键或点击菜单外可取消。长菜单可以滚动，展开方向按窗口空间与布局方向确定。属性数值、色相和透明度滑杆使用项目统一的细轨圆形样式，数值范围和历史合并规则沿用原设置。

“设计”“源码”“资源”操作同一份工程。源码有未同步修改时，切换文件或工作区会先校验同步；失败会保留文本并阻止切换、应用或导出。JSON 的字符串提供轻量高亮；JavaScript 区分字符串、模板字符串、注释、关键字、数字、函数与常见上下文名称。高亮使用编辑器当前明暗色板，只添加颜色，不插入字符或调整缩进，保留查找、光标、选择及输入法组合的原文偏移。JSON 格式化只调整文本，还需同步。JS 同步保存源码不等于脚本已在 Android 成功执行，应用与导出会验证浅色、深色两套结果及资源。

撤销/重做最多保留 24 步，连续数值或滑杆调整合为一步；历史也保存解码资源对象。载入森林/信纸模板会替换当前工程，可撤销恢复。资源目录支持导入、分类、搜索、重命名和移动；只允许删除空目录。

| 操作 | 保存或改变什么 |
| --- | --- |
| 保存工程草稿 | 所选角色/角色组的制作进度，下次进入继续编辑 |
| 应用到角色草稿 | 校验两种模式，把工程引用写入当前主题页草稿 |
| 主题页保存 | 持久保存角色主题及相应主题页改动 |
| 工作室导出工程 | 直接导出当前工程及当前主题参数/布局，不要求先应用 |
| 工作室导入工程 | 只载入 `.oktheme` 中的脚本工程，要求包内有 `script` |
| 原主题页完整主题导入 | 预览、确认并应用完整包的角色参数、布局和脚本 |

工程草稿按角色作用域保存在应用私有目录，不是自动生成到下载目录的分享文件。换角色时应检查目标角色和当前草稿。

`StudioMetadata` 全字段如下：

| 字段 | 类型 | 默认值 | 规则 |
| --- | --- | --- | --- |
| `name` | 字符串 | `""` | 最多 128 字符，工程名称 |
| `author` | 字符串 | `""` | 最多 128 字符 |
| `version` | 字符串 | `"1.0.0"` | 最多 32 字符，是作者版本标签，不要求特定语义版本格式 |
| `description` | 字符串 | `""` | 最多 2048 字符 |
| `license` | 字符串 | `""` | 最多 8192 字符，可记录素材和作品许可 |
| `id` | 字符串 | `""` | 可空；否则 `[A-Za-z][A-Za-z0-9._-]{0,95}` |
| `folders` | 路径字符串数组 | `[]` | 最多 96 个，不重复，使用合法 `assets/` 子目录路径 |

制作元数据不进入 JS 上下文，不是执行参数。完整主题根清单另要求 `name` 非空且最多 80 字符、`author` 最多 80 字符，均不能含控制字符；工程信息上限较大不代表导出名称也允许同样长度。

<a id="sharing"></a>
## 打包与分享

完整 `.oktheme` 是 ZIP，根清单字段如下，文件必须位于 ZIP 根部，不能再套一层工程文件夹。

| 根字段 | 要求 |
| --- | --- |
| `format` | 字符串 `"oktheme"` |
| `schema` | 数字 `1` |
| `name` | 非空主题名，最多 80 字符，无控制字符 |
| `author` | 字符串，最多 80 字符，无控制字符 |
| `config` | 普通角色主题配置，使用分享白名单字段，完整包要求完整字段集 |
| `layout` | 既有五个布局值，全部必填；见参数目录 |
| `script` | 可选的 `ScriptManifest`，包含 `appearance`、`entry`、`assets`、`studio` |

关系可以按路径理解：工作室 `theme.json` → 工作室 `manifest.appearance` → 完整包根 `manifest.script.appearance`。JS 和素材按入口与映射中的相对路径加入包。根清单不接受额外字段。

建议通过工作室导出完整包，让应用补齐传统主题参数、布局和媒体引用。只把外观 JSON 改扩展名为 `.oktheme`，或只压缩工作室清单和 JS，无法得到完整主题包。应用内部脚本资源 ZIP 也不是对外分享格式。

完整主题导入按完整替换应用：没有 `script` 的完整包会移除目标原有脚本外观。工作室“导入工程”则只读取脚本部分，不应用包中的传统参数。导入失败和取消会清理暂存文件，确认应用后由应用管理私有资源引用。

参数分享码使用 `format: "oktheme-params"`、`schema: 1`，分享链接形如 `oktheme://theme/import?schema=1&encoding=zlib-base64url&data=...`。它只携带允许分享的普通参数和布局，不携带 JS、字体或绘图素材，也不删除接收角色已有脚本外观。需要分享新皮肤时使用完整 `.oktheme`。

已发布旧格式 `operit-theme-v1`、`operit-theme-params-v1` 与 `operit://theme/import?v=1` 保留明确读取入口，新导出使用上文格式。旧应用不认识新格式时会报告不支持。

<a id="troubleshooting"></a>
## 常见错误与检查顺序

| 现象或诊断 | 检查与处理 |
| --- | --- |
| 不认识字段、`Unknown...` | 核对所在对象与精确拼写；`surfaces`、`icons`、`palette` 不能混放；未知 CSS 字段会拒绝 |
| `Unsupported...API` 或版本错误 | 工程、JS 导出对象、外观的 `apiVersion` 各为数字 1；完整包 schema 也为数字 1 |
| `Expected module.exports...` | 入口导出 CommonJS 对象；确认不是 TS、ES module 或普通函数 |
| `synchronous appearance object` | 去掉 `async/Promise`，同步返回外观对象 |
| 冻结对象赋值失败 | 用展开或新数组创建结果；不修改 context 内任何对象 |
| 可视化改动没出现 | 检查 JS 是否保留了相应字段，及当前模式是否整部件覆盖公共对象 |
| 图层不显示 | 检查区域背景不透明度、Surface/图层 opacity、placement、state、bounds 和素材引用 |
| 调整描边仍看不到边线 | 确认当前浅深模式、可见描边色和正数 `strokeWidth`；继承色只在已有原生边框的控件上调整宽度。`input` 已接入 Classic/Agent 真实外框，背景不展示描边；线段类型最少画 1 个物理像素 |
| 看不到原有背景图片 | 检查是否配置了不透明背景区域填色或覆盖整屏的图层 |
| 按钮图标没替换 | 检查 13 个精确槽位及素材 ID；该操作必须在当前原生入口存在 |
| 文字或局部颜色没变化 | 确认消费入口读取该局部字段；检查业务显式样式、原有自定义字体与全局排版叠加 |
| `undeclared asset` | 先资源导入，再使用真实 ID；不要把路径当 ID，不要把字体当图像 |
| 九宫格失败 | 使用真实位图，取整后的两侧切片和严格小于图像对应尺寸 |
| SVG 导入失败 | 检查支持元素、属性、本地引用和容量；将文字转路径，复杂效果转位图 |
| Android 12 相关错误 | 当前设备不支持非零图层 blur；制作兼容主题时使用 `blur: 0` |
| 结果过大、层数或像素超限 | 减少生成图层、缩小素材、复用 ID；分别检查执行结果和静态清单预算 |
| 超时或内存错误 | 缩短模块初始化与 render 的计算，减少临时对象，避免递归或无界循环 |
| 源码同步通过但导出失败 | 检查两种模式求值、资源实际解析、完整清单 8 层深度、名称 80 字符和整包限制 |
| 保存后角色未改变 | 区分保存工程草稿、应用角色草稿、主题页保存三个步骤 |

排查时先用默认 JS 返回 `context.appearance`，确认静态配置和资源，再逐段加入计算。应用显示的脚本错误和日志包含具体失败原因；脚本异常不会改写已持久保存的主题参数。

<a id="sources"></a>
## 核对依据与验证边界

本手册与入门文件做字段覆盖、JSON 解析与约束静态核对、JS 语法检查和相对链接检查。这些检查不能代替 Kotlin 编译、QuickJS 求值、Android 素材加载、主题导入或界面观感验证。原生交互全量操作及可填写结果见 [主题回归记录](../../TODO/feat/theme-script-api/03-verification.md)。

<details><summary>源码核对入口</summary>

- [工程与上下文模型](../../../app/src/main/java/com/ai/assistance/operit/data/model/theme/ThemeScriptDefinition.kt)、[外观、区域、图层、留白与坐标](../../../app/src/main/java/com/ai/assistance/operit/data/model/theme/ThemeScriptAppearance.kt)
- [配色、字体、形状与图标](../../../app/src/main/java/com/ai/assistance/operit/data/model/theme/ThemeAppearanceStyle.kt)、[模式、状态、工程信息与路径](../../../app/src/main/java/com/ai/assistance/operit/data/model/theme/ThemeStudioMetadata.kt)
- [字段校验和容量限制](../../../app/src/main/java/com/ai/assistance/operit/data/model/theme/ThemeScriptContract.kt)、[资源格式与 SVG 限制](../../../app/src/main/java/com/ai/assistance/operit/data/resources/theme/ThemeScriptBundleStore.kt)
- [JS 执行器](../../../app/src/main/java/com/ai/assistance/operit/ui/theme/script/ThemeScriptResolver.kt)、[上下文和求值触发](../../../app/src/main/java/com/ai/assistance/operit/ui/theme/script/ThemeScriptHost.kt)
- [图层绘制](../../../app/src/main/java/com/ai/assistance/operit/ui/theme/script/ThemeScriptDecoration.kt)、[排版和图标消费](../../../app/src/main/java/com/ai/assistance/operit/ui/theme/script/ThemeAppearanceRuntime.kt)、[资源加载与实物校验](../../../app/src/main/java/com/ai/assistance/operit/ui/theme/script/ThemeScriptAssets.kt)
- [工作室状态与源码同步](../../../app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/theme/studio/ThemeStudioState.kt)、[编辑能力目录](../../../app/src/main/java/com/ai/assistance/operit/ui/features/management/settings/screens/theme/studio/ThemeStudioCatalog.kt)
- [完整分享清单](../../../app/src/main/java/com/ai/assistance/operit/data/themesharing/ThemeTransferSchema.kt)、[归档规则](../../../app/src/main/java/com/ai/assistance/operit/data/themesharing/ThemeArchive.kt)

</details>
