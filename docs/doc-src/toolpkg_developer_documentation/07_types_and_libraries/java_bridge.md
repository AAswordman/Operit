---
title: Java 与 Kotlin Bridge
status: complete
---

# Java 与 Kotlin Bridge

全局 `Java` 提供 QuickJS 到 Android JVM 的动态反射代理。它不是 JavaScript 的 `import`，也不自动把 JVM API 类型加入 TypeScript；包代码应只引用目标 Operit 运行时中存在且可访问的类。`Java` 与 `Kotlin` 的全局声明见 `examples/types/index.d.ts`。

当前 `java-bridge.d.ts` 没有 ToolPkg API `@since` 标记；以下接口按当前基线记录。运行时同时把同一个 bridge 暴露为全局 `Java` 和 `Kotlin`，但声明文件只定义 `JavaBridgeApi`。外部 DEX/JAR 加载和类代理实现分别位于 `JsExternalJavaCodeLoader`、`JsJavaBridge` 与 `JsJavaBridgeDelegates`。

## 类与包解析

| API | 签名 | 行为 |
| --- | --- | --- |
| `Java.type` | `(className: string): JavaBridgeClass` | 去除首尾空白；空类名抛错。返回动态类代理，不保证此时类已存在；访问成员或构造时才可能报 `class not found`。 |
| `Java.use` | `(className: string): JavaBridgeClass` | `Java.type` 的别名。 |
| `Java.importClass` | `(className: string): JavaBridgeClass` | `Java.type` 的别名；不会把类写入当前 JS 词法作用域。 |
| `Java.package` | `(packageName: string): JavaBridgePackage` | 要求非空包名，返回可继续点访问的动态包代理。 |
| `Java.classExists` | `(className: string): boolean` | 空名称或类加载失败返回 `false`；此检查不会抛出类不存在异常。 |

也可以使用 `Java.java.lang.StringBuilder` 这样的属性链。代理在每一级尝试解析类；未命中时继续作为子包。包代理不可作为空路径构造或调用。

## 创建对象与调用

### `Java.newInstance(className, ...args)`

```ts
newInstance<T extends JavaBridgeInstance = JavaBridgeInstance>(
  className: string,
  ...args: JavaBridgeArg[]
): T
```

通过反射选择可匹配的 public 构造函数并创建 JVM 对象；重载选择按参数可转换性和匹配分数进行。无匹配构造函数、参数不能转换、类无法解析或构造器本身抛错时，bridge envelope 为 `{ success: false, message }`，JS wrapper 抛出 `Error`。返回的是带 native handle 的动态代理，不是复制到 JS 的普通对象。

如果目标类是 interface，且只传入一个普通函数或对象，运行时在检测到 interface constructor 错误后会把该调用兼容转换为 `Java.implement(className, impl)`；显式调用 `Java.implement` 更可靠。抽象类没有 public constructor 时不会自动生成实现。

### `Java.callStatic(className, methodName, ...args)`

同步调用静态方法，返回经 bridge 转换的值。`className`、`methodName` 会转成字符串；方法名为空时原生端拒绝调用。重载根据参数类型进行匹配。Kotlin companion 方法有额外回退逻辑，因此 Java 风格静态调用也可能解析到 companion 实例方法。

### `Java.callSuspend(className, methodName, ...args)`

```ts
callSuspend(className: string, methodName: string, ...args: JavaBridgeArg[]): Promise<JavaBridgeValue>
```

用于 JVM suspend 方法。JS 端分配 callback ID，native 完成后 resolve 值或以 `Error` reject；此包装没有独立 timeout、AbortSignal 或取消句柄。不要把普通同步 Java 方法传给该入口。

### `Java.getApplicationContext()` / `Java.getContext()`

两者都返回宿主 Application Context 代理；`getContext()` 直接委托给 `getApplicationContext()`。

### `Java.getCurrentActivity()` / `Java.getActivity()`

取得当前 Activity 的 Java 代理；`getActivity()` 是别名。当前没有 Activity 时通过失败 envelope 抛错，不能把 Activity 引用跨页面生命周期保存为稳定引用。

`suspend` bridge 通过 callback ID 在 native 侧异步完成；没有独立 timeout、AbortSignal 或取消句柄。动态实例对 `java.lang.Thread` 的 `join()` 会在等待期间轮询并处理 pending JS callbacks；对 `java.util.concurrent.FutureTask` 的 `get()` 会轮询完成状态，并支持按 TimeUnit 字符串换算 timeout。

