/* METADATA
{
    "name": "system_tools",

    "display_name": {
        "zh": "系统工具",
        "en": "System Tools"
    },
    "description": {
        "zh": "提供系统级操作工具，包括设置管理、应用安装卸载与启动、通知获取、位置服务、设备信息查询，以及 Intent/广播调用。",
        "en": "System-level operations: settings management, app install/uninstall & launch, notification retrieval, location services, device info queries, plus Intent/broadcast execution."
    },
    "enabledByDefault": true,
    "category": "System",
    "tools": [
        {
            "name": "get_system_setting",
            "description": { "zh": "获取系统设置的值。需要用户授权。", "en": "Get the value of a system setting. Requires user authorization." },
            "parameters": [
                { "name": "setting", "description": { "zh": "设置名称", "en": "Setting key/name" }, "type": "string", "required": true },
                { "name": "namespace", "description": { "zh": "命名空间：system/secure/global，默认system", "en": "Namespace: system/secure/global (default: system)" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "modify_system_setting",
            "description": { "zh": "修改系统设置的值。需要用户授权。", "en": "Modify the value of a system setting. Requires user authorization." },
            "parameters": [
                { "name": "setting", "description": { "zh": "设置名称", "en": "Setting key/name" }, "type": "string", "required": true },
                { "name": "value", "description": { "zh": "设置值", "en": "Setting value" }, "type": "string", "required": true },
                { "name": "namespace", "description": { "zh": "命名空间：system/secure/global，默认system", "en": "Namespace: system/secure/global (default: system)" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "install_app",
            "description": { "zh": "安装应用程序。需要用户授权。", "en": "Install an app. Requires user authorization." },
            "parameters": [
                { "name": "path", "description": { "zh": "APK文件路径", "en": "APK file path" }, "type": "string", "required": true }
            ]
        },
        {
            "name": "uninstall_app",
            "description": { "zh": "卸载应用程序。需要用户授权。", "en": "Uninstall an app. Requires user authorization." },
            "parameters": [
                { "name": "package_name", "description": { "zh": "应用包名", "en": "App package name" }, "type": "string", "required": true },
                { "name": "keep_data", "description": { "zh": "是否保留数据，默认false", "en": "Whether to keep app data (default: false)" }, "type": "boolean", "required": false }
            ]
        },
        {
            "name": "list_installed_apps",
            "description": { "zh": "获取已安装应用程序列表。需要用户授权。", "en": "List installed apps. Requires user authorization." },
            "parameters": [
                { "name": "include_system_apps", "description": { "zh": "是否包含系统应用，默认false", "en": "Whether to include system apps (default: false)" }, "type": "boolean", "required": false }
            ]
        },
        {
            "name": "start_app",
            "description": { "zh": "启动应用程序。需要用户授权。", "en": "Launch an app. Requires user authorization." },
            "parameters": [
                { "name": "package_name", "description": { "zh": "应用包名", "en": "App package name" }, "type": "string", "required": true },
                { "name": "activity", "description": { "zh": "可选活动名称", "en": "Optional activity name" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "stop_app",
            "description": { "zh": "停止正在运行的应用程序。需要用户授权。", "en": "Force stop a running app. Requires user authorization." },
            "parameters": [
                { "name": "package_name", "description": { "zh": "应用包名", "en": "App package name" }, "type": "string", "required": true }
            ]
        },
        {
            "name": "send_broadcast",
            "description": { "zh": "发送广播（Broadcast Intent）。需要用户授权。", "en": "Send a broadcast (Broadcast Intent). Requires user authorization." },
            "parameters": [
                { "name": "action", "description": { "zh": "Intent action，例如 android.intent.action.VIEW", "en": "Intent action, e.g. android.intent.action.VIEW" }, "type": "string", "required": true },
                { "name": "package_name", "description": { "zh": "可选：限制广播目标包名", "en": "Optional: restrict target package" }, "type": "string", "required": false },
                { "name": "component", "description": { "zh": "可选：组件名 package/class，优先于package_name", "en": "Optional: component package/class, takes priority over package_name" }, "type": "string", "required": false },
                { "name": "uri", "description": { "zh": "可选：data uri", "en": "Optional: data uri" }, "type": "string", "required": false },
                { "name": "extras", "description": { "zh": "可选：extras（对象，可用于传参）", "en": "Optional: extras (object for parameters)" }, "type": "object", "required": false }
            ]
        },
        {
            "name": "execute_intent",
            "description": { "zh": "执行 Intent（Activity/Service/Broadcast），支持 extras 传参。需要用户授权。", "en": "Execute an Intent (Activity/Service/Broadcast) with extras parameters. Requires user authorization." },
            "parameters": [
                { "name": "type", "description": { "zh": "类型：activity/broadcast/service，默认activity", "en": "Type: activity/broadcast/service (default: activity)" }, "type": "string", "required": false },
                { "name": "action", "description": { "zh": "Intent action（action 或 component 至少一个必填）", "en": "Intent action (either action or component is required)" }, "type": "string", "required": false },
                { "name": "package_name", "description": { "zh": "可选：包名", "en": "Optional: package name" }, "type": "string", "required": false },
                { "name": "component", "description": { "zh": "可选：组件名 package/class", "en": "Optional: component package/class" }, "type": "string", "required": false },
                { "name": "uri", "description": { "zh": "可选：data uri", "en": "Optional: data uri" }, "type": "string", "required": false },
                { "name": "flags", "description": { "zh": "可选：flags（整数或JSON数组字符串）", "en": "Optional: flags (integer or JSON array string)" }, "type": "string", "required": false },
                { "name": "extras", "description": { "zh": "可选：extras（对象，可用于传参）", "en": "Optional: extras (object for parameters)" }, "type": "object", "required": false }
            ]
        },
        {
            "name": "get_notifications",
            "description": { "zh": "获取设备通知内容。", "en": "Retrieve device notifications." },
            "parameters": [
                { "name": "limit", "description": { "zh": "最大返回条数，默认10", "en": "Max number of entries to return (default: 10)" }, "type": "number", "required": false },
                { "name": "include_ongoing", "description": { "zh": "是否包含常驻通知，默认false", "en": "Whether to include ongoing notifications (default: false)" }, "type": "boolean", "required": false }
            ]
        },
        {
            "name": "get_app_usage_time",
            "description": { "zh": "获取应用前台使用时长。需要授予“使用情况访问权限”。", "en": "Get app foreground usage time. Requires Usage Access permission." },
            "parameters": [
                { "name": "package_name", "description": { "zh": "可选：精确应用包名", "en": "Optional exact package name" }, "type": "string", "required": false },
                { "name": "since_hours", "description": { "zh": "向前统计多少小时，默认24", "en": "How many hours to look back (default: 24)" }, "type": "number", "required": false },
                { "name": "limit", "description": { "zh": "不传包名时最多返回多少个应用，默认10", "en": "Max apps to return when package_name is omitted (default: 10)" }, "type": "number", "required": false },
                { "name": "include_system_apps", "description": { "zh": "不传包名时是否包含系统应用，默认false", "en": "Whether to include system apps when package_name is omitted (default: false)" }, "type": "boolean", "required": false }
            ]
        },
        {
            "name": "get_device_location",
            "description": { "zh": "获取设备当前位置信息。", "en": "Get current device location." },
            "parameters": [
                { "name": "high_accuracy", "description": { "zh": "是否使用高精度模式，默认false", "en": "Use high accuracy mode (default: false)" }, "type": "boolean", "required": false },
                { "name": "timeout", "description": { "zh": "超时时间（秒），默认10", "en": "Timeout in seconds (default: 10)" }, "type": "number", "required": false }
            ]
        },
        {
            "name": "request_bluetooth_permission",
            "description": { "zh": "请求蓝牙附近设备权限。", "en": "Request Bluetooth nearby devices permission." },
            "parameters": []
        },
        {
            "name": "get_bluetooth_state",
            "description": { "zh": "获取蓝牙适配器状态。", "en": "Get Bluetooth adapter state." },
            "parameters": []
        },
        {
            "name": "request_enable_bluetooth",
            "description": { "zh": "打开系统蓝牙开启对话框。", "en": "Open the system dialog to enable Bluetooth." },
            "parameters": []
        },
        {
            "name": "list_bluetooth_bonded_devices",
            "description": { "zh": "列出已配对蓝牙设备。", "en": "List bonded Bluetooth devices." },
            "parameters": []
        },
        {
            "name": "scan_bluetooth_devices",
            "description": { "zh": "扫描附近蓝牙 Classic 与 BLE 设备。", "en": "Scan nearby Bluetooth classic and BLE devices." },
            "parameters": [
                { "name": "duration_ms", "description": { "zh": "扫描时长毫秒。", "en": "Scan duration in milliseconds." }, "type": "number", "required": false },
                { "name": "include_ble", "description": { "zh": "是否包含 BLE 扫描。", "en": "Whether to include BLE scanning." }, "type": "boolean", "required": false }
            ]
        },
        {
            "name": "bluetooth_connect",
            "description": { "zh": "连接蓝牙 Classic 设备。", "en": "Connect to a Bluetooth classic device." },
            "parameters": [
                { "name": "address", "description": { "zh": "蓝牙 MAC 地址", "en": "Bluetooth MAC address" }, "type": "string", "required": true },
                { "name": "uuid", "description": { "zh": "RFCOMM UUID。", "en": "RFCOMM UUID." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "bluetooth_listen",
            "description": { "zh": "监听别人连接本机的蓝牙 Classic 通道。", "en": "Listen for another device connecting to this phone over Bluetooth classic." },
            "parameters": [
                { "name": "name", "description": { "zh": "服务名。", "en": "Service name." }, "type": "string", "required": false },
                { "name": "uuid", "description": { "zh": "RFCOMM UUID。", "en": "RFCOMM UUID." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "bluetooth_accept",
            "description": { "zh": "接受蓝牙 Classic 监听会话中的一个传入连接。", "en": "Accept one incoming connection from a Bluetooth classic listener." },
            "parameters": [
                { "name": "listener_session_id", "description": { "zh": "监听会话 ID", "en": "Listener session ID" }, "type": "string", "required": true },
                { "name": "timeout_ms", "description": { "zh": "等待毫秒数。", "en": "Wait time in milliseconds." }, "type": "number", "required": false }
            ]
        },
        {
            "name": "bluetooth_send",
            "description": { "zh": "向蓝牙 Classic 会话发送文本或 base64 字节。", "en": "Send text or base64 bytes to a Bluetooth classic session." },
            "parameters": [
                { "name": "session_id", "description": { "zh": "会话 ID", "en": "Session ID" }, "type": "string", "required": true },
                { "name": "text", "description": { "zh": "UTF-8 文本", "en": "UTF-8 text" }, "type": "string", "required": false },
                { "name": "data_base64", "description": { "zh": "base64 字节", "en": "Base64 bytes" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "bluetooth_read",
            "description": { "zh": "从蓝牙 Classic 会话读取文本或字节。", "en": "Read text or bytes from a Bluetooth classic session." },
            "parameters": [
                { "name": "session_id", "description": { "zh": "会话 ID", "en": "Session ID" }, "type": "string", "required": true },
                { "name": "max_bytes", "description": { "zh": "最大读取字节数。", "en": "Maximum bytes to read." }, "type": "number", "required": false },
                { "name": "timeout_ms", "description": { "zh": "等待毫秒数。", "en": "Wait time in milliseconds." }, "type": "number", "required": false }
            ]
        },
        {
            "name": "bluetooth_send_and_read",
            "description": { "zh": "向蓝牙 Classic 会话发送后读取响应。", "en": "Send to a Bluetooth classic session and read the response." },
            "parameters": [
                { "name": "session_id", "description": { "zh": "会话 ID", "en": "Session ID" }, "type": "string", "required": true },
                { "name": "text", "description": { "zh": "UTF-8 文本", "en": "UTF-8 text" }, "type": "string", "required": false },
                { "name": "data_base64", "description": { "zh": "base64 字节", "en": "Base64 bytes" }, "type": "string", "required": false },
                { "name": "max_bytes", "description": { "zh": "最大读取字节数。", "en": "Maximum bytes to read." }, "type": "number", "required": false },
                { "name": "timeout_ms", "description": { "zh": "等待毫秒数。", "en": "Wait time in milliseconds." }, "type": "number", "required": false }
            ]
        },
        {
            "name": "bluetooth_close",
            "description": { "zh": "关闭蓝牙 Classic、监听或 BLE 会话。", "en": "Close a Bluetooth classic, listener, or BLE session." },
            "parameters": [
                { "name": "session_id", "description": { "zh": "会话 ID", "en": "Session ID" }, "type": "string", "required": true }
            ]
        },
        {
            "name": "bluetooth_ble_connect",
            "description": { "zh": "连接 BLE 设备。", "en": "Connect to a BLE device." },
            "parameters": [
                { "name": "address", "description": { "zh": "蓝牙 MAC 地址", "en": "Bluetooth MAC address" }, "type": "string", "required": true },
                { "name": "auto_connect", "description": { "zh": "是否自动连接。", "en": "Whether to use auto connect." }, "type": "boolean", "required": false }
            ]
        },
        {
            "name": "bluetooth_ble_discover_services",
            "description": { "zh": "发现 BLE 服务和 characteristic。", "en": "Discover BLE services and characteristics." },
            "parameters": [
                { "name": "session_id", "description": { "zh": "BLE 会话 ID", "en": "BLE session ID" }, "type": "string", "required": true },
                { "name": "timeout_ms", "description": { "zh": "等待毫秒数。", "en": "Wait time in milliseconds." }, "type": "number", "required": false }
            ]
        },
        {
            "name": "bluetooth_ble_read_characteristic",
            "description": { "zh": "读取 BLE characteristic。", "en": "Read a BLE characteristic." },
            "parameters": [
                { "name": "session_id", "description": { "zh": "BLE 会话 ID", "en": "BLE session ID" }, "type": "string", "required": true },
                { "name": "service_uuid", "description": { "zh": "Service UUID", "en": "Service UUID" }, "type": "string", "required": true },
                { "name": "characteristic_uuid", "description": { "zh": "Characteristic UUID", "en": "Characteristic UUID" }, "type": "string", "required": true },
                { "name": "timeout_ms", "description": { "zh": "等待毫秒数。", "en": "Wait time in milliseconds." }, "type": "number", "required": false }
            ]
        },
        {
            "name": "bluetooth_ble_write_characteristic",
            "description": { "zh": "写入 BLE characteristic。", "en": "Write a BLE characteristic." },
            "parameters": [
                { "name": "session_id", "description": { "zh": "BLE 会话 ID", "en": "BLE session ID" }, "type": "string", "required": true },
                { "name": "service_uuid", "description": { "zh": "Service UUID", "en": "Service UUID" }, "type": "string", "required": true },
                { "name": "characteristic_uuid", "description": { "zh": "Characteristic UUID", "en": "Characteristic UUID" }, "type": "string", "required": true },
                { "name": "text", "description": { "zh": "UTF-8 文本", "en": "UTF-8 text" }, "type": "string", "required": false },
                { "name": "data_base64", "description": { "zh": "base64 字节", "en": "Base64 bytes" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "bluetooth_ble_write_and_read_characteristic",
            "description": { "zh": "写入 BLE characteristic 后读取另一个 characteristic 响应。", "en": "Write a BLE characteristic and read another characteristic response." },
            "parameters": [
                { "name": "session_id", "description": { "zh": "BLE 会话 ID", "en": "BLE session ID" }, "type": "string", "required": true },
                { "name": "write_service_uuid", "description": { "zh": "写入 Service UUID", "en": "Write service UUID" }, "type": "string", "required": true },
                { "name": "write_characteristic_uuid", "description": { "zh": "写入 Characteristic UUID", "en": "Write characteristic UUID" }, "type": "string", "required": true },
                { "name": "read_service_uuid", "description": { "zh": "读取 Service UUID", "en": "Read service UUID" }, "type": "string", "required": true },
                { "name": "read_characteristic_uuid", "description": { "zh": "读取 Characteristic UUID", "en": "Read characteristic UUID" }, "type": "string", "required": true },
                { "name": "text", "description": { "zh": "UTF-8 文本", "en": "UTF-8 text" }, "type": "string", "required": false },
                { "name": "data_base64", "description": { "zh": "base64 字节", "en": "Base64 bytes" }, "type": "string", "required": false },
                { "name": "timeout_ms", "description": { "zh": "等待毫秒数。", "en": "Wait time in milliseconds." }, "type": "number", "required": false }
            ]
        },
        {
            "name": "bluetooth_ble_subscribe_characteristic",
            "description": { "zh": "订阅或取消订阅 BLE characteristic 通知。", "en": "Subscribe or unsubscribe BLE characteristic notifications." },
            "parameters": [
                { "name": "session_id", "description": { "zh": "BLE 会话 ID", "en": "BLE session ID" }, "type": "string", "required": true },
                { "name": "service_uuid", "description": { "zh": "Service UUID", "en": "Service UUID" }, "type": "string", "required": true },
                { "name": "characteristic_uuid", "description": { "zh": "Characteristic UUID", "en": "Characteristic UUID" }, "type": "string", "required": true },
                { "name": "enable", "description": { "zh": "是否订阅。", "en": "Whether to subscribe." }, "type": "boolean", "required": false }
            ]
        },
        {
            "name": "bluetooth_ble_read_notifications",
            "description": { "zh": "读取已收到的 BLE 通知。", "en": "Read received BLE notifications." },
            "parameters": [
                { "name": "session_id", "description": { "zh": "BLE 会话 ID", "en": "BLE session ID" }, "type": "string", "required": true },
                { "name": "limit", "description": { "zh": "读取条数。", "en": "Number of notifications to read." }, "type": "number", "required": false }
            ]
        },
        {
            "name": "get_device_info",
            "description": { "zh": "获取详细的设备信息，包括型号、操作系统版本、内存、存储、网络状态等。", "en": "Get detailed device information, including model, OS version, memory, storage, network status, etc." },
            "parameters": []
        }
    ]
}*/

