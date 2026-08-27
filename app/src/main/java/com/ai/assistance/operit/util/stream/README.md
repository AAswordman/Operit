# Stream 库 - 综合应用指南

`Stream` 是一个受 Kotlin Flow 启发的轻量级异步数据流处理库。它旨在提供一套简洁、强大且易于使用的API，用于创建、转换和消费异步数据序列。无论您是处理用户输入、网络响应还是复杂的数据管道，`Stream` 都能提供优雅的解决方案。

本文档将作为一份全面的指南，帮助您从入门到精通 `Stream` 库的各项功能。

## 核心概念

-   **`Stream<T>`**: 这是库的核心，代表一个"冷"的异步数据序列。这意味着只有当存在收集器（Collector）时，`Stream` 才会开始执行其代码并发射元素。
-   **`SharedStream<T>`**: 一种"热"流，类似于 `SharedFlow`。它可以在多个收集器之间共享数据。即使没有收集器，它也可以保持活动状态。
-   **`StateStream<T>`**: 一种特殊的"热"流，类似于 `StateFlow`。它总是拥有一个当前值，并且新的收集器会立即收到最新的值。

---

## 快速上手

### 1. 创建 Stream

`Stream` 库提供了多种灵活的方式来创建数据流。

**从现有数据创建：**

```kotlin
// 从单个值创建
val singleValueStream = streamOf("Hello, Stream!")

// 从多个值创建
val multiValueStream = streamOf(1, 2, 3, 4, 5)

// 从集合创建
val list = listOf("A", "B", "C")
val streamFromList = list.asStream()

// 从序列创建
val streamFromSequence = generateSequence(0) { it + 1 }.asStream()
```

**使用构建器创建：**

`stream { ... }` 构建器是最通用和强大的创建方式，您可以在其中定义异步操作。

```kotlin
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

// 创建一个每秒发射一次递增数字的 Stream
val counterStream = stream<Int> {
    var count = 0
    while (true) {
        emit(count++)
        delay(1.seconds)
    }
}
```

**使用预设构建器：**

```kotlin
// 创建一个发射指定范围整数的 Stream
val rangeStream = rangeStream(start = 1, count = 5) // 会发射 1, 2, 3, 4, 5

// 创建一个按指定时间间隔发射的 Stream
val intervalStream = intervalStream(period = 2.seconds) // 每2秒发射 0, 1, 2, ...
```

### 2. 消费 Stream

一旦创建了 `Stream`，您就可以使用 `collect` 来消费它发射的元素。

```kotlin
suspend fun main() {
    rangeStream(1, 3)
        .collect { number ->
            println("Received: $number")
        }
    // 输出:
    // Received: 1
    // Received: 2
    // Received: 3
}
```

在协程作用域中，您可以使用 `launchIn` 来启动一个 `Stream` 的收集，而无需手动管理 `Job`。

```kotlin
// 在 ViewModel 的 CoroutineScope 中启动
counterStream.launchIn(viewModelScope) { count ->
    // 更新 UI
    _uiState.value = "Current count: $count"
}
```

### 3. 字符串到字符流的转换

Stream 库提供了将字符串转换为字符流的便捷扩展函数：

```kotlin
// 将字符串转换为字符流
val text = "Hello, Stream!"
val charStream = text.stream()

// 收集字符流
charStream.collect { c ->
    print(c)  // 逐字符输出: Hello, Stream!
}
```

这个转换非常适合处理需要逐字符分析的文本，尤其在实现 Markdown 解析器、模式匹配或文本处理工作流时非常有用。

```kotlin
// 字符流转换的实现方式
fun String.stream(): Stream<Char> {
    return stream {
        for (c in this@stream) {
            emit(c)
        }
    }
}
```

> **注意**：在 MarkdownProcessor 相关的代码中，可能会看到 `toCharStream()` 方法，它与 `stream()` 方法功能相同，只是命名不同。

---

## 操作符 (Operators)

