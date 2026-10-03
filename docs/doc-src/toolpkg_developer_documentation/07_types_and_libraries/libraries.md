---
title: 内置库与压缩接口
status: draft
---

# 内置库与压缩接口

ToolPkg 运行时按需向 QuickJS global context 注入少量全局库；这不等同于完整 npm 包。只依赖仓库声明和本页列出的子集。

## Pako Bridge

运行时全局为 `pako`，当前唯一成员为 `inflate(data, options)`：

```ts
interface InflateOptions {
  to?: "string";
}

pako.inflate(data: string | Uint8Array, options?: InflateOptions): string;
```

当前实现比声明更严格：

- 必须传入 `options.to === "string"`；虽然声明允许省略 `options`，运行时省略会抛错。
- `data` 必须是字符串；运行时拒绝 `Uint8Array`，即使声明将其列入输入联合类型。
- 字符串表示 Base64 压缩数据或宿主生成的 opaque binary handle。binary handle 由 `Tools.Files.readBinary()` 等路径产生时，应保持原样传递。
- native 端只接受 `deflate` 算法，使用 raw DEFLATE 流解压，再按 UTF-8 解码为字符串。它不是任意 ZLIB/gzip 解码器，也不支持返回 byte array。
- 空压缩输入返回空字符串。非法 Base64、过期 binary handle、不完整/损坏的 DEFLATE 数据会由 native 端返回错误，JS 包装层再抛出 `Error`。
- 当前 JS 包装层解析 native error 的 `try/catch` 也会捕获自己构造的错误，因此错误文案可能落到“could not parse error message”；错误原因文本不应作为稳定协议解析。
- 调用是同步 native bridge，不提供进度回调、取消或超时参数。

```js
const text = pako.inflate(base64Deflate, { to: "string" });
complete({ text });
```

### 声明差异

`examples/types/pako.d.ts` 的注释称 ZLIB 数据，签名接受 `Uint8Array` 且把 options 标成可选；当前 runtime 实际只接受带 `to: "string"` 的字符串，并按 raw DEFLATE 处理。以上运行时行为优先于声明，差异已列入[覆盖索引](../09_compatibility/coverage.md)。

## 其他内置库

- [CryptoJS](../04_modules/cryptojs.md)：当前桥接子集包括 MD5、AES 解密和有限编码/模式对象；不代表完整版 CryptoJS。
- [Jimp](../04_modules/jimp.md)：当前包装器提供 Base64 读取、创建、裁剪、合成、导出和资源释放。
- [FFmpeg](../04_modules/ffmpeg.md)：通过 `Tools.FFmpeg` 工具命名空间调用，不是普通 JS 全局库。
- `_` 与 `dataUtils`：轻量辅助对象，具体函数和失败回退见[全局运行时 API](../03_runtime/global_api.md)。

## 运行时来源

- `app/src/main/assets/js/pako.js`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsEmbeddedLibraryLoader.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsNativeInterfaceDelegates.kt`
- `examples/types/pako.d.ts`