package com.ai.assistance.operit.core.tools.packTool

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 包管理加号「新建模板」生成的模板种类。 */
enum class PackageTemplateKind {
    /** 插件：打成可直接加载的 .toolpkg，入口是 dist/main.js。 */
    PLUGIN_JS,
    /** 插件：同时带 src/main.ts 和已编译的 dist/main.js，修改后需自行编译。 */
    PLUGIN_TS,
    /** 沙盒包：带 METADATA 头的单个 .js 文件。 */
    SANDBOX_JS
}

/** 模板落盘失败原因，由界面映射成文案。 */
enum class PackageTemplateCreateError {
    INVALID_DISPLAY_NAME,
    INVALID_PACKAGE_ID,
    FILE_EXISTS,
    IO
}

/** 模板写入结果。成功时 [file] 指向 packages 目录里的新文件。 */
data class PackageTemplateCreateResult(
    val success: Boolean,
    val file: File? = null,
    val packageId: String = "",
    val error: PackageTemplateCreateError? = null
)

/**
 * 把加号「新建模板」写成外部包目录里的真实文件。
 *
 * 外部扫描只认 [packages] 目录下的文件，不扫子目录，所以插件必须打成 `.toolpkg` zip，
 * 沙盒包则是带 METADATA 的 `.js`。
 */
object PackageTemplateFactory {
    private val SANDBOX_PACKAGE_ID = Regex("^[A-Za-z][A-Za-z0-9_]{0,62}$")
    private val TOOLPKG_ID = Regex("^[A-Za-z][A-Za-z0-9._-]{0,126}$")

    fun isToolPkgKind(kind: PackageTemplateKind): Boolean {
        return kind != PackageTemplateKind.SANDBOX_JS
    }

    fun isValidDisplayName(displayName: String): Boolean {
        return displayName.trim().isNotEmpty()
    }

    fun isValidPackageId(packageId: String, forToolPkg: Boolean): Boolean {
        val id = packageId.trim()
        if (id.contains("..") || id.startsWith(".") || id.endsWith(".")) {
            return false
        }
        return if (forToolPkg) {
            TOOLPKG_ID.matches(id)
        } else {
            SANDBOX_PACKAGE_ID.matches(id)
        }
    }

    /**
     * 根据显示名生成可编辑的默认标识。
     * 中文名里如果抽不出字母数字，就回退到 my_package，避免生成空 id。
     */
    fun suggestPackageId(displayName: String, forToolPkg: Boolean): String {
        val builder = StringBuilder()
        displayName.trim().lowercase().forEach { ch ->
            when {
                ch in 'a'..'z' || ch in '0'..'9' -> builder.append(ch)
                ch == '.' && forToolPkg -> builder.append(ch)
                builder.isNotEmpty() && builder.last() != '_' -> builder.append('_')
            }
        }
        var base = builder.toString().trim('_').trim('.')
        if (base.isBlank()) {
            base = "my_package"
        } else if (base.first().isDigit()) {
            base = "pkg_$base"
        }
        base = base.take(40)
        return if (forToolPkg) "local.$base" else base
    }

    fun create(
        packagesDir: File,
        kind: PackageTemplateKind,
        displayName: String,
        packageId: String
    ): PackageTemplateCreateResult {
        val trimmedName = displayName.trim()
        if (!isValidDisplayName(trimmedName)) {
            return PackageTemplateCreateResult(
                success = false,
                error = PackageTemplateCreateError.INVALID_DISPLAY_NAME
            )
        }

        val trimmedId = packageId.trim()
        if (!isValidPackageId(trimmedId, isToolPkgKind(kind))) {
            return PackageTemplateCreateResult(
                success = false,
                error = PackageTemplateCreateError.INVALID_PACKAGE_ID
            )
        }

        if (!packagesDir.exists() && !packagesDir.mkdirs()) {
            return PackageTemplateCreateResult(
                success = false,
                packageId = trimmedId,
                error = PackageTemplateCreateError.IO
            )
        }

        val target = File(packagesDir, fileNameFor(kind, trimmedId))
        if (target.exists()) {
            return PackageTemplateCreateResult(
                success = false,
                file = target,
                packageId = trimmedId,
                error = PackageTemplateCreateError.FILE_EXISTS
            )
        }

        return try {
            when (kind) {
                PackageTemplateKind.SANDBOX_JS ->
                    target.writeText(buildSandboxJs(trimmedName, trimmedId), Charsets.UTF_8)
                PackageTemplateKind.PLUGIN_JS ->
                    writeZip(target, pluginJsEntries(trimmedName, trimmedId))
                PackageTemplateKind.PLUGIN_TS ->
                    writeZip(target, pluginTsEntries(trimmedName, trimmedId))
            }
            PackageTemplateCreateResult(
                success = true,
                file = target,
                packageId = trimmedId
            )
        } catch (_: Exception) {
            if (target.exists()) {
                target.delete()
            }
            PackageTemplateCreateResult(
                success = false,
                packageId = trimmedId,
                error = PackageTemplateCreateError.IO
            )
        }
    }