操作符是 `Stream` 的核心功能，它们可以对数据流进行各种方式的转换和组合。

### 流控制操作

- `lock()`: 锁定流，暂停接收新数据，但发送方仍然可以继续发送（数据会被缓存）
- `unlock()`: 解锁流，恢复数据接收，并按顺序发送锁定期间缓存的所有数据
- `clearBuffer()`: 清空锁定期间缓存的数据
- `isLocked`: 检查流是否处于锁定状态
- `bufferedCount`: 查看当前缓存的元素数量

```kotlin
// 使用锁定功能控制数据流
val stream = intervalStream(100.milliseconds)

// 在某个时刻锁定流，后续数据将被缓存
stream.lock()

// 检查锁定状态和缓冲区数据
println("流已锁定: ${stream.isLocked}")
println("缓冲区项数: ${stream.bufferedCount}")

// 在需要时解锁流，之前缓存的数据会按顺序发送
stream.unlock()

// 可以选择在解锁前清空缓冲区数据
stream.clearBuffer()
```

### 转换操作

-   `map { ... }`: 将每个元素转换为一个新的元素。
-   `flatMap { ... }`: 将每个元素转换为一个新的 `Stream`，然后将所有这些新的 `Stream` 合并为一个。

```kotlin
streamOf(1, 2, 3)
    .map { "Item #$it" } // Stream<String>
    .collect { println(it) } // 输出: Item #1, Item #2, Item #3

streamOf("A", "B")
    .flatMap { letter ->
        streamOf("${letter}1", "${letter}2")
    }
    .collect { println(it) } // 输出: A1, A2, B1, B2
```

### 过滤操作

-   `filter { ... }`: 只保留满足条件的元素。
-   `take(n)`: 只取前 `n` 个元素。
-   `drop(n)`: 丢弃前 `n` 个元素。
-   `distinctUntilChanged()`: 移除连续的重复元素。

```kotlin
streamOf(1, 2, 2, 3, 3, 3, 4, 3)
    .distinctUntilChanged()
    .collect { print("$it ") } // 输出: 1 2 3 4 3
```

### 组合操作

-   `merge(other)`: 将两个 `Stream` 合并，元素发射顺序不确定。
-   `concatWith(other)`: 连接两个 `Stream`，当前一个完成后再开始收集另一个。
-   `combine(other) { a, b -> ... }`: 将两个 `Stream` 的最新值组合在一起。

```kotlin
val streamA = streamOf("A", "B").onEach { delay(100) }
val streamB = streamOf(1, 2).onEach { delay(150) }

streamA.combine(streamB) { letter, number -> "$letter$number" }
    .collect { println(it) }
// 可能的输出: A1, B1, B2
```

### 工具操作

-   `onEach { ... }`: 对每个元素执行一个操作，但不改变元素本身，常用于调试或副作用。
-   `chunked(size)`: 将流中的元素按指定大小分块。
-   `throttleFirst(duration)`: 在指定时间窗口内只发射第一个元素。
-   `timeout(duration)`: 如果在指定时间内没有发射新元素，则抛出 `TimeoutException`。

```kotlin
streamOf(1, 2, 3, 4, 5, 6, 7)
    .chunked(3)
    .collect { group ->
        println("Chunk: $group")
    }
// 输出:
// Chunk: [1, 2, 3]
// Chunk: [4, 5, 6]
// Chunk: [7]
```

### 错误处理

-   `catch { e -> ... }`: 捕获上游 `Stream` 中发生的异常。
-   `finally { ... }`: 在 `Stream` 完成或被取消时执行一个动作。

```kotlin
stream<Int> {
    emit(1)
    emit(2)
    throw IllegalStateException("Something went wrong")
    }
.catch { error ->
    println("Caught error: ${error.message}")
    }
.collect {
    println("Received: $it")
}
// 输出:
// Received: 1
// Received: 2
// Caught error: Something went wrong
```

---

