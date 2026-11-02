---
title: Material 3 组件参考
status: complete
---

# Material 3 组件参考

ToolPkg 的 `ctx.UI` 类型由 `ComposeUiFactoryRegistry` 与生成的 `ComposeMaterial3GeneratedUiFactoryRegistry` 组成。所有工厂的运行时签名均为 `ComposeNodeFactory<Props>`，即 `(props?: Props, children?: ComposeChildren) => ComposeNode`；回调会按 [Compose DSL action 协议](./compose_dsl.md#节点与渲染周期)序列化。此页按组件族列出全部生成 registry key，精确字段、optional 标记和回调参数以各自 `ComposeGenerated<Name>Props` 声明为准。

生成文件来自 Compose Material3/Foundation 源码签名，文件头标记为自动生成，不应手工修改。当前声明包含 84 个 generated props 接口和 84 个 registry key；Kotlin 自动生成 registry 包含 83 个 key，缺少的 `Canvas` 由 Compose DSL 专用 renderer 直接处理，因此最终运行时仍覆盖该声明 key。`ctx.UI` 本身是动态 Proxy，未登记名称也能构造节点，但未进入专用 renderer 或生成 registry 的节点会显示 `Unsupported node: <type>`，不会自动映射到任意 Compose composable。

生成组件的函数 props 会被序列化为 Compose action ID，slot props 会进入节点的 `slots` 字段。`checked`、`selected`、`expanded`、`isRefreshing` 等状态由调用方控制；callback 只提交建议的新状态，不会替调用方更新下一次 render 的 props。完整运行时分发顺序和 action/state 语义见 [Compose DSL](./compose_dsl.md#节点与渲染周期)。

## 布局与列表

| `ctx.UI` 工厂 | Props 类型 | 专属字段摘要 |
| --- | --- | --- |
| `Column` | `ComposeGeneratedColumnProps` | `content`、`horizontalAlignment`、`verticalArrangement` |
| `Row` | `ComposeGeneratedRowProps` | `content`、`horizontalArrangement`、`verticalAlignment`、`onClick` |
| `Box` | `ComposeGeneratedBoxProps` | `content`、`contentAlignment`、`propagateMinConstraints` |
| `Spacer` | `ComposeGeneratedSpacerProps` | 仅继承公共属性与 `zIndex` |
| `LazyColumn` | `ComposeGeneratedLazyColumnProps` | `autoScrollToEnd`、`content`、`horizontalAlignment`、`reverseLayout`、`spacing`、`verticalArrangement` |
| `LazyRow` | `ComposeGeneratedLazyRowProps` | `content`、`horizontalArrangement`、`reverseLayout`、`verticalAlignment` |
| `BoxWithConstraints` | `ComposeGeneratedBoxWithConstraintsProps` | `content`、`contentAlignment`、`propagateMinConstraints` |

## 文本与媒体

| 工厂 | Props 类型 | 专属字段摘要 |
| --- | --- | --- |
| `Text` | `ComposeGeneratedTextProps` | `text`、`color`、`fontFamily`、`fontSize`、`fontWeight`、`maxLines`、`overflow`、`softWrap`、`style` |
| `BasicText` | `ComposeGeneratedBasicTextProps` | 基础文本样式字段和 `text` |
| `TextField` | `ComposeGeneratedTextFieldProps` | 必填 `value`、`onValueChange`；label、placeholder、leading/trailing icon、prefix/suffix、supportingText、单行/行数、readOnly、error/password 与 style |
| `Image` | `ComposeGeneratedImageProps` | `fileUri`、`path`、`src`、`uri`、`url`、`icon`、`name`、`contentDescription`、`contentScale`、`alpha`、`contentAlignment` |
| `Icon` | `ComposeGeneratedIconProps` | `name`、`contentDescription`、`size`、`tint` |
| `Canvas` | `ComposeGeneratedCanvasProps` | `commands: ComposeCanvasCommand[]` |
| `SelectionContainer` | `ComposeGeneratedSelectionContainerProps` | `content` |
| `DisableSelection` | `ComposeGeneratedDisableSelectionProps` | `content` |
| `ProvideTextStyle` | `ComposeGeneratedProvideTextStyleProps` | `style`、`content` |

`Image` 的多个来源字段在类型上同时可出现，但接口没有声明优先级；同一节点只应设置一种明确来源。Canvas 命令类型见 [Canvas 命令](./compose_dsl.md#canvas) 与 `examples/types/compose-dsl.d.ts`。

## 状态控件

| 工厂 | Props 类型 | 契约 |
| --- | --- | --- |
| `Switch` | `ComposeGeneratedSwitchProps` | 必填 `checked` 与 `onCheckedChange(checked)`；可设置 thumb/track 选中与未选中色、enabled 和 thumbContent。 |
| `Checkbox` | `ComposeGeneratedCheckboxProps` | 必填 `checked` 与 `onCheckedChange(checked)`；可设置 enabled。 |
| `RadioButton` | `ComposeGeneratedRadioButtonProps` | `selected`、`onClick`、`enabled`、`shape`。 |
| `FilledIconToggleButton` | `ComposeGeneratedFilledIconToggleButtonProps` | `checked`、`onCheckedChange`、`content`、`enabled`、`shape`。 |
| `FilledTonalIconToggleButton` | `ComposeGeneratedFilledTonalIconToggleButtonProps` | 与 Filled icon toggle 相同状态字段。 |
| `IconToggleButton` | `ComposeGeneratedIconToggleButtonProps` | `checked`、`onCheckedChange`、`icon`、`content`、`enabled`、`shape`。 |
| `OutlinedIconToggleButton` | `ComposeGeneratedOutlinedIconToggleButtonProps` | `checked`、`onCheckedChange`、`content`、`enabled`、`shape`。 |

状态控件是 controlled API：本次点击只通过 callback 通知建议的新值，界面当前状态仍来自下一次 render 的 `checked`/`selected`。

## 按钮与 Chips

| 工厂 | Props 类型 | 字段要点 |
| --- | --- | --- |
| `Button`、`ElevatedButton`、`FilledTonalButton`、`OutlinedButton`、`TextButton` | 分别为 `ComposeGeneratedButtonProps`、`ComposeGeneratedElevatedButtonProps`、`ComposeGeneratedFilledTonalButtonProps`、`ComposeGeneratedOutlinedButtonProps`、`ComposeGeneratedTextButtonProps` | 必填 `onClick`；`content`、`text`、`enabled`、容器/内容及禁用颜色、`contentPadding`、`shape`。 |
| `IconButton`、`FilledIconButton`、`FilledTonalIconButton`、`OutlinedIconButton` | 对应同名 `ComposeGenerated...Props` | 必填 `onClick`；可设置 `icon`、`content`、`enabled`、`shape`。 |
| `ExtendedFloatingActionButton`、`FloatingActionButton`、`LargeFloatingActionButton`、`SmallFloatingActionButton` | 对应同名 props interface | `content`、`contentColor`、必填 `onClick`、`shape`。 |
| `AssistChip`、`ElevatedAssistChip` | 对应同名 props interface | `label`、`leadingIcon`、`trailingIcon`、`enabled`、必填 `onClick`。 |
| `FilterChip`、`ElevatedFilterChip` | 对应同名 props interface | `label`、`selected`、`leadingIcon`、`trailingIcon`、`enabled`、必填 `onClick`。 |
| `SuggestionChip`、`ElevatedSuggestionChip` | 对应同名 props interface | `label`、`icon`、`enabled`、必填 `onClick`。 |
| `InputChip` | `ComposeGeneratedInputChipProps` | `avatar`、`label`、`leadingIcon`、`trailingIcon`、`selected`、`enabled`、必填 `onClick`。 |

图标内容以 Compose 子节点提供；`Icon` 的 name 可以使用 [Material Icons](./material_icons.md) 页面列出的 `Icons.Name`。

## Surface、反馈与 Scaffold

| 工厂 | Props 类型 | 字段要点 |
| --- | --- | --- |
| `Card`、`ElevatedCard`、`OutlinedCard` | 对应同名 props interface | `content`、`containerColor`、`contentColor`、`shape`、`border`、`elevation`。 |
| `Surface` | `ComposeGeneratedSurfaceProps` | `color`、`containerColor`、`contentColor`、`content`、`alpha`、`shadowElevation`、`tonalElevation`、`shape`、可选 `onClick`。 |
| `MaterialTheme` | `ComposeGeneratedMaterialThemeProps` | `content`。与 context 的 `MaterialTheme.colorScheme` token API 相区别。 |
| `Snackbar` | `ComposeGeneratedSnackbarProps` | `content`、`action`、`dismissAction`、`actionOnNewLine`、`contentColor`。 |
| `SnackbarHost` | `ComposeGeneratedSnackbarHostProps` | 仅公共属性与 `zIndex`。 |
| `Scaffold` | `ComposeGeneratedScaffoldProps` | `topBar`、`bottomBar`、`snackbarHost`、`floatingActionButton`、`content`、container/content color。 |
| `Divider`、`HorizontalDivider`、`VerticalDivider` | 对应同名 props interface | `color`、`thickness`。 |
| `Badge` | `ComposeGeneratedBadgeProps` | `content`、`contentColor`。 |
| `BadgedBox` | `ComposeGeneratedBadgedBoxProps` | `badge` 与 `content` slots。 |
| `PullToRefreshBox` | `ComposeGeneratedPullToRefreshBoxProps` | `content`、`contentAlignment`、`indicator`、`isRefreshing`、`onRefresh`。 |
| `TimePickerDialog` | `ComposeGeneratedTimePickerDialogProps` | `title`、`content`、`confirmButton`、`dismissButton`、`modeToggleButton`、`onDismissRequest`。 |
| `VerticalDragHandle` | `ComposeGeneratedVerticalDragHandleProps` | 公共属性与 `zIndex`。 |

## 导航、抽屉与 Tabs

- 导航表面：`NavigationBar`、`ShortNavigationBar`、`NavigationRail`、`WideNavigationRail`、`ModalWideNavigationRail`。分别使用同名 `ComposeGenerated...Props`，主要提供 `content`、`header`、`contentColor`、`verticalArrangement` 或 `hideOnCollapse`。
- 导航项：`NavigationDrawerItem`、`NavigationRailItem`、`ShortNavigationBarItem`、`WideNavigationRailItem`。字段包括 `icon`、`label`、`selected`、`enabled`、`onClick`；Wide rail item 另有 `railExpanded`，Rail item 另有 `alwaysShowLabel`，Drawer item 可带 `badge`。
- Drawer 容器：`DismissibleNavigationDrawer`、`ModalNavigationDrawer`、`PermanentNavigationDrawer`，字段为 `drawerContent`、`content`，dismissible/modal 另有 `gesturesEnabled`。
- Drawer sheet：`DismissibleDrawerSheet`、`ModalDrawerSheet`、`PermanentDrawerSheet`，字段为 `content` 与 `drawerTonalElevation`。
- `ModalWideNavigationRail` 另有 `expandedHeaderTopPadding`、`header`、`hideOnCollapse`、`verticalArrangement`。
- Tabs：`PrimaryTabRow`、`PrimaryScrollableTabRow`、`SecondaryTabRow`、`SecondaryScrollableTabRow`，字段为 `selectedTabIndex`、`tabs`、`indicator`、`divider`、`contentColor`；Scrollable 变体另有 `edgePadding`。
- `Tab`、`LeadingIconTab` 的字段包括 `selected`、`enabled`、`onClick`、`content`；LeadingIconTab 另有 `icon`、`text`。

## 全量 Registry

以下名称全部可由 `ctx.UI` 访问；每个名字对应 `ComposeGenerated<Name>Props`：

```text
Column Row Box Spacer LazyColumn LazyRow BoxWithConstraints
Text BasicText TextField Image Icon Canvas SelectionContainer DisableSelection ProvideTextStyle
Switch Checkbox RadioButton
Button ElevatedButton FilledTonalButton OutlinedButton TextButton
IconButton FilledIconButton FilledTonalIconButton OutlinedIconButton
FilledIconToggleButton FilledTonalIconToggleButton IconToggleButton OutlinedIconToggleButton
ExtendedFloatingActionButton FloatingActionButton LargeFloatingActionButton SmallFloatingActionButton
AssistChip ElevatedAssistChip FilterChip ElevatedFilterChip SuggestionChip ElevatedSuggestionChip InputChip
Card ElevatedCard OutlinedCard Surface MaterialTheme Scaffold Snackbar SnackbarHost
Divider HorizontalDivider VerticalDivider Badge BadgedBox PullToRefreshBox TimePickerDialog VerticalDragHandle
DismissibleDrawerSheet ModalDrawerSheet PermanentDrawerSheet
DismissibleNavigationDrawer ModalNavigationDrawer PermanentNavigationDrawer ModalWideNavigationRail
NavigationBar ShortNavigationBar NavigationRail WideNavigationRail
NavigationDrawerItem NavigationRailItem ShortNavigationBarItem WideNavigationRailItem
PrimaryTabRow PrimaryScrollableTabRow SecondaryTabRow SecondaryScrollableTabRow Tab LeadingIconTab
```

生成 props 的完整字段类型与 optional 性见 [`compose-dsl.material3.generated.d.ts`](../../../../examples/types/compose-dsl.material3.generated.d.ts)。API `@since` 标注仅以声明中的注释为准；常规 Material 3 组件没有因该文件生成而自动变为 ToolPkg API `1.0.1`。基础 `UI.Dialog` 与 `UI.AlertDialog` 的版本要求见 [Compose DSL](./compose_dsl.md#api-版本)。

## 相关源码

- `examples/types/compose-dsl.material3.generated.d.ts`
- `tools/compose_dsl/generate_compose_dsl_artifacts.py`
- `app/src/main/java/com/ai/assistance/operit/ui/common/composedsl/ToolPkgComposeDslGeneratedRegistry.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/common/composedsl/ToolPkgComposeDslGeneratedRenderers.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/common/composedsl/ToolPkgComposeDslScreen.kt`