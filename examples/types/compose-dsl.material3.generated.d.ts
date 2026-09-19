import type {
  ComposeAlignment,
  ComposeArrangement,
  ComposeBorder,
  ComposeChildren,
  ComposeColor,
  ComposeContentAlignment,
  ComposeCommonProps,
  ComposeNodeFactory,
  ComposePadding,
  ComposeShape,
  ComposeTextFieldStyle,
  ComposeTextAlign,
  ComposeTextOverflow,
  ComposeTextStyle,
  ComposeCanvasCommand,
  ComposeContentScale,
} from "./compose-dsl";

/**
 * AUTO-GENERATED from Compose Material3/Foundation source signatures.
 * Do not edit manually. Regenerate via tools/compose_dsl/generate_compose_dsl_artifacts.py.
 */

export interface ComposeGeneratedColumnProps extends ComposeCommonProps {
  content?: ComposeChildren;
  horizontalAlignment?: ComposeAlignment;
  verticalArrangement?: ComposeArrangement;
  zIndex?: number;
}

export interface ComposeGeneratedRowProps extends ComposeCommonProps {
  content?: ComposeChildren;
  horizontalArrangement?: ComposeArrangement;
  onClick?: () => void | Promise<void>;
  verticalAlignment?: ComposeAlignment;
  zIndex?: number;
}

export interface ComposeGeneratedBoxProps extends ComposeCommonProps {
  content?: ComposeChildren;
  contentAlignment?: ComposeContentAlignment;
  propagateMinConstraints?: boolean;
  zIndex?: number;
}

export interface ComposeGeneratedSpacerProps extends ComposeCommonProps {
  zIndex?: number;
}

export interface ComposeGeneratedLazyColumnProps extends ComposeCommonProps {
  autoScrollToEnd?: boolean;
  content?: ComposeChildren;
  horizontalAlignment?: ComposeAlignment;
  reverseLayout?: boolean;
  spacing?: number;
  verticalArrangement?: ComposeArrangement;
  zIndex?: number;
}

export interface ComposeGeneratedLazyRowProps extends ComposeCommonProps {
  content?: ComposeChildren;
  horizontalArrangement?: ComposeArrangement;
  reverseLayout?: boolean;
  verticalAlignment?: ComposeAlignment;
  zIndex?: number;
}

export interface ComposeGeneratedTextProps extends ComposeCommonProps {
  color?: ComposeColor;
  fontFamily?: string;
  fontSize?: number;
  fontWeight?: string;
  lineHeight?: number;
  maxLines?: number;
  overflow?: ComposeTextOverflow;
  softWrap?: boolean;
  style?: ComposeTextStyle;
  text: string;
  textAlign?: ComposeTextAlign;
  zIndex?: number;
}

export interface ComposeGeneratedTextFieldProps extends ComposeCommonProps {
  enabled?: boolean;
  isError?: boolean;
  isPassword?: boolean;
  label?: string | ComposeChildren;
  leadingIcon?: ComposeChildren;
  maxLines?: number;
  minLines?: number;
  onValueChange: (value: string) => void;
  placeholder?: string | ComposeChildren;
  prefix?: ComposeChildren;
  readOnly?: boolean;
  singleLine?: boolean;
  style?: ComposeTextFieldStyle;
  suffix?: ComposeChildren;
  supportingText?: ComposeChildren;
  trailingIcon?: ComposeChildren;
  value: string;
  zIndex?: number;
}

export interface ComposeGeneratedSwitchProps extends ComposeCommonProps {
  checked: boolean;
  checkedThumbColor?: ComposeColor;
  checkedTrackColor?: ComposeColor;
  enabled?: boolean;
  onCheckedChange: (checked: boolean) => void;
  thumbContent?: ComposeChildren;
  uncheckedThumbColor?: ComposeColor;
  uncheckedTrackColor?: ComposeColor;
  zIndex?: number;
}

export interface ComposeGeneratedCheckboxProps extends ComposeCommonProps {
  checked: boolean;
  enabled?: boolean;
  onCheckedChange: (checked: boolean) => void;
  zIndex?: number;
}

