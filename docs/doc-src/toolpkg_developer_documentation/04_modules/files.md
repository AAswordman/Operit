# API 文档：`files.d.ts`

`files.d.ts` 描述 `Tools.Files` 命名空间。facade 将公开方法转换为宿主文件工具调用，覆盖 Android 本地文件、Linux 终端/SSH 文件系统、代码搜索、归档和下载。

## 作用与运行时入口

```ts
Tools.Files
```

声明中的 `FileEnvironment` 只有 `android` 和 `linux`，但当前宿主还接受 `repo:<bookmark>` 执行环境，用于访问已保存的 SAF 文档树；结果中的 `env` 可能因此是完整的 `repo:<bookmark>` 字符串。`content://` 文档 URI 也由 SAF 实现直接解析。

facade 方法返回宿主结果数据。宿主失败时，工具桥会使 Promise rejected；成功时返回 `data` 对象，不包含内部 `ToolResult` 包装层。

路径校验依赖具体环境：

- Android 默认环境要求绝对路径，以 `/` 开头。
- Linux 文件工具接受 `/` 或 `~` 开头的路径；路径由终端/SSH 文件系统提供者解释。
- `repo:<bookmark>` 将绝对逻辑路径解析到已保存的 SAF 文档树，也可以直接传 `content://` URI。这个环境是运行时扩展，不在 `FileEnvironment` 声明中。

除特别说明外，空路径会失败。返回结构中的时间和权限格式由后端实现决定，不应从 TypeScript 注释推断成固定格式。

## 基本类型

### `FileEnvironment`

```ts
type FileEnvironment = 'android' | 'linux'
```

多数方法省略 `environment` 时使用 Android 实现。

### `ApplyFileType`

```ts
type ApplyFileType = 'replace' | 'delete' | 'create'
```

用于 `apply()`。

## 目录与读取

### `list(path, environment?)`

```ts
list(path: string, environment?: FileEnvironment): Promise<DirectoryListingData>
```

列出目录直接子项，不递归。Android 和 Linux 路径必须存在且为目录；`repo:` 必须能解析为 tree-backed 文档 URI。每个 `FileEntry` 包含 `name`、`isDirectory`、`size`、`permissions` 和 `lastModified`。

Android 本地实现使用简化的 `rwx` 权限字符串和 `MMM dd HH:mm` 时间格式；Linux 使用文件系统提供者返回的权限/时间；SAF 使用猜测的权限字符串，目录大小为 `0`。目录不存在、路径不是目录、bookmark/URI 无效或查询失败都会失败。

### `read(path)` / `read(options)`

```ts
read(path: string): Promise<FileContentData>

read(options: {
  path: string
  environment?: FileEnvironment
  intent?: string
  direct_image?: boolean
}): Promise<FileContentData>
```

facade 的 `read()` 实际调用 `read_file_full`，不是宿主中另行注册的截断版 `read_file` 工具。因此普通文本读取不会使用 `MAX_FILE_READ_BYTES = 32000` 的截断限制；`read_file` 的内部注册入口不等同于这个公开 facade 方法。

文件必须存在且是普通文件，并且内容需要被识别为文本。`direct_image` 和 `intent` 主要用于 Android 特殊文件处理：

- `doc`/`docx` 和 PDF 尝试提取文本。
- 图片可以通过 `intent` 交给识图服务；`direct_image` 走图片池注册路径，但当前实现返回的图片内容 link 为空字符串，这是运行时差异。
- 音频/视频等特殊类型返回宿主生成的文本或媒体信息；具体格式不是声明文件契约。
- 非文本普通二进制文件会失败，并提示使用二进制读取。

运行时还读取一个未写入 `ReadFileOptions` 声明的 `text_only` 参数；启用后会先检查前 512 字节是否像文本，非文本文件直接失败。Android、Linux 和 `repo:` 的特殊文件处理能力并不完全相同。

成功返回：

- `path`：原始请求路径。
- `content`：文本内容。
- `size`：Android/Linux 普通文件使用文件长度或生成内容长度；SAF 优先使用 provider 的文件大小。
- `env`：实际环境标签。

