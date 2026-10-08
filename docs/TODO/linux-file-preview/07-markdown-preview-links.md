# 工作区 Markdown 预览链接与普通失败处理

对应 [Issue #1347](https://github.com/AAswordman/Operit/issues/1347)，修正接入现有工作区 PR #1344，目标分支为 dev。

## 原行为与修正

工作区 Markdown 预览直接调用 LocalUriHandler.openUri，把相对路径、锚点、空链接和无处理器的应用协议都当成系统链接。Android 没有匹配 Activity 时，异常从点击回调离开并终止进程；已有应用市场 Markdown 的保护入口未覆盖工作区预览。

WorkspaceMarkdownPreview 拆到 workspace/markdown，接收完整 OpenFileInfo 和文件打开回调。WorkspaceMarkdownLink 按当前文档所在目录和来源解析相对路径，保留中文、空格、查询参数、编码字符和显式来源。未指定来源的绝对路径及 file URI 继承当前文档来源；显式 Android、Linux、仓库协议或 environment 参数优先。

文件和目录复用 WorkspaceFileLinkDialog 的文件检查、10 MiB 文本门槛与异步读取。读取成功的文件交回当前工作区标签；已打开文件保留内存内容和未保存状态。缺失文件、权限拒绝、超限文本和无法定位的标题在信息页或 Toast 提示，页面可返回；普通失败记录为警告，异常不离开点击或加载入口。取消仍按协程取消处理。

文本行号支持 #L12、:12、?line=12，沿用 fragment、query、路径后缀优先级。编码后的冒号属于真实文件名。同一文件反复点击相同行号会产生显式定位请求，同文件输入不会重新触发行号定位。

标题锚点（例如 #页脚、target.md#附录）解析为对应源码行并进入编辑器定位。支持 ATX、Setext、中文、常见标题 slug 和重复标题后缀，代码块中的伪标题不参与匹配；找不到标题时给出明确提示。标题扫描在后台执行。预览滚动状态按文件标识保存，切回预览可继续阅读。

外链打开提取到 ui/common/markdown/links/MarkdownLinkOpener，工作区预览、聊天链接弹窗和应用市场 Markdown 共用。空地址、无 scheme、畸形 URI、无应用处理、系统权限与参数异常均返回失败提示；合法网页和应用协议仍交给系统。此次处理预期的链接失败，不改写真正崩溃或 ANR 的检测逻辑。

## 完整回归范围

既有基础、文件入口、主题、两轮审阅与文件引用格式说明全部保留，见 [01-interaction-design.md](01-interaction-design.md)、[02-file-links-and-theme.md](02-file-links-and-theme.md)、[03-review-fixes.md](03-review-fixes.md)、[04-file-links-review.md](04-file-links-review.md) 和 [06-file-reference-conventions.md](06-file-reference-conventions.md)。下表追加本轮操作与预期，结果表单独填写。

| 编号 | 操作 | 预期 |
| --- | --- | --- |
| M-R01 | 预览 main.md，点击 target.md、./target.md、subdir/nested.md | 以 main.md 父目录打开正确文件，在当前工作区标签显示 |
| M-R02 | 点击 ../README.md、反斜杠路径、中文及带空格目标 | 父目录与分隔符解析正确，文件名保持真实字符 |
| M-R03 | 分别在 Android、Linux、附加仓库预览中点击相对路径、绝对路径及 file URI | 未指定来源的链接保持文档来源，不根据 /mnt 等路径形状切换环境 |
| M-R04 | 点击显式 Android、Linux、仓库协议及 environment 参数链接 | 使用指定来源，路径按该来源访问 |
| M-R05 | 点击 target.md#L12、target.md:12、target.md?line=12 及混合优先级链接 | 分别定位正确的一基行号，真实路径不包含行号后缀 |
| M-R06 | 目标已打开并修改未保存，再反复点击相同行号，继续输入 | 复用原标签和未保存内容；每次点击重新定位，输入不重复跳行 |
| M-R07 | 点击 #页脚、#L12 与裸 #，再切回预览 | 定位当前文档标题源码行、指定行或首行，预览可继续阅读 |
| M-R08 | 点击跨文件标题、重复标题后缀、Setext 标题与代码块伪标题 | 有效标题定位对应源码行；伪标题与缺失锚点给出提示 |
| M-R09 | 点击 HTTP、HTTPS 和带网页锚点的链接，再返回应用 | 系统正常打开网页，原工作区与文档内容保留 |
| M-R10 | 点击 foo://bar、未安装应用协议及无处理器的邮件或电话链接 | 提示打开失败，应用继续响应，不出现崩溃或 ANR 报告 |
| M-R11 | 点击空链接、非法百分号编码、非法来源、NUL 与无效行号 | 无效链接或位置给出提示，不发起错误的系统打开或终止进程 |
| M-R12 | 点击缺失文件、不可读文件、目录和未知二进制格式 | 显示来源对应的错误或信息；目录可浏览，信息页可返回 |
| M-R13 | 点击文本大小边界、图片、媒体、PDF 与文档链接 | 复用既有读取门槛与预览；超限文本显示信息，界面不阻塞 |
| M-R14 | 点击表格中的相对路径、网页和锚点，再测试 Issue 原主文件全部链接 | 表格与正文共用入口；可点击链接按类型处理，普通失败不闪退 |
| M-R15 | 连续点击链接、加载中返回或切换工作区，切换中英文 | 取消不显示为错误；原标签保留，错误提示使用当前语言 |

## 结果表

| 编号 | 结果 | 构建、设备与来源 | 证据或未测原因 |
| --- | --- | --- | --- |
| M-R01 |  |  |  |
| M-R02 |  |  |  |
| M-R03 |  |  |  |
| M-R04 |  |  |  |
| M-R05 |  |  |  |
| M-R06 |  |  |  |
| M-R07 |  |  |  |
| M-R08 |  |  |  |
| M-R09 |  |  |  |
| M-R10 |  |  |  |
| M-R11 |  |  |  |
| M-R12 |  |  |  |
| M-R13 |  |  |  |
| M-R14 |  |  |  |
| M-R15 |  |  |  |

## 自动回归与验证

WorkspaceMarkdownLinkTest 新增 16 个 JVM 用例，覆盖文档路径、来源、编码、行号和标题定位；WorkspaceFileLinkTest 追加两个锚点与非法编码用例。MarkdownLinkOpenerAndroidTest 新增三个设备用例，确定性模拟系统打开成功、无处理器、权限及参数异常；WorkspaceEditorInitialLineTest 增加重复定位保留输入用例。

本地 Android 构建、JVM 和设备仪器测试未执行，构建与 JVM 用例交由仓库 Android Build、Android Tests；设备用例和本轮真机结果独立记录。源码接线、资源格式、文档、样例回读与运行通过分别保存，既有签收不作为本轮新增项目的实测证据。

[DONE] 文档链接解析、应用内导航、外链保护与回归代码已接入，运行及设备结果单独记录。