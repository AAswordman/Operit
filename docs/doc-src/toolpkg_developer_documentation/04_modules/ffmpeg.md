# API 文档：`ffmpeg.d.ts`

`ffmpeg.d.ts` 为包内脚本提供全局 `Tools.FFmpeg` 命名空间和 FFmpeg 转码参数的静态类型。当前 facade 暴露 `execute`、`info`、`convert` 三个异步方法。

## 运行时入口与调用约定

`Tools.FFmpeg` 的方法由 `JsTools` 映射到以下宿主工具：

| Facade 方法 | 宿主工具 | 参数 |
| --- | --- | --- |
| `Tools.FFmpeg.execute` | `ffmpeg_execute` | `command` |
| `Tools.FFmpeg.info` | `ffmpeg_info` | 无 |
| `Tools.FFmpeg.convert` | `ffmpeg_convert` | `input_path`、`output_path` 及可选转码参数 |

三个方法都返回 `Promise`。成功时 Promise resolve 为宿主返回的数据；宿主工具失败时，运行时会 reject 一个 `Error`，错误对象的 `message` 来自宿主错误信息，`data` 可能包含宿主附带的错误数据。

`Tools.FFmpeg` 调用的是 Android 宿主内置的 FFmpegKit，不是包脚本所在环境中的命令行程序。路径必须是应用进程可以访问的文件路径。声明文件没有为这些方法标注 `@since`，因此不能仅从该声明推断 ToolPkg API 的最低版本。

## 类型别名

### `FFmpegVideoCodec`

视频编码器字面量包括：

- `h264`
- `hevc`
- `vp8`
- `vp9`
- `av1`
- `libx265`
- `libvpx`
- `libaom`
- `mpeg4`
- `mjpeg`
- `prores`

### `FFmpegAudioCodec`

音频编码器字面量包括：

- `aac`
- `mp3`
- `opus`
- `vorbis`
- `flac`
- `pcm`
- `wav`
- `ac3`
- `eac3`

### `FFmpegResolution`

支持四个声明的预设值，以及 `${number}x${number}` 形式的自定义分辨率：

- `1280x720`
- `1920x1080`
- `3840x2160`
- `7680x4320`
- `${number}x${number}`

该类型只提供 TypeScript 静态检查。宿主转换器不会再次校验这些字面量，而是将字符串直接放入 FFmpeg 参数中。

### `FFmpegBitrate`

支持以下预设和模板：

- `500k`
- `1000k`
- `2000k`
- `4000k`
- `8000k`
- `${number}k`
- `${number}M`

## 运行时 API

### `Tools.FFmpeg.execute(command)`

```ts
execute(command: string): Promise<FFmpegResultData>
```

将 `command` 原样作为 FFmpegKit 的命令参数执行。参数只应包含 FFmpeg 参数，不要写开头的 `ffmpeg`；宿主会直接调用 `FFmpegKit.execute(command)`。

成功结果至少包含：

- `command`：实际执行的命令字符串。
- `returnCode`：FFmpeg 返回码，成功通常为 `0`。
- `output`：FFmpeg 输出文本。
- `duration`：执行耗时，单位为毫秒。

```ts
const result = await Tools.FFmpeg.execute(
  '-i /sdcard/input.mp4 -vf scale=1280:720 /sdcard/output.mp4'
);
console.log(result.returnCode, result.output);
```

空命令在宿主校验阶段会失败。执行被取消、返回非成功码或抛出异常时，Promise 会 reject；当前宿主错误信息分别使用以下形式：

- `Must provide command parameter`
- `FFmpeg command was cancelled`
- `FFmpeg execution failed, return code: ...`
- `FFmpeg execution exception: ...`

### `Tools.FFmpeg.info()`

```ts
info(): Promise<FFmpegResultData>
```

不接收参数。宿主先读取 FFmpegKit 版本和构建日期，再执行 `-codecs`，并将这些信息和支持的编解码器文本写入 `output`。该调用的结果中 `command` 为 `-codecs`，`output` 的结构是可读文本，不是结构化的编解码器数组。

```ts
const result = await Tools.FFmpeg.info();
console.log(result.output);
```

当前宿主的信息执行器即使 `-codecs` 返回非零 `returnCode`，仍会构造成功的 `ToolResult`；调用方应自行检查 `returnCode`。只有信息收集过程抛出异常时，Promise 才会进入 reject 路径。

### `Tools.FFmpeg.convert(inputPath, outputPath, options?)`

```ts
convert(
  inputPath: string,
  outputPath: string,
  options?: {
    video_codec?: FFmpegVideoCodec;
    audio_codec?: FFmpegAudioCodec;
    resolution?: FFmpegResolution;
    bitrate?: FFmpegBitrate;
  }
): Promise<FFmpegResultData>
```

该方法把路径和选项转交给 `ffmpeg_convert`。选项不会提供默认编码器、分辨率或比特率；未提供的选项不会出现在生成的命令中。

宿主按以下顺序构造命令：