    internal fun fileNameFor(kind: PackageTemplateKind, packageId: String): String {
        return if (isToolPkgKind(kind)) "$packageId.toolpkg" else "$packageId.js"
    }

    internal fun buildSandboxJs(displayName: String, packageId: String): String {
        val nameJson = escapeJson(displayName)
        val idJson = escapeJson(packageId)
        return """
            /* METADATA
            {
                "name": "$idJson",
                "display_name": {
                    "zh": "$nameJson",
                    "en": "$nameJson"
                },
                "description": {
                    "zh": "这是一份可直接运行的沙盒包模板。文件开头的 METADATA 告诉宿主这个包叫什么、有哪些工具；文件末尾的 exports 则提供工具的实际实现。",
                    "en": "A runnable sandbox package template. METADATA describes the package; exports implement the tools."
                },
                "category": "Other",
                "enabledByDefault": false,
                "tools": [
                    {
                        "name": "hello_world",
                        "description": {
                            "zh": "返回一句问候，用来确认这个沙盒包已经被正确加载。",
                            "en": "Return a greeting to confirm this sandbox package is loaded."
                        },
                        "parameters": [
                            {
                                "name": "name",
                                "description": {
                                    "zh": "可选：要问候的名字。",
                                    "en": "Optional name to greet."
                                },
                                "type": "string",
                                "required": false
                            }
                        ]
                    }
                ]
            }
            */

            // METADATA 必须写在文件最开头的块注释里，宿主靠它生成工具列表，不会执行这段 JSON。
            // enabledByDefault 设为 false，避免新建的示范包一出现就被自动启用。

            // 用 IIFE 包住实现，避免辅助函数泄漏到全局；宿主真正调用的是底部 exports 上的同名函数。
            const HelloWorldPackage = (function () {
                // 函数名必须和 METADATA.tools[].name 完全一致，否则宿主找不到实现。
                async function hello_world(params) {
                    const rawName = params && params.name != null ? String(params.name).trim() : "";
                    const message = rawName
                        ? ("你好，" + rawName + "。这个沙盒包模板已经可以工作了。")
                        : "你好。这个沙盒包模板已经可以工作了。";
                    // 约定返回 { success, message, data }，方便宿主直接展示结果。
                    return {
                        success: true,
                        message: message,
                        data: {
                            name: rawName
                        }
                    };
                }

                return {
                    hello_world: hello_world
                };
            })();

            // 必须把 METADATA 里声明过的每个工具都导出，缺一个宿主就会判定这个工具不可用。
            exports.hello_world = HelloWorldPackage.hello_world;
        """.trimIndent() + "\n"
    }

    internal fun buildPluginManifest(displayName: String, toolpkgId: String): String {
        val nameJson = escapeJson(displayName)
        val idJson = escapeJson(toolpkgId)
        return """
            {
              "schema_version": 1,
              "toolpkg_id": "$idJson",
              "version": "1.0.0",
              "api_version": "1.0.0",
              "main": "dist/main.js",
              "display_name": {
                "zh": "$nameJson",
                "en": "$nameJson"
              },
              "description": {
                "zh": "这是一份可直接加载的插件模板。manifest 负责声明身份和入口，dist/main.js 里的 registerToolPkg() 负责向宿主注册能力。",
                "en": "A loadable plugin template. The manifest declares identity and entry; registerToolPkg() in dist/main.js registers capabilities."
              },
              "enabled_by_default": false
            }
        """.trimIndent() + "\n"
    }