## 热流：共享与状态管理

热流用于在多个观察者之间广播数据或管理应用状态。

-   `MutableSharedStream<T>`: 可变共享流，用于手动发射事件。
-   `MutableStateStream<T>`: 可变状态流，用于维护和更新状态。

**应用示例：在 ViewModel 中管理 UI 状态和事件**

```kotlin
class MyViewModel : ViewModel() {
    // 用于管理一次性事件，如Toast或导航
    private val _events = MutableSharedStream<String>()
    val events: SharedStream<String> = _events

    // 用于管理UI状态
    private val _uiState = MutableStateStream("Initial State")
    val uiState: StateStream<String> = _uiState

    fun performAction() {
        viewModelScope.launch {
            // 更新状态
            _uiState.value = "Loading..."
            delay(1000)

            // 模拟操作成功
            _uiState.value = "Action Successful"
            // 发射一次性事件
            _events.emit("Show success toast")
        }
    }
    }
```

### 将冷流转换为热流

您可以使用 `shareIn` 和 `stateIn` 将任何冷流转换为热流。

-   `share(scope, ...)`: 将冷流转换为 `SharedStream`。
-   `state(scope, ...)`: 将冷流转换为 `StateStream`。

```kotlin
val coldStream = intervalStream(1.seconds)

// 转换为在 viewModelScope 中共享的 SharedStream
val shared = coldStream.share(viewModelScope, replay = 1)

// 转换为在 viewModelScope 中维护状态的 StateStream
val state = coldStream.state(viewModelScope, initialValue = -1L)
```

---

## 高级功能：流分割与模式匹配

这是 `Stream` 库一个非常强大的特性，允许您将一个字符流 (`Stream<Char>`) 基于复杂的模式匹配规则分割成多个带有语义的子流。这在解析结构化文本、处理协议或任何需要从原始字节流中提取信息的场景中非常有用。

### 核心概念

-   **`StreamPlugin`**: 一个插件，定义了如何识别和处理流中的特定模式。插件会根据状态处理输入字符。
-   **`PluginState`**: 插件的状态枚举，包含三种状态：
    -   `IDLE`: 插件处于空闲状态，等待匹配模式的开始。
    -   `TRYING`: 插件已经匹配到模式的开始部分，正在尝试验证完整匹配。
    -   `PROCESSING`: 插件已确认完整匹配，正在处理匹配到的内容。
-   **`splitBy(plugins)`**: `Stream<Char>` 的一个操作符，它接收一组插件，并根据这些插件的匹配结果将字符流分割。
-   **`StreamGroup`**: `splitBy` 的输出结果，它包含一个标签（匹配到的插件）和一个子 `Stream`（匹配到的内容）。未被任何插件匹配的文本将进入一个标签为 `null` 的默认组。
-   **`kmpPattern` DSL**: 一个用于在插件中定义匹配模式的领域特定语言，其底层基于高效的KMP算法。

### 示例 1：使用内置 `StreamXmlPlugin` 解析 XML

`Stream` 库提供了一个内置插件 `StreamXmlPlugin` 用于识别 XML/HTML 标签。下面的例子展示了如何用它来分割一个包含文本和XML的混合流。

```kotlin
// 流包含前导文本、一个XML块和尾随文本
val mixedContentStream = "Some leading text<item>Content</item>Some trailing text".asCharStream()

val plugins = listOf(StreamXmlPlugin())

mixedContentStream.splitBy(plugins)
    .collect { group ->
        val groupType = when (group.tag) {
            is StreamXmlPlugin -> "XML"
            null -> "Text" // tag为null表示默认的文本组
        }
        print("发现组 '$groupType': ")
        
        val content = StringBuilder()
        group.stream.collect { content.append(it) }
        
        println(content.toString())
}

// 输出:
// 发现组 'Text': Some leading text
// 发现组 'XML': <item>Content</item>
// 发现组 'Text': Some trailing text
```
*注意：`StreamXmlPlugin` 是一个基础插件，用于演示 `splitBy` 的能力。它会将从开始标签到对应结束标签的所有内容（包括内部的其它标签）视为单个组，并且不支持真正的 XML 嵌套解析。对于复杂的 XML 处理，需要使用更专业的库。*

