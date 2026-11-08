# API 文档：`cryptojs.d.ts`

`cryptojs.d.ts` 描述全局 `CryptoJS` 对象的轻量 native bridge。它只提供应用当前实现的 MD5、AES 解密和少量兼容占位对象，不是完整的上游 CryptoJS。

## 运行时入口

```ts
CryptoJS
```

`CryptoJS` 由 QuickJS 启动模块注入为全局对象，不需要 `import`。`NativeInterface.crypto()` 是同步调用，因此 `MD5()`、`AES.decrypt()` 和 `WordArray.toString()` 都同步返回。

实现位于：

- `app/src/main/assets/js/CryptoJS.js`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsNativeInterfaceDelegates.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsEngine.kt`

运行时最终导出的对象只有 `MD5`、`AES`、`enc`、`pad` 和 `mode`。声明中的 `CryptoJS.WordArray` 是类型接口，不会作为 `CryptoJS.WordArray` 构造器挂到运行时对象上。

## `WordArray`

```ts
interface WordArray {
  toString(encoding?: any): string
}
```

native bridge 返回的实际对象是普通 JavaScript 对象：

```ts
{
  data: string,
  toString(encoding?: any): string
}
```

`toString()` 直接返回 `data`，不会根据 `encoding` 做转换；`CryptoJS.enc.Utf8` 只是兼容调用所需的 marker。因此下面两种写法在当前实现中得到相同字符串：

```ts
const result = CryptoJS.MD5('hello');
result.toString();
result.toString(CryptoJS.enc.Utf8);
```

`data` 字段没有出现在 `.d.ts` 接口中，但运行时存在。native 错误在某些路径下也可能让 `data` 成为错误对象，不能把错误路径当作正常字符串结果使用。

## `CryptoJS.MD5(message)`

```ts
MD5(message: string): WordArray
```

native 使用 Android `MessageDigest` 对 `message` 的 UTF-8 字节计算 MD5，并返回小写十六进制字符串。正常结果为 32 个十六进制字符：

```ts
const digest = CryptoJS.MD5('assistance').toString();
```

JS wrapper 不自行校验参数；公开声明要求传字符串。native 参数解码按字符串数组读取，传入其他值可能在 native bridge 中产生 `nativeError` 结果。

## `CryptoJS.AES.decrypt(ciphertext, key, cfg?)`

```ts
CryptoJS.AES.decrypt(
  ciphertext: string,
  key: any,
  cfg?: any
): WordArray
```

### 参数归一

JS wrapper 对 key 的处理规则是：

- 当 `key` 是对象且有 truthy `data` 属性时，使用 `key.data`。
- 其他情况使用 `String(key)`。
- `cfg` 不会转发给 native，也不会改变算法、模式或 padding。

因此 `CryptoJS.enc.Hex.parse()` 返回的 wrapper 会被取出其原始 `data` 字符串；当前实现不会把十六进制文本解码为对应字节。

### native 算法

native 固定执行以下流程：

1. 把 key 字符串按 UTF-8 转为 AES key bytes。
2. 使用 `AES/ECB/NoPadding` 初始化解密 cipher。
3. 把 ciphertext 按 Base64 解码。
4. 解密后按 PKCS7 规则读取最后一个字节并移除 padding。
5. 按 UTF-8 把剩余字节转换为字符串。

因此 key 的 UTF-8 字节长度必须满足 Android AES 支持的有效长度；传入 32 个十六进制字符会按 32 个 ASCII 字节处理，而不是按 16 个解码后的字节处理。ciphertext 必须是可解码的 Base64，且密文长度和 padding 必须符合该固定算法流程。

### 错误语义

native 失败时返回形如 `{"nativeError":"..."}` 的 JSON 字符串。JS wrapper 将其解析为错误对象、写入 `console.error`，然后返回 `data` 为空字符串的 `WordArray`；它不会把 native 解密异常重新抛出。

```ts
const result = CryptoJS.AES.decrypt(ciphertext, key, {
  mode: CryptoJS.mode.ECB,
  padding: CryptoJS.pad.Pkcs7
});
const plaintext = result.toString(CryptoJS.enc.Utf8);
```

示例中的 `mode` 和 `padding` 只用于匹配声明形状；当前 native 实现始终使用上述固定算法，不读取这些配置。

## 编码对象

### `CryptoJS.enc.Hex.parse(hexStr)`

```ts
CryptoJS.enc.Hex.parse(hexStr: string): WordArray
```

返回一个 `WordArray` 形状的普通对象，其 `data` 就是原始 `hexStr`。当前实现不验证字符是否为十六进制，也不把两个字符转换为一个字节；它主要用于把 key 字符串包装成 AES wrapper 能读取的对象。

### `CryptoJS.enc.Utf8`

```ts
CryptoJS.enc.Utf8
```

空 marker 对象，没有编码方法。`WordArray.toString(encoding)` 会忽略传入的对象。

## Padding 和 mode marker

```ts
CryptoJS.pad.Pkcs7
CryptoJS.mode.ECB
```

两者都是空对象 marker，没有独立实现。它们可以放入 AES 配置对象以兼容调用代码，但 `AES.decrypt()` 当前忽略 `cfg`，native 侧的 ECB 和 PKCS7 行为是固定的。

## 示例

### 计算 MD5

```ts
const digest = CryptoJS.MD5('hello').toString();
complete({ digest });
```

### 包装 key 并解密

```ts
const key = CryptoJS.enc.Hex.parse(rawKey);
const decrypted = CryptoJS.AES.decrypt(ciphertextBase64, key, {
  mode: CryptoJS.mode.ECB,
  padding: CryptoJS.pad.Pkcs7
});
const plaintext = decrypted.toString(CryptoJS.enc.Utf8);
```

这里的 `rawKey` 必须是 native 最终使用的有效 AES key 字符串；`Hex.parse()` 不会把它从十六进制文本转换成字节。

## 声明与运行时差异

- `CryptoJS.WordArray` 只作为类型接口存在，运行时不导出 `CryptoJS.WordArray` 构造器。
- runtime `WordArray` 有声明未列出的 `data` 字段，`toString(encoding)` 忽略 encoding 并直接返回 data。
- `enc.Hex.parse()` 保留原始字符串，不执行十六进制解析或校验。
- `enc.Utf8`、`pad.Pkcs7` 和 `mode.ECB` 是 marker 对象，不提供独立方法。
- `AES.decrypt()` 忽略 `cfg`；native 固定使用 AES/ECB/NoPadding，再手动移除 PKCS7。
- AES key 按 UTF-8 原始字节处理，不按 key 名称 `keyHex` 所暗示的方式解码十六进制。
- AES native 错误最终转换为空 `WordArray`，通常不会以 rejected 或 throw 的形式暴露给脚本。
- 当前 bridge 没有声明或实现 AES encrypt、SHA、随机数、完整编码器或其他上游 CryptoJS API。

## 相关声明与源码

- `examples/types/cryptojs.d.ts`
- `app/src/main/assets/js/CryptoJS.js`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsNativeInterfaceDelegates.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsEngine.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsLibraries.kt`