### `readPart(path, startLine?, endLine?, environment?)`

```ts
readPart(
  path: string,
  startLine?: number,
  endLine?: number,
  environment?: FileEnvironment
): Promise<FilePartContentData>
```

按行读取，行号参数在 facade 中转为字符串后交给宿主解析。默认 `startLine=1`，默认结束行为是从起始行开始最多 `200` 行；起止值会被限制到文件实际行数范围。返回内容带有一基行号前缀，并受 `32000` 字节/字符级结果限制，截断时追加提示文本。

`FilePartContentData` 的行号字段有兼容性约定：`startLine` 是零基起始值，`endLine` 是包含末行的壹基值；`content` 中显示的行号仍从 1 开始。`partIndex` 固定为 `0`，`totalParts` 固定为 `1`，它们保留用于兼容旧结果格式。

### `write(path, content, append?, environment?)`

```ts
write(
  path: string,
  content: string,
  append?: boolean,
  environment?: FileEnvironment
): Promise<FileOperationData>
```

`append` 默认 `false`。Android 本地实现会尝试创建父目录；覆盖写使用 `writeText`，追加写只在目标文件已存在时追加。Linux 和 SAF 使用各自的文件系统/文档 provider。成功结果的 `operation` 为 `write` 或 `append`；失败时会将权限、I/O 或目标路径原因写入 `details` 和错误字段。

### `writeBinary(path, base64Content, environment?)`

```ts
writeBinary(
  path: string,
  base64Content: string,
  environment?: FileEnvironment
): Promise<FileOperationData>
```

使用 Android Base64 解码器写入二进制内容。Android/Linux 会尝试创建父目录；SAF 创建文档时会根据扩展名选择 MIME 类型，未知扩展名可能失败。成功结果的 `operation` 为 `write_binary`。

### `readBinary(path, environment?)`

```ts
readBinary(path: string, environment?: FileEnvironment): Promise<BinaryFileContentData>
```

读取普通文件并以无换行 Base64 返回 `contentBase64`，同时返回字节数 `size`。路径不存在、不是普通文件、URI 无法打开或读取失败都会 rejected。

## 删除、存在性、移动与复制

### `deleteFile(path, recursive?, environment?)`

```ts
deleteFile(
  path: string,
  recursive?: boolean,
  environment?: FileEnvironment
): Promise<FileOperationData>
```

`recursive` 默认 `false`。Android 本地实现删除非空目录时会失败；设置为 `true` 才递归删除。Linux 行为由文件系统 provider 决定。SAF 当前通过 provider 的 `delete` 直接删除，`recursive` 不参与额外的递归实现。

结果的 `operation` 为 `delete`，`successful` 表示实际删除结果。不存在的路径、权限问题或 provider 返回失败都会 rejected。

### `exists(path, environment?)`

```ts
exists(path: string, environment?: FileEnvironment): Promise<FileExistsData>
```

成功查询不存在的路径时，Promise 仍然 resolve，返回 `exists: false`；查询过程本身出错或路径无效时才 rejected。存在时还返回 `isDirectory` 和 `size`。SAF 目录的大小固定为 `0`。

### `move(source, destination, environment?)`

```ts
move(
  source: string,
  destination: string,
  environment?: FileEnvironment
): Promise<FileOperationData>
```

Android 本地优先使用 `renameTo`，失败时对目录执行复制后删除，对普通文件执行复制、校验长度后删除。Linux 使用 provider 的移动能力。

`repo:` 移动要求至少一个路径是 `content://` URI，内部先复制再删除；SAF 本地路径和普通 Android 路径不能按普通文件路径混用。源/目标为空或源不存在时失败。

### `copy(source, destination, recursive?, sourceEnvironment?, destEnvironment?)`

```ts
copy(
  source: string,
  destination: string,
  recursive?: boolean,
  sourceEnvironment?: FileEnvironment,
  destEnvironment?: FileEnvironment
): Promise<FileOperationData>
```