export interface ComposeGeneratedButtonProps extends ComposeCommonProps {
  containerColor?: ComposeColor;
  content?: ComposeChildren;
  contentColor?: ComposeColor;
  contentPadding?: ComposePadding;
  disabledContainerColor?: ComposeColor;
  disabledContentColor?: ComposeColor;
  enabled?: boolean;
  onClick: () => void | Promise<void>;
  shape?: ComposeShape;
  text?: string;
  zIndex?: number;
}

export interface ComposeGeneratedIconButtonProps extends ComposeCommonProps {
  content?: ComposeChildren;
  enabled?: boolean;
  icon?: string;
  onClick: () => void | Promise<void>;
  shape?: ComposeShape;
  zIndex?: number;
}

export interface ComposeGeneratedCardProps extends ComposeCommonProps {
  border?: ComposeBorder;
  containerColor?: ComposeColor;
  content?: ComposeChildren;
  contentColor?: ComposeColor;
  elevation?: number;
  shape?: ComposeShape;
  zIndex?: number;
}

export interface ComposeGeneratedMaterialThemeProps extends ComposeCommonProps {
  content?: ComposeChildren;
  zIndex?: number;
}

export interface ComposeGeneratedSurfaceProps extends ComposeCommonProps {
  alpha?: number;
  color?: ComposeColor;
  containerColor?: ComposeColor;
  content?: ComposeChildren;
  contentColor?: ComposeColor;
  onClick?: () => void | Promise<void>;
  shadowElevation?: number;
  shape?: ComposeShape;
  tonalElevation?: number;
  zIndex?: number;
}

export interface ComposeGeneratedIconProps extends ComposeCommonProps {
  contentDescription?: string;
  name?: string;
  size?: number;
  tint?: ComposeColor;
  zIndex?: number;
}

export interface ComposeGeneratedLinearProgressIndicatorProps extends ComposeCommonProps {
  color?: ComposeColor;
  progress?: number;
  zIndex?: number;
}

export interface ComposeGeneratedCircularProgressIndicatorProps extends ComposeCommonProps {
  color?: ComposeColor;
  strokeWidth?: number;
  zIndex?: number;
}

export interface ComposeGeneratedSliderProps extends ComposeCommonProps {
  enabled?: boolean;
  onValueChange: (value: number) => void;
  onValueChangeFinished?: () => void | Promise<void>;
  steps?: number;
  value: number;
  zIndex?: number;
}

export interface ComposeGeneratedSnackbarHostProps extends ComposeCommonProps {
  zIndex?: number;
}

export interface ComposeGeneratedAssistChipProps extends ComposeCommonProps {
  enabled?: boolean;
  label: ComposeChildren;
  leadingIcon?: ComposeChildren;
  onClick: () => void | Promise<void>;
  trailingIcon?: ComposeChildren;
  zIndex?: number;
}

export interface ComposeGeneratedBadgeProps extends ComposeCommonProps {
  content?: ComposeChildren;
  contentColor?: ComposeColor;
  zIndex?: number;
}

export interface ComposeGeneratedBadgedBoxProps extends ComposeCommonProps {
  badge: ComposeChildren;
  content?: ComposeChildren;
  zIndex?: number;
}

export interface ComposeGeneratedDismissibleDrawerSheetProps extends ComposeCommonProps {
  content?: ComposeChildren;
  drawerTonalElevation?: number;
  zIndex?: number;
}

export interface ComposeGeneratedDismissibleNavigationDrawerProps extends ComposeCommonProps {
  content?: ComposeChildren;
  drawerContent: ComposeChildren;
  gesturesEnabled?: boolean;
  zIndex?: number;
}

export interface ComposeGeneratedDividerProps extends ComposeCommonProps {
  color?: ComposeColor;
  thickness?: number;
  zIndex?: number;
}

export interface ComposeGeneratedDropdownMenuProps extends ComposeCommonProps {
  content?: ComposeChildren;
  expanded: boolean;
  offset?: number;
  onDismissRequest: () => void | Promise<void>;
  properties?: { focusable?: boolean; dismissOnBackPress?: boolean; dismissOnClickOutside?: boolean; clippingEnabled?: boolean; usePlatformDefaultWidth?: boolean; };
  zIndex?: number;
}