```text
-i "inputPath" [-c:v video_codec] [-c:a audio_codec] [-s resolution] [-b:v bitrate] "outputPath"
```

对应关系如下：

| 选项 | FFmpeg 参数 |
| --- | --- |
| `video_codec` | `-c:v` |
| `audio_codec` | `-c:a` |
| `resolution` | `-s` |
| `bitrate` | `-b:v` |

调用前置条件和副作用：

- `inputPath` 和 `outputPath` 不能为空。
- 宿主会先检查 `inputPath` 对应的文件是否存在；不存在时不会启动 FFmpeg。
- `outputPath` 的父目录和覆盖行为没有在转换器中单独校验，最终由 FFmpegKit 执行结果决定。
- 宿主只对路径做双引号包裹，不提供额外的路径转义；应传入正常的文件系统路径。
- 声明中的四个选项只提供静态类型约束，宿主不会验证编码器或分辨率是否被当前 FFmpeg 构建支持。

```ts
const result = await Tools.FFmpeg.convert(
  '/sdcard/input.mp4',
  '/sdcard/output.mp4',
  {
    video_codec: 'h264',
    audio_codec: 'aac',
    resolution: '1920x1080',
    bitrate: '4000k'
  }
);

console.log(result.outputFile);
console.log(result.mediaInfo);
```

转换失败时 Promise 会 reject。当前宿主对空路径、输入文件不存在、FFmpeg 非成功返回和异常分别使用以下错误形式：

- `Input path and output path cannot be empty`
- `Input file does not exist: ...`
- `Video conversion failed, return code: ...`
- `Video conversion exception: ...`

转换成功后，宿主会尝试用 FFprobe 读取输出文件。读取成功时，结果额外包含 `outputFile` 和 `mediaInfo`；读取媒体信息失败不影响已经成功的 FFmpeg 转换，`mediaInfo` 可以缺省。

`convert` 的实现还会读取名为 `format` 的工具参数，但当前 facade 类型和包元数据都没有公开该参数，宿主也不会将它写入命令，因此不要把它当作受支持的转换选项。

## 实际返回结构

`results.d.ts` 中的声明与当前 Kotlin DTO 并不完全一致。当前运行时实际使用的结构可概括为：

```ts
interface RuntimeFFmpegResultData {
  command: string;
  returnCode: number;
  output: string;
  duration: number;
  outputFile?: string;
  mediaInfo?: {
    format: string;
    duration: string;
    bitrate: string;
    videoStreams: RuntimeStreamInfo[];
    audioStreams: RuntimeStreamInfo[];
  };
}

interface RuntimeStreamInfo {
  index: number;
  codecType: string;
  codecName: string;
  resolution?: string;
  frameRate?: string;
  sampleRate?: string;
  channels?: number;
}
```

`mediaInfo.videoStreams` 中的视频流通常包含 `resolution` 和 `frameRate`；`mediaInfo.audioStreams` 中的音频流通常包含 `sampleRate` 和 `channels`。这些字段来自输出文件的 FFprobe 信息，部分值可能为空。

## 声明与运行时差异

需要按实际运行时处理以下差异：

- 声明把 `videoStreams` 和 `audioStreams` 放在 `FFmpegResultData` 顶层；当前 DTO 将它们放在可选的 `mediaInfo` 对象内。
- 声明中的 `FFmpegStreamInfo` 使用 `type`、`codec` 等字段；当前 DTO 使用 `codecType`、`codecName`，并额外返回 `resolution`。
- 声明中的 `FFmpegStreamInfo.codec` 使用视频/音频编码字面量联合类型；运行时 `codecName` 是普通字符串，不提供相同的 TypeScript 联合约束。
- 声明没有 `outputFile` 和 `mediaInfo` 字段；`convert` 成功时当前运行时可能返回这两个字段。
- `format` 不是当前公开的 `convert` 选项；宿主读取该参数但不使用它。

## 包内工具入口

内置 `ffmpeg` 包的元数据还注册了三个工具函数：`ffmpeg_execute`、`ffmpeg_info` 和 `ffmpeg_convert`。它们位于 `app/src/main/assets/packages/ffmpeg.js`，参数名称使用下划线形式：

- `ffmpeg_execute({ command })`
- `ffmpeg_info()`
- `ffmpeg_convert({ input_path, output_path, video_codec?, audio_codec?, resolution?, bitrate? })`

这些 wrapper 会调用上面的 `Tools.FFmpeg` facade，并把成功结果转换为：

```ts
{
  success: boolean;
  message: string;
  data?: string;
}
```

其中 `data` 只有 `result.output`，不是完整的 `FFmpegResultData`。wrapper 会捕获 facade 的异常并返回 `success: false`；因此在包工具中应检查 `success` 和 `message`，不能只依赖 `data`。

## 相关文件

- `examples/types/ffmpeg.d.ts`
- `examples/types/results.d.ts`
- `examples/types/tool-types.d.ts`
- `app/src/main/assets/packages/ffmpeg.js`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsTools.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardFFmpegTool.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/ToolResultDataClasses.kt`
