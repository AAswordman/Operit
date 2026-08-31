# Composer 测量

## 旧实现

`AIChatScreen` 向 `chat.main` 的 `composer` slot 提供了两层 `Box(fillMaxSize())`。scene scaffold 会先测量非 weight 的 bottom；该子树因此可以吃掉剩余高度，使 weight transcript 区域高度变为零。

## 修正

- `ChatInputBottomBar` 接收并下传 layout modifier。
- composer slot 直接提供 `fillMaxWidth()`、按实际内容高度测量的输入器。
- 高度观察和 IME translation 附着于真实输入器根节点，不再依赖满屏占位 Box。

## 验收

- 有任意 composer 内容时，transcript 的已测量高度大于零。
- composer 高度随附件、排队消息和多行输入变化，且不会覆盖 transcript。
