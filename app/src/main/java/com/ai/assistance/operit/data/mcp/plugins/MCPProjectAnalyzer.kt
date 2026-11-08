package com.ai.assistance.operit.data.mcp.plugins

import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import org.json.JSONObject

/**
 * MCP 项目结构分析器
 *
 * 负责分析插件项目结构，识别项目类型和特征
 */
class MCPProjectAnalyzer {

    companion object {
        private const val TAG = "MCPProjectAnalyzer"

        // 用于匹配命令块的正则表达式
        private val CODE_BLOCK_REGEX = "```(?:bash|shell|cmd|sh|json)?([\\s\\S]*?)```".toRegex()

        // JSON配置块正则表达式
        private val JSON_CONFIG_REGEX = "\\{[\\s\\S]*?\"mcpServers\"[\\s\\S]*?\\}".toRegex()
    }

    /**
     * 分析项目结构
     *
     * @param pluginDir 插件目录
     * @param readmeContent README内容
     * @return 项目结构信息
     */
    fun analyzeProjectStructure(pluginDir: File, readmeContent: String): ProjectStructure {
        // 检查文件存在性
        val hasRequirementsTxt = File(pluginDir, "requirements.txt").exists()
        val hasPyprojectToml = File(pluginDir, "pyproject.toml").exists()
        val hasSetupPy = File(pluginDir, "setup.py").exists()
        val hasPackageJson = File(pluginDir, "package.json").exists()
        val hasTsConfig = File(pluginDir, "tsconfig.json").exists()

        // 查找入口文件
        val pythonFiles =
                pluginDir.listFiles { file -> file.extension.equals("py", ignoreCase = true) }
        val jsFiles = pluginDir.listFiles { file -> file.extension.equals("js", ignoreCase = true) }
        val tsFiles =
                pluginDir.listFiles { file ->
                    file.extension.equals("ts", ignoreCase = true) ||
                            file.extension.equals("tsx", ignoreCase = true)
                }
        val hasTsFiles = tsFiles != null && tsFiles.isNotEmpty()

        val mainPythonModule = findMainPythonModule(pluginDir, pythonFiles)
        val mainJsFile = findMainJsFile(pluginDir, jsFiles)
        val mainTsFile = findMainTsFile(pluginDir, tsFiles)

        // 检查package.json中是否有TypeScript依赖
        var hasTypeScriptDependency = false
        var packageJsonContent: String? = null
        var packageJsonScripts: JSONObject? = null

        if (hasPackageJson) {
            val packageJsonFile = File(pluginDir, "package.json")
            try {
                // 读取并保存package.json内容
                packageJsonContent = packageJsonFile.readText()
                val packageJson = JSONObject(packageJsonContent)

                // 检查dependencies和devDependencies中是否有TypeScript相关依赖
                val dependencies = packageJson.optJSONObject("dependencies")
                val devDependencies = packageJson.optJSONObject("devDependencies")

                if (dependencies != null &&
                                (dependencies.has("typescript") || dependencies.has("ts-node"))
                ) {
                    hasTypeScriptDependency = true
                } else if (devDependencies != null &&
                                (devDependencies.has("typescript") ||
                                        devDependencies.has("ts-node"))
                ) {
                    hasTypeScriptDependency = true
                }

                // 提取并保存scripts部分
                val scripts = packageJson.optJSONObject("scripts")
                if (scripts != null) {
                    packageJsonScripts = scripts
                    for (key in scripts.keys()) {
                        val value = scripts.optString(key)
                        if (value.contains("tsc") ||
                                        value.contains("ts-node") ||
                                        value.contains("typescript")
                        ) {
                            hasTypeScriptDependency = true
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "解析package.json失败", e)
            }
        }

        // 解析tsconfig.json并提取配置
        var tsConfigOutDir: String? = null
        var tsConfigRootDir: String? = null
        var tsConfigContent: String? = null

        if (hasTsConfig) {
            val tsConfigInfo = parseTsConfig(pluginDir)
            tsConfigOutDir = tsConfigInfo.first
            tsConfigRootDir = tsConfigInfo.second
            tsConfigContent = tsConfigInfo.third
        }

        // 解析 pyproject.toml 并提取 Python 包名
        val pythonPackageName = if (hasPyprojectToml) {
            parsePyprojectToml(pluginDir)
        } else {
            null
        }

        // 从README中提取配置示例
        val configExample = extractConfigExample(readmeContent)

        // 确定项目类型
        val projectType =
                when {
                    // TypeScript项目特征: 有tsconfig.json或.ts文件
                    hasTsConfig || hasTsFiles || hasTypeScriptDependency -> ProjectType.TYPESCRIPT
                    // Node.js项目特征
                    hasPackageJson || (jsFiles != null && jsFiles.isNotEmpty()) ->
                            ProjectType.NODEJS
                    // Python项目特征
                    hasRequirementsTxt ||
                            hasPyprojectToml ||
                            hasSetupPy ||
                            (pythonFiles != null && pythonFiles.isNotEmpty()) -> ProjectType.PYTHON
                    else -> ProjectType.UNKNOWN
                }

        // 验证提取的配置是否可信
        val validatedConfigExample = validateConfigExample(configExample, projectType)

        return ProjectStructure(
                type = projectType,
                hasRequirementsTxt = hasRequirementsTxt,
                hasPyprojectToml = hasPyprojectToml,
                hasSetupPy = hasSetupPy,
                hasPackageJson = hasPackageJson,
                hasTsConfig = hasTsConfig,
                mainPythonModule = mainPythonModule,
                mainJsFile = mainJsFile,
                mainTsFile = mainTsFile,
                hasTsFiles = hasTsFiles,
                configExample = validatedConfigExample,
                moduleNameFromConfig = null,  // 已废弃，现在使用 pythonPackageName
                hasTypeScriptDependency = hasTypeScriptDependency,
                packageJsonScripts = packageJsonScripts,
                packageJsonContent = packageJsonContent,
                tsConfigOutDir = tsConfigOutDir,
                tsConfigRootDir = tsConfigRootDir,
                tsConfigContent = tsConfigContent,
                pythonPackageName = pythonPackageName
        )
    }

    /** 解析tsconfig.json并提取outDir和rootDir配置 */
    private fun parseTsConfig(pluginDir: File): Triple<String?, String?, String?> {
        val tsConfigFile = File(pluginDir, "tsconfig.json")
        if (!tsConfigFile.exists()) {
            return Triple(null, null, null)
        }

        try {
            val tsConfigContent = tsConfigFile.readText()

            // 移除注释（简单处理，可能不完全准确但够用）
            val contentWithoutComments =
                    tsConfigContent
                            .replace(Regex("//.*"), "") // 移除单行注释
                            .replace(Regex("/\\*[\\s\\S]*?\\*/"), "") // 移除多行注释

            val tsConfig = JSONObject(contentWithoutComments)
            val compilerOptions = tsConfig.optJSONObject("compilerOptions")

            // 获取原始路径
            val rawOutDir = compilerOptions?.optString("outDir")
            val rawRootDir = compilerOptions?.optString("rootDir")
            
            // 标准化路径：移除 ./ 前缀和尾部斜杠
            val outDir = rawOutDir?.removePrefix("./")?.removeSuffix("/")?.ifEmpty { null }
            val rootDir = rawRootDir?.removePrefix("./")?.removeSuffix("/")?.ifEmpty { null }

            AppLogger.d(
                    TAG,
                    "解析tsconfig.json - outDir: $outDir (原始: $rawOutDir), rootDir: $rootDir (原始: $rawRootDir)"
            )

            return Triple(outDir, rootDir, tsConfigContent)
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析tsconfig.json失败", e)
            return Triple(null, null, null)
        }
    }

    /** 解析 pyproject.toml 并提取 Python 包名 */
    private fun parsePyprojectToml(pluginDir: File): String? {
        val pyprojectFile = File(pluginDir, "pyproject.toml")
        if (!pyprojectFile.exists()) {
            return null
        }

        try {
            val content = pyprojectFile.readText()
            AppLogger.d(TAG, "开始解析 pyproject.toml")

            // 方法1: 从 [project.scripts] 提取完整的模块路径
            // 例如: word_mcp_server = "word_document_server.main:run_server"
            // 应该提取 "word_document_server.main"
            val scriptsSection = """\[project\.scripts\]([\s\S]*?)(?=\n\[|$)""".toRegex().find(content)
            if (scriptsSection != null) {
                val scriptsSectionContent = scriptsSection.groupValues[1]
                // 匹配 script_name = "module.path:function"，提取完整的模块路径
                val scriptPattern = """^\s*[\w-]+\s*=\s*"([^:"]+)""".toRegex(RegexOption.MULTILINE)
                scriptPattern.find(scriptsSectionContent)?.let { match ->
                    val modulePath = match.groupValues[1].trim()
                    if (modulePath.isNotBlank() && !modulePath.contains("/")) {
                        AppLogger.d(TAG, "从 [project.scripts] 提取到模块路径: $modulePath")
                        return modulePath
                    }
                }
            }

            // 方法2: 从 [tool.hatch.build.targets.wheel] 的 packages 提取
            // 例如: packages = ["src/excel_mcp"]
            val packagesPattern = """packages\s*=\s*\["([^"]+)"\]""".toRegex()
            packagesPattern.find(content)?.let { match ->
                val packagePath = match.groupValues[1]
                // 提取最后一个路径部分作为包名
                val packageName = packagePath.split('/').last()
                if (packageName.isNotBlank()) {
                    AppLogger.d(TAG, "从 packages 提取到包名: $packageName")
                    return packageName
                }
            }

            // 方法3: 从 [project] 的 name 字段提取，转换为模块名格式
            val namePattern = """^\s*name\s*=\s*"([^"]+)"\s*$""".toRegex(RegexOption.MULTILINE)
            namePattern.find(content)?.let { match ->
                val projectName = match.groupValues[1]
                // 将连字符转换为下划线（Python 模块命名约定）
                val packageName = projectName.replace("-", "_")
                if (packageName.isNotBlank()) {
                    AppLogger.d(TAG, "从 [project] name 提取到包名: $packageName")
                    return packageName
                }
            }

            AppLogger.w(TAG, "无法从 pyproject.toml 提取包名")
            return null
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析 pyproject.toml 失败", e)
            return null
        }
    }

    /** 查找主Python模块 */
    private fun findMainPythonModule(pluginDir: File, pythonFiles: Array<File>?): String? {
        // 检查常见的Python入口文件
        val commonEntryFiles = listOf("main.py", "__main__.py", "app.py", "server.py")

        // 检查src目录下是否有与目录同名的Python包
        val srcDir = File(pluginDir, "src")
        if (srcDir.exists() && srcDir.isDirectory) {
            val packages = srcDir.listFiles { file -> file.isDirectory }
            packages?.forEach { pkg ->
                val initFile = File(pkg, "__init__.py")
                if (initFile.exists()) {
                    return pkg.name
                }
            }
        }

        // 检查常见入口文件
        commonEntryFiles.forEach { filename ->
            val file = File(pluginDir, filename)
            if (file.exists()) {
                // 从文件名推断模块名
                val moduleName = filename.removeSuffix(".py")
                if (moduleName != "__main__") {
                    return moduleName
                } else {
                    // 对于__main__.py，使用目录名作为模块名
                    return pluginDir.name.replace("-", "_").lowercase()
                }
            }
        }

        // 如果是典型的Python包结构
        val initFile = File(pluginDir, "__init__.py")
        if (initFile.exists()) {
            return pluginDir.name.replace("-", "_").lowercase()
        }

        // 最后尝试查找目录名对应的模块
        val dirNameModule = pluginDir.name.replace("-", "_").lowercase()
        val dirNameModuleFile = File(pluginDir, "${dirNameModule}.py")
        if (dirNameModuleFile.exists()) {
            return dirNameModule
        }

        return null
    }

    /** 查找主JS文件 */
    private fun findMainJsFile(pluginDir: File, jsFiles: Array<File>?): String? {
        // 如果有package.json，尝试从中提取入口点
        val packageJsonFile = File(pluginDir, "package.json")
        if (packageJsonFile.exists()) {
            try {
                val packageJson = JSONObject(packageJsonFile.readText())
                if (packageJson.has("main")) {
                    return packageJson.getString("main")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "解析package.json失败", e)
            }
        }

        // 检查常见的JS入口文件
        val commonEntryFiles = listOf("index.js", "server.js", "app.js", "main.js")
        commonEntryFiles.forEach { filename ->
            val file = File(pluginDir, filename)
            if (file.exists()) {
                return filename
            }
        }

        return null
    }

    /** 查找主TS文件 */
    private fun findMainTsFile(pluginDir: File, tsFiles: Array<File>?): String? {
        // 如果有package.json，尝试从中提取入口点
        val packageJsonFile = File(pluginDir, "package.json")
        if (packageJsonFile.exists()) {
            try {
                val packageJson = JSONObject(packageJsonFile.readText())

                // 检查bin字段（优先级最高，因为它明确指定了可执行入口）
                if (packageJson.has("bin")) {
                    val binField = packageJson.get("bin")
                    val binPath = when {
                        binField is String -> binField
                        binField is JSONObject -> binField.keys().asSequence().firstOrNull()?.let { binField.getString(it) }
                        else -> null
                    }
                    if (binPath != null && binPath.endsWith(".js")) {
                        // 尝试推断.ts源文件位置：dist/stdio.js -> src/stdio.ts
                        val jsFileName = binPath.substringAfterLast('/')
                        val tsFileName = jsFileName.replace(".js", ".ts")
                        listOf("src/$tsFileName", tsFileName, binPath.replace(".js", ".ts")).forEach { 
                            if (File(pluginDir, it).exists()) return it
                        }
                    } else if (binPath != null && binPath.endsWith(".ts")) {
                        return binPath
                    }
                }

                // 检查main字段
                if (packageJson.has("main")) {
                    val mainField = packageJson.getString("main")
                    // 如果main字段是.ts文件或没有扩展名(可能是TypeScript模块)
                    if (mainField.endsWith(".ts") || !mainField.contains(".")) {
                        return mainField
                    }
                }

                // 检查特殊的源文件字段
                if (packageJson.has("source") && packageJson.getString("source").endsWith(".ts")) {
                    return packageJson.getString("source")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "解析package.json失败", e)
            }
        }

        // 检查src目录下的index.ts
        val srcDir = File(pluginDir, "src")
        if (srcDir.exists() && srcDir.isDirectory) {
            val srcIndexTs = File(srcDir, "index.ts")
            if (srcIndexTs.exists()) {
                return "src/index.ts"
            }

            val srcMainTs = File(srcDir, "main.ts")
            if (srcMainTs.exists()) {
                return "src/main.ts"
            }

            val srcAppTs = File(srcDir, "app.ts")
            if (srcAppTs.exists()) {
                return "src/app.ts"
            }

            val srcServerTs = File(srcDir, "server.ts")
            if (srcServerTs.exists()) {
                return "src/server.ts"
            }
        }

        // 检查常见的TS入口文件
        val commonEntryFiles = listOf("index.ts", "server.ts", "app.ts", "main.ts")
        commonEntryFiles.forEach { filename ->
            val file = File(pluginDir, filename)
            if (file.exists()) {
                return filename
            }
        }

        // 如果都没找到，返回第一个.ts文件
        if (tsFiles != null && tsFiles.isNotEmpty()) {
            return tsFiles[0].name
        }

        return null
    }

    /** 从README中提取配置示例 */
    private fun extractConfigExample(readmeContent: String): String? {
        // 尝试从代码块中找到JSON配置
        val codeBlocks = CODE_BLOCK_REGEX.findAll(readmeContent)
        for (match in codeBlocks) {
            val codeContent = match.groupValues[1].trim()
            if (codeContent.contains("\"mcpServers\"") ||
                            codeContent.contains("\"command\"") ||
                            codeContent.contains("\"args\"")
            ) {
                return codeContent
            }
        }

        // 如果代码块没找到，尝试用正则表达式匹配整个JSON配置
        val jsonMatches = JSON_CONFIG_REGEX.findAll(readmeContent)
        jsonMatches.forEach { match ->
            return match.value
        }

        return null
    }

    /** 验证配置示例是否可信 */
    private fun validateConfigExample(configExample: String?, projectType: ProjectType): String? {
        if (configExample == null) return null

        val lowerCaseConfig = configExample.lowercase()

        // 对于JS/Node/TS项目，只有包含@字符才可信
        if (projectType == ProjectType.TYPESCRIPT || projectType == ProjectType.NODEJS) {
            if (!configExample.contains("@")) {
                AppLogger.w(TAG, "JS/Node/TS项目配置中未找到@字符，该配置不可信，已过滤")
                return null
            }
        }

        // 对于Python项目，如果包含path/to或pathto/则一票否决
        if (projectType == ProjectType.PYTHON) {
            if (lowerCaseConfig.contains("path/to") || lowerCaseConfig.contains("pathto/")) {
                AppLogger.w(TAG, "Python项目配置中包含占位符路径(path/to或pathto/)，该配置不可信，已过滤")
                return null
            }
        }

        return configExample
    }

    /** 查找README文件 */
    fun findReadmeFile(pluginDir: File): File? {
        // 先查找根目录下的README文件
        var readmeFile = File(pluginDir, "README.md")
        if (readmeFile.exists() && readmeFile.isFile) {
            return readmeFile
        }

        // 查找小写readme文件
        readmeFile = File(pluginDir, "readme.md")
        if (readmeFile.exists() && readmeFile.isFile) {
            return readmeFile
        }

        // 查找INSTALL.md文件
        readmeFile = File(pluginDir, "INSTALL.md")
        if (readmeFile.exists() && readmeFile.isFile) {
            return readmeFile
        }

        // 查找docs目录下的README文件
        val docsDir = File(pluginDir, "docs")
        if (docsDir.exists() && docsDir.isDirectory) {
            readmeFile = File(docsDir, "README.md")
            if (readmeFile.exists() && readmeFile.isFile) {
                return readmeFile
            }

            // 查找小写readme文件
            readmeFile = File(docsDir, "readme.md")
            if (readmeFile.exists() && readmeFile.isFile) {
                return readmeFile
            }
        }

        // 查找任何可能的md文件
        val mdFiles = pluginDir.listFiles { file -> file.extension.equals("md", ignoreCase = true) }
        if (mdFiles != null && mdFiles.isNotEmpty()) {
            return mdFiles.first()
        }

        return null
    }
}