### 示例 2：创建自定义插件解析键值对

假设我们有一个流，格式为 `[KEY1:VALUE1][KEY2:VALUE2]...`，我们想把它们解析出来。

**1. 定义我们的自定义插件**

```kotlin
private const val GROUP_KEY = 1
private const val GROUP_VALUE = 2

class KeyValuePlugin : StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        private set
        
    private var matcher: StreamKmpGraph

    init {
        matcher = StreamKmpGraphBuilder().build(kmpPattern {
            char('[')
            group(GROUP_KEY) { greedyStar { noneOf(':') } }
            char(':')
            group(GROUP_VALUE) { greedyStar { noneOf(']') } }
            char(']')
        })
        reset()
    }

    override fun processChar(c: Char): Boolean {
        when (val result = matcher.processChar(c)) {
            is StreamKmpMatchResult.Match -> {
                // 完全匹配，转为IDLE状态
                state = PluginState.IDLE
                matcher.reset()
                return true
            }
            is StreamKmpMatchResult.InProgress -> {
                // 正在匹配中
                state = if (result.isMatchStarted) PluginState.PROCESSING else PluginState.TRYING
                return true
            }
            is StreamKmpMatchResult.NoMatch -> {
                // 匹配失败，重置状态
                if (state != PluginState.IDLE) {
                    state = PluginState.IDLE
                    matcher.reset()
                }
                return false
            }
        }
    }

    override fun reset() {
        matcher.reset()
        state = PluginState.IDLE
    }
    
    override fun initPlugin(): Boolean {
        reset()
        return true
    }
    
    override fun destroy() {
        // 清理资源
    }
}
``` 

**2. 使用 `kmpPattern` DSL**

`kmpPattern` DSL 是定义匹配逻辑的核心。

-   `char('[')`: 匹配单个字符 `[`。
-   `group(ID) { ... }`: 定义一个捕获组。匹配到的内容可以通过ID获取。
-   `greedyStar { ... }`: 贪心匹配。它会尽可能多地匹配满足内部条件的字符。
-   `noneOf(':')`: 匹配任何不是冒号 `:` 的字符。

**3. 使用插件分割流**

一旦插件定义好了，使用它就和使用内置插件一样简单。`splitBy` 会处理所有的状态转换和缓冲逻辑。

这个强大的模式匹配系统，结合 `Stream` 的异步处理能力，为处理复杂的、连续的数据流提供了极大的灵活性。

---

## 高级主题：使用插件系统进行流解析

这是 `Stream` 库一个非常强大的特性，它允许您将一个字符流 (`Stream<Char>`) 基于复杂的模式匹配规则，分割成多个带有语义的子流。这在解析结构化文本（如Markdown、XML）、处理网络协议或任何需要从原始字节流中提取信息的场景中非常有用。

### 核心概念

-   **`Stream<Char>.splitBy(plugins)`**: 这是核心操作符。它接收一个 `StreamPlugin` 列表，并根据这些插件的匹配规则，将输入的 `Stream<Char>` 分割成一个 `Stream<StreamGroup<StreamPlugin?>>`。
-   **`StreamPlugin`**: 插件是实现模式识别逻辑的地方。它本质上是一个状态机，通过 `processChar(c: Char, atStartOfLine: Boolean)` 方法处理流中的每个字符。
    -   `atStartOfLine` 参数对于解析那些对位置敏感的语法（如Markdown的标题和列表）至关重要。
    -   插件内部有三种状态 (`PluginState`):
        -   `IDLE`: 空闲状态，等待匹配模式的开始。
        -   `TRYING`: 已经匹配到模式的开始部分，正在尝试验证完整匹配。如果后续字符不匹配，插件将重置并放弃。
        -   `PROCESSING`: 已确认完整匹配，正在处理匹配到的内容，直到模式结束。