export interface ComposeGeneratedElevatedAssistChipProps extends ComposeCommonProps {
  enabled?: boolean;
  label: ComposeChildren;
  leadingIcon?: ComposeChildren;
  onClick: () => void | Promise<void>;
  trailingIcon?: ComposeChildren;
  zIndex?: number;
}

export interface ComposeGeneratedElevatedButtonProps extends ComposeCommonProps {
  containerColor?: ComposeColor;
  content?: ComposeChildren;
  contentColor?: ComposeColor;
  contentPadding?: ComposePadding;
  disabledContainerColor?: ComposeColor;
  disabledContentColor?: ComposeColor;
  enabled?: boolean;
  onClick: () => void | Promise<void>;
  shape?: ComposeShape;
  zIndex?: number;
}

export interface ComposeGeneratedElevatedCardProps extends ComposeCommonProps {
  border?: ComposeBorder;
  containerColor?: ComposeColor;
  content?: ComposeChildren;
  contentColor?: ComposeColor;
  elevation?: number;
  shape?: ComposeShape;
  zIndex?: number;
}

export interface ComposeGeneratedElevatedFilterChipProps extends ComposeCommonProps {
  enabled?: boolean;
  label: ComposeChildren;
  leadingIcon?: ComposeChildren;
  onClick: () => void | Promise<void>;
  selected: boolean;
  trailingIcon?: ComposeChildren;
  zIndex?: number;
}

export interface ComposeGeneratedElevatedSuggestionChipProps extends ComposeCommonProps {
  enabled?: boolean;
  icon?: ComposeChildren;
  label: ComposeChildren;
  onClick: () => void | Promise<void>;
  zIndex?: number;
}

export interface ComposeGeneratedExtendedFloatingActionButtonProps extends ComposeCommonProps {
  content?: ComposeChildren;
  contentColor?: ComposeColor;
  onClick: () => void | Promise<void>;
  shape?: ComposeShape;
  zIndex?: number;
}

export interface ComposeGeneratedFilledIconButtonProps extends ComposeCommonProps {
  content?: ComposeChildren;
  enabled?: boolean;
  icon?: string;
  onClick: () => void | Promise<void>;
  shape?: ComposeShape;
  zIndex?: number;
}

export interface ComposeGeneratedFilledIconToggleButtonProps extends ComposeCommonProps {
  checked: boolean;
  content?: ComposeChildren;
  enabled?: boolean;
  onCheckedChange: (checked: boolean) => void;
  shape?: ComposeShape;
  zIndex?: number;
}

export interface ComposeGeneratedFilledTonalButtonProps extends ComposeCommonProps {
  containerColor?: ComposeColor;
  content?: ComposeChildren;
  contentColor?: ComposeColor;
  contentPadding?: ComposePadding;
  disabledContainerColor?: ComposeColor;
  disabledContentColor?: ComposeColor;
  enabled?: boolean;
  onClick: () => void | Promise<void>;
  shape?: ComposeShape;
  zIndex?: number;
}

export interface ComposeGeneratedFilledTonalIconButtonProps extends ComposeCommonProps {
  content?: ComposeChildren;
  enabled?: boolean;
  icon?: string;
  onClick: () => void | Promise<void>;
  shape?: ComposeShape;
  zIndex?: number;
}

export interface ComposeGeneratedFilledTonalIconToggleButtonProps extends ComposeCommonProps {
  checked: boolean;
  content?: ComposeChildren;
  enabled?: boolean;
  onCheckedChange: (checked: boolean) => void;
  shape?: ComposeShape;
  zIndex?: number;
}

export interface ComposeGeneratedFilterChipProps extends ComposeCommonProps {
  enabled?: boolean;
  label: ComposeChildren;
  leadingIcon?: ComposeChildren;
  onClick: () => void | Promise<void>;
  selected: boolean;
  trailingIcon?: ComposeChildren;
  zIndex?: number;
}

export interface ComposeGeneratedFloatingActionButtonProps extends ComposeCommonProps {
  content?: ComposeChildren;
  contentColor?: ComposeColor;
  onClick: () => void | Promise<void>;
  shape?: ComposeShape;
  zIndex?: number;
}

export interface ComposeGeneratedHorizontalDividerProps extends ComposeCommonProps {
  color?: ComposeColor;
  thickness?: number;
  zIndex?: number;
}