## 动态代理对象

### `JavaBridgeInstance`

`Java.type("...").newInstance(...)`、`new Java.type("...")(...)` 或类代理的 `new` 调用都会得到实例代理。

| 成员 | 行为 |
| --- | --- |
| `className`、`handle` | 只读类名和 native 对象句柄；句柄是不透明标识。 |
| `call(methodName, ...args)` | 明确调用实例方法，适合属性名冲突或调试。 |
| `callSuspend(methodName, ...args)` | 异步调用实例 suspend 方法，返回 Promise。 |
| `get()` | 通过实例的 `get` 方法读取值。 |
| `get(fieldName)` | 反射读取实例字段/property。 |
| `set(value)` | 通过实例的 `set` 方法写入值。 |
| `set(fieldName, value)` | 反射写入实例字段/property。 |
| `toJSON()` | 序列化为 `{ __javaHandle, __javaClass }` 句柄标记；不序列化实例内部字段。 |
| `toString()` | 返回代理的字符串表示。 |

未知属性读取优先探测实例方法，再回退到实例 field/property；如果探测失败，最后仍生成一个方法 callable，因此拼写错误通常会在实际调用时才失败。未知属性赋值执行 field/property 写入；只读 final field 或不存在 setter 会失败。实例 proxy 被 QuickJS GC 后，native 使用 phantom/finalization 跟踪释放句柄；`handle` 过期后再次调用会返回 `instance handle not found or expired`。

通常使用 `obj.methodName(...)`；只有存在歧义时才调用 `obj.call("methodName", ...)`。`get()` 无参数实际调用 JVM 的 `get` 方法，`get(fieldName)` 才走 field/property 读取；同理，单参数 `set(value)` 调用 JVM 的 `set` 方法，双参数 `set(fieldName, value)` 执行 field/property 写入。

### `JavaBridgeClass`

类代理支持直接函数调用、`new` 和 `newInstance(...)` 三种构造方式。`exists()` 查询类是否可解析；`callStatic()`、`callSuspend()`、`getStatic()`、`setStatic()` 分别执行静态方法、suspend 静态方法、静态字段读取和写入。未知属性按静态 field/property、嵌套类、静态方法 callable 的顺序解析；未知属性写入会设置静态字段。静态方法/字段找不到时，运行时还会尝试 Kotlin `Companion` 实例和 `$Companion` 类。

`JavaPackage` 的属性访问先用 `classExists` 判定是否为类，否则继续构造子包；空包路径不能调用或构造，最终把不存在的类当作实例化错误。根 `Java` Proxy 也会把未知属性解析为类或包，因此 `Java.java.lang.StringBuilder` 与 `Java.package("java.lang").StringBuilder` 都是动态路径。

### `JavaBridgePackage`

包代理支持 `.path`、`toString()`、继续读取子包/类成员以及直接构造已解析的类。属性访问区分不了编译期包与类，最终以运行时类加载结果为准。

## Java 接口回调

```ts
Java.implement(interfaceName, implementation);
Java.implement(interfaceNames, implementation);
Java.implement(implementation);
Java.proxy(interfaceName, implementation);
```

`implementation` 必须是函数或对象。函数用于单方法/SAM 接口；对象按接口方法名提供回调。接口引用可用全限定类名字符串或 `Java.type(...)` 的类代理。`Java.proxy` 是 `Java.implement` 的别名。返回值是 bridge marker，供后续 Java 构造器或方法参数接收，不是可直接调用的 Java 实例。

回调在 QuickJS runtime 线程执行。调用 Java 方法时，marker 会延迟注册 JS object ID；回调参数和返回值通过 bridge value 转换，JS 对象 ID 与 native proxy 有生命周期跟踪，代理被回收时会释放相应注册。对象 implementation 按 method name 查找函数；函数 implementation 作为 callable target。void/Unit 方法的 JS callback 失败会记录日志并返回 null，非 void 方法的 callback 失败会让 Java 调用失败。不要在接口回调中假设自己处于 Android UI 线程。

## 值传递与异常

bridge 支持 string、number、boolean、null、数组、普通记录、Java 句柄和接口 marker。native 返回的 primitive、Enum、Class、Map、Iterable 和数组会转成 JSON 值；其它 Java 对象会注册到 object registry，并返回 `{ __javaHandle, __javaClass }`。通过句柄传回 native 时会重新查找 registry；句柄不存在或已被 GC 释放时失败。

`java-bridge.d.ts` 把 `bigint` 列入 `JavaBridgePrimitive`，但当前 JS bridge 最终使用 `JSON.stringify(normalizeArgs(...))`，BigInt 会导致 JSON 序列化异常，因此不能把 bigint 当作已实现的可传递值。`undefined` 也会按 JSON 序列化规则被省略或变成 null，不应依赖其保留身份。

同步 bridge 要求 native 返回 JSON `{ "success": true, "data": ... }`；返回 JSON 无效、缺少 `success`、`success` 不为 `true` 或只包含 `error` 而没有 `message` 时，JS wrapper 抛出 JS `Error`，错误文本来自 `message`。suspend 调用由 callback 的 error/value 参数分别 reject/resolve；bridge 本身不承诺调用耗时上限，也没有取消句柄。

## 加载外部 DEX/JAR

```ts
interface JavaBridgeExternalCodeLoadOptions {
  nativeLibraryDir?: string;
  childFirstPrefixes?: string[];
}

Java.loadDex(path, options?): JavaBridgeLoadedCodePath;
Java.loadJar(path, options?): JavaBridgeLoadedCodePath;
Java.listLoadedCodePaths(): JavaBridgeLoadedCodePath[];
```

- `path` 必须是可读文件；`loadDex` 只接受 `.dex`，`loadJar` 只接受 `.jar` 且归档中必须有 `classes.dex`，普通 JVM bytecode JAR 不支持。
- `options` 可省略、传 `JavaBridgeExternalCodeLoadOptions`，或传字符串。字符串兼容形式表示 `nativeLibraryDir`。
- `nativeLibraryDir` 必须指向存在的目录。`childFirstPrefixes` 会去空白、移除空项并去重；匹配此前缀的类优先从该 DEX/JAR 加载，未命中时回退父加载器。
- 加载源会复制到应用 code cache 的 `js-external-code-sources` 只读目录；返回记录中的 `path` 是这份 prepared copy 的绝对路径，不是调用方传入路径。相同 source type、canonical input path、native library dir 和 child-first prefixes 再次调用时复用已注册项，返回 `alreadyLoaded: true`。
- 返回记录包含 `index`、`type`、`path`、`nativeLibraryDir`、`childFirstPrefixes` 和 `alreadyLoaded`。`listLoadedCodePaths()` 返回当前 loader 链快照，列表中的项都标为 `alreadyLoaded: true`；后加载的 loader 成为 effective parent chain 顶层。
- 路径、扩展名、归档内容、native 库目录或 class loader 初始化失败时，native 返回失败 envelope，JS `invokeBridge` 同步抛错；此 API 不返回 Promise。

加载入口不会验证 DEX/JAR 中每个类的可用性；实际解析仍受 Android class loader、依赖项和 ABI/native 库条件影响。

## 示例

```js
const StringBuilder = Java.type("java.lang.StringBuilder");
const builder = StringBuilder.newInstance();
builder.append("ToolPkg");
const text = builder.toString();

const Runnable = Java.implement("java.lang.Runnable", {
  run() {
    console.log("callback");
  }
});

complete({ text, hasRunnable: Runnable !== null });
```

## 声明与实现差异

- `JavaBridgePrimitive` 声明包含 `bigint`，但当前 JSON bridge 不能序列化 BigInt。
- `JavaBridgeInstance` 的 `[member: string]: any` 与 `JavaBridgeClass`/`JavaBridgePackage` 的动态 index signature 只是 TypeScript 放宽；运行时未知成员仍按反射、field/property、嵌套类或 fallback method 顺序解析。
- `JavaBridgeInstance.get(fieldName?)` 和 `set(fieldName?, value?)` 的单参数重载对应 JVM `get`/`set` 方法，不是无条件的字段访问；字段访问应传 field name 形式。
- `Java.implement`/`proxy` 的 marker 在创建时可能还没有 native object ID，第一次作为 Java 参数发送时才注册实现对象。
- native failure 字段使用 `message`；部分 bridge envelope/历史注释使用 `error` 的地方不能据此得到错误文本。
- `Java.loadDex`/`loadJar` 返回的 `path` 指向只读 prepared copy；它不是输入文件路径，也不代表其中每个类都已经加载成功。

## 相关源码

- 声明：`examples/types/java-bridge.d.ts`
- JS facade：`app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsJavaBridge.kt`
- 类型转换、反射与接口代理：`app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsJavaBridgeDelegates.kt`
- JavaScript bridge 注册：`app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsEngine.kt`
- DEX/JAR loader：`app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsExternalJavaCodeLoader.kt`