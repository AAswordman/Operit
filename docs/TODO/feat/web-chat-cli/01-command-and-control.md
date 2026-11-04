# 命令与本地服务控制

旧实现仅在设置页面提供端口、启停和 Token 管理，终端没有服务控制命令。

新增独立 `tools/web_chat_cli` Rust 包负责参数解析及 Unix socket 通信，Kotlin 的 `integrations/http/commands` 包负责命令校验、配置读写、示例、浏览器和服务控制。新增 ResultReceiver 回执，在前台服务实际处理请求后回复状态，避免直接用旧 StateFlow 值判断成功。

安装沿用宿主提供的会话初始化入口，不改终端子模块。普通 proot 与 root chroot 均使用包装命令中记录的宿主 socket 名称。命令严格拒绝未知、重复或放错位置的参数。

源码实现已完成，尚未执行本地编译、测试或设备验证。

[DONE]