export interface ComposeGeneratedIconToggleButtonProps extends ComposeCommonProps {
  checked: boolean;
  content?: ComposeChildren;
  enabled?: boolean;
  icon?: string;
  onCheckedChange: (checked: boolean) => void;
  shape?: ComposeShape;
  zIndex?: number;
}

export interface ComposeGeneratedInputChipProps extends ComposeCommonProps {
  avatar?: ComposeChildren;
  enabled?: boolean;
  label: ComposeChildren;
  leadingIcon?: ComposeChildren;
  onClick: () => void | Promise<void>;
  selected: boolean;
  trailingIcon?: ComposeChildren;
  zIndex?: number;
}

export interface ComposeGeneratedLargeFloatingActionButtonProps extends ComposeCommonProps {
  content?: ComposeChildren;
  contentColor?: ComposeColor;
  onClick: () => void | Promise<void>;
  shape?: ComposeShape;
  zIndex?: number;
}

export interface ComposeGeneratedLeadingIconTabProps extends ComposeCommonProps {
  enabled?: boolean;
  icon: ComposeChildren;
  onClick: () => void | Promise<void>;
  selected: boolean;
  text: ComposeChildren;
  zIndex?: number;
}

export interface ComposeGeneratedListItemProps extends ComposeCommonProps {
  headlineContent: ComposeChildren;
  leadingContent?: ComposeChildren;
  overlineContent?: ComposeChildren;
  shadowElevation?: number;
  supportingContent?: ComposeChildren;
  tonalElevation?: number;
  trailingContent?: ComposeChildren;
  zIndex?: number;
}

export interface ComposeGeneratedModalDrawerSheetProps extends ComposeCommonProps {
  content?: ComposeChildren;
  drawerTonalElevation?: number;
  zIndex?: number;
}

export interface ComposeGeneratedModalNavigationDrawerProps extends ComposeCommonProps {
  content?: ComposeChildren;
  drawerContent: ComposeChildren;
  gesturesEnabled?: boolean;
  zIndex?: number;
}

export interface ComposeGeneratedModalWideNavigationRailProps extends ComposeCommonProps {
  content?: ComposeChildren;
  expandedHeaderTopPadding?: number;
  header?: ComposeChildren;
  hideOnCollapse?: boolean;
  verticalArrangement?: ComposeArrangement;
  zIndex?: number;
}

export interface ComposeGeneratedNavigationBarProps extends ComposeCommonProps {
  content?: ComposeChildren;
  contentColor?: ComposeColor;
  tonalElevation?: number;
  zIndex?: number;
}

export interface ComposeGeneratedNavigationDrawerItemProps extends ComposeCommonProps {
  badge?: ComposeChildren;
  icon?: ComposeChildren;
  label: ComposeChildren;
  onClick: () => void | Promise<void>;
  selected: boolean;
  zIndex?: number;
}

export interface ComposeGeneratedNavigationRailProps extends ComposeCommonProps {
  content?: ComposeChildren;
  contentColor?: ComposeColor;
  header?: ComposeChildren;
  zIndex?: number;
}

export interface ComposeGeneratedNavigationRailItemProps extends ComposeCommonProps {
  alwaysShowLabel?: boolean;
  enabled?: boolean;
  icon: ComposeChildren;
  label?: ComposeChildren;
  onClick: () => void | Promise<void>;
  selected: boolean;
  zIndex?: number;
}

export interface ComposeGeneratedOutlinedButtonProps extends ComposeCommonProps {
  containerColor?: ComposeColor;
  content?: ComposeChildren;
  contentColor?: ComposeColor;
  contentPadding?: ComposePadding;
  disabledContainerColor?: ComposeColor;
  disabledContentColor?: ComposeColor;
  enabled?: boolean;
  onClick: () => void | Promise<void>;
  shape?: ComposeShape;
  zIndex?: number;
}

export interface ComposeGeneratedOutlinedCardProps extends ComposeCommonProps {
  border?: ComposeBorder;
  containerColor?: ComposeColor;
  content?: ComposeChildren;
  contentColor?: ComposeColor;
  elevation?: number;
  shape?: ComposeShape;
  zIndex?: number;
}

