这里是从 Jetpack Compose Foundation 提取并本地化的 `LazyColumn` 相关源码。

- 来源版本：`androidx.compose.foundation:foundation-android:1.10.4`
- 入口文件：[RecyclerLazyColumn.kt](RecyclerLazyColumn.kt)
- 核心布局：[LazyList.kt](LazyList.kt)
- 核心测量：[LazyListMeasure.kt](LazyListMeasure.kt)
- 状态管理：[LazyListState.kt](LazyListState.kt)

处理方式：

- 已改为本地包名 `com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy`
- 文件全部平铺在当前目录下，不再保留上游多级目录
- `LazyColumn` 入口已重命名为 `RecyclerLazyColumn`
- 仅保留 `LazyColumn/LazyList` 这条链路相关文件，没有带入 `grid` 和 `staggeredgrid`

这批文件目前是为了后续魔改准备的本地副本，这次没有执行编译、构建或测试。