`recursive` 默认 `true`。环境选择顺序分别是 `sourceEnvironment`/`destEnvironment`，其次是兼容用的共同 `environment` 参数，最后为 `android`。facade 的公开签名没有单独的共同 `environment` 位置，但 options/底层工具仍可能接收该字段。

- 同环境 Android 复制普通文件会校验目标文件存在且长度一致；目录需要 `recursive=true`。
- Android/Linux 跨环境复制支持文件，也支持递归目录；跨环境实现以分块/缓冲方式传输。
- `repo:` 与 Linux 环境不能混用。
- SAF 到本地或本地到 SAF 的目录复制当前不支持；文件复制需要有效的 `content://` 路径或可创建的目标文档。

结果 `path` 是源路径，`operation` 为 `copy`。

### `mkdir(path, create_parents?, environment?)`

```ts
mkdir(
  path: string,
  create_parents?: boolean,
  environment?: FileEnvironment
): Promise<FileOperationData>
```

`create_parents` 默认 `false`。Android 本地使用 `mkdir()` 或 `mkdirs()`；目标目录已存在时按成功处理，目标路径已有但不是目录时失败。Linux/SAF 使用各自 provider 创建目录。

## 搜索与文件信息

### `find(path, pattern, options?, environment?)`

```ts
find(
  path: string,
  pattern: string,
  options?: {
    use_path_pattern?: boolean
    case_insensitive?: boolean
    max_depth?: number
    [key: string]: unknown
  },
  environment?: FileEnvironment
): Promise<FindFilesResultData>
```

`pattern` 是 glob，而不是正则。当前 Android/SAF 实现支持 `*`、`?`、字符组、花括号和逗号替代；`case_insensitive` 默认 `false`，`use_path_pattern` 默认 `false`，`max_depth` 默认 `-1` 表示不限制。Android 对目录递归搜索时，`use_path_pattern=true` 匹配相对根目录路径，否则匹配文件名；SAF 不把目录本身加入结果。

Linux 实现将搜索委托给 Linux provider，当前固定为不区分大小写关闭、无限深度，因而不会完整应用上述三个可选项。文件路径本身也可以作为 `path`；匹配时按最后一段文件名判断。

结果包含 `path`、原始 `pattern`、匹配路径数组 `files` 和 `env`。路径不存在、`path`/`pattern` 为空或搜索失败都会 rejected。

### `grep(path, pattern, options?)`

```ts
grep(path: string, pattern: string, options?: {
  file_pattern?: string
  case_insensitive?: boolean
  context_lines?: number
  max_results?: number
  environment?: FileEnvironment
}): Promise<GrepResultData>
```

使用正则表达式搜索文件内容。默认 `file_pattern='*'`、`case_insensitive=false`、`context_lines=3`、`max_results=100`。Android 和 Linux 使用 native ripgrep；`repo:` 环境明确不支持 `grep_code`。

当前结果包含 `searchPath`、`pattern`、`matches`、`totalMatches`、`filesSearched` 和 `env`。每个文件匹配包含 `filePath` 与行匹配；行匹配包含 `lineNumber`、`lineContent` 和可选 `matchContext`。宿主最多保留 20 个文件分组，`totalMatches` 受实际结果上限影响，不应理解为整个磁盘的精确总数。

### `grepContext(path, intent, options?)`

```ts
grepContext(path: string, intent: string, options?: {
  file_pattern?: string
  max_results?: number
  environment?: FileEnvironment
}): Promise<GrepResultData>
```

按自然语言意图搜索相关文件或代码段。默认 `file_pattern='*'`、`max_results=10`。Android/Linux 使用 GREP 功能模型执行多轮候选搜索和上下文读取；`path` 可以是目录或单个文件。`repo:` 环境当前不支持该工具。缺少路径或意图、路径校验失败或模型/搜索过程失败都会 rejected。

### `info(path, environment?)`

```ts
info(path: string, environment?: FileEnvironment): Promise<FileInfoData>
```

返回 `fileType`、`size`、`permissions`、`owner`、`group`、`lastModified` 和 `rawStatOutput`。Android 的 owner 来自进程用户属性，group 为空；Linux provider 当前 owner/group 为空；SAF 使用 URI、显示名和文档元数据拼接 `rawStatOutput`，权限是猜测值。