const SystemTools = (function () {

    interface ToolResponse {
        success: boolean;
        message: string;
        data?: any;
    }

    type BluetoothScanParams = { duration_ms?: number | string, include_ble?: boolean };
    type BluetoothConnectParams = { address: string, uuid?: string };
    type BluetoothListenParams = { name?: string, uuid?: string };
    type BluetoothAcceptParams = { listener_session_id: string, timeout_ms?: number | string };
    type BluetoothPayloadParams = { session_id: string, text?: string, data_base64?: string };
    type BluetoothReadParams = { session_id: string, max_bytes?: number | string, timeout_ms?: number | string };
    type BluetoothSendAndReadParams = BluetoothPayloadParams & { max_bytes?: number | string, timeout_ms?: number | string };
    type BluetoothCloseParams = { session_id: string };
    type BluetoothBleConnectParams = { address: string, auto_connect?: boolean };
    type BluetoothBleDiscoverParams = { session_id: string, timeout_ms?: number | string };
    type BluetoothBleCharacteristicParams = { session_id: string, service_uuid: string, characteristic_uuid: string, timeout_ms?: number | string };
    type BluetoothBleWriteParams = { session_id: string, service_uuid: string, characteristic_uuid: string, text?: string, data_base64?: string };
    type BluetoothBleWriteAndReadParams = {
        session_id: string,
        write_service_uuid: string,
        write_characteristic_uuid: string,
        read_service_uuid: string,
        read_characteristic_uuid: string,
        text?: string,
        data_base64?: string,
        timeout_ms?: number | string
    };
    type BluetoothBleSubscribeParams = { session_id: string, service_uuid: string, characteristic_uuid: string, enable?: boolean };
    type BluetoothBleReadNotificationsParams = { session_id: string, limit?: number | string };

    async function get_system_setting(params: { setting: string, namespace?: string }): Promise<ToolResponse> {
        const result = await Tools.System.getSetting(params.setting, params.namespace || 'system');
        return { success: true, message: '成功获取系统设置', data: result };
    }

    async function modify_system_setting(params: { setting: string, value: string, namespace?: string }): Promise<ToolResponse> {
        const result = await Tools.System.setSetting(params.setting, params.value, params.namespace || 'system');
        const success = result && result.value === params.value;
        return { success: success, message: success ? '成功修改系统设置' : '修改系统设置失败', data: result };
    }

    async function install_app(params: { path: string }): Promise<ToolResponse> {
        const result = await Tools.System.installApp(params.path);
        return { success: result.success, message: result.success ? '应用安装成功' : '应用安装失败', data: result };
    }

    async function uninstall_app(params: { package_name: string, keep_data?: boolean }): Promise<ToolResponse> {
        const result = await Tools.System.uninstallApp(params.package_name);
        return { success: result.success, message: result.success ? '应用卸载成功' : '应用卸载失败', data: result };
    }

    async function list_installed_apps(params: { include_system_apps?: boolean }): Promise<ToolResponse> {
        const result = await Tools.System.listApps(params.include_system_apps || false);
        return { success: true, message: '成功获取应用列表', data: result };
    }

    async function start_app(params: { package_name: string, activity?: string }): Promise<ToolResponse> {
        const result = await Tools.System.startApp(params.package_name, params.activity);
        return { success: result.success, message: result.success ? '应用启动成功' : '应用启动失败', data: result };
    }

    async function stop_app(params: { package_name: string }): Promise<ToolResponse> {
        const result = await Tools.System.stopApp(params.package_name);
        return { success: result.success, message: result.success ? '应用停止成功' : '应用停止失败', data: result };
    }

    async function send_broadcast(params: { action: string, package_name?: string, component?: string, uri?: string, extras?: any, extra_key?: string, extra_value?: string, extra_key2?: string, extra_value2?: string }): Promise<ToolResponse> {
        const result = await Tools.System.sendBroadcast({
            action: params.action,
            uri: params.uri,
            package: params.package_name,
            component: params.component,
            extras: params.extras,
            extra_key: params.extra_key,
            extra_value: params.extra_value,
            extra_key2: params.extra_key2,
            extra_value2: params.extra_value2
        });
        return { success: true, message: '广播发送成功', data: result };
    }

    async function execute_intent(params: { type?: 'activity' | 'broadcast' | 'service' | string, action?: string, uri?: string, package_name?: string, component?: string, flags?: number | number[] | string, extras?: any }): Promise<ToolResponse> {
        const flags = Array.isArray(params.flags) ? JSON.stringify(params.flags) : params.flags;
        const result = await Tools.System.intent({
            action: params.action,
            uri: params.uri,
            package: params.package_name,
            component: params.component,
            flags: flags as any,
            extras: params.extras,
            type: (params.type as any) || 'activity'
        });
        return { success: true, message: 'Intent 执行成功', data: result };
    }

    async function get_notifications(params: { limit?: number, include_ongoing?: boolean }): Promise<ToolResponse> {
        const result = await Tools.System.getNotifications(params.limit || 10, params.include_ongoing || false);
        return { success: true, message: '成功获取通知', data: result };
    }

    async function get_app_usage_time(params: { package_name?: string, since_hours?: number, limit?: number, include_system_apps?: boolean }): Promise<ToolResponse> {
        const result = await Tools.System.getAppUsageTime({
            packageName: params.package_name,
            sinceHours: params.since_hours || 24,
            limit: params.limit || 10,
            includeSystemApps: params.include_system_apps || false
        });
        return { success: true, message: '成功获取应用使用时长', data: result };
    }

    async function get_device_location(params: { high_accuracy?: boolean, timeout?: number, include_address?: boolean }): Promise<ToolResponse> {
        const result = await Tools.System.getLocation(params.high_accuracy || false, params.timeout || 10, params.include_address);
        return { success: true, message: '成功获取位置信息', data: result };
    }

    async function request_bluetooth_permission(params: Record<string, never>): Promise<ToolResponse> {
        const result = await Tools.System.bluetooth.requestPermission();
        return { success: true, message: '成功请求蓝牙权限', data: result };
    }

    async function get_bluetooth_state(params: Record<string, never>): Promise<ToolResponse> {
        const result = await Tools.System.bluetooth.getState();
        return { success: true, message: '成功获取蓝牙状态', data: result };
    }

    async function request_enable_bluetooth(params: Record<string, never>): Promise<ToolResponse> {
        const result = await Tools.System.bluetooth.requestEnable();
        return { success: true, message: '已打开蓝牙开启请求', data: result };
    }

    async function list_bluetooth_bonded_devices(params: Record<string, never>): Promise<ToolResponse> {
        const result = await Tools.System.bluetooth.listBondedDevices();
        return { success: true, message: '成功获取已配对蓝牙设备', data: result };
    }

    async function scan_bluetooth_devices(params: BluetoothScanParams): Promise<ToolResponse> {
        const result = await Tools.System.bluetooth.scan({
            durationMs: params.duration_ms,
            includeBle: params.include_ble
        });
        return { success: true, message: '成功扫描蓝牙设备', data: result };
    }

    async function bluetooth_connect(params: BluetoothConnectParams): Promise<ToolResponse> {
        const result = await Tools.System.bluetooth.connect({
            address: params.address,
            uuid: params.uuid
        });
        return { success: true, message: '成功连接蓝牙设备', data: result };
    }

    async function bluetooth_listen(params: BluetoothListenParams): Promise<ToolResponse> {
        const result = await Tools.System.bluetooth.listen({
            name: params.name,
            uuid: params.uuid
        });
        return { success: true, message: '成功创建蓝牙监听', data: result };
    }

    async function bluetooth_accept(params: BluetoothAcceptParams): Promise<ToolResponse> {
        const result = await Tools.System.bluetooth.accept(params.listener_session_id, params.timeout_ms);
        return { success: true, message: '成功接受蓝牙连接', data: result };
    }

    async function bluetooth_send(params: BluetoothPayloadParams): Promise<ToolResponse> {
        const result = await Tools.System.bluetooth.send(params.session_id, {
            text: params.text,
            dataBase64: params.data_base64
        });
        return { success: true, message: '成功发送蓝牙数据', data: result };
    }

    async function bluetooth_read(params: BluetoothReadParams): Promise<ToolResponse> {
        const result = await Tools.System.bluetooth.read(params.session_id, {
            maxBytes: params.max_bytes,
            timeoutMs: params.timeout_ms
        });
        return { success: true, message: '成功读取蓝牙数据', data: result };
    }

    async function bluetooth_send_and_read(params: BluetoothSendAndReadParams): Promise<ToolResponse> {
        const result = await Tools.System.bluetooth.sendAndRead(params.session_id, {
            text: params.text,
            dataBase64: params.data_base64,
            maxBytes: params.max_bytes,
            timeoutMs: params.timeout_ms
        });
        return { success: true, message: '成功发送并读取蓝牙数据', data: result };
    }

    async function bluetooth_close(params: BluetoothCloseParams): Promise<ToolResponse> {
        const result = await Tools.System.bluetooth.close(params.session_id);
        return { success: true, message: '成功关闭蓝牙会话', data: result };
    }

    async function bluetooth_ble_connect(params: BluetoothBleConnectParams): Promise<ToolResponse> {
        const result = await Tools.System.bluetooth.ble.connect({
            address: params.address,
            autoConnect: params.auto_connect
        });
        return { success: true, message: '成功连接 BLE 设备', data: result };
    }

    async function bluetooth_ble_discover_services(params: BluetoothBleDiscoverParams): Promise<ToolResponse> {
        const result = await Tools.System.bluetooth.ble.discoverServices(params.session_id, params.timeout_ms);
        return { success: true, message: '成功发现 BLE 服务', data: result };
    }

    async function bluetooth_ble_read_characteristic(params: BluetoothBleCharacteristicParams): Promise<ToolResponse> {
        const result = await Tools.System.bluetooth.ble.readCharacteristic(params.session_id, {
            serviceUuid: params.service_uuid,
            characteristicUuid: params.characteristic_uuid,
            timeoutMs: params.timeout_ms
        });
        return { success: true, message: '成功读取 BLE characteristic', data: result };
    }

    async function bluetooth_ble_write_characteristic(params: BluetoothBleWriteParams): Promise<ToolResponse> {
        const result = await Tools.System.bluetooth.ble.writeCharacteristic(params.session_id, {
            serviceUuid: params.service_uuid,
            characteristicUuid: params.characteristic_uuid,
            text: params.text,
            dataBase64: params.data_base64
        });
        return { success: true, message: '成功写入 BLE characteristic', data: result };
    }

    async function bluetooth_ble_write_and_read_characteristic(params: BluetoothBleWriteAndReadParams): Promise<ToolResponse> {
        const result = await Tools.System.bluetooth.ble.writeAndReadCharacteristic(params.session_id, {
            writeServiceUuid: params.write_service_uuid,
            writeCharacteristicUuid: params.write_characteristic_uuid,
            readServiceUuid: params.read_service_uuid,
            readCharacteristicUuid: params.read_characteristic_uuid,
            text: params.text,
            dataBase64: params.data_base64,
            timeoutMs: params.timeout_ms
        });
        return { success: true, message: '成功写入并读取 BLE characteristic', data: result };
    }

    async function bluetooth_ble_subscribe_characteristic(params: BluetoothBleSubscribeParams): Promise<ToolResponse> {
        const result = await Tools.System.bluetooth.ble.subscribe(params.session_id, {
            serviceUuid: params.service_uuid,
            characteristicUuid: params.characteristic_uuid,
            enable: params.enable
        });
        return { success: true, message: '成功更新 BLE 订阅', data: result };
    }

    async function bluetooth_ble_read_notifications(params: BluetoothBleReadNotificationsParams): Promise<ToolResponse> {
        const result = await Tools.System.bluetooth.ble.readNotifications(params.session_id, params.limit);
        return { success: true, message: '成功读取 BLE 通知', data: result };
    }

    async function get_device_info(params: {}): Promise<ToolResponse> {
        const result = await Tools.System.getDeviceInfo();
        return { success: true, message: '成功获取设备信息', data: result };
    }

    async function wrapToolExecution<P>(func: (params: P) => Promise<ToolResponse>, params: P) {
        try {
            const result = await func(params);
            complete(result);
        } catch (error: any) {
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({
                success: false,
                message: `工具执行时发生意外错误: ${error.message}`,
            });
        }
    }

    async function main() {
        console.log("=== System Tools 全面测试开始 ===\n");
        const results: any[] = [];

        try {
            // 1. 测试 get_device_info（新增工具）
            console.log("1. 测试 get_device_info...");
            try {
                const deviceInfoResult = await get_device_info({});
                results.push({ tool: 'get_device_info', result: deviceInfoResult });
                console.log("✓ get_device_info 测试完成");
                if (deviceInfoResult.data) {
                    console.log(`  设备信息: ${JSON.stringify(deviceInfoResult.data).substring(0, 100)}...`);
                }
                console.log();
            } catch (error: any) {
                console.log("⚠ get_device_info 测试失败:", error.message, "\n");
                results.push({ tool: 'get_device_info', result: { success: false, message: error.message } });
            }

            // 2. 测试 get_notifications
            console.log("2. 测试 get_notifications...");
            try {
                const notificationsResult = await get_notifications({ limit: 5, include_ongoing: false });
                results.push({ tool: 'get_notifications', result: notificationsResult });
                console.log("✓ get_notifications 测试完成");
                if (notificationsResult.data) {
                    console.log(`  获取到 ${notificationsResult.data.length || 0} 条通知`);
                }
                console.log();
            } catch (error: any) {
                console.log("⚠ get_notifications 测试失败:", error.message, "\n");
                results.push({ tool: 'get_notifications', result: { success: false, message: error.message } });
            }

            // 3. 测试 get_app_usage_time
            console.log("3. 测试 get_app_usage_time...");
            try {
                const usageResult = await get_app_usage_time({ since_hours: 24, limit: 5, include_system_apps: false });
                results.push({ tool: 'get_app_usage_time', result: usageResult });
                console.log("✓ get_app_usage_time 测试完成");
                if (usageResult.data) {
                    console.log(`  返回条目数: ${usageResult.data.entries ? usageResult.data.entries.length : 0}`);
                }
                console.log();
            } catch (error: any) {
                console.log("⚠ get_app_usage_time 测试失败（可能需要使用情况访问权限）:", error.message, "\n");
                results.push({ tool: 'get_app_usage_time', result: { success: false, message: error.message } });
            }

            // 4. 测试 get_device_location
            console.log("4. 测试 get_device_location...");
            try {
                const locationResult = await get_device_location({ high_accuracy: false, timeout: 5 });
                results.push({ tool: 'get_device_location', result: locationResult });
                console.log("✓ get_device_location 测试完成");
                if (locationResult.data) {
                    console.log(`  位置: ${JSON.stringify(locationResult.data)}`);
                }
                console.log();
            } catch (error: any) {
                console.log("⚠ get_device_location 测试失败（可能需要位置权限）:", error.message, "\n");
                results.push({ tool: 'get_device_location', result: { success: false, message: error.message } });
            }

            // 5. 测试 list_installed_apps
            console.log("5. 测试 list_installed_apps...");
            try {
                const appsResult = await list_installed_apps({ include_system_apps: false });
                results.push({ tool: 'list_installed_apps', result: appsResult });
                console.log("✓ list_installed_apps 测试完成");
                if (appsResult.data) {
                    console.log(`  已安装应用数量: ${appsResult.data.length || 0}`);
                }
                console.log();
            } catch (error: any) {
                console.log("⚠ list_installed_apps 测试失败（可能需要用户授权）:", error.message, "\n");
                results.push({ tool: 'list_installed_apps', result: { success: false, message: error.message } });
            }

            // 6. 测试 get_system_setting（读取一个常见的系统设置）
            console.log("6. 测试 get_system_setting...");
            try {
                const settingResult = await get_system_setting({ setting: 'screen_brightness', namespace: 'system' });
                results.push({ tool: 'get_system_setting', result: settingResult });
                console.log("✓ get_system_setting 测试完成");
                if (settingResult.data) {
                    console.log(`  screen_brightness = ${settingResult.data.value || settingResult.data}`);
                }
                console.log();
            } catch (error: any) {
                console.log("⚠ get_system_setting 测试失败:", error.message, "\n");
                results.push({ tool: 'get_system_setting', result: { success: false, message: error.message } });
            }

            // 7-11. 其他需要用户授权或可能造成系统变更的工具，仅做说明不实际执行
            console.log("7-11. 跳过破坏性/需要特殊权限的工具测试:");
            console.log("  ⊘ modify_system_setting - 需要WRITE_SETTINGS权限，会修改系统设置");
            console.log("  ⊘ install_app - 需要INSTALL_PACKAGES权限");
            console.log("  ⊘ uninstall_app - 需要DELETE_PACKAGES权限");
            console.log("  ⊘ start_app - 需要应用包名，可能会启动应用");
            console.log("  ⊘ stop_app - 需要KILL_BACKGROUND_PROCESSES权限\n");

            results.push({ tool: 'modify_system_setting', result: { success: null, message: '未测试（避免修改系统）' } });
            results.push({ tool: 'install_app', result: { success: null, message: '未测试（需要APK文件）' } });
            results.push({ tool: 'uninstall_app', result: { success: null, message: '未测试（避免卸载应用）' } });
            results.push({ tool: 'start_app', result: { success: null, message: '未测试（避免启动应用）' } });
            results.push({ tool: 'stop_app', result: { success: null, message: '未测试（避免停止应用）' } });

            console.log("=== System Tools 测试完成 ===\n");
            console.log("测试结果汇总:");
            results.forEach((r, i) => {
                const status = r.result.success === true ? '✓' : r.result.success === false ? '✗' : '⊘';
                console.log(`${i + 1}. ${status} ${r.tool}: ${r.result.message}`);
            });

            const successCount = results.filter(r => r.result.success === true).length;
            const failCount = results.filter(r => r.result.success === false).length;
            const skipCount = results.filter(r => r.result.success === null).length;

            console.log(`\n总计: ${successCount} 成功, ${failCount} 失败, ${skipCount} 跳过`);

            complete({
                success: true,
                message: "系统工具全面测试完成",
                data: {
                    results,
                    summary: {
                        total: results.length,
                        success: successCount,
                        failed: failCount,
                        skipped: skipCount
                    }
                }
            });
        } catch (error: any) {
            console.error("测试过程中发生错误:", error);
            complete({
                success: false,
                message: `测试失败: ${error.message}`,
                data: results
            });
        }
    }

    return {
        get_system_setting: (params: { setting: string, namespace?: string }) => wrapToolExecution(get_system_setting, params),
        modify_system_setting: (params: { setting: string, value: string, namespace?: string }) => wrapToolExecution(modify_system_setting, params),
        install_app: (params: { path: string }) => wrapToolExecution(install_app, params),
        uninstall_app: (params: { package_name: string, keep_data?: boolean }) => wrapToolExecution(uninstall_app, params),
        list_installed_apps: (params: { include_system_apps?: boolean }) => wrapToolExecution(list_installed_apps, params),
        start_app: (params: { package_name: string, activity?: string }) => wrapToolExecution(start_app, params),
        stop_app: (params: { package_name: string }) => wrapToolExecution(stop_app, params),
        send_broadcast: (params: { action: string, package_name?: string, component?: string, uri?: string, extras?: any }) => wrapToolExecution(send_broadcast, params),
        execute_intent: (params: { type?: 'activity' | 'broadcast' | 'service' | string, action?: string, uri?: string, package_name?: string, component?: string, flags?: number | number[] | string, extras?: any }) => wrapToolExecution(execute_intent, params),
        get_notifications: (params: { limit?: number, include_ongoing?: boolean }) => wrapToolExecution(get_notifications, params),
        get_app_usage_time: (params: { package_name?: string, since_hours?: number, limit?: number, include_system_apps?: boolean }) => wrapToolExecution(get_app_usage_time, params),
        get_device_location: (params: { high_accuracy?: boolean, timeout?: number }) => wrapToolExecution(get_device_location, params),
        request_bluetooth_permission: (params: Record<string, never>) => wrapToolExecution(request_bluetooth_permission, params),
        get_bluetooth_state: (params: Record<string, never>) => wrapToolExecution(get_bluetooth_state, params),
        request_enable_bluetooth: (params: Record<string, never>) => wrapToolExecution(request_enable_bluetooth, params),
        list_bluetooth_bonded_devices: (params: Record<string, never>) => wrapToolExecution(list_bluetooth_bonded_devices, params),
        scan_bluetooth_devices: (params: BluetoothScanParams) => wrapToolExecution(scan_bluetooth_devices, params),
        bluetooth_connect: (params: BluetoothConnectParams) => wrapToolExecution(bluetooth_connect, params),
        bluetooth_listen: (params: BluetoothListenParams) => wrapToolExecution(bluetooth_listen, params),
        bluetooth_accept: (params: BluetoothAcceptParams) => wrapToolExecution(bluetooth_accept, params),
        bluetooth_send: (params: BluetoothPayloadParams) => wrapToolExecution(bluetooth_send, params),
        bluetooth_read: (params: BluetoothReadParams) => wrapToolExecution(bluetooth_read, params),
        bluetooth_send_and_read: (params: BluetoothSendAndReadParams) => wrapToolExecution(bluetooth_send_and_read, params),
        bluetooth_close: (params: BluetoothCloseParams) => wrapToolExecution(bluetooth_close, params),
        bluetooth_ble_connect: (params: BluetoothBleConnectParams) => wrapToolExecution(bluetooth_ble_connect, params),
        bluetooth_ble_discover_services: (params: BluetoothBleDiscoverParams) => wrapToolExecution(bluetooth_ble_discover_services, params),
        bluetooth_ble_read_characteristic: (params: BluetoothBleCharacteristicParams) => wrapToolExecution(bluetooth_ble_read_characteristic, params),
        bluetooth_ble_write_characteristic: (params: BluetoothBleWriteParams) => wrapToolExecution(bluetooth_ble_write_characteristic, params),
        bluetooth_ble_write_and_read_characteristic: (params: BluetoothBleWriteAndReadParams) => wrapToolExecution(bluetooth_ble_write_and_read_characteristic, params),
        bluetooth_ble_subscribe_characteristic: (params: BluetoothBleSubscribeParams) => wrapToolExecution(bluetooth_ble_subscribe_characteristic, params),
        bluetooth_ble_read_notifications: (params: BluetoothBleReadNotificationsParams) => wrapToolExecution(bluetooth_ble_read_notifications, params),
        get_device_info: (params: {}) => wrapToolExecution(get_device_info, params),
        main,
    };
})();

