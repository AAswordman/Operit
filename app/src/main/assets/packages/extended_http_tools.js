/* METADATA
{
    "name": "extended_http_tools",

    "display_name": {
        "zh": "增强 HTTP 工具",
        "en": "Extended HTTP Tools"
    },
    "description": {
        "zh": "允许文件上传，以及 GET/POST 等网络直接访问操作。",
        "en": "Allows file uploads and direct network access operations such as GET/POST."
    },
    "enabledByDefault": true,
    "category": "Network",
    "tools": [
        {
            "name": "http_request",
            "description": { "zh": "发送 HTTP 请求。", "en": "Send an HTTP request." },
            "parameters": [
                { "name": "url", "description": { "zh": "请求 URL", "en": "Request URL" }, "type": "string", "required": true },
                { "name": "method", "description": { "zh": "请求方法：GET/POST/PUT/DELETE", "en": "Method: GET/POST/PUT/DELETE" }, "type": "string", "required": true },
                { "name": "headers", "description": { "zh": "可选：headers（JSON 对象字符串）", "en": "Optional: headers (JSON object string)" }, "type": "string", "required": false },
                { "name": "body", "description": { "zh": "可选：请求体（字符串）", "en": "Optional: body (string)" }, "type": "string", "required": false },
                { "name": "body_type", "description": { "zh": "可选：json/form/text/xml", "en": "Optional: json/form/text/xml" }, "type": "string", "required": false },
                { "name": "ignore_ssl", "description": { "zh": "可选：是否忽略 HTTPS 证书校验（true/false）", "en": "Optional: ignore HTTPS certificate verification (true/false)" }, "type": "boolean", "required": false },
                { "name": "timeout", "description": { "zh": "可选：整次请求超时，毫秒或秒（>=1000 按毫秒）", "en": "Optional overall timeout in milliseconds or seconds (>=1000 treated as ms)" }, "type": "number", "required": false }
            ]
        },
        {
            "name": "multipart_request",
            "description": { "zh": "上传文件（multipart）。", "en": "Upload files (multipart)." },
            "parameters": [
                { "name": "url", "description": { "zh": "请求 URL", "en": "Request URL" }, "type": "string", "required": true },
                { "name": "method", "description": { "zh": "请求方法：POST/PUT", "en": "Method: POST/PUT" }, "type": "string", "required": true },
                { "name": "headers", "description": { "zh": "可选：headers（JSON 对象字符串）", "en": "Optional: headers (JSON object string)" }, "type": "string", "required": false },
                { "name": "form_data", "description": { "zh": "可选：form_data（字符串）", "en": "Optional: form_data (string)" }, "type": "string", "required": false },
                { "name": "files", "description": { "zh": "可选：files（JSON 数组字符串）", "en": "Optional: files (JSON array string)" }, "type": "string", "required": false },
                { "name": "ignore_ssl", "description": { "zh": "可选：是否忽略 HTTPS 证书校验（true/false）", "en": "Optional: ignore HTTPS certificate verification (true/false)" }, "type": "boolean", "required": false }
            ]
        },
        {
            "name": "manage_cookies",
            "description": { "zh": "管理 Cookies。", "en": "Manage cookies." },
            "parameters": [
                { "name": "action", "description": { "zh": "操作：get/set/clear", "en": "Action: get/set/clear" }, "type": "string", "required": true },
                { "name": "domain", "description": { "zh": "可选：域名", "en": "Optional: domain" }, "type": "string", "required": false },
                { "name": "cookies", "description": { "zh": "可选：cookies（字符串）", "en": "Optional: cookies (string)" }, "type": "string", "required": false }
            ]
        }
    ]
}*/
const ExtendedHttpTools = (function () {
    const MAX_INLINE_HTTP_RESPONSE_CHARS = 12000;
    async function http_request(params) {
        const toolParams = {
            url: params.url,
            method: params.method,
        };
        if (params.headers !== undefined)
            toolParams.headers = params.headers;
        if (params.body !== undefined)
            toolParams.body = params.body;
        if (params.body_type !== undefined)
            toolParams.body_type = params.body_type;
        if (params.ignore_ssl !== undefined)
            toolParams.ignore_ssl = params.ignore_ssl;
        if (params.timeout !== undefined)
            toolParams.timeout = params.timeout;
        if (params.timeout_ms !== undefined)
            toolParams.timeout_ms = params.timeout_ms;
        if (params.connect_timeout !== undefined)
            toolParams.connect_timeout = params.connect_timeout;
        if (params.read_timeout !== undefined)
            toolParams.read_timeout = params.read_timeout;
        if (params.write_timeout !== undefined)
            toolParams.write_timeout = params.write_timeout;
        const result = await toolCall({ name: "http_request", params: toolParams });
        const success = result.statusCode >= 200 && result.statusCode < 400;
        const contentStr = typeof result?.content === "string" ? result.content : "";
        if (contentStr.length > MAX_INLINE_HTTP_RESPONSE_CHARS) {
            await Tools.Files.mkdir(OPERIT_CLEAN_ON_EXIT_DIR, true);
            const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
            const rand = Math.floor(Math.random() * 1000000);
            let ext = "txt";
            const ct = typeof result?.contentType === "string" ? result.contentType.toLowerCase() : "";
            if (ct.includes("json"))
                ext = "json";
            else if (ct.includes("html"))
                ext = "html";
            else if (ct.includes("xml"))
                ext = "xml";
            const filePath = `${OPERIT_CLEAN_ON_EXIT_DIR}/http_response_${timestamp}_${rand}.${ext}`;
            await Tools.Files.write(filePath, contentStr, false);
            const resultMeta = {
                url: result?.url,
                statusCode: result?.statusCode,
                statusMessage: result?.statusMessage,
                headers: result?.headers,
                contentType: result?.contentType,
                size: result?.size,
                content: "(saved_to_file)",
            };
            return {
                success,
                message: `HTTP 请求完成；响应内容过大，已保存到文件：${filePath}。请使用 read_file_part 读取指定行范围，或用 grep_code 在该文件中检索关键字。`,
                data: {
                    result: resultMeta,
                    content_saved_to: filePath,
                    operit_clean_on_exit_dir: OPERIT_CLEAN_ON_EXIT_DIR,
                },
            };
        }
        return { success, message: 'HTTP 请求完成', data: result };
    }
    async function multipart_request(params) {
        const toolParams = {
            url: params.url,
            method: params.method,
        };
        if (params.headers !== undefined)
            toolParams.headers = params.headers;
        if (params.form_data !== undefined)
            toolParams.form_data = params.form_data;
        if (params.files !== undefined)
            toolParams.files = params.files;
        if (params.ignore_ssl !== undefined)
            toolParams.ignore_ssl = params.ignore_ssl;
        const result = await toolCall({ name: "multipart_request", params: toolParams });
        const success = result.statusCode >= 200 && result.statusCode < 400;
        return { success, message: '文件上传完成', data: result };
    }
    async function manage_cookies(params) {
        const toolParams = {
            action: params.action,
        };
        if (params.domain !== undefined)
            toolParams.domain = params.domain;
        if (params.cookies !== undefined)
            toolParams.cookies = params.cookies;
        const result = await toolCall({ name: "manage_cookies", params: toolParams });
        const success = result.statusCode >= 200 && result.statusCode < 400;
        return { success, message: 'Cookies 操作完成', data: result };
    }
    async function wrapToolExecution(func, params) {
        try {
            const result = await func(params);
            complete(result);
        }
        catch (error) {
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({
                success: false,
                message: `工具执行时发生意外错误: ${error.message}`,
            });
        }
    }
    async function main() {
        const results = [];
        // 这些工具可能对外发请求 / 写 cookies，默认不做自动化演示。
        results.push({ tool: 'http_request', result: { success: null, message: '未测试（会对外发起网络请求）' } });
        results.push({ tool: 'multipart_request', result: { success: null, message: '未测试（会上传文件/对外发起网络请求）' } });
        results.push({ tool: 'manage_cookies', result: { success: null, message: '未测试（会读写 cookies）' } });
        complete({
            success: true,
            message: "拓展 HTTP 工具包加载完成（未执行网络请求测试）",
            data: { results }
        });
    }
    return {
        http_request: (params) => wrapToolExecution(http_request, params),
        multipart_request: (params) => wrapToolExecution(multipart_request, params),
        manage_cookies: (params) => wrapToolExecution(manage_cookies, params),
        main,
    };
})();
exports.http_request = ExtendedHttpTools.http_request;
exports.multipart_request = ExtendedHttpTools.multipart_request;
exports.manage_cookies = ExtendedHttpTools.manage_cookies;
exports.main = ExtendedHttpTools.main;