## 结构化应用与归档

### `apply(path, type, oldContent?, newContent?, environment?)`

```ts
apply(
  path: string,
  type: ApplyFileType,
  oldContent?: string,
  newContent?: string,
  environment?: FileEnvironment
): Promise<FileApplyResultData>
```

`type` 会 trim 后按小写解析，只接受 `replace`、`delete`、`create`：

- `create`：目标不存在时要求非空 `newContent`，通过写文件创建；目标已存在时失败，并提示使用 `deleteFile` 后再 `write`。
- `replace`：目标必须存在，并且 `oldContent` 与 `newContent` 都非空；宿主先读取原文，再调用 `FileBindingService`/功能模型应用结构化替换，最后写回合并内容。
- `delete`：目标必须存在，并且 `oldContent` 非空；按结构化删除操作处理。

整个操作会发布进度，成功结果的 `operation` 为 `apply`，并包含 `aiDiffInstructions`。Kotlin 结果类还返回可空的 `diffContent` unified diff 字段，但该字段缺少于 `results.d.ts` 声明。操作失败会返回文件操作错误；facade 最终 Promise rejected。

实现开始时统一使用 Android 绝对路径校验，因此 `content://` 和以 `~` 开头的路径会在 `apply` 入口被拒绝；即使内部读写方法支持 Linux/SAF，也不能据此推断 `apply` 的所有环境都可用。

### `create(path, newContent, environment?)`

```ts
create(path: string, newContent: string, environment?: FileEnvironment): Promise<FileApplyResultData>
```

这是 `apply(path, 'create', undefined, newContent, environment)` 的包装，返回同样的 diff 结果和错误语义。

### `edit(path, oldContent, newContent, environment?)`

```ts
edit(path: string, oldContent: string, newContent: string, environment?: FileEnvironment): Promise<FileApplyResultData>
```

这是 `apply(path, 'replace', oldContent, newContent, environment)` 的包装，不是普通的无条件覆盖写。

### `zip(source, destination, environment?, include_root_directory?)`

```ts
zip(
  source: string,
  destination: string,
  environment?: FileEnvironment,
  include_root_directory?: boolean
): Promise<FileOperationData>
```

`include_root_directory` 默认 `true`。源是目录时，默认将源目录名作为压缩包顶层目录；传 `false` 时只写入目录内容。源是单个文件时直接使用文件名作为 entry。

实现通过 `PathMapper` 处理 Linux 路径，将其映射到应用内 Ubuntu rootfs；压缩入口仍要求以 `/` 开头。当前 zip 实现没有 SAF 专用分支。源不存在、参数为空或压缩包创建失败都会 rejected。

### `unzip(source, destination, environment?)`

```ts
unzip(
  source: string,
  destination: string,
  environment?: FileEnvironment
): Promise<FileOperationData>
```

目标目录不存在时会创建。解压前会校验每个 entry 的 canonical path 必须位于目标目录下，阻止 `../` 路径逃逸；过程会发布进度。Linux 路径通过 `PathMapper` 映射，当前同样不是 SAF 专用实现。

## 打开、分享与下载

### `open(path, environment?)`

```ts
open(path: string, environment?: FileEnvironment): Promise<FileOperationData>
```

Android 使用 `FileProvider`、文件扩展名 MIME 类型和 `ACTION_VIEW` 启动系统处理器。没有匹配应用、文件不存在或启动失败时 rejected。Linux 明确返回“不支持打开文件”；当前实现没有把 `repo:` 转入 SAF 打开流程。

### `share(path, title?, environment?)`

```ts
share(path: string, title?: string, environment?: FileEnvironment): Promise<FileOperationData>
```

Android 使用 `FileProvider` 和 `ACTION_SEND` 启动分享 chooser；标题省略时默认是 `Share File`。Linux 明确不支持分享，文件不存在或没有可用系统处理器时失败。

### `download(url, destination, environment?, headers?)`

