# API 文档：`jimp.d.ts`

`jimp.d.ts` 描述全局 `Jimp` 对象提供的轻量图片处理桥接层。当前实现不是完整的 Jimp 库，而是通过 `NativeInterface.image_processing()` 把少量图片操作交给 Android `Bitmap` 执行。

## 运行时入口

```ts
Jimp
```

`Jimp` 由 QuickJS 启动时作为全局对象注入，不需要 `import`。所有图片操作都返回 Promise；native bridge 在后台线程执行，处理失败时 Promise rejected。

实现位于：

- `app/src/main/assets/js/Jimp.js`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsNativeInterfaceDelegates.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsEngine.kt`

## `JimpWrapper`

```ts
class JimpWrapper {
  readonly id: string
  crop(x: number, y: number, w: number, h: number): Promise<JimpWrapper>
  composite(src: JimpWrapper, x: number, y: number): Promise<this>
  getWidth(): Promise<number>
  getHeight(): Promise<number>
  getBase64(mime: string): Promise<string>
  release(): Promise<void>
}
```

wrapper 保存 native bitmap registry 中的 UUID，并通过 `id` 找回 Android `Bitmap`。`read()`、`create()` 和 `crop()` 会返回 wrapper。

声明中的 `Jimp.JimpWrapper` 是类型形状，但当前 `Jimp.js` 没有把 `JimpWrapper` 挂到最终返回的 `Jimp` 对象上；运行时应使用 `Jimp.read()` 或 `Jimp.create()` 获取实例，不应调用 `new Jimp.JimpWrapper()` 或依赖该构造器存在。

声明把 `id` 标为只读字符串；实现的 `release()` 会把实例的 `id` 设置为 `undefined`，因此释放后的对象不能再当作有效图片使用。

### `crop(x, y, w, h)`

```ts
crop(x: number, y: number, w: number, h: number): Promise<JimpWrapper>
```

根据当前图片创建裁剪结果，并返回新的 wrapper。原图片保持不变，native registry 会同时保留原 bitmap 和新 bitmap。

native 实现通过 `JsonPrimitive.int` 读取 `x`、`y`、`w`、`h`，因此实际应传整数。源图片不存在、区域越界、宽高无效或 Android Bitmap 裁剪失败时调用 rejected。

### `composite(src, x, y)`

```ts
composite(src: JimpWrapper, x: number, y: number): Promise<this>
```

把 `src` 绘制到当前图片上，并原地修改当前图片。方法成功后返回当前 wrapper，也就是 `this`；源图片不会被修改。

JS wrapper 要求 `src` 是同一运行时创建的 `JimpWrapper` 实例，否则同步抛出 `Source image must be a Jimp object.`。native 实现要求两个 bitmap ID 都仍存在，并以整数坐标调用 Android `Canvas.drawBitmap()`；图片不存在或绘制失败时 Promise rejected。

### `getWidth()` / `getHeight()`

```ts
getWidth(): Promise<number>
getHeight(): Promise<number>
```

读取 native bitmap 的宽度或高度。图片已经释放、ID 无效或 registry 中不存在时调用 rejected。

### `getBase64(mime)`

```ts
getBase64(mime: string): Promise<string>
```

将当前图片压缩为 Base64 字符串。返回值是不带 `data:` 前缀、也不带换行的原始 Base64。

当前 native 编码规则是：

- `mime === 'image/png'` 时使用 PNG。
- 其他非空 mime 都回退到 JPEG。
- JS wrapper 在 `mime` 为假值时默认使用 `Jimp.MIME_JPEG`。
- JPEG 压缩质量固定为 90。

因此声明中的任意 `string` 并不对应任意输出格式；需要 PNG 时应传 `Jimp.MIME_PNG`。

### `release()`

```ts
release(): Promise<void>
```

从 native bitmap registry 删除当前 ID，并调用 Android `Bitmap.recycle()`。成功后 wrapper 的 `id` 被设为 `undefined`。重复调用已释放对象时，JS wrapper 不再发起 native 调用并直接完成；对已失效但仍有旧 ID 的对象，native 删除不到条目时也不会报告“未找到”错误。

应在不再使用图片时显式释放原图、裁剪结果和合成使用的源图，避免 bitmap registry 和 Android bitmap 资源长期占用。

## `Jimp.read(base64)`

```ts
read(base64: string): Promise<JimpWrapper>
```

解码 Base64 图片数据并返回 wrapper。native 使用 Android `BitmapFactory.decodeByteArray()`，支持范围由 Android 图片解码器决定；Base64 无法解码、数据损坏或格式不支持时调用 rejected。

实现还识别宿主内部的二进制句柄格式 `@binary_handle:<handle>`：它会从当前 JS 执行上下文的二进制 registry 一次性取出对应字节并删除句柄。该输入形式未写入 `.d.ts`，普通调用应传图片 Base64 字符串。

## `Jimp.create(w, h)`

```ts
create(w: number, h: number): Promise<JimpWrapper>
```

使用 `Bitmap.Config.ARGB_8888` 创建指定尺寸的透明图片，并返回 wrapper。native 通过整数 JSON 值读取宽高；零、负数、非整数或超出 Android Bitmap 限制时会失败。

## MIME 常量

```ts
Jimp.MIME_JPEG // 'image/jpeg'
Jimp.MIME_PNG  // 'image/png'
```

两个常量是运行时字符串，不是可扩展的格式 registry。除 `image/png` 以外的 `getBase64()` mime 值都会按 JPEG 处理。

## 示例

### 读取、裁剪、导出并释放

```ts
const image = await Jimp.read(base64Image);
const cropped = await image.crop(0, 0, 200, 200);
const outputBase64 = await cropped.getBase64(Jimp.MIME_PNG);

await cropped.release();
await image.release();
```

### 创建画布并合成

```ts
const canvas = await Jimp.create(800, 600);
const icon = await Jimp.read(iconBase64);

await canvas.composite(icon, 24, 24);
const mergedBase64 = await canvas.getBase64(Jimp.MIME_JPEG);

await icon.release();
await canvas.release();
```

## 声明与运行时差异

- `Jimp.JimpWrapper` 在声明中是 namespace 内的 class，但当前运行时没有导出 `Jimp.JimpWrapper` 构造器。
- 声明中的 `id: string` 在 `release()` 后实际变为 `undefined`。
- `number` 参数在 native 侧通过整数解析；裁剪、创建和合成坐标应使用整数。
- `getBase64()` 只把精确的 `image/png` 映射为 PNG，其余 mime 回退到 JPEG，质量固定为 90。
- `read()` 额外支持宿主内部 `@binary_handle:` 输入，但该格式不属于公开声明契约。
- `composite()` 修改当前 wrapper 并返回同一个实例；`crop()` 创建独立 bitmap，不会自动释放源图。

## 相关声明与源码

- `examples/types/jimp.d.ts`
- `app/src/main/assets/js/Jimp.js`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsNativeInterfaceDelegates.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsEngine.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsLibraries.kt`