export interface ComposeGeneratedOutlinedIconButtonProps extends ComposeCommonProps {
  content?: ComposeChildren;
  enabled?: boolean;
  icon?: string;
  onClick: () => void | Promise<void>;
  shape?: ComposeShape;
  zIndex?: number;
}

export interface ComposeGeneratedOutlinedIconToggleButtonProps extends ComposeCommonProps {
  checked: boolean;
  content?: ComposeChildren;
  enabled?: boolean;
  onCheckedChange: (checked: boolean) => void;
  shape?: ComposeShape;
  zIndex?: number;
}

export interface ComposeGeneratedPermanentDrawerSheetProps extends ComposeCommonProps {
  content?: ComposeChildren;
  drawerTonalElevation?: number;
  zIndex?: number;
}

export interface ComposeGeneratedPermanentNavigationDrawerProps extends ComposeCommonProps {
  content?: ComposeChildren;
  drawerContent: ComposeChildren;
  zIndex?: number;
}

export interface ComposeGeneratedPrimaryScrollableTabRowProps extends ComposeCommonProps {
  contentColor?: ComposeColor;
  divider?: ComposeChildren;
  edgePadding?: number;
  indicator?: ComposeChildren;
  selectedTabIndex: number;
  tabs: ComposeChildren;
  zIndex?: number;
}

export interface ComposeGeneratedPrimaryTabRowProps extends ComposeCommonProps {
  contentColor?: ComposeColor;
  divider?: ComposeChildren;
  indicator?: ComposeChildren;
  selectedTabIndex: number;
  tabs: ComposeChildren;
  zIndex?: number;
}

export interface ComposeGeneratedProvideTextStyleProps extends ComposeCommonProps {
  content?: ComposeChildren;
  style?: ComposeTextStyle;
  zIndex?: number;
}

export interface ComposeGeneratedPullToRefreshBoxProps extends ComposeCommonProps {
  content?: ComposeChildren;
  contentAlignment?: ComposeContentAlignment;
  indicator?: ComposeChildren;
  isRefreshing: boolean;
  onRefresh: () => void | Promise<void>;
  zIndex?: number;
}

export interface ComposeGeneratedRadioButtonProps extends ComposeCommonProps {
  enabled?: boolean;
  onClick: () => void | Promise<void>;
  selected: boolean;
  shape?: ComposeShape;
  zIndex?: number;
}

export interface ComposeGeneratedScaffoldProps extends ComposeCommonProps {
  bottomBar?: ComposeChildren;
  containerColor?: ComposeColor;
  content?: ComposeChildren;
  contentColor?: ComposeColor;
  floatingActionButton?: ComposeChildren;
  snackbarHost?: ComposeChildren;
  topBar?: ComposeChildren;
  zIndex?: number;
}

export interface ComposeGeneratedSecondaryScrollableTabRowProps extends ComposeCommonProps {
  contentColor?: ComposeColor;
  divider?: ComposeChildren;
  edgePadding?: number;
  indicator?: ComposeChildren;
  selectedTabIndex: number;
  tabs: ComposeChildren;
  zIndex?: number;
}

export interface ComposeGeneratedSecondaryTabRowProps extends ComposeCommonProps {
  contentColor?: ComposeColor;
  divider?: ComposeChildren;
  indicator?: ComposeChildren;
  selectedTabIndex: number;
  tabs: ComposeChildren;
  zIndex?: number;
}

export interface ComposeGeneratedShortNavigationBarProps extends ComposeCommonProps {
  content?: ComposeChildren;
  contentColor?: ComposeColor;
  zIndex?: number;
}

export interface ComposeGeneratedShortNavigationBarItemProps extends ComposeCommonProps {
  enabled?: boolean;
  icon: ComposeChildren;
  label: ComposeChildren;
  onClick: () => void | Promise<void>;
  selected: boolean;
  zIndex?: number;
}

export interface ComposeGeneratedSmallFloatingActionButtonProps extends ComposeCommonProps {
  content?: ComposeChildren;
  contentColor?: ComposeColor;
  onClick: () => void | Promise<void>;
  shape?: ComposeShape;
  zIndex?: number;
}

export interface ComposeGeneratedSnackbarProps extends ComposeCommonProps {
  action?: ComposeChildren;
  actionOnNewLine?: boolean;
  content?: ComposeChildren;
  contentColor?: ComposeColor;
  dismissAction?: ComposeChildren;
  zIndex?: number;
}

