---
title: Material Icons
status: complete
---

# Material Icons

运行时全局 `Icons` 是动态属性代理：`Icons.Add` 返回字符串 `"Add"`，不执行存在性校验，也不返回图像对象。Compose 图标组件再消费该名称，例如：

```js
ctx.UI.Icon({ name: Icons.Add, size: 24 });
```

`material-icons.d.ts` 提供三个类型：

- `KnownMaterialIconName`：下方固定字面量联合，用于编辑器自动补全。
- `MaterialIconName`：`KnownMaterialIconName | (string & {})`，因此类型层仍接受任意字符串。
- `MaterialIconsRegistry`：已知名称映射为同名只读字符串，同时带 `[key: string]: string` 索引签名。该声明描述代理外形，不表示运行时维护了枚举表。

非字符串属性读取会返回空字符串；任意字符串属性都会返回属性名本身。运行时能否渲染给定名称由宿主图标资源决定，未知名称可能不显示。图标名称区分大小写，以下拼写以声明为准。

## `KnownMaterialIconName`

```text
AccountCircle AccountTree Add AddCircleOutline AddComment AddPhotoAlternate
AdminPanelSettings Analytics Android Api Apps AppShortcut Archive
ArrowBack ArrowDropDown ArrowForward ArrowUpward Article AspectRatio
Assignment AssignmentInd Assistant AttachFile AudioFile AutoAwesome AutoFixHigh AutoMode
Badge Block Bolt Book Bookmark Brightness4 Brush Build Cake
CalendarMonth CalendarToday Call Cancel Chat ChatBubble Check CheckBox
CheckBoxOutlineBlank CheckCircle ChevronLeft ChevronRight Class Clear Close
Cloud CloudDownload CloudUpload Code ColorLens Computer Contacts ContentCopy
ContentCut ContentPaste CreateNewFolder CreditCard Crop Dashboard DataObject
DateRange Delete DeleteForever DeleteSweep Description DesktopWindows DeviceHub
Devices Difference DirectionsRun Dns Done Download DragHandle DriveFileRenameOutline
Edit Email EmojiEmotions Error ErrorOutline ExpandLess ExpandMore Extension
Face Favorite FiberManualRecord FileDownload FileOpen FileUpload Folder FolderCopy
FolderOff FolderOpen FolderZip FormatListBulleted Fullscreen FullscreenExit Functions
Group Groups Help HelpOutline History Home HourglassBottom HourglassTop Html Hub
Image Info Insights InsertDriveFile Key KeyboardArrowDown KeyboardArrowRight KeyboardArrowUp
Label Language Launch Lightbulb Link LinkOff List LocationOn Lock LockOpen
Login Logout Loop ManageHistory Memory Menu Message Mic MicNone MicOff
Minimize MoreHoriz MoreVert NetworkCheck NewReleases Notifications OpenInBrowser OpenInFull
OpenInNew Palette Pause Pending People Person Phone PhoneAndroid PhotoCamera
PictureAsPdf PictureInPicture PlayArrow PlayCircle PlaylistAddCheck Policy Portrait PriorityHigh
Psychology QrCode2 RecordVoiceOver Redo Refresh Remove Repeat Reply RestartAlt Restore
Router Save Schedule Science ScreenshotMonitor SdCard Search SearchOff Security
SelectAll Send Sensors Settings SettingsApplications SettingsEthernet Share Shield
Smartphone SmartToy Sms Sort Source Speed Star StarOutline Stop Storage Store
SportsEsports SubdirectoryArrowRight Summarize SwapHoriz Sync TableChart TableView Tag
Terminal TextFields TextSnippet ThumbUp ToggleOn Token TouchApp Translate Tune
Undo Update Upload UploadFile Videocam VideoFile VideoSettings ViewComfy ViewList
ViewModule Visibility VisibilityOff VolumeOff VolumeUp Warning Web Whatshot Widgets
Wifi WifiOff Work ZoomIn ZoomOut
```

该名称集的唯一事实源是 [`examples/types/material-icons.d.ts`](../../../../examples/types/material-icons.d.ts)。在 Compose DSL 组件的 props 中，图标字段类型仍通常是 `string`；`Icons.Name` 主要提供拼写补全。

## 运行时来源

- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsLibraries.kt` 中的 `Icons` Proxy
- `examples/types/material-icons.d.ts`
- [Compose DSL](./compose_dsl.md)
- [Material 3 组件](./material3_components.md)