```ts
download(
  url: string,
  destination: string,
  environment?: FileEnvironment,
  headers?: Record<string, string>
): Promise<FileOperationData>

download(options: {
  url?: string
  visit_key?: string
  link_number?: number
  image_number?: number
  destination: string
  environment?: FileEnvironment
  headers?: Record<string, string>
}): Promise<FileOperationData>
```

下载来源有两种：

- 直接提供 `url`；必须以 `http://` 或 `https://` 开头。
- 提供 `visit_key` 和 `link_number` 或 `image_number`，从最近的 `visit_web` 缓存中按一基序号取链接/图片 URL。索引越界或 visit key 无效会失败。

facade 会把 headers 对象 JSON 序列化；宿主无法解析的 headers JSON 会退化为空 header 集合。目标路径不能为空，Linux 目标通过 `PathMapper` 映射并使用多线程下载器；下载完成后还会检查目标文件确实存在。`repo:` 没有独立的 SAF 下载分支。

## 返回值

主要结果数据字段如下：

| 类型 | 关键字段 | 运行时注意事项 |
| --- | --- | --- |
| `DirectoryListingData` | `env`, `path`, `entries` | `entries` 是直接子项，权限/时间格式依环境变化 |
| `FileContentData` | `env`, `path`, `content`, `size` | `read()` 使用完整读取；`readPart()`/截断读取会加入行号和截断提示 |
| `BinaryFileContentData` | `env`, `path`, `contentBase64`, `size` | Base64 无换行 |
| `FilePartContentData` | `env`, `path`, `content`, `partIndex`, `totalParts`, `startLine`, `endLine`, `totalLines` | `startLine` 零基，`endLine` 一基；当前分段字段固定为 0/1 |
| `FileExistsData` | `env`, `path`, `exists`, `isDirectory`, `size` | 不存在通常是成功结果 `exists=false` |
| `FileInfoData` | `env`, `path`, `exists`, `fileType`, `size`, `permissions`, `owner`, `group`, `lastModified`, `rawStatOutput` | owner/group/权限由环境决定 |
| `FileOperationData` | `env`, `operation`, `path`, `successful`, `details` | 文件操作成功与否同时体现在 `successful` 和宿主调用状态 |
| `FileApplyResultData` | `operation`, `aiDiffInstructions` | 运行时还可能有声明遗漏的 `diffContent` |
| `FindFilesResultData` | `env`, `path`, `pattern`, `files` | glob 搜索结果的目录包含规则随环境实现变化 |
| `GrepResultData` | `env`, `searchPath`, `pattern`, `matches`, `totalMatches`, `filesSearched` | 结果受 ripgrep/模型和宿主上限影响 |

## 示例

### 读取完整文本与部分行

```ts
const file = await Tools.Files.read({
  path: '/sdcard/notes/todo.txt',
  environment: 'android'
});
console.log(file.content);

const part = await Tools.Files.readPart('/sdcard/app.log', 1, 80);
console.log(part.content);
```

### 写入并跨环境复制

```ts
await Tools.Files.write('/sdcard/notes/todo.txt', 'first line\n');
await Tools.Files.copy(
  '/sdcard/input.txt',
  '/tmp/input.txt',
  false,
  'android',
  'linux'
);
```

### 搜索代码

```ts
const matches = await Tools.Files.grep('/workspace', 'toolCall\\(', {
  file_pattern: '*.ts',
  context_lines: 2,
  max_results: 20,
  environment: 'linux'
});
console.log(matches.matches);
```

### 应用结构化替换

```ts
const result = await Tools.Files.apply(
  '/sdcard/demo.txt',
  'replace',
  'old text',
  'new text'
);
console.log(result.operation.details);
```

### 从网页结果下载

```ts
await Tools.Files.download({
  visit_key: visit.visitKey,
  link_number: 1,
  destination: '/sdcard/downloads/item.bin'
});
```

## 相关源码与声明

- `examples/types/files.d.ts`
- `examples/types/results.d.ts`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsTools.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/ToolRegistration.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardFileSystemTools.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/LinuxFileSystemTools.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/SafFileSystemTools.kt`