exports.get_system_setting = SystemTools.get_system_setting;
exports.modify_system_setting = SystemTools.modify_system_setting;
exports.install_app = SystemTools.install_app;
exports.uninstall_app = SystemTools.uninstall_app;
exports.list_installed_apps = SystemTools.list_installed_apps;
exports.start_app = SystemTools.start_app;
exports.stop_app = SystemTools.stop_app;
exports.send_broadcast = SystemTools.send_broadcast;
exports.execute_intent = SystemTools.execute_intent;
exports.get_notifications = SystemTools.get_notifications;
exports.get_app_usage_time = SystemTools.get_app_usage_time;
exports.get_device_location = SystemTools.get_device_location;
exports.request_bluetooth_permission = SystemTools.request_bluetooth_permission;
exports.get_bluetooth_state = SystemTools.get_bluetooth_state;
exports.request_enable_bluetooth = SystemTools.request_enable_bluetooth;
exports.list_bluetooth_bonded_devices = SystemTools.list_bluetooth_bonded_devices;
exports.scan_bluetooth_devices = SystemTools.scan_bluetooth_devices;
exports.bluetooth_connect = SystemTools.bluetooth_connect;
exports.bluetooth_listen = SystemTools.bluetooth_listen;
exports.bluetooth_accept = SystemTools.bluetooth_accept;
exports.bluetooth_send = SystemTools.bluetooth_send;
exports.bluetooth_read = SystemTools.bluetooth_read;
exports.bluetooth_send_and_read = SystemTools.bluetooth_send_and_read;
exports.bluetooth_close = SystemTools.bluetooth_close;
exports.bluetooth_ble_connect = SystemTools.bluetooth_ble_connect;
exports.bluetooth_ble_discover_services = SystemTools.bluetooth_ble_discover_services;
exports.bluetooth_ble_read_characteristic = SystemTools.bluetooth_ble_read_characteristic;
exports.bluetooth_ble_write_characteristic = SystemTools.bluetooth_ble_write_characteristic;
exports.bluetooth_ble_write_and_read_characteristic = SystemTools.bluetooth_ble_write_and_read_characteristic;
exports.bluetooth_ble_subscribe_characteristic = SystemTools.bluetooth_ble_subscribe_characteristic;
exports.bluetooth_ble_read_notifications = SystemTools.bluetooth_ble_read_notifications;
exports.get_device_info = SystemTools.get_device_info;
exports.main = SystemTools.main;