    internal fun buildPluginMainJs(): String {
        return """
            "use strict";

            /**
             * registerToolPkg 是 ToolPkg 的唯一注册入口。
             * 宿主加载这个 .toolpkg 时会调用它，让你声明 UI、钩子、菜单项等。
             *
             * 这里只能做“注册声明”：
             * - 可以调用 ToolPkg.registerToolboxUiModule / registerChatMessageMenuItem 等
             * - 不可以 ToolPkg.readResource(...)
             * - 不可以 ToolPkg.wasm.call(...)
             * 读资源和调用 WASM 会在注册阶段直接抛错。
             *
             * 空模板先什么都不注册，保证能被成功加载。你加能力时，把注册调用写在 return true 之前。
             */
            function registerToolPkg() {
                // 示例（需要时再取消注释并补全字段）：
                // ToolPkg.registerChatMessageMenuItem({
                //     id: "hello_menu_item",
                //     title: { zh: "问好", en: "Say hello" },
                //     function: "onHelloMenuItem"
                // });
                return true;
            }

            // 必须导出这个函数名，宿主按名字查找，写错就加载失败。
            exports.registerToolPkg = registerToolPkg;
        """.trimIndent() + "\n"
    }

    internal fun buildPluginMainTs(): String {
        return """
            /**
             * 这是 TypeScript 源码。宿主实际加载的是 dist/main.js。
             * 你改完本文件后，需要在有 tsc 的环境里重新编译，再把新的 dist/main.js 打进 .toolpkg。
             *
             * registerToolPkg 是 ToolPkg 的唯一注册入口。
             * 注册阶段只能声明 hook / UI / 菜单，不能读资源，也不能调用 WASM。
             */
            export function registerToolPkg(): boolean {
                // 示例（需要时再取消注释并补全字段）：
                // ToolPkg.registerChatMessageMenuItem({
                //     id: "hello_menu_item",
                //     title: { zh: "问好", en: "Say hello" },
                //     function: "onHelloMenuItem"
                // });
                return true;
            }
        """.trimIndent() + "\n"
    }

    internal fun buildPluginCompiledJs(): String {
        return """
            "use strict";
            Object.defineProperty(exports, "__esModule", { value: true });
            exports.registerToolPkg = registerToolPkg;
            // 本文件由 src/main.ts 编译而来。宿主只执行 dist/main.js。
            // 修改 TypeScript 源码后请重新编译并替换本文件，否则运行的仍是旧逻辑。
            function registerToolPkg() {
                return true;
            }
        """.trimIndent() + "\n"
    }

    internal fun buildPluginTsconfig(): String {
        return """
            {
              "compilerOptions": {
                "target": "es2020",
                "module": "commonjs",
                "outDir": "./dist",
                "rootDir": "./src",
                "lib": ["es2020"],
                "declaration": false,
                "strict": false,
                "skipLibCheck": true,
                "esModuleInterop": true,
                "forceConsistentCasingInFileNames": true
              },
              "include": ["src/**/*.ts"],
              "exclude": ["node_modules"]
            }
        """.trimIndent() + "\n"
    }

    private fun pluginJsEntries(displayName: String, toolpkgId: String): Map<String, String> {
        return linkedMapOf(
            "manifest.json" to buildPluginManifest(displayName, toolpkgId),
            "dist/main.js" to buildPluginMainJs()
        )
    }

    private fun pluginTsEntries(displayName: String, toolpkgId: String): Map<String, String> {
        return linkedMapOf(
            "manifest.json" to buildPluginManifest(displayName, toolpkgId),
            "tsconfig.json" to buildPluginTsconfig(),
            "src/main.ts" to buildPluginMainTs(),
            "dist/main.js" to buildPluginCompiledJs()
        )
    }

    private fun writeZip(target: File, entries: Map<String, String>) {
        target.outputStream().buffered().use { fileOutput ->
            ZipOutputStream(fileOutput).use { zipOutput ->
                entries.forEach { (name, content) ->
                    zipOutput.putNextEntry(ZipEntry(name))
                    zipOutput.write(content.toByteArray(Charsets.UTF_8))
                    zipOutput.closeEntry()
                }
            }
        }
    }

    private fun escapeJson(value: String): String {
        return buildString(value.length) {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
    }
}