export interface ComposeGeneratedSuggestionChipProps extends ComposeCommonProps {
  enabled?: boolean;
  icon?: ComposeChildren;
  label: ComposeChildren;
  onClick: () => void | Promise<void>;
  zIndex?: number;
}

export interface ComposeGeneratedTabProps extends ComposeCommonProps {
  content?: ComposeChildren;
  enabled?: boolean;
  onClick: () => void | Promise<void>;
  selected: boolean;
  zIndex?: number;
}

export interface ComposeGeneratedTextButtonProps extends ComposeCommonProps {
  containerColor?: ComposeColor;
  content?: ComposeChildren;
  contentColor?: ComposeColor;
  contentPadding?: ComposePadding;
  disabledContainerColor?: ComposeColor;
  disabledContentColor?: ComposeColor;
  enabled?: boolean;
  onClick: () => void | Promise<void>;
  shape?: ComposeShape;
  zIndex?: number;
}

export interface ComposeGeneratedTimePickerDialogProps extends ComposeCommonProps {
  confirmButton: ComposeChildren;
  content?: ComposeChildren;
  dismissButton?: ComposeChildren;
  modeToggleButton?: ComposeChildren;
  onDismissRequest: () => void | Promise<void>;
  title: ComposeChildren;
  zIndex?: number;
}

export interface ComposeGeneratedVerticalDividerProps extends ComposeCommonProps {
  color?: ComposeColor;
  thickness?: number;
  zIndex?: number;
}

export interface ComposeGeneratedVerticalDragHandleProps extends ComposeCommonProps {
  zIndex?: number;
}

export interface ComposeGeneratedWideNavigationRailProps extends ComposeCommonProps {
  content?: ComposeChildren;
  header?: ComposeChildren;
  verticalArrangement?: ComposeArrangement;
  zIndex?: number;
}

export interface ComposeGeneratedWideNavigationRailItemProps extends ComposeCommonProps {
  enabled?: boolean;
  icon: ComposeChildren;
  label: ComposeChildren;
  onClick: () => void | Promise<void>;
  railExpanded: boolean;
  selected: boolean;
  zIndex?: number;
}

export interface ComposeGeneratedBoxWithConstraintsProps extends ComposeCommonProps {
  content?: ComposeChildren;
  contentAlignment?: ComposeContentAlignment;
  propagateMinConstraints?: boolean;
  zIndex?: number;
}

export interface ComposeGeneratedBasicTextProps extends ComposeCommonProps {
  fontFamily?: string;
  fontSize?: number;
  lineHeight?: number;
  maxLines?: number;
  onTextLayout?: () => void | Promise<void>;
  overflow?: ComposeTextOverflow;
  softWrap?: boolean;
  style?: ComposeTextStyle;
  text: string;
  textAlign?: ComposeTextAlign;
  zIndex?: number;
}

export interface ComposeGeneratedDisableSelectionProps extends ComposeCommonProps {
  content?: ComposeChildren;
  zIndex?: number;
}

export interface ComposeGeneratedImageProps extends ComposeCommonProps {
  alpha?: number;
  contentAlignment?: ComposeContentAlignment;
  contentDescription?: string;
  contentScale?: ComposeContentScale;
  fileUri?: string;
  icon?: string;
  name?: string;
  path?: string;
  src?: string;
  uri?: string;
  url?: string;
  zIndex?: number;
}

export interface ComposeGeneratedSelectionContainerProps extends ComposeCommonProps {
  content?: ComposeChildren;
  zIndex?: number;
}

export interface ComposeGeneratedCanvasProps extends ComposeCommonProps {
  zIndex?: number;
  commands?: ComposeCanvasCommand[];
}

