# 无外部素材的原生主题入门工程

本示例依据 `3005a7d7` 的已实现接口。静态外观提供浅深配色、消息形状和宽度、头像、按钮状态；JS 保留这些字段，再按实际模式计算输入框边色，读取原输入风格选择最小高度，并生成十二个静态背景点。

配置与语法经过静态核对；没有在 Android 上导入、求值或检查实际观感。它用于学习现有字段，不代表已发布成品主题。

## 文件与用途

```text
starter/
	README.md
	theme.json
	manifest.json
	assets/
		main.js
```

- [theme.json](theme.json)：工作室外观视图，放入工作室同名文件
- [manifest.json](manifest.json)：脚本工程清单，包含与 theme.json 相同的 appearance；不是完整 `.oktheme` 根清单
- [assets/main.js](assets/main.js)：实际可导入的 CommonJS JS，必须与配套外观一起使用

## 在工作室使用

1. 进入“主题 → 基础 → 主题工作室 → 创作主题”，打开“源码”。
2. 先把 `theme.json` 全文复制到工作室的 `theme.json`，同步成功后再处理 JS。已有自定义源码时，先保存自己的工程草稿。
3. 选择清单指定的 JS 文件，用脚本导入功能导入 `assets/main.js`，同步源码。新工程默认入口为 `assets/main.js`；入口名称不同时，导入仍更新当前工程入口的内容。
4. 需要连工程名称一起导入时，可将本目录 `manifest.json` 全文复制到工作室同名视图。此例无素材，`assets` 是空映射；这一步会替换当前工程外观和制作信息。
5. 在设计区分别检查浅色、深色、聊天、组件与导航场景。应用到角色草稿后，返回主题页保存。
6. 分享时从工作室导出 `.oktheme`，应用会把当前角色传统参数与布局一起装入完整包。

只想体验静态外观时，完成第 2 步并保留默认 `render(context) { return context.appearance; }` 即可，不必使用计算脚本。

## 按效果理解代码

JSON 的 `palette.light/dark` 分别覆盖九个颜色 token，其余 token 使用原生主题值；全局排版使用系统无衬线字体与 1.05 倍行高。`modes.dark.surfaces.userMessage` 是完整部件替换，因此重复写了公共形状、留白和消息宽度。

`main.js` 使用 `context.mode`，因为实际模式可能跟随系统；`context.settings.strings.input_style` 是只读配置。它只改变返回外观的 `minHeight`，不会写回输入风格。背景点使用有界数组生成，所有坐标在 0..1 内，保留已有背景 layers。

输入容器的 `states.focused` 也使用内层展开，保留基础状态中其他字段。聚焦是原生状态，不触发 JS 重新运行。`button` 的按下与禁用透明度、`navigationItem` 的选中描边同样交给原生界面处理。

点阵装饰还会乘以原有 `background_image_opacity`；原主题把它设为 0 时看不到点阵。消息填色和装饰也受各自区域背景不透明度约束。像素九宫格、图标与字体素材绑定见完整手册，不需要为此例准备图片或字体。

## 完整参考

- [制作手册与全部字段](../../../docs/doc-src/feature-protocol/theme_authoring_guide.md)
- [只读设置参数目录](../../../docs/doc-src/feature-protocol/theme_settings_reference.md)
- [接口概览](../../../docs/doc-src/feature-protocol/theme_script_api.md)
- [TypeScript 类型声明](../oktheme.d.ts)

完整包的根 JSON 目前最多 8 层，本例把复杂图层放在 JS 中生成，静态模式覆盖仅使用较浅的部件字段。独立脚本输出仍受 16 层、128 KiB、128 图层及执行预算限制。

全局文字颜色示例在 `modes.light/dark.typography.color`，普通主题字体窗口和工作室编辑同一字段。图片与九宫格位置可设为 `-100%..100%`，移动保持尺寸，越界部分裁剪；图层模糊上限为 8 dp。
