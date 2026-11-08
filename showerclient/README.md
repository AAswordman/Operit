## Shower 客户端库（`showerclient` 模块）

一个最小化的 Shower 客户端库，只保留 **如何接入和使用** 的说明。

---

## 1. 添加依赖

在宿主 App 模块的 `build.gradle.kts` 中：

```kotlin
dependencies {
    implementation(project(":showerclient"))
}
```

---

## 2. 提供 `ShellRunner` 实现

`showerclient` 不直接执行 shell 命令，只通过一个接口向外要能力：

```kotlin
fun interface ShellRunner {
    suspend fun run(command: String, identity: ShellIdentity): ShellCommandResult
}
```

在 App 模块中实现它，并在应用启动时注入：

```kotlin
class OperitShowerShellRunner : ShellRunner {
    override suspend fun run(command: String, identity: ShellIdentity): ShellCommandResult {
        // 在这里调用你自己的 shell 执行器
        // 并把结果转换成 ShellCommandResult 返回
    }
}

class YourApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        ShowerEnvironment.shellRunner = OperitShowerShellRunner()
    }
}
```

`ShellIdentity` 用于区分执行身份，例如 `DEFAULT` / `SHELL` / `ROOT`，按你的项目需要映射即可。

---

## 3. Shower Server JAR（已内置）

本库在构建时从仓库中的 `tools/shower/app/src/main/java/` 编译服务端，并通过 SDK Build Tools 的 D8 生成 `shower-server.jar`。生成任务为 `:showerclient:generateShowerServerAssets`，由模块的 `preBuild` 自动调用，产物在 `showerclient/build/generated/showerServer/assets/`。不再使用已提交的预编译 JAR，以保证服务端与客户端 Binder 协议来自同一份源码。

主屏 PhoneAgent 在输入准备后持有客户端 Binder 令牌，模型等待、暂停及观察期间服务保持存活；任务退出或取消时释放，宿主进程死亡也会自动注销。原有无客户端 15 秒退出策略保留，无需心跳输入、延长超时或重发动作。

打包与运行：

- 宿主 App **不需要** 再手动打包或拷贝任何 JAR 文件；
- 运行时库会自动从自身 `assets` 中读取，并复制到 `/sdcard/Download/Operit/shower-server.jar`，再拷贝到 `/data/local/tmp/shower-server.jar`。

---

## 4. 处理 Binder 交接广播

Shower server 启动后，会通过广播把 `IShowerService` 的 `IBinder` 发送给客户端。宿主 App 需要在广播接收器中把它交给本库：

广播协议（与主项目保持一致）：

 - **Action**：`com.ai.assistance.operit.action.SHOWER_BINDER_READY`
 - **Extra key**：`binder_container`
 - **Extra 类型**：`com.ai.assistance.shower.ShowerBinderContainer`（`Parcelable`，内部包含 `IBinder`）

```kotlin
class ShowerBinderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SHOWER_BINDER_READY) return

        val container = intent.getParcelableExtra<ShowerBinderContainer>(EXTRA_BINDER_CONTAINER)
        val binder = container?.binder ?: return
        val service = IShowerService.Stub.asInterface(binder)
        ShowerBinderRegistry.setService(service)
    }

    companion object {
        const val ACTION_SHOWER_BINDER_READY =
            "com.ai.assistance.operit.action.SHOWER_BINDER_READY"
        const val EXTRA_BINDER_CONTAINER = "binder_container"
    }
}
```

在 `AndroidManifest.xml` 注册 Receiver（**需要 `exported=true`**，因为广播发送端运行在独立的 shower-server 进程里）：

```xml
<receiver
    android:name=".ShowerBinderReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="com.ai.assistance.operit.action.SHOWER_BINDER_READY" />
    </intent-filter>
</receiver>
```

说明：

 - `ShowerServerManager` 启动命令会附带宿主包名参数：`CLASSPATH=/data/local/tmp/shower-server.jar app_process / com.ai.assistance.shower.Main <hostPackage> &`。
 - shower-server 读取该参数后，会通过 `IActivityManager.broadcastIntent(...)` 发送 `SHOWER_BINDER_READY`，并用 `Intent.setPackage(<hostPackage>)` 只投递给目标宿主包。
 - 因此宿主 App 侧的关键是：**Manifest 里声明 intent-filter 的 action 必须匹配**，并在 `onReceive()` 里把 `ShowerBinderContainer` 交给 `ShowerBinderRegistry.setService()`。

---

## 5. 启动 Shower server 并建立虚拟显示

在需要使用 Shower 的地方（例如某个 Agent 或 Service）：

```kotlin
// 1）确保 server 已启动并且成功收到 Binder
val ok = ShowerServerManager.ensureServerStarted(context)
if (!ok) return

// 2）创建 / 更新虚拟显示
val displayOk = ShowerController.ensureDisplay(
    context = context,
    width = 1080,
    height = 2400,
    dpi = 480,
    bitrateKbps = 8000,
)
if (!displayOk) return
```

之后可以通过 `ShowerController` 发送输入事件：

```kotlin
ShowerController.touchDown(x, y)
ShowerController.touchMove(x, y)
ShowerController.touchUp(x, y)
ShowerController.key(KeyEvent.KEYCODE_BACK)
```

截图：

```kotlin
val pngBytes: ByteArray? = ShowerController.requestScreenshot()
```

---

## 6. 使用内置视频组件（可选）

如果你希望直接复用内置的视频解码和渲染，可以使用：

- `com.ai.assistance.showerclient.ShowerVideoRenderer`
- `com.ai.assistance.showerclient.ui.ShowerSurfaceView`

示例：在某个浮层中渲染 Shower 虚拟屏：

```kotlin
// SurfaceView（也可以继承 ui.ShowerSurfaceView 做一层包装）
class ShowerSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : com.ai.assistance.showerclient.ui.ShowerSurfaceView(context, attrs)
```

`ui.ShowerSurfaceView` 内部已经帮你：

- 等待 `ShowerController.getVideoSize()` 就绪；
- 在 `surfaceCreated` 时调用 `ShowerVideoRenderer.attach(surface, w, h)`；
- 把 `ShowerController` 的二进制帧回调绑定到 `ShowerVideoRenderer.onFrame`；
- 在 `surfaceDestroyed` 时清理 handler 并 `detach()`。

如果你需要自定义解码/渲染（录屏、推流等），可以不使用这些组件，而是自己实现：

```kotlin
ShowerController.setBinaryHandler { frame: ByteArray ->
    // 自己解码并渲染
}
```

---

## 7. 最小心智模型

- 你提供：`ShellRunner`、广播接收器；
- 本库提供：`ShowerServerManager` + `ShowerController` + 可选的 `ShowerVideoRenderer` / `ui.ShowerSurfaceView`；
- 常见调用顺序：**注入 ShellRunner → 启动 server → 收 Binder → ensureDisplay → 发送输入 / 渲染视频**。