export interface ComposeMaterial3GeneratedUiFactoryRegistry {
  Column: ComposeNodeFactory<ComposeGeneratedColumnProps>;
  Row: ComposeNodeFactory<ComposeGeneratedRowProps>;
  Box: ComposeNodeFactory<ComposeGeneratedBoxProps>;
  Spacer: ComposeNodeFactory<ComposeGeneratedSpacerProps>;
  LazyColumn: ComposeNodeFactory<ComposeGeneratedLazyColumnProps>;
  LazyRow: ComposeNodeFactory<ComposeGeneratedLazyRowProps>;
  Text: ComposeNodeFactory<ComposeGeneratedTextProps>;
  TextField: ComposeNodeFactory<ComposeGeneratedTextFieldProps>;
  Switch: ComposeNodeFactory<ComposeGeneratedSwitchProps>;
  Checkbox: ComposeNodeFactory<ComposeGeneratedCheckboxProps>;
  Button: ComposeNodeFactory<ComposeGeneratedButtonProps>;
  IconButton: ComposeNodeFactory<ComposeGeneratedIconButtonProps>;
  Card: ComposeNodeFactory<ComposeGeneratedCardProps>;
  MaterialTheme: ComposeNodeFactory<ComposeGeneratedMaterialThemeProps>;
  Surface: ComposeNodeFactory<ComposeGeneratedSurfaceProps>;
  Icon: ComposeNodeFactory<ComposeGeneratedIconProps>;
  LinearProgressIndicator: ComposeNodeFactory<ComposeGeneratedLinearProgressIndicatorProps>;
  CircularProgressIndicator: ComposeNodeFactory<ComposeGeneratedCircularProgressIndicatorProps>;
  Slider: ComposeNodeFactory<ComposeGeneratedSliderProps>;
  SnackbarHost: ComposeNodeFactory<ComposeGeneratedSnackbarHostProps>;
  AssistChip: ComposeNodeFactory<ComposeGeneratedAssistChipProps>;
  Badge: ComposeNodeFactory<ComposeGeneratedBadgeProps>;
  BadgedBox: ComposeNodeFactory<ComposeGeneratedBadgedBoxProps>;
  DismissibleDrawerSheet: ComposeNodeFactory<ComposeGeneratedDismissibleDrawerSheetProps>;
  DismissibleNavigationDrawer: ComposeNodeFactory<ComposeGeneratedDismissibleNavigationDrawerProps>;
  Divider: ComposeNodeFactory<ComposeGeneratedDividerProps>;
  DropdownMenu: ComposeNodeFactory<ComposeGeneratedDropdownMenuProps>;
  ElevatedAssistChip: ComposeNodeFactory<ComposeGeneratedElevatedAssistChipProps>;
  ElevatedButton: ComposeNodeFactory<ComposeGeneratedElevatedButtonProps>;
  ElevatedCard: ComposeNodeFactory<ComposeGeneratedElevatedCardProps>;
  ElevatedFilterChip: ComposeNodeFactory<ComposeGeneratedElevatedFilterChipProps>;
  ElevatedSuggestionChip: ComposeNodeFactory<ComposeGeneratedElevatedSuggestionChipProps>;
  ExtendedFloatingActionButton: ComposeNodeFactory<ComposeGeneratedExtendedFloatingActionButtonProps>;
  FilledIconButton: ComposeNodeFactory<ComposeGeneratedFilledIconButtonProps>;
  FilledIconToggleButton: ComposeNodeFactory<ComposeGeneratedFilledIconToggleButtonProps>;
  FilledTonalButton: ComposeNodeFactory<ComposeGeneratedFilledTonalButtonProps>;
  FilledTonalIconButton: ComposeNodeFactory<ComposeGeneratedFilledTonalIconButtonProps>;
  FilledTonalIconToggleButton: ComposeNodeFactory<ComposeGeneratedFilledTonalIconToggleButtonProps>;
  FilterChip: ComposeNodeFactory<ComposeGeneratedFilterChipProps>;
  FloatingActionButton: ComposeNodeFactory<ComposeGeneratedFloatingActionButtonProps>;
  HorizontalDivider: ComposeNodeFactory<ComposeGeneratedHorizontalDividerProps>;
  IconToggleButton: ComposeNodeFactory<ComposeGeneratedIconToggleButtonProps>;
  InputChip: ComposeNodeFactory<ComposeGeneratedInputChipProps>;
  LargeFloatingActionButton: ComposeNodeFactory<ComposeGeneratedLargeFloatingActionButtonProps>;
  LeadingIconTab: ComposeNodeFactory<ComposeGeneratedLeadingIconTabProps>;
  ListItem: ComposeNodeFactory<ComposeGeneratedListItemProps>;
  ModalDrawerSheet: ComposeNodeFactory<ComposeGeneratedModalDrawerSheetProps>;
  ModalNavigationDrawer: ComposeNodeFactory<ComposeGeneratedModalNavigationDrawerProps>;
  ModalWideNavigationRail: ComposeNodeFactory<ComposeGeneratedModalWideNavigationRailProps>;
  NavigationBar: ComposeNodeFactory<ComposeGeneratedNavigationBarProps>;
  NavigationDrawerItem: ComposeNodeFactory<ComposeGeneratedNavigationDrawerItemProps>;
  NavigationRail: ComposeNodeFactory<ComposeGeneratedNavigationRailProps>;
  NavigationRailItem: ComposeNodeFactory<ComposeGeneratedNavigationRailItemProps>;
  OutlinedButton: ComposeNodeFactory<ComposeGeneratedOutlinedButtonProps>;
  OutlinedCard: ComposeNodeFactory<ComposeGeneratedOutlinedCardProps>;
  OutlinedIconButton: ComposeNodeFactory<ComposeGeneratedOutlinedIconButtonProps>;
  OutlinedIconToggleButton: ComposeNodeFactory<ComposeGeneratedOutlinedIconToggleButtonProps>;
  PermanentDrawerSheet: ComposeNodeFactory<ComposeGeneratedPermanentDrawerSheetProps>;
  PermanentNavigationDrawer: ComposeNodeFactory<ComposeGeneratedPermanentNavigationDrawerProps>;
  PrimaryScrollableTabRow: ComposeNodeFactory<ComposeGeneratedPrimaryScrollableTabRowProps>;
  PrimaryTabRow: ComposeNodeFactory<ComposeGeneratedPrimaryTabRowProps>;
  ProvideTextStyle: ComposeNodeFactory<ComposeGeneratedProvideTextStyleProps>;
  PullToRefreshBox: ComposeNodeFactory<ComposeGeneratedPullToRefreshBoxProps>;
  RadioButton: ComposeNodeFactory<ComposeGeneratedRadioButtonProps>;
  Scaffold: ComposeNodeFactory<ComposeGeneratedScaffoldProps>;
  SecondaryScrollableTabRow: ComposeNodeFactory<ComposeGeneratedSecondaryScrollableTabRowProps>;
  SecondaryTabRow: ComposeNodeFactory<ComposeGeneratedSecondaryTabRowProps>;
  ShortNavigationBar: ComposeNodeFactory<ComposeGeneratedShortNavigationBarProps>;
  ShortNavigationBarItem: ComposeNodeFactory<ComposeGeneratedShortNavigationBarItemProps>;
  SmallFloatingActionButton: ComposeNodeFactory<ComposeGeneratedSmallFloatingActionButtonProps>;
  Snackbar: ComposeNodeFactory<ComposeGeneratedSnackbarProps>;
  SuggestionChip: ComposeNodeFactory<ComposeGeneratedSuggestionChipProps>;
  Tab: ComposeNodeFactory<ComposeGeneratedTabProps>;
  TextButton: ComposeNodeFactory<ComposeGeneratedTextButtonProps>;
  TimePickerDialog: ComposeNodeFactory<ComposeGeneratedTimePickerDialogProps>;
  VerticalDivider: ComposeNodeFactory<ComposeGeneratedVerticalDividerProps>;
  VerticalDragHandle: ComposeNodeFactory<ComposeGeneratedVerticalDragHandleProps>;
  WideNavigationRail: ComposeNodeFactory<ComposeGeneratedWideNavigationRailProps>;
  WideNavigationRailItem: ComposeNodeFactory<ComposeGeneratedWideNavigationRailItemProps>;
  BoxWithConstraints: ComposeNodeFactory<ComposeGeneratedBoxWithConstraintsProps>;
  BasicText: ComposeNodeFactory<ComposeGeneratedBasicTextProps>;
  DisableSelection: ComposeNodeFactory<ComposeGeneratedDisableSelectionProps>;
  Image: ComposeNodeFactory<ComposeGeneratedImageProps>;
  SelectionContainer: ComposeNodeFactory<ComposeGeneratedSelectionContainerProps>;
  Canvas: ComposeNodeFactory<ComposeGeneratedCanvasProps>;
}
