package com.ai.assistance.operit.core.tools.javascript

/** JavaScript Tools 对象定义 提供了一组便捷的工具调用方法，用于在JavaScript脚本中调用Android工具 */
fun getJsToolsDefinition(): String {
    return """
        // 工具调用的便捷方法
        var Tools = {
            // 文件系统操作
            Files: {
                list: (path, environment) => {
                    const params = { path };
                    if (environment) params.environment = environment;
                    return toolCall("list_files", params);
                },
                read: (pathOrOptions) => {
                    let params;
                    if (typeof pathOrOptions === 'string') {
                        // Simple form: read(path)
                        params = { path: pathOrOptions };
                    } else if (pathOrOptions && typeof pathOrOptions === 'object') {
                        // Options form: read({ path, environment?, intent? })
                        params = { ...pathOrOptions };
                    } else {
                        params = {};
                    }
                    return toolCall("read_file_full", params);
                },
                readBinary: (path, environment) => {
                    const params = { path };
                    if (environment) params.environment = environment;
                    return toolCall("read_file_binary", params);
                },
                readPart: (path, startLine, endLine, environment) => {
                    const params = { path };
                    if (startLine !== undefined) params.start_line = String(startLine);
                    if (endLine !== undefined) params.end_line = String(endLine);
                    if (environment) params.environment = environment;
                    return toolCall("read_file_part", params);
                },
                write: (path, content, append, environment) => {
                    const params = { path, content };
                    if (append !== undefined) params.append = append ? "true" : "false";
                    if (environment) params.environment = environment;
                    return toolCall("write_file", params);
                },
                writeBinary: (path, base64Content, environment) => {
                    const params = { path, base64Content };
                    if (environment) params.environment = environment;
                    return toolCall("write_file_binary", params);
                },
                deleteFile: (path, recursive, environment) => {
                    const params = { path };
                    if (recursive !== undefined) params.recursive = recursive ? "true" : "false";
                    if (environment) params.environment = environment;
                    return toolCall("delete_file", params);
                },
                exists: (path, environment) => {
                    const params = { path };
                    if (environment) params.environment = environment;
                    return toolCall("file_exists", params);
                },
                move: (source, destination, environment) => {
                    const params = { source, destination };
                    if (environment) params.environment = environment;
                    return toolCall("move_file", params);
                },
                copy: (source, destination, recursive, sourceEnvironment, destEnvironment) => {
                    const params = { source, destination };
                    if (recursive !== undefined) params.recursive = recursive ? "true" : "false";
                    if (sourceEnvironment) params.source_environment = sourceEnvironment;
                    if (destEnvironment) params.dest_environment = destEnvironment;
                    return toolCall("copy_file", params);
                },
                mkdir: (path, create_parents, environment) => {
                    const params = { path };
                    if (create_parents !== undefined) params.create_parents = create_parents ? "true" : "false";
                    if (environment) params.environment = environment;
                    return toolCall("make_directory", params);
                },
                find: (path, pattern, options = {}, environment) => {
                    const params = { path, pattern, ...options };
                    if (environment) params.environment = environment;
                    return toolCall("find_files", params);
                },
                grep: (path, pattern, options = {}) => {
                    const params = { path, pattern, ...options };
                    // environment can be included in options
                    return toolCall("grep_code", params);
                },
                grepContext: (path, intent, options = {}) => {
                    const params = { path, intent, ...options };
                    // environment and file_pattern can be included in options
                    return toolCall("grep_context", params);
                },
                info: (path, environment) => {
                    const params = { path };
                    if (environment) params.environment = environment;
                    return toolCall("file_info", params);
                },
                // 智能应用文件绑定
                apply: (path, type, oldContent, newContent, environment) => {
                    const params = { path, type };
                    if (oldContent !== undefined && oldContent !== null) params.old = String(oldContent);
                    if (newContent !== undefined && newContent !== null) params.new = String(newContent);
                    if (environment) params.environment = environment;
                    return toolCall("apply_file", params);
                },
                create: (path, newContent, environment) => {
                    const params = { path, new: newContent };
                    if (environment) params.environment = environment;
                    return toolCall("create_file", params);
                },
                edit: (path, oldContent, newContent, environment) => {
                    const params = { path, old: oldContent, new: newContent };
                    if (environment) params.environment = environment;
                    return toolCall("edit_file", params);
                },
                zip: (source, destination, environment, include_root_directory) => {
                    const params = { source, destination };
                    if (environment) params.environment = environment;
                    if (include_root_directory !== undefined) {
                        params.include_root_directory = include_root_directory ? "true" : "false";
                    }
                    return toolCall("zip_files", params);
                },
                unzip: (source, destination, environment) => {
                    const params = { source, destination };
                    if (environment) params.environment = environment;
                    return toolCall("unzip_files", params);
                },
                open: (path, environment) => {
                    const params = { path };
                    if (environment) params.environment = environment;
                    return toolCall("open_file", params);
                },
                share: (path, title, environment) => {
                    const params = { path };
                    if (title) params.title = title;
                    if (environment) params.environment = environment;
                    return toolCall("share_file", params);
                },
                download: (urlOrOptions, destination, environment, headers) => {
                    let params;
                    if (typeof urlOrOptions === 'string') {
                        params = { url: urlOrOptions };
                        if (destination !== undefined && destination !== null) params.destination = destination;
                        if (environment) params.environment = environment;
                        if (headers !== undefined && headers !== null && typeof headers === 'object') {
                            params.headers = JSON.stringify(headers);
                        }
                    } else if (urlOrOptions && typeof urlOrOptions === 'object') {
                        params = { ...urlOrOptions };
                        if (destination !== undefined && destination !== null) params.destination = destination;
                        if (environment) params.environment = environment;
                        if (headers !== undefined && headers !== null && typeof headers === 'object') {
                            params.headers = JSON.stringify(headers);
                        }
                        if (params.headers !== undefined && params.headers !== null && typeof params.headers === 'object') {
                            params.headers = JSON.stringify(params.headers);
                        }
                    } else {
                        params = {};
                    }
                    return toolCall("download_file", params);
                },
            },
            // 网络操作
            Net: {
                httpGet: (url, ignoreSsl) => {
                    const params = { url, method: "GET" };
                    if (ignoreSsl !== undefined) params.ignore_ssl = ignoreSsl ? "true" : "false";
                    return toolCall("http_request", params);
                },
                httpPost: (url, body, ignoreSsl) => {
                    const params = { url, method: "POST", body };
                    if (typeof body === 'object') {
                        params.body = JSON.stringify(body);
                    }
                    if (ignoreSsl !== undefined) params.ignore_ssl = ignoreSsl ? "true" : "false";
                    return toolCall("http_request", params);
                },
                visit: (params) => {
                    if (typeof params === 'string') {
                        // 向后兼容，如果只传入一个字符串，则假定为URL
                        return toolCall("visit_web", { url: params });
                    }
                    // 否则，假定为参数对象
                    if (params && typeof params === 'object' && params.headers !== undefined && typeof params.headers === 'object') {
                        params = { ...params, headers: JSON.stringify(params.headers) };
                    }
                    return toolCall("visit_web", params);
                },
                browserNavigate: (urlOrOptions) => {
                    const params = typeof urlOrOptions === 'string' ? { url: urlOrOptions } : { ...(urlOrOptions || {}) };
                    if (!params.url) {
                        throw new Error("browserNavigate requires url");
                    }
                    if (params.headers !== undefined && typeof params.headers === 'object') {
                        params.headers = JSON.stringify(params.headers);
                    }
                    return toolCall("browser_navigate", params);
                },
                browserNavigateBack: (options) => {
                    if (options !== undefined && (typeof options !== 'object' || Array.isArray(options))) {
                        throw new Error("browserNavigateBack only accepts one options object");
                    }
                    return toolCall("browser_navigate_back", options || {});
                },
                browserClick: (options) => {
                    if (!options || typeof options !== 'object' || Array.isArray(options)) {
                        throw new Error("browserClick only accepts one options object");
                    }
                    const params = { ...options };
                    if (params.ref !== undefined && params.ref !== null) {
                        params.ref = String(params.ref).trim();
                    }
                    if (params.selector !== undefined && params.selector !== null) {
                        params.selector = String(params.selector).trim();
                        if (!params.selector) {
                            delete params.selector;
                        }
                    }
                    if (!params.ref && !params.selector) {
                        throw new Error("browserClick requires ref or selector");
                    }
                    if (params.element !== undefined && params.element !== null) {
                        params.element = String(params.element).trim();
                        if (!params.element) {
                            delete params.element;
                        }
                    }
                    if (params.button !== undefined && params.button !== null) {
                        const button = String(params.button).trim();
                        if (button !== 'left' && button !== 'right' && button !== 'middle') {
                            throw new Error("button must be one of: left, right, middle");
                        }
                        params.button = button;
                    }
                    if (params.modifiers !== undefined) {
                        if (!Array.isArray(params.modifiers)) {
                            throw new Error("modifiers must be an array");
                        }
                        const allowedModifiers = ['Alt', 'Control', 'ControlOrMeta', 'Meta', 'Shift'];
                        const normalized = params.modifiers.map((modifier) => String(modifier).trim());
                        const invalid = normalized.filter((modifier) => !allowedModifiers.includes(modifier));
                        if (invalid.length > 0) {
                            throw new Error("Invalid modifiers: " + invalid.join(', '));
                        }
                        params.modifiers = normalized;
                    }
                    if (params.doubleClick !== undefined) {
                        params.doubleClick = !!params.doubleClick;
                    }
                    return toolCall("browser_click", params);
                },
                browserClose: (options) => {
                    if (options !== undefined && (typeof options !== 'object' || Array.isArray(options))) {
                        throw new Error("browserClose only accepts one options object");
                    }
                    return toolCall("browser_close", options || {});
                },
                browserCloseAll: (options) => {
                    if (options !== undefined && (typeof options !== 'object' || Array.isArray(options))) {
                        throw new Error("browserCloseAll only accepts one options object");
                    }
                    return toolCall("browser_close_all", options || {});
                },
                browserConsoleMessages: (options) => {
                    if (options !== undefined && (typeof options !== 'object' || Array.isArray(options))) {
                        throw new Error("browserConsoleMessages only accepts one options object");
                    }
                    const params = { ...(options || {}) };
                    if (params.level !== undefined && params.level !== null) {
                        params.level = String(params.level).trim();
                    }
                    if (!params.level) {
                        params.level = "info";
                    }
                    if (params.filename !== undefined && params.filename !== null) {
                        params.filename = String(params.filename);
                    }
                    return toolCall("browser_console_messages", params);
                },
                browserDrag: (options) => {
                    if (!options || typeof options !== 'object' || Array.isArray(options)) {
                        throw new Error("browserDrag only accepts one options object");
                    }
                    const params = { ...options };
                    ['startElement', 'startRef', 'endElement', 'endRef'].forEach((key) => {
                        if (params[key] !== undefined && params[key] !== null) {
                            params[key] = String(params[key]).trim();
                        }
                        if (!params[key]) {
                            throw new Error("browserDrag requires " + key);
                        }
                    });
                    return toolCall("browser_drag", params);
                },
                browserEvaluate: (options) => {
                    if (!options || typeof options !== 'object' || Array.isArray(options)) {
                        throw new Error("browserEvaluate only accepts one options object");
                    }
                    const params = { ...options };
                    if (params.function !== undefined && params.function !== null) {
                        params.function = String(params.function);
                    }
                    if (!params.function) {
                        throw new Error("browserEvaluate requires function");
                    }
                    if (params.element !== undefined && params.element !== null) {
                        params.element = String(params.element);
                    }
                    if (params.ref !== undefined && params.ref !== null) {
                        params.ref = String(params.ref).trim();
                    }
                    if (params.element && !params.ref) {
                        throw new Error("ref is required when element is provided");
                    }
                    return toolCall("browser_evaluate", params);
                },
                browserFileUpload: (options) => {
                    if (options !== undefined && (typeof options !== 'object' || Array.isArray(options))) {
                        throw new Error("browserFileUpload only accepts one options object");
                    }
                    const params = { ...(options || {}) };
                    if (params.paths !== undefined) {
                        if (!Array.isArray(params.paths)) {
                            throw new Error("paths must be an array");
                        }
                        params.paths = params.paths.map((path) => String(path));
                    }
                    return toolCall("browser_file_upload", params);
                },
                browserFillForm: (options) => {
                    if (!options || typeof options !== 'object' || Array.isArray(options)) {
                        throw new Error("browserFillForm only accepts one options object");
                    }
                    const params = { ...options };
                    if (!Array.isArray(params.fields) || params.fields.length === 0) {
                        throw new Error("browserFillForm requires a non-empty fields array");
                    }
                    return toolCall("browser_fill_form", params);
                },
                browserHandleDialog: (options) => {
                    if (!options || typeof options !== 'object' || Array.isArray(options)) {
                        throw new Error("browserHandleDialog only accepts one options object");
                    }
                    const params = { ...options };
                    if (typeof params.accept !== 'boolean') {
                        throw new Error("accept must be a boolean");
                    }
                    if (params.promptText !== undefined && params.promptText !== null) {
                        params.promptText = String(params.promptText);
                    }
                    return toolCall("browser_handle_dialog", params);
                },
                browserHover: (options) => {
                    if (!options || typeof options !== 'object' || Array.isArray(options)) {
                        throw new Error("browserHover only accepts one options object");
                    }
                    const params = { ...options };
                    if (params.ref !== undefined && params.ref !== null) {
                        params.ref = String(params.ref).trim();
                    }
                    if (!params.ref) {
                        throw new Error("browserHover requires ref");
                    }
                    if (params.element !== undefined && params.element !== null) {
                        params.element = String(params.element);
                    }
                    return toolCall("browser_hover", params);
                },
                browserNetworkRequests: (options) => {
                    if (options !== undefined && (typeof options !== 'object' || Array.isArray(options))) {
                        throw new Error("browserNetworkRequests only accepts one options object");
                    }
                    const params = { ...(options || {}) };
                    if (params.includeStatic !== undefined) {
                        params.includeStatic = !!params.includeStatic;
                    }
                    if (params.filename !== undefined && params.filename !== null) {
                        params.filename = String(params.filename);
                    }
                    return toolCall("browser_network_requests", params);
                },
                browserPressKey: (keyOrOptions) => {
                    const params = typeof keyOrOptions === 'string' ? { key: keyOrOptions } : { ...(keyOrOptions || {}) };
                    if (params.key !== undefined && params.key !== null) {
                        params.key = String(params.key).trim();
                    }
                    if (!params.key) {
                        throw new Error("browserPressKey requires key");
                    }
                    return toolCall("browser_press_key", params);
                },
                browserResize: (options) => {
                    if (!options || typeof options !== 'object' || Array.isArray(options)) {
                        throw new Error("browserResize only accepts one options object");
                    }
                    const params = { ...options };
                    if (params.width === undefined || params.height === undefined) {
                        throw new Error("browserResize requires width and height");
                    }
                    return toolCall("browser_resize", params);
                },
                browserRunCode: (options) => {
                    if (!options || typeof options !== 'object' || Array.isArray(options)) {
                        throw new Error("browserRunCode only accepts one options object");
                    }
                    const params = { ...options };
                    if (params.code !== undefined && params.code !== null) {
                        params.code = String(params.code);
                    }
                    if (!params.code) {
                        throw new Error("browserRunCode requires code");
                    }
                    return toolCall("browser_run_code", params);
                },
                browserSelectOption: (options) => {
                    if (!options || typeof options !== 'object' || Array.isArray(options)) {
                        throw new Error("browserSelectOption only accepts one options object");
                    }
                    const params = { ...options };
                    if (params.ref !== undefined && params.ref !== null) {
                        params.ref = String(params.ref).trim();
                    }
                    if (!params.ref) {
                        throw new Error("browserSelectOption requires ref");
                    }
                    if (!Array.isArray(params.values) || params.values.length === 0) {
                        throw new Error("browserSelectOption requires a non-empty values array");
                    }
                    params.values = params.values.map((value) => String(value));
                    if (params.element !== undefined && params.element !== null) {
                        params.element = String(params.element);
                    }
                    return toolCall("browser_select_option", params);
                },
                browserSnapshot: (options) => {
                    if (options !== undefined && (typeof options !== 'object' || Array.isArray(options))) {
                        throw new Error("browserSnapshot only accepts one options object");
                    }
                    const params = { ...(options || {}) };
                    if (params.filename !== undefined && params.filename !== null) {
                        params.filename = String(params.filename).trim();
                        if (!params.filename) {
                            delete params.filename;
                        }
                    }
                    if (params.selector !== undefined && params.selector !== null) {
                        params.selector = String(params.selector).trim();
                        if (!params.selector) {
                            delete params.selector;
                        }
                    }
                    if (params.depth !== undefined && params.depth !== null) {
                        const depth = Number(params.depth);
                        if (!Number.isInteger(depth) || depth < 0) {
                            throw new Error("browserSnapshot depth must be a non-negative integer");
                        }
                        params.depth = depth;
                    }
                    return toolCall("browser_snapshot", params);
                },
                browserTabs: (options) => {
                    if (!options || typeof options !== 'object' || Array.isArray(options)) {
                        throw new Error("browserTabs only accepts one options object");
                    }
                    const params = { ...options };
                    if (params.action !== undefined && params.action !== null) {
                        params.action = String(params.action).trim();
                    }
                    if (!params.action) {
                        throw new Error("browserTabs requires action");
                    }
                    return toolCall("browser_tabs", params);
                },
                browserTakeScreenshot: (options) => {
                    if (!options || typeof options !== 'object' || Array.isArray(options)) {
                        throw new Error("browserTakeScreenshot only accepts one options object");
                    }
                    const params = { ...options };
                    if (params.type !== undefined && params.type !== null) {
                        params.type = String(params.type).trim();
                    }
                    if (!params.type) {
                        params.type = "png";
                    }
                    if (params.element !== undefined && params.element !== null) {
                        params.element = String(params.element);
                    }
                    if (params.ref !== undefined && params.ref !== null) {
                        params.ref = String(params.ref).trim();
                    }
                    if (params.ref && !params.element) {
                        throw new Error("element is required when ref is provided");
                    }
                    if (params.element && !params.ref) {
                        throw new Error("ref is required when element is provided");
                    }
                    if (params.fullPage !== undefined) {
                        params.fullPage = !!params.fullPage;
                    }
                    return toolCall("browser_take_screenshot", params);
                },
                browserType: (options) => {
                    if (!options || typeof options !== 'object' || Array.isArray(options)) {
                        throw new Error("browserType only accepts one options object");
                    }
                    const params = { ...options };
                    if (params.ref !== undefined && params.ref !== null) {
                        params.ref = String(params.ref).trim();
                    }
                    if (!params.ref) {
                        throw new Error("browserType requires ref");
                    }
                    if (params.text === undefined || params.text === null) {
                        throw new Error("browserType requires text");
                    }
                    params.text = String(params.text);
                    if (params.element !== undefined && params.element !== null) {
                        params.element = String(params.element);
                    }
                    if (params.submit !== undefined) {
                        params.submit = !!params.submit;
                    }
                    if (params.slowly !== undefined) {
                        params.slowly = !!params.slowly;
                    }
                    return toolCall("browser_type", params);
                },
                browserWaitFor: (options) => {
                    if (!options || typeof options !== 'object' || Array.isArray(options)) {
                        throw new Error("browserWaitFor only accepts one options object");
                    }
                    const params = { ...options };
                    if (
                        params.time === undefined &&
                        params.text === undefined &&
                        params.textGone === undefined
                    ) {
                        throw new Error("browserWaitFor requires one of: time, text, textGone");
                    }
                    if (params.text !== undefined && params.text !== null) {
                        params.text = String(params.text);
                    }
                    if (params.textGone !== undefined && params.textGone !== null) {
                        params.textGone = String(params.textGone);
                    }
                    return toolCall("browser_wait_for", params);
                },
                // 新增增强版HTTP请求
                http: (options) => {
                    const params = { ...options };
                    if (params.body !== undefined && typeof params.body === 'object') {
                        params.body = JSON.stringify(params.body);
                    }
                    if (params.headers !== undefined && typeof params.headers === 'object') {
                        params.headers = JSON.stringify(params.headers);
                    }
                    if (params.ignore_ssl !== undefined && typeof params.ignore_ssl === 'boolean') {
                        params.ignore_ssl = params.ignore_ssl ? "true" : "false";
                    }
                    return toolCall("http_request", params);
                },
                // 新增文件上传
                uploadFile: (options) => {
                    const params = {
                        ...options,
                        files: JSON.stringify(options.files || []),
                        form_data: JSON.stringify(options.form_data || {})
                    };
                    if (options.headers !== undefined && typeof options.headers === 'object') {
                        params.headers = JSON.stringify(options.headers);
                    }
                    if (params.ignore_ssl !== undefined && typeof params.ignore_ssl === 'boolean') {
                        params.ignore_ssl = params.ignore_ssl ? "true" : "false";
                    }
                    return toolCall("multipart_request", params);
                },
                // 新增Cookie管理
                cookies: {
                    get: (domain) => toolCall("manage_cookies", { action: "get", domain }),
                    set: (domain, cookies) => toolCall("manage_cookies", { action: "set", domain, cookies }),
                    clear: (domain) => toolCall("manage_cookies", { action: "clear", domain })
                }
            },
            // 系统操作
            System: {
                sleep: (milliseconds) => toolCall("sleep", { duration_ms: parseInt(milliseconds) }),
                getSetting: (setting, namespace) => toolCall("get_system_setting", { setting, namespace }),
                setSetting: (setting, value, namespace) => toolCall("modify_system_setting", { setting, value, namespace }),
                getDeviceInfo: () => toolCall("device_info"),
                toast: (message) => toolCall("toast", { message: String(message ?? "") }),
                sendNotification: (message, title) => {
                    const params = { message: String(message ?? "") };
                    if (title !== undefined && title !== null && String(title).trim() !== "") {
                        params.title = String(title);
                    }
                    return toolCall("send_notification", params);
                },
                // 使用工具包
                usePackage: (packageName) => toolCall("use_package", { package_name: String(packageName ?? "") }),
                // 安装应用
                installApp: (path) => toolCall("install_app", { path }),
                // 卸载应用
                uninstallApp: (packageName) => toolCall("uninstall_app", { package_name: packageName }),
                startApp: (packageName, activity) => {
                    const params = { package_name: packageName };
                    if (activity) params.activity = activity;
                    return toolCall("start_app", params);
                },
                stopApp: (packageName) => toolCall("stop_app", { package_name: packageName }),
                listApps: (includeSystem) => toolCall("list_installed_apps", { include_system: !!includeSystem }),
                // 获取设备通知
                getNotifications: (limit = 10, includeOngoing = false) => 
                    toolCall("get_notifications", { limit: parseInt(limit), include_ongoing: !!includeOngoing }),
                // 获取应用使用时长
                getAppUsageTime: (options = {}) => {
                    const params = { ...(options || {}) };
                    if (params.packageName !== undefined && params.packageName !== null) {
                        params.package_name = String(params.packageName);
                        delete params.packageName;
                    }
                    if (params.sinceHours !== undefined && params.sinceHours !== null) {
                        params.since_hours = parseInt(params.sinceHours);
                        delete params.sinceHours;
                    }
                    if (params.includeSystemApps !== undefined) {
                        params.include_system_apps = !!params.includeSystemApps;
                        delete params.includeSystemApps;
                    }
                    if (params.limit !== undefined && params.limit !== null) {
                        params.limit = parseInt(params.limit);
                    }
                    return toolCall("get_app_usage_time", params);
                },
                // 获取设备位置
                getLocation: (highAccuracy = false, timeout = 10, includeAddress = true) =>
                    toolCall("get_device_location", {
                        high_accuracy: !!highAccuracy,
                        timeout: parseInt(timeout),
                        include_address: !!includeAddress
                    }),
                bluetooth: {
                    requestPermission: () => toolCall("request_bluetooth_permission", {}),
                    getState: () => toolCall("get_bluetooth_state", {}),
                    requestEnable: () => toolCall("request_enable_bluetooth", {}),
                    listBondedDevices: () => toolCall("list_bluetooth_bonded_devices", {}),
                    scan: (options = {}) => {
                        const params = {};
                        if (options && typeof options === "object") {
                            if (options.durationMs !== undefined && options.durationMs !== null) {
                                params.duration_ms = String(options.durationMs);
                            }
                            if (options.includeBle !== undefined) {
                                params.include_ble = !!options.includeBle;
                            }
                        }
                        return toolCall("scan_bluetooth_devices", params);
                    },
                    connect: (options) => {
                        const params = { address: options.address };
                        if (options.uuid !== undefined && options.uuid !== null) {
                            params.uuid = String(options.uuid);
                        }
                        return toolCall("bluetooth_connect", params);
                    },
                    listen: (options = {}) => {
                        const params = {};
                        if (options && typeof options === "object") {
                            if (options.name !== undefined && options.name !== null) {
                                params.name = String(options.name);
                            }
                            if (options.uuid !== undefined && options.uuid !== null) {
                                params.uuid = String(options.uuid);
                            }
                        }
                        return toolCall("bluetooth_listen", params);
                    },
                    accept: (listenerSessionId, timeoutMs) => {
                        const params = { listener_session_id: listenerSessionId };
                        if (timeoutMs !== undefined && timeoutMs !== null) {
                            params.timeout_ms = String(timeoutMs);
                        }
                        return toolCall("bluetooth_accept", params);
                    },
                    send: (sessionId, options) => {
                        const params = { session_id: sessionId };
                        if (options.text !== undefined && options.text !== null) {
                            params.text = String(options.text);
                        }
                        if (options.dataBase64 !== undefined && options.dataBase64 !== null) {
                            params.data_base64 = String(options.dataBase64);
                        }
                        return toolCall("bluetooth_send", params);
                    },
                    read: (sessionId, options = {}) => {
                        const params = { session_id: sessionId };
                        if (options && typeof options === "object") {
                            if (options.maxBytes !== undefined && options.maxBytes !== null) {
                                params.max_bytes = String(options.maxBytes);
                            }
                            if (options.timeoutMs !== undefined && options.timeoutMs !== null) {
                                params.timeout_ms = String(options.timeoutMs);
                            }
                        }
                        return toolCall("bluetooth_read", params);
                    },
                    sendAndRead: (sessionId, options) => {
                        const params = { session_id: sessionId };
                        if (options.text !== undefined && options.text !== null) {
                            params.text = String(options.text);
                        }
                        if (options.dataBase64 !== undefined && options.dataBase64 !== null) {
                            params.data_base64 = String(options.dataBase64);
                        }
                        if (options.maxBytes !== undefined && options.maxBytes !== null) {
                            params.max_bytes = String(options.maxBytes);
                        }
                        if (options.timeoutMs !== undefined && options.timeoutMs !== null) {
                            params.timeout_ms = String(options.timeoutMs);
                        }
                        return toolCall("bluetooth_send_and_read", params);
                    },
                    close: (sessionId) => toolCall("bluetooth_close", { session_id: sessionId }),
                    ble: {
                        connect: (options) => {
                            const params = { address: options.address };
                            if (options.autoConnect !== undefined) {
                                params.auto_connect = !!options.autoConnect;
                            }
                            return toolCall("bluetooth_ble_connect", params);
                        },
                        discoverServices: (sessionId, timeoutMs) => {
                            const params = { session_id: sessionId };
                            if (timeoutMs !== undefined && timeoutMs !== null) {
                                params.timeout_ms = String(timeoutMs);
                            }
                            return toolCall("bluetooth_ble_discover_services", params);
                        },
                        readCharacteristic: (sessionId, options) => {
                            const params = {
                                session_id: sessionId,
                                service_uuid: options.serviceUuid,
                                characteristic_uuid: options.characteristicUuid
                            };
                            if (options.timeoutMs !== undefined && options.timeoutMs !== null) {
                                params.timeout_ms = String(options.timeoutMs);
                            }
                            return toolCall("bluetooth_ble_read_characteristic", params);
                        },
                        writeCharacteristic: (sessionId, options) => {
                            const params = {
                                session_id: sessionId,
                                service_uuid: options.serviceUuid,
                                characteristic_uuid: options.characteristicUuid
                            };
                            if (options.text !== undefined && options.text !== null) {
                                params.text = String(options.text);
                            }
                            if (options.dataBase64 !== undefined && options.dataBase64 !== null) {
                                params.data_base64 = String(options.dataBase64);
                            }
                            return toolCall("bluetooth_ble_write_characteristic", params);
                        },
                        writeAndReadCharacteristic: (sessionId, options) => {
                            const params = {
                                session_id: sessionId,
                                write_service_uuid: options.writeServiceUuid,
                                write_characteristic_uuid: options.writeCharacteristicUuid,
                                read_service_uuid: options.readServiceUuid,
                                read_characteristic_uuid: options.readCharacteristicUuid
                            };
                            if (options.text !== undefined && options.text !== null) {
                                params.text = String(options.text);
                            }
                            if (options.dataBase64 !== undefined && options.dataBase64 !== null) {
                                params.data_base64 = String(options.dataBase64);
                            }
                            if (options.timeoutMs !== undefined && options.timeoutMs !== null) {
                                params.timeout_ms = String(options.timeoutMs);
                            }
                            return toolCall("bluetooth_ble_write_and_read_characteristic", params);
                        },
                        subscribe: (sessionId, options) => {
                            const params = {
                                session_id: sessionId,
                                service_uuid: options.serviceUuid,
                                characteristic_uuid: options.characteristicUuid
                            };
                            if (options.enable !== undefined) {
                                params.enable = !!options.enable;
                            }
                            return toolCall("bluetooth_ble_subscribe_characteristic", params);
                        },
                        readNotifications: (sessionId, limit) => {
                            const params = { session_id: sessionId };
                            if (limit !== undefined && limit !== null) {
                                params.limit = String(limit);
                            }
                            return toolCall("bluetooth_ble_read_notifications", params);
                        }
                    }
                },
                shell: (command) => toolCall("execute_shell", { command }),
                // 执行终端命令 - 一次性收集输出
                terminal: {
                    create: (sessionName) => toolCall("create_terminal_session", { session_name: sessionName }),
                    exec: (sessionId, command, timeoutMs) => {
                        const params = { session_id: sessionId, command };
                        if (timeoutMs !== undefined && timeoutMs !== null) {
                            params.timeout_ms = String(timeoutMs);
                        }
                        return toolCall("execute_in_terminal_session", params);
                    },
                    execStreaming: (sessionId, command, options = {}) => {
                        const params = { session_id: sessionId, command };
                        const toolOptions = {};
                        if (options && typeof options === "object") {
                            if (options.timeoutMs !== undefined && options.timeoutMs !== null) {
                                params.timeout_ms = String(options.timeoutMs);
                            }
                            if (typeof options.onIntermediateResult === "function") {
                                toolOptions.onIntermediateResult = options.onIntermediateResult;
                            }
                        }
                        return toolCall("execute_in_terminal_session_streaming", params, toolOptions);
                    },
                    hiddenExec: (command, options = {}) => {
                        const params = { command };
                        if (options && typeof options === "object") {
                            if (options.executorKey !== undefined && options.executorKey !== null) {
                                params.executor_key = String(options.executorKey);
                            }
                            if (options.timeoutMs !== undefined && options.timeoutMs !== null) {
                                params.timeout_ms = String(options.timeoutMs);
                            }
                        }
                        return toolCall("execute_hidden_terminal_command", params);
                    },
                    screen: (sessionId) => toolCall("get_terminal_session_screen", { session_id: sessionId }),
                    close: (sessionId) => toolCall("close_terminal_session", { session_id: sessionId }),
                    input: (sessionId, options = {}) => {
                        const params = { session_id: sessionId };
                        if (options && typeof options === "object") {
                            if (options.input !== undefined && options.input !== null) {
                                params.input = String(options.input);
                            }
                            if (options.control !== undefined && options.control !== null) {
                                params.control = String(options.control);
                            }
                        }
                        return toolCall("input_in_terminal_session", params);
                    }
                },
                music: {
                    play: (options) => {
                        if (!options || typeof options !== "object" || Array.isArray(options)) {
                            throw new Error("music.play requires one options object");
                        }
                        const params = { ...options };
                        if (params.sourceType !== undefined && params.sourceType !== null) {
                            params.source_type = String(params.sourceType);
                            delete params.sourceType;
                        }
                        if (params.startPositionMs !== undefined && params.startPositionMs !== null) {
                            params.start_position_ms = String(params.startPositionMs);
                            delete params.startPositionMs;
                        }
                        if (params.source !== undefined && params.source !== null) {
                            params.source = String(params.source);
                        }
                        if (params.source_type !== undefined && params.source_type !== null) {
                            params.source_type = String(params.source_type);
                        }
                        if (params.title !== undefined && params.title !== null) {
                            params.title = String(params.title);
                        }
                        if (params.artist !== undefined && params.artist !== null) {
                            params.artist = String(params.artist);
                        }
                        if (params.loop !== undefined) {
                            params.loop = !!params.loop;
                        }
                        if (params.volume !== undefined && params.volume !== null) {
                            params.volume = String(params.volume);
                        }
                        return toolCall("music_play", params);
                    },
                    playQueue: (options) => {
                        if (!options || typeof options !== "object" || Array.isArray(options)) {
                            throw new Error("music.playQueue requires one options object");
                        }
                        if (!Array.isArray(options.items)) {
                            throw new Error("music.playQueue requires items array");
                        }
                        const items = options.items.map((item, index) => {
                            if (!item || typeof item !== "object" || Array.isArray(item)) {
                                throw new Error("music.playQueue items[" + index + "] must be an object");
                            }
                            const mapped = { ...item };
                            if (mapped.sourceType !== undefined && mapped.sourceType !== null) {
                                mapped.source_type = String(mapped.sourceType);
                                delete mapped.sourceType;
                            }
                            if (mapped.source !== undefined && mapped.source !== null) {
                                mapped.source = String(mapped.source);
                            }
                            if (mapped.source_type !== undefined && mapped.source_type !== null) {
                                mapped.source_type = String(mapped.source_type);
                            }
                            if (mapped.title !== undefined && mapped.title !== null) {
                                mapped.title = String(mapped.title);
                            }
                            if (mapped.artist !== undefined && mapped.artist !== null) {
                                mapped.artist = String(mapped.artist);
                            }
                            return mapped;
                        });
                        const params = { items: JSON.stringify(items) };
                        if (options.loop !== undefined) {
                            params.loop = !!options.loop;
                        }
                        if (options.volume !== undefined && options.volume !== null) {
                            params.volume = String(options.volume);
                        }
                        if (options.startIndex !== undefined && options.startIndex !== null) {
                            params.start_index = String(options.startIndex);
                        }
                        if (options.startPositionMs !== undefined && options.startPositionMs !== null) {
                            params.start_position_ms = String(options.startPositionMs);
                        }
                        return toolCall("music_play_queue", params);
                    },
                    pause: () => toolCall("music_pause", {}),
                    resume: () => toolCall("music_resume", {}),
                    stop: () => toolCall("music_stop", {}),
                    seek: (positionMs) => toolCall("music_seek", { position_ms: String(positionMs) }),
                    setVolume: (volume) => toolCall("music_set_volume", { volume: String(volume) }),
                    status: () => toolCall("music_status", {})
                },
                // 执行Intent
                intent: (options = {}) => {
                    return toolCall("execute_intent", options);
                },
                // 发送广播（避免手动拼接 extras JSON 的场景）
                sendBroadcast: (options = {}) => {
                    return toolCall("send_broadcast", options);
                }
            },
            // 软件设置
            SoftwareSettings: {
                readEnvironmentVariable: (key) => {
                    return toolCall("read_environment_variable", { key: String(key ?? "") });
                },
                writeEnvironmentVariable: (key, value) => {
                    const params = { key: String(key ?? "") };
                    if (value !== undefined && value !== null) params.value = String(value);
                    else params.value = "";
                    return toolCall("write_environment_variable", params);
                },
                listSandboxPackages: () => {
                    return toolCall("list_sandbox_packages", {});
                },
                setSandboxPackageEnabled: (packageName, enabled) => {
                    const params = { package_name: String(packageName ?? "") };
                    params.enabled = !!enabled;
                    return toolCall("set_sandbox_package_enabled", params);
                },
                executeSandboxScriptDirect: (options = {}) => {
                    const params = { ...(options || {}) };
                    return toolCall("execute_sandbox_script_direct", params);
                },
                restartMcpWithLogs: (timeoutMs) => {
                    const params = {};
                    if (timeoutMs !== undefined && timeoutMs !== null) params.timeout_ms = String(timeoutMs);
                    return toolCall("restart_mcp_with_logs", params);
                },
                getSpeechServicesConfig: () => {
                    return toolCall("get_speech_services_config", {});
                },
                setSpeechServicesConfig: (updates = {}) => {
                    const params = { ...(updates || {}) };
                    if (params.tts_locale !== undefined && params.tts_locale !== null) {
                        params.tts_locale = String(params.tts_locale);
                    }
                    if (params.tts_headers !== undefined && params.tts_headers !== null && typeof params.tts_headers === 'object') {
                        params.tts_headers = JSON.stringify(params.tts_headers);
                    }
                    if (params.tts_response_pipeline !== undefined && params.tts_response_pipeline !== null && Array.isArray(params.tts_response_pipeline)) {
                        params.tts_response_pipeline = JSON.stringify(params.tts_response_pipeline);
                    }
                    if (params.tts_cleaner_regexs !== undefined && params.tts_cleaner_regexs !== null && Array.isArray(params.tts_cleaner_regexs)) {
                        params.tts_cleaner_regexs = JSON.stringify(params.tts_cleaner_regexs);
                    }
                    return toolCall("set_speech_services_config", params);
                },
                testTtsPlayback: (text, options = {}) => {
                    const params = { ...(options || {}), text: String(text ?? "") };
                    if (params.interrupt !== undefined && params.interrupt !== null) {
                        params.interrupt = !!params.interrupt;
                    }
                    return toolCall("test_tts_playback", params);
                },
                listModelConfigs: () => {
                    return toolCall("list_model_configs", {});
                },
                createModelConfig: (options = {}) => {
                    const params = { ...(options || {}) };
                    if (params.custom_parameters !== undefined && params.custom_parameters !== null && typeof params.custom_parameters === 'object') {
                        params.custom_parameters = JSON.stringify(params.custom_parameters);
                    }
                    if (params.custom_headers !== undefined && params.custom_headers !== null && typeof params.custom_headers === 'object') {
                        params.custom_headers = JSON.stringify(params.custom_headers);
                    }
                    return toolCall("create_model_config", params);
                },
                updateModelConfig: (configId, updates = {}) => {
                    const params = { ...(updates || {}), config_id: String(configId ?? "") };
                    if (params.custom_parameters !== undefined && params.custom_parameters !== null && typeof params.custom_parameters === 'object') {
                        params.custom_parameters = JSON.stringify(params.custom_parameters);
                    }
                    if (params.custom_headers !== undefined && params.custom_headers !== null && typeof params.custom_headers === 'object') {
                        params.custom_headers = JSON.stringify(params.custom_headers);
                    }
                    return toolCall("update_model_config", params);
                },
                deleteModelConfig: (configId) => {
                    return toolCall("delete_model_config", { config_id: String(configId ?? "") });
                },
                listFunctionModelConfigs: () => {
                    return toolCall("list_function_model_configs", {});
                },
                getFunctionModelConfig: (functionType) => {
                    return toolCall("get_function_model_config", { function_type: String(functionType ?? "") });
                },
                setFunctionModelConfig: (functionType, configId, modelIndex) => {
                    const params = {
                        function_type: String(functionType ?? ""),
                        config_id: String(configId ?? "")
                    };
                    if (modelIndex !== undefined && modelIndex !== null) params.model_index = String(modelIndex);
                    return toolCall("set_function_model_config", params);
                },
                testModelConfigConnection: (configId, modelIndex) => {
                    const params = { config_id: String(configId ?? "") };
                    if (modelIndex !== undefined && modelIndex !== null) params.model_index = String(modelIndex);
                    return toolCall("test_model_config_connection", params);
                },
                listCharacterCards: () => {
                    return toolCall("list_character_cards_settings", {});
                },
                getCharacterCard: (characterCardId) => {
                    return toolCall("get_character_card", { character_card_id: characterCardId });
                },
                createCharacterCard: (options = {}) => {
                    const params = { ...options };
                    [
                        "attached_tag_ids",
                        "allowed_builtin_tools",
                        "allowed_packages",
                        "allowed_skills",
                        "allowed_mcp_servers"
                    ].forEach((name) => {
                        if (Array.isArray(params[name])) {
                            params[name] = JSON.stringify(params[name]);
                        }
                    });
                    return toolCall("create_character_card", params);
                },
                updateCharacterCard: (characterCardId, updates = {}) => {
                    const params = { ...updates, character_card_id: characterCardId };
                    [
                        "attached_tag_ids",
                        "allowed_builtin_tools",
                        "allowed_packages",
                        "allowed_skills",
                        "allowed_mcp_servers"
                    ].forEach((name) => {
                        if (Array.isArray(params[name])) {
                            params[name] = JSON.stringify(params[name]);
                        }
                    });
                    return toolCall("update_character_card", params);
                },
                deleteCharacterCard: (characterCardId) => {
                    return toolCall("delete_character_card", { character_card_id: characterCardId });
                },
                setActiveCharacterCard: (characterCardId) => {
                    return toolCall("set_active_character_card", { character_card_id: characterCardId });
                },
                clearActiveCharacterCard: () => {
                    return toolCall("clear_active_character_card", {});
                },
                importCharacterCardFromTavernJson: (tavernJson) => {
                    return toolCall("import_character_card_from_tavern_json", { tavern_json: tavernJson });
                },
                exportCharacterCardToTavernJson: (characterCardId) => {
                    return toolCall("export_character_card_to_tavern_json", { character_card_id: characterCardId });
                }
            },
            // Tasker event
            Tasker: {
                triggerEvent: (params) => {
                    return toolCall("trigger_tasker_event", params || {});
                }
            },
            // UI操作
            UI: {
                getPageInfo: () => toolCall("get_page_info"),
                captureScreenshot: () => toolCall("capture_screenshot"),
                tap: (x, y) => toolCall("tap", { x, y }),
                longPress: (x, y) => toolCall("long_press", { x, y }),
                // 增强的clickElement方法，支持多种参数类型
                clickElement: function(param1, param2, param3) {
                    // 根据参数类型和数量判断调用方式
                    if (typeof param1 === 'object') {
                        // 如果第一个参数是对象，直接传递参数对象
                        return toolCall("click_element", param1);
                    } else if (arguments.length === 1) {
                        // 单参数，假定为resourceId
                        if (param1.startsWith('[') && param1.includes('][')) {
                            // 参数看起来像bounds格式 [x,y][x,y]
                            return toolCall("click_element", { bounds: param1 });
                        }
                        return toolCall("click_element", { resourceId: param1 });
                    } else if (arguments.length === 2) {
                        // 两个参数，假定为(resourceId, index)或(className, index)
                        if (param1 === 'resourceId') {
                            return toolCall("click_element", { resourceId: param2 });
                        } else if (param1 === 'className') {
                            return toolCall("click_element", { className: param2 });
                        } else if (param1 === 'bounds') {
                            return toolCall("click_element", { bounds: param2 });
                        } else {
                            return toolCall("click_element", { resourceId: param1, index: param2 });
                        }
                    } else if (arguments.length === 3) {
                        // 三个参数，假定为(type, value, index)
                        if (param1 === 'resourceId') {
                            return toolCall("click_element", { resourceId: param2, index: param3 });
                        } else if (param1 === 'className') {
                            return toolCall("click_element", { className: param2, index: param3 });
                        } else {
                            return toolCall("click_element", { resourceId: param1, className: param2, index: param3 });
                        }
                    }
                    // 默认情况
                    return toolCall("click_element", { resourceId: param1 });
                },

                setText: (text, resourceId) => {
                    const params = { text };
                    if (resourceId) params.resourceId = resourceId;
                    return toolCall("set_input_text", params);
                },
                swipe: (startX, startY, endX, endY, duration) => {
                    const params = { 
                        start_x: startX, 
                        start_y: startY, 
                        end_x: endX, 
                        end_y: endY 
                    };
                    if (duration) params.duration = duration;
                    return toolCall("swipe", params);
                },
                pressKey: (keyCode) => toolCall("press_key", { key_code: keyCode }),
                /**
                 * Run the built-in UI automation subagent.
                 * @param intent High-level task description for the subagent.
                 * @param maxSteps Optional maximum number of steps (default 20).
                 * @param agentId Optional agent id to reuse the same virtual screen session.
                 * @param targetApp Optional target app package name.
                 */
                runSubAgent: (intent, maxSteps, agentId, targetApp) => {
                    const params = { intent: String(intent || "") };
                    if (maxSteps !== undefined) params.max_steps = String(maxSteps);
                    if (agentId !== undefined && agentId !== null && String(agentId).length > 0) params.agent_id = String(agentId);
                    if (targetApp !== undefined && targetApp !== null && String(targetApp).length > 0) params.target_app = String(targetApp);
                    return toolCall("run_ui_subagent", params);
                },
            },
            // 记忆管理
            Memory: {
                _normalizeCallerCardId: (callerCardId) => {
                    if (callerCardId === undefined || callerCardId === null) return undefined;
                    const normalized = String(callerCardId).trim();
                    return normalized.length > 0 ? normalized : undefined;
                },
                // 查询记忆库
                query: (query, folderPath, limit, startTime, endTime, snapshotId, threshold, callerCardId) => {
                    const options =
                        query && typeof query === 'object' && !Array.isArray(query)
                            ? query
                            : {
                                query,
                                folderPath,
                                limit,
                                startTime,
                                endTime,
                                snapshotId,
                                threshold,
                                callerCardId
                            };
                    const params = { query: options.query };
                    if (options.folderPath) params.folder_path = options.folderPath;
                    if (options.startTime !== undefined) params.start_time = options.startTime;
                    if (options.endTime !== undefined) params.end_time = options.endTime;
                    if (options.limit !== undefined) params.limit = options.limit;
                    if (options.snapshotId !== undefined && options.snapshotId !== null) params.snapshot_id = String(options.snapshotId);
                    if (options.threshold !== undefined) params.threshold = options.threshold;
                    const normalizedCallerCardId = Tools.Memory._normalizeCallerCardId(options.callerCardId);
                    if (normalizedCallerCardId !== undefined) params.caller_card_id = normalizedCallerCardId;
                    return toolCall("query_memory", params);
                },
                // 通过标题获取记忆
                getByTitle: (title, chunkIndex, chunkRange, query, limit, callerCardId) => {
                    const options =
                        title && typeof title === 'object' && !Array.isArray(title)
                            ? title
                            : {
                                title,
                                chunkIndex,
                                chunkRange,
                                query,
                                limit,
                                callerCardId
                            };
                    const params = { title: options.title };
                    if (options.chunkIndex !== undefined) params.chunk_index = options.chunkIndex;
                    if (options.chunkRange) params.chunk_range = options.chunkRange;
                    if (options.query) params.query = options.query;
                    if (options.limit !== undefined) params.limit = options.limit;
                    const normalizedCallerCardId = Tools.Memory._normalizeCallerCardId(options.callerCardId);
                    if (normalizedCallerCardId !== undefined) params.caller_card_id = normalizedCallerCardId;
                    return toolCall("get_memory_by_title", params);
                },
                // 创建记忆
                create: (title, content, contentType, source, folderPath, tags, callerCardId) => {
                    const options =
                        title && typeof title === 'object' && !Array.isArray(title)
                            ? title
                            : {
                                title,
                                content,
                                contentType,
                                source,
                                folderPath,
                                tags,
                                callerCardId
                            };
                    const params = { title: options.title, content: options.content };
                    if (options.contentType) params.content_type = options.contentType;
                    if (options.source) params.source = options.source;
                    if (options.folderPath) params.folder_path = options.folderPath;
                    if (options.tags) params.tags = options.tags;
                    const normalizedCallerCardId = Tools.Memory._normalizeCallerCardId(options.callerCardId);
                    if (normalizedCallerCardId !== undefined) params.caller_card_id = normalizedCallerCardId;
                    return toolCall("create_memory", params);
                },
                // 更新记忆
                update: (oldTitle, updates = {}, callerCardId) => {
                    const options =
                        oldTitle && typeof oldTitle === 'object' && !Array.isArray(oldTitle)
                            ? oldTitle
                            : { oldTitle, ...updates, callerCardId };
                    const params = { old_title: options.oldTitle };
                    if (options.newTitle) params.new_title = options.newTitle;
                    if (options.content) params.content = options.content;
                    if (options.contentType) params.content_type = options.contentType;
                    if (options.source) params.source = options.source;
                    if (options.credibility !== undefined) params.credibility = options.credibility;
                    if (options.importance !== undefined) params.importance = options.importance;
                    if (options.folderPath) params.folder_path = options.folderPath;
                    if (options.tags) params.tags = options.tags;
                    const normalizedCallerCardId = Tools.Memory._normalizeCallerCardId(options.callerCardId);
                    if (normalizedCallerCardId !== undefined) params.caller_card_id = normalizedCallerCardId;
                    return toolCall("update_memory", params);
                },
                // 删除记忆
                deleteMemory: (title, callerCardId) => {
                    const options =
                        title && typeof title === 'object' && !Array.isArray(title)
                            ? title
                            : { title, callerCardId };
                    const params = { title: options.title };
                    const normalizedCallerCardId = Tools.Memory._normalizeCallerCardId(options.callerCardId);
                    if (normalizedCallerCardId !== undefined) params.caller_card_id = normalizedCallerCardId;
                    return toolCall("delete_memory", params);
                },
                // 批量移动记忆（按标题列表和/或来源文件夹筛选）
                move: (targetFolderPath, titles, sourceFolderPath, callerCardId) => {
                    const options =
                        targetFolderPath && typeof targetFolderPath === 'object' && !Array.isArray(targetFolderPath)
                            ? targetFolderPath
                            : { targetFolderPath, titles, sourceFolderPath, callerCardId };
                    const params = { target_folder_path: options.targetFolderPath };
                    if (options.titles) {
                        params.titles = Array.isArray(options.titles) ? options.titles.join(",") : String(options.titles);
                    }
                    if (options.sourceFolderPath !== undefined && options.sourceFolderPath !== null) params.source_folder_path = String(options.sourceFolderPath);
                    const normalizedCallerCardId = Tools.Memory._normalizeCallerCardId(options.callerCardId);
                    if (normalizedCallerCardId !== undefined) params.caller_card_id = normalizedCallerCardId;
                    return toolCall("move_memory", params);
                },
                // 链接记忆
                link: (sourceTitle, targetTitle, linkType, weight, description, callerCardId) => {
                    const options =
                        sourceTitle && typeof sourceTitle === 'object' && !Array.isArray(sourceTitle)
                            ? sourceTitle
                            : { sourceTitle, targetTitle, linkType, weight, description, callerCardId };
                    const params = { source_title: options.sourceTitle, target_title: options.targetTitle };
                    if (options.linkType) params.link_type = options.linkType;
                    if (options.weight !== undefined) params.weight = options.weight;
                    if (options.description) params.description = options.description;
                    const normalizedCallerCardId = Tools.Memory._normalizeCallerCardId(options.callerCardId);
                    if (normalizedCallerCardId !== undefined) params.caller_card_id = normalizedCallerCardId;
                    return toolCall("link_memories", params);
                },
                // 查询记忆链接
                queryLinks: (linkId, sourceTitle, targetTitle, linkType, limit, callerCardId) => {
                    const options =
                        linkId && typeof linkId === 'object' && !Array.isArray(linkId)
                            ? linkId
                            : { linkId, sourceTitle, targetTitle, linkType, limit, callerCardId };
                    const params = {};
                    if (options.linkId !== undefined && options.linkId !== null) params.link_id = options.linkId;
                    if (options.sourceTitle) params.source_title = options.sourceTitle;
                    if (options.targetTitle) params.target_title = options.targetTitle;
                    if (options.linkType) params.link_type = options.linkType;
                    if (options.limit !== undefined) params.limit = options.limit;
                    const normalizedCallerCardId = Tools.Memory._normalizeCallerCardId(options.callerCardId);
                    if (normalizedCallerCardId !== undefined) params.caller_card_id = normalizedCallerCardId;
                    return toolCall("query_memory_links", params);
                },
                // 更新记忆链接（优先按 linkId）
                updateLink: (linkId, sourceTitle, targetTitle, linkType, newLinkType, weight, description, callerCardId) => {
                    const options =
                        linkId && typeof linkId === 'object' && !Array.isArray(linkId)
                            ? linkId
                            : { linkId, sourceTitle, targetTitle, linkType, newLinkType, weight, description, callerCardId };
                    const params = {};
                    if (options.linkId !== undefined && options.linkId !== null) params.link_id = options.linkId;
                    if (options.sourceTitle) params.source_title = options.sourceTitle;
                    if (options.targetTitle) params.target_title = options.targetTitle;
                    if (options.linkType) params.link_type = options.linkType;
                    if (options.newLinkType) params.new_link_type = options.newLinkType;
                    if (options.weight !== undefined) params.weight = options.weight;
                    if (options.description !== undefined) params.description = options.description;
                    const normalizedCallerCardId = Tools.Memory._normalizeCallerCardId(options.callerCardId);
                    if (normalizedCallerCardId !== undefined) params.caller_card_id = normalizedCallerCardId;
                    return toolCall("update_memory_link", params);
                },
                // 删除记忆链接（优先按 linkId）
                deleteLink: (linkId, sourceTitle, targetTitle, linkType, callerCardId) => {
                    const options =
                        linkId && typeof linkId === 'object' && !Array.isArray(linkId)
                            ? linkId
                            : { linkId, sourceTitle, targetTitle, linkType, callerCardId };
                    const params = {};
                    if (options.linkId !== undefined && options.linkId !== null) params.link_id = options.linkId;
                    if (options.sourceTitle) params.source_title = options.sourceTitle;
                    if (options.targetTitle) params.target_title = options.targetTitle;
                    if (options.linkType) params.link_type = options.linkType;
                    const normalizedCallerCardId = Tools.Memory._normalizeCallerCardId(options.callerCardId);
                    if (normalizedCallerCardId !== undefined) params.caller_card_id = normalizedCallerCardId;
                    return toolCall("delete_memory_link", params);
                }
            },
            // 计算功能
            calc: (expression) => toolCall("calculate", { expression }),
            
            // FFmpeg工具
            FFmpeg: {
                // 执行自定义FFmpeg命令
                execute: (command) => toolCall("ffmpeg_execute", { command }),
                
                // 获取FFmpeg系统信息
                info: () => toolCall("ffmpeg_info"),
                
                // 转换视频文件
                convert: (inputPath, outputPath, options = {}) => {
                    const params = {
                        input_path: inputPath,
                        output_path: outputPath,
                        ...options
                    };
                    return toolCall("ffmpeg_convert", params);
                }
            },
            
            // 工作流工具
            Workflow: {
                // 获取所有工作流
                getAll: () => {
                    return toolCall("get_all_workflows", {});
                },
                // 创建新工作流
                create: (name, description = "", nodes = null, connections = null, enabled = true) => {
                    const params = { name, description, enabled: enabled.toString() };
                    if (nodes) params.nodes = typeof nodes === 'string' ? nodes : JSON.stringify(nodes);
                    if (connections) params.connections = typeof connections === 'string' ? connections : JSON.stringify(connections);
                    return toolCall("create_workflow", params);
                },
                // 获取工作流详情
                get: (workflowId) => {
                    const params = { workflow_id: workflowId };
                    return toolCall("get_workflow", params);
                },
                // 更新工作流
                update: (workflowId, updates = {}) => {
                    const params = { workflow_id: workflowId };
                    if (updates.name !== undefined) params.name = updates.name;
                    if (updates.description !== undefined) params.description = updates.description;
                    if (updates.nodes !== undefined) params.nodes = typeof updates.nodes === 'string' ? updates.nodes : JSON.stringify(updates.nodes);
                    if (updates.connections !== undefined) params.connections = typeof updates.connections === 'string' ? updates.connections : JSON.stringify(updates.connections);
                    if (updates.enabled !== undefined) params.enabled = updates.enabled.toString();
                    return toolCall("update_workflow", params);
                },
                // 差异更新工作流（增量 patch）
                patch: (workflowId, patch = {}) => {
                    const params = { workflow_id: workflowId };
                    if (patch.name !== undefined) params.name = patch.name;
                    if (patch.description !== undefined) params.description = patch.description;
                    if (patch.enabled !== undefined) params.enabled = patch.enabled.toString();
                    if (patch.node_patches !== undefined) {
                        params.node_patches = typeof patch.node_patches === 'string'
                            ? patch.node_patches
                            : JSON.stringify(patch.node_patches);
                    }
                    if (patch.connection_patches !== undefined) {
                        params.connection_patches = typeof patch.connection_patches === 'string'
                            ? patch.connection_patches
                            : JSON.stringify(patch.connection_patches);
                    }
                    return toolCall("patch_workflow", params);
                },
                // 设置工作流启用状态
                setEnabled: (workflowId, enabled) => {
                    const params = { workflow_id: workflowId };
                    return toolCall(enabled ? "enable_workflow" : "disable_workflow", params);
                },
                // 启用工作流
                enable: (workflowId) => {
                    const params = { workflow_id: workflowId };
                    return toolCall("enable_workflow", params);
                },
                // 禁用工作流
                disable: (workflowId) => {
                    const params = { workflow_id: workflowId };
                    return toolCall("disable_workflow", params);
                },
                // 删除工作流
                delete: (workflowId) => {
                    const params = { workflow_id: workflowId };
                    return toolCall("delete_workflow", params);
                },
                // 触发工作流执行
                trigger: (workflowId) => {
                    const params = { workflow_id: workflowId };
                    return toolCall("trigger_workflow", params);
                }
            },
            // 对话管理工具
            Chat: {
                // 启动对话服务
                startService: (options = {}) => {
                    const params = {};
                    if (options.initial_mode !== undefined && options.initial_mode !== null && String(options.initial_mode).trim() !== "") {
                        params.initial_mode = String(options.initial_mode);
                    }
                    if (options.auto_enter_voice_chat !== undefined && options.auto_enter_voice_chat !== null) {
                        params.auto_enter_voice_chat = options.auto_enter_voice_chat;
                    }
                    if (options.wake_launched !== undefined && options.wake_launched !== null) {
                        params.wake_launched = options.wake_launched;
                    }
                    if (options.timeout_ms !== undefined && options.timeout_ms !== null && !isNaN(Number(options.timeout_ms))) {
                        params.timeout_ms = String(options.timeout_ms);
                    }
                    if (options.keep_if_exists !== undefined && options.keep_if_exists !== null) {
                        params.keep_if_exists = options.keep_if_exists;
                    }
                    return toolCall("start_chat_service", params);
                },
                // 创建新对话
                createNew: (group, setAsCurrentChat, characterCardId) => {
                    const params = {};
                    if (group !== undefined && group !== null && String(group).trim() !== "") {
                        params.group = String(group);
                    }
                    if (setAsCurrentChat !== undefined && setAsCurrentChat !== null) {
                        params.set_as_current_chat = String(setAsCurrentChat);
                    }
                    if (characterCardId !== undefined && characterCardId !== null && String(characterCardId).trim() !== "") {
                        params.character_card_id = String(characterCardId);
                    }
                    return toolCall("create_new_chat", params);
                },
                // 列出所有对话
                listAll: () => toolCall("list_chats", {}),
                listChats: (params = {}) => toolCall("list_chats", params),
                findChat: (params = {}) => toolCall("find_chat", params),
                agentStatus: (chatId) => toolCall("agent_status", { chat_id: chatId }),
                // 切换对话
                switchTo: (chatId) => toolCall("switch_chat", { chat_id: chatId }),
                updateTitle: (chatId, title) => {
                    const params = { chat_id: String(chatId ?? ""), title: String(title ?? "") };
                    return toolCall("update_chat_title", params);
                },
                deleteChat: (chatId) => {
                    return toolCall("delete_chat", { chat_id: String(chatId ?? "") });
                },
                getMessages: (chatId, options = {}) => {
                    const params = { chat_id: String(chatId ?? "") };
                    if (options.order !== undefined && options.order !== null && String(options.order).trim() !== "") params.order = String(options.order);
                    if (options.limit !== undefined && options.limit !== null && !isNaN(Number(options.limit))) params.limit = String(options.limit);
                    return toolCall("get_chat_messages", params);
                },
                getMessagesRange: (chatId, options = {}) => {
                    const params = { chat_id: String(chatId ?? "") };
                    if (options.order !== undefined && options.order !== null && String(options.order).trim() !== "") params.order = String(options.order);
                    if (options.start !== undefined && options.start !== null && !isNaN(Number(options.start))) params.start = String(options.start);
                    if (options.end !== undefined && options.end !== null && !isNaN(Number(options.end))) params.end = String(options.end);
                    return toolCall("get_chat_messages_range", params);
                },
                // 发送消息给AI
                sendMessage: (message, chatId, roleCardId, senderName, options = {}) => {
                    const params = { message };
                    if (chatId) params.chat_id = chatId;
                    if (roleCardId) params.role_card_id = roleCardId;
                    if (senderName) params.sender_name = senderName;
                    if (options.runtime) params.runtime = String(options.runtime);
                    if (options.persist_turn !== undefined) params.persist_turn = options.persist_turn;
                    if (options.notify_reply !== undefined) params.notify_reply = options.notify_reply;
                    if (options.hide_user_message !== undefined) params.hide_user_message = options.hide_user_message;
                    if (options.disable_warning !== undefined) params.disable_warning = options.disable_warning;
                    if (options.timeout_ms !== undefined && options.timeout_ms !== null) params.timeout_ms = String(options.timeout_ms);
                    return toolCall("send_message_to_ai", params);
                },
                sendMessageStreaming: (message, chatId, roleCardId, senderName, options = {}) => {
                    const params = { message };
                    const toolOptions = {};
                    if (chatId) params.chat_id = chatId;
                    if (roleCardId) params.role_card_id = roleCardId;
                    if (senderName) params.sender_name = senderName;
                    if (options.runtime) params.runtime = String(options.runtime);
                    if (options.persist_turn !== undefined) params.persist_turn = options.persist_turn;
                    if (options.notify_reply !== undefined) params.notify_reply = options.notify_reply;
                    if (options.hide_user_message !== undefined) params.hide_user_message = options.hide_user_message;
                    if (options.disable_warning !== undefined) params.disable_warning = options.disable_warning;
                    if (options.timeout_ms !== undefined && options.timeout_ms !== null) params.timeout_ms = String(options.timeout_ms);
                    if (options.waifu !== undefined) params.waifu = options.waifu;
                    if (typeof options.onIntermediateResult === "function") {
                        toolOptions.onIntermediateResult = options.onIntermediateResult;
                    }
                    return toolCall("send_message_to_ai_streaming", params, toolOptions);
                },
                // 列出所有角色卡
                listCharacterCards: () => toolCall("list_character_cards", {})
            }
        };
    """.trimIndent()
}
