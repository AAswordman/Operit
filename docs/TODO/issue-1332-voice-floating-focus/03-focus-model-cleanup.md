---
step: 3
scope: 焦点建模清理与软键盘入口
---

# 删掉语音管理器里的焦点建模

## 旧实现

`SpeechInteractionManager` 自建了一套「焦点」概念，但它和 Android 的输入焦点、以及麦克风采集资格都不是一回事：

```kotlin
fun requestFocus(view: View?): Boolean {
    if (view == null) { hasFocus = false; return false }
    view.requestFocus()
    inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    hasFocus = true
    return true
}
```

三处问题：

- `showSoftInput` 与 `hideSoftInputFromWindow` 针对同一个 windowToken 前后紧挨着调用，语义自相矛盾，等于请求弹出后立刻收起
- 传进来的是 ComposeView 根节点，不是任何文本编辑器，`showSoftInput` 对它本来就不会生效
- `hasFocus` 无条件置 `true`，于是 `startListening()` 开头的 `if (!hasFocus) return` 在语音态里永远不触发，是一段不可达的死代码，却把排查方向带偏了

Issue 作者正是据此判断「首轮结束后焦点丢失导致第二轮不收音」。实际上 `hasFocus` 只有 `requestFocus` 置真、`releaseFocus` 置假，而 `releaseFocus` 只在 `cleanup()` 里调用，一轮识别结束不会碰它。

软键盘真正的入口在 `BottomControlBar`：文本胶囊的短按走 `focusRequester.requestFocus()` 加 `keyboardController?.show()`，全程不读 `hasFocus`。全屏窗口的 `WindowManager.LayoutParams` 不带 `FLAG_NOT_FOCUSABLE`，本身就能接受输入焦点，Compose 焦点系统足以把键盘拉起来，不需要在 ViewModel 层对着 ComposeView 根节点做模拟。

## 意图修正

语音管理器只负责语音。输入焦点归窗口和 Compose 焦点系统，麦克风资格归前台服务类型。把自制焦点概念整体删掉，同时删掉依赖它的死分支。

## 新实现

`SpeechInteractionManager` 移除 `hasFocus`、`requestFocus()`、`releaseFocus()`、`inputMethodManager` 字段，以及 `startListening()` 开头的 `!hasFocus` 短路，连同 `INPUT_METHOD_SERVICE`、`View`、`InputMethodManager` 三个已经无用的 import。

调用点同步收敛：

- `FloatingFullscreenModeViewModel.initialize()` 直接给出提示文案，不再用 `requestFocus` 的返回值决定提示
- `FloatingFullscreenModeViewModel.cleanup()` 不再调 `releaseFocus`
- `SiriBall` 的 `DisposableEffect` 只保留 `initialize()`
- `FloatingFullscreenModeViewModel` 的 `hasFocus` 代理属性删除

随之失效的两个字符串资源 `floating_cannot_get_focus` 与 `floating_cannot_get_input_service` 在全部 8 个语言文件里一并删除，避免留下指向已删逻辑的死资源。

## 结果

语音链路上不再有与真实状态脱节的焦点标志，`startListening()` 的成功与否只取决于识别器能否启动。软键盘入口回到 Compose 焦点系统这一条路径上，不再经过语音管理器。

[DONE]