-   **`StreamGroup<TAG>`**: `splitBy` 的输出单元。它包含一个 `tag`（匹配成功的插件实例，如果是不属于任何插件的默认文本，则为 `null`）和一个 `stream: Stream<String>`。注意，子流的类型是 `Stream<String>`，它按块发射由插件捕获的字符。

### 实战：构建一个流式 Markdown 解析器

这个库提供了一套完整的Markdown插件。下面的例子将展示如何利用它们来构建一个高性能的流式Markdown解析器，这正是本库中 `StreamMarkdownRenderer` 组件的工作原理。

#### 1. 两阶段解析策略

Markdown语法具有嵌套结构（例如，一个列表项可以包含粗体文本）。最优的处理方式是分两步进行：
1.  **块级解析**：首先，使用块级元素插件（如标题、列表、代码块）分割整个Markdown文本。
2.  **内联解析**：然后，对每个块级元素的内容，再次使用内联元素插件（如粗体、斜体、链接）进行分割。

#### 2. 获取预设插件

为了方便使用，`MarkdownProcessor.kt` 提供了预设的插件列表。

```kotlin
// 获取所有块级插件
val blockPlugins = NestedMarkdownProcessor.getBlockPlugins()

// 获取所有内联插件
val inlinePlugins = NestedMarkdownProcessor.getInlinePlugins()
```

> **重要提示**：插件的顺序至关重要！对于有重叠分隔符的语法（例如 `**` 用于粗体，`*` 用于斜体），必须将更长的分隔符插件放在前面，以确保 `**text**` 被正确解析为粗体，而不是两个连续的斜体。`getInlinePlugins()` 已经处理好了正确的顺序。

#### 3. 实现流式解析

下面的代码演示了如何实现流式解析和UI模型构建：

```kotlin
// 假设 markdownStream: Stream<Char> 是输入的Markdown字符流
// nodes 是一个UI状态列表 (e.g., mutableStateListOf<MarkdownNode>())

// 1. 块级解析
markdownStream.splitBy(blockPlugins).collect { blockGroup ->
    // 根据匹配的插件确定块类型
    val blockType = NestedMarkdownProcessor.getTypeForPlugin(blockGroup.tag)
    
    // 为这个新块创建一个UI节点
    val node = MarkdownNode(type = blockType, content = "", children = mutableListOf())
    val nodeIndex = nodes.add(node) // 添加到UI列表并获取索引

    val isInlineContainer = blockType != MarkdownProcessorType.CODE_BLOCK
    val contentBuilder = StringBuilder()

    // 收集块的内容
    blockGroup.stream.collect { contentChunk ->
        // 流式更新UI节点的内容，实现"打字机"效果
        nodes[nodeIndex] = nodes[nodeIndex].copy(content = nodes[nodeIndex].content + contentChunk)
        contentBuilder.append(contentChunk)
    }

    val blockContent = contentBuilder.toString()

    // 2. 如果块可以包含内联元素，则进行内联解析
    if (isInlineContainer && blockContent.isNotEmpty()) {
        val inlineChildren = mutableListOf<MarkdownNode>()
        
        // 将块内容转换为字符流以供再次分割
        val charStream = blockContent.toCharStream()  // 使用 String.toCharStream() 扩展函数，等同于 String.stream()

        charStream.splitBy(inlinePlugins).collect { inlineGroup ->
            val inlineType = NestedMarkdownProcessor.getTypeForPlugin(inlineGroup.tag)
            val inlineContent = inlineGroup.stream.collectToString() // 辅助函数
            
            if (inlineContent.isNotEmpty()) {
                inlineChildren.add(MarkdownNode(type = inlineType, content = inlineContent))
            }
        }
        
        // 内联解析完成后，用解析出的子节点更新UI节点
        if (inlineChildren.isNotEmpty()) {
            nodes[nodeIndex] = nodes[nodeIndex].copy(children = inlineChildren)
        }
    }
}
```

这个例子完美地展示了 `splitBy` 的嵌套能力，实现了复杂文本的流式增量解析。

### 可用的 Markdown 插件

以下是 `com.ai.assistance.operit.util.stream.plugins` 包中提供的主要Markdown插件：

| 插件类 | 功能 | 主要构造参数 |
| :--- | :--- | :--- |
| `StreamMarkdownHeaderPlugin` | 识别ATX风格的标题 (`# ...`) | `includeMarker: Boolean` - 是否包含`#` |
| `StreamMarkdownFencedCodeBlockPlugin` | 识别代码块 (```...```) | `includeFences: Boolean` - 是否包含 ``` |
| `StreamMarkdownBlockQuotePlugin` | 识别引用块 (`> ...`) | `includeMarker: Boolean` - 是否包含 `>` |
| `StreamMarkdownOrderedListPlugin` | 识别有序列表 (`1. ...`) | `includeMarker: Boolean` - 是否包含 `1.` |
| `StreamMarkdownUnorderedListPlugin`| 识别无序列表 (`- ...` 或 `* ...`) | `includeMarker: Boolean` - 是否包含 `-` |
| `StreamMarkdownHorizontalRulePlugin`| 识别水平分割线 (`---`, `***`) | `includeMarker: Boolean` - 是否包含分隔符 |
| `StreamMarkdownBoldPlugin` | 识别粗体 (`**...**`) | `includeAsterisks: Boolean` - 是否包含 `**` |
| `StreamMarkdownItalicPlugin` | 识别斜体 (`*...*`) | `includeAsterisks: Boolean` - 是否包含 `*` |
| `StreamMarkdownInlineCodePlugin` | 识别行内代码 (`` `...` ``) | `includeTicks: Boolean` - 是否包含 `` ` `` |
| `StreamMarkdownLinkPlugin` | 识别链接 (`[text](url)`) | `includeDelimiters: Boolean` - 是否包含 `[]()` |
| `StreamMarkdownImagePlugin` | 识别图片 (`![alt](url)`) | `includeDelimiters: Boolean` - 是否包含 `![]()` |
| `StreamMarkdownStrikethroughPlugin`| 识别删除线 (`~~...~~`) | `includeDelimiters: Boolean` - 是否包含 `~~` |
| `StreamMarkdownUnderlinePlugin` | 识别下划线 (`__...__`) | `includeDelimiters: Boolean` - 是否包含 `__` |

---

## 与 Kotlin Flow 互操作

`Stream` 提供了与 Kotlin Flow 的无缝转换。

-   `Flow<T>.asStream()`: 将一个 `Flow` 转换为 `Stream`。
-   `Stream<T>.asFlow()`: 将一个 `Stream` 转换为 `Flow`。

这使得您可以在现有项目中逐步引入 `Stream`，或者在需要时利用 `Flow` 生态系统中的特定功能。

---

## 最佳实践

-   **冷流 vs 热流**: 对短暂、一次性的数据转换使用冷流（普通 `Stream`）。对于需要在多个地方共享的数据（如配置、用户状态）或事件源，使用热流（`SharedStream`, `StateStream`）。
-   **异常处理**: 始终考虑数据流中可能出现的异常，并使用 `catch` 操作符来优雅地处理它们，防止应用崩溃。
-   **生命周期管理**: 在 Android 等具有生命周期的组件中，使用 `viewModelScope` 或 `lifecycleScope` 并结合 `launchIn` 来自动管理流的收集，避免内存泄漏。
-   **利用 `onEach` 调试**: 当流的行为不符合预期时，`onEach { ... }` 是一个极佳的调试工具，可以在不影响流的情况下观察每个阶段的数据。

希望这份指南能帮助您充分利用 `Stream` 库的强大功能！ 