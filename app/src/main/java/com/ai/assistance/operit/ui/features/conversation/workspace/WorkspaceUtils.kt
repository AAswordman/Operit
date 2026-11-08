package com.ai.assistance.operit.ui.features.conversation.workspace

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.files.OperitPaths
import java.io.File
import java.io.IOException

fun createAndGetDefaultWorkspace(context: Context, chatId: String): File {
    return createAndGetDefaultWorkspace(context, chatId, null)
}

fun createAndResetWorkspaceDirectory(context: Context, chatId: String): File {
    val workspaceDir = File(getWorkspacePath(context, chatId))
    if (workspaceDir.exists()) {
        workspaceDir.deleteRecursively()
    }
    workspaceDir.mkdirs()
    return workspaceDir
}

fun createAndGetDefaultWorkspace(context: Context, chatId: String, projectType: String?): File {
    if (projectType != "blank") {
        check(com.ai.assistance.operit.data.resources.packs.PackResources.isReady(context, "template-${projectType ?: "web"}")) { context.getString(R.string.hub_template_missing) }
    }
    // 创建内部存储工作区
    val workspacePath = getWorkspacePath(context, chatId)
    ensureWorkspaceDirExists(workspacePath)

    val webContentDir = File(workspacePath)

    // 根据项目类型复制模板文件并创建配置
    when (projectType) {
        "android" -> {
            copyTemplateFiles(context, webContentDir, "android")
            createProjectConfigIfNeeded(context, webContentDir, ProjectType.ANDROID)
        }
        "flutter" -> {
            copyTemplateFiles(context, webContentDir, "flutter")
            createProjectConfigIfNeeded(context, webContentDir, ProjectType.FLUTTER)
        }
        "node" -> {
            copyTemplateFiles(context, webContentDir, "node")
            createProjectConfigIfNeeded(context, webContentDir, ProjectType.NODE)
        }
        "typescript" -> {
            copyTemplateFiles(context, webContentDir, "typescript")
            createProjectConfigIfNeeded(context, webContentDir, ProjectType.TYPESCRIPT)
        }
        "python" -> {
            copyTemplateFiles(context, webContentDir, "python")
            createProjectConfigIfNeeded(context, webContentDir, ProjectType.PYTHON)
        }
        "java" -> {
            copyTemplateFiles(context, webContentDir, "java")
            createProjectConfigIfNeeded(context, webContentDir, ProjectType.JAVA)
        }
        "go" -> {
            copyTemplateFiles(context, webContentDir, "go")
            createProjectConfigIfNeeded(context, webContentDir, ProjectType.GO)
        }
        "office" -> {
            copyTemplateFiles(context, webContentDir, "office")
            createProjectConfigIfNeeded(context, webContentDir, ProjectType.OFFICE)
        }
        "blank" -> {
            createProjectConfigIfNeeded(context, webContentDir, ProjectType.BLANK)
        }
        else -> {
            copyTemplateFiles(context, webContentDir, "web")
            createProjectConfigIfNeeded(context, webContentDir, ProjectType.WEB)
        }
    }

    return webContentDir
}

/**
 * 获取工作区路径（新位置：内部存储）
 * 路径: /data/data/com.ai.assistance.operit/files/workspace/{chatId}
 */
fun getWorkspacePath(context: Context, chatId: String): String {
    return File(context.filesDir, "workspace/$chatId").absolutePath
}

/**
 * 获取旧的工作区路径（外部存储）
 * 路径: /sdcard/Download/Operit/workspace/{chatId}
 */
fun getLegacyWorkspacePath(chatId: String): String {
    return OperitPaths.workspacePathSdcard(chatId)
}

fun ensureWorkspaceDirExists(path: String): File {
    val workspaceDir = File(path)
    if (!workspaceDir.exists()) {
        workspaceDir.mkdirs()
    }
    return workspaceDir
}

private enum class ProjectType {
    WEB, ANDROID, FLUTTER, NODE, TYPESCRIPT, PYTHON, JAVA, GO, OFFICE, BLANK
}

/**
 * 生成空白项目配置JSON
 */
private fun generateBlankProjectConfig(context: Context): String {
    return """
{
    "projectType": "blank",
    "title": "${context.getString(R.string.workspace_project_blank_title)}",
    "description": "${context.getString(R.string.workspace_project_blank_description)}",
    "server": {
        "enabled": false,
        "port": 8080,
        "autoStart": false
    },
    "preview": {
        "type": "terminal",
        "url": "",
        "showPreviewButton": false,
        "previewButtonLabel": ""
    },
    "commands": [],
    "export": {
        "enabled": false
    }
}
""".trimIndent()
}

/**
 * 生成Android项目配置JSON
 */
private fun generateAndroidProjectConfig(context: Context): String {
    return """
{
    "projectType": "android",
    "title": "${context.getString(R.string.workspace_project_android_title)}",
    "description": "${context.getString(R.string.workspace_project_android_description)}",
    "server": {
        "enabled": false,
        "port": 8080,
        "autoStart": false
    },
    "preview": {
        "type": "terminal",
        "url": "",
        "showPreviewButton": false,
        "previewButtonLabel": ""
    },
    "commands": [
        {
            "id": "android_setup_env",
            "label": "${context.getString(R.string.workspace_cmd_android_setup_env)}",
            "command": "bash setup_android_env.sh",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "gradle_assemble_debug",
            "label": "${context.getString(R.string.workspace_cmd_android_assemble_debug)}",
            "command": "./gradlew assembleDebug",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "gradle_assemble_release",
            "label": "${context.getString(R.string.workspace_cmd_android_assemble_release)}",
            "command": "./gradlew assembleRelease",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "android_install_debug_apk",
            "label": "${context.getString(R.string.workspace_cmd_android_install_debug)}",
            "tool": "install_app",
            "toolParameters": {
                "path": "${'$'}WORKSPACE/app/build/outputs/apk/debug/app-debug.apk"
            },
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "share_release_apk",
            "label": "${context.getString(R.string.workspace_cmd_android_share_release_apk)}",
            "tool": "share_file",
            "toolParameters": {
                "path": "${'$'}WORKSPACE/app/build/outputs/apk/release/app-release.apk",
                "title": "Share Release APK"
            },
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "gradle_lint",
            "label": "${context.getString(R.string.workspace_cmd_android_lint)}",
            "command": "./gradlew lint",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "gradle_test",
            "label": "${context.getString(R.string.workspace_cmd_android_test)}",
            "command": "./gradlew test",
            "workingDir": ".",
            "shell": true
        }
    ],
    "export": {
        "enabled": false
    }
}
""".trimIndent()
}

/**
 * 生成Flutter项目配置JSON
 */
private fun generateFlutterProjectConfig(context: Context): String {
    return """
{
    "projectType": "flutter",
    "title": "${context.getString(R.string.workspace_project_flutter_title)}",
    "description": "${context.getString(R.string.workspace_project_flutter_description)}",
    "server": {
        "enabled": false,
        "port": 5013,
        "autoStart": false
    },
    "preview": {
        "type": "terminal",
        "url": "http://localhost:5013",
        "showPreviewButton": true,
        "previewButtonLabel": "${context.getString(R.string.workspace_preview_button_label_browser)}"
    },
    "commands": [
        {
            "id": "flutter_android_setup_env",
            "label": "${context.getString(R.string.workspace_cmd_flutter_setup_env)}",
            "command": "bash android/setup_android_env.sh",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "flutter_doctor",
            "label": "${context.getString(R.string.workspace_cmd_flutter_doctor)}",
            "command": "flutter doctor",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "flutter_pub_get",
            "label": "${context.getString(R.string.workspace_cmd_flutter_pub_get)}",
            "command": "flutter pub get",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "flutter_run_web_server",
            "label": "${context.getString(R.string.workspace_cmd_flutter_run_web)}",
            "command": "flutter run -d web-server --web-hostname 0.0.0.0 --web-port 5013",
            "workingDir": ".",
            "shell": true,
            "usesDedicatedSession": true,
            "sessionTitle": "Flutter Web Server"
        },
        {
            "id": "flutter_analyze",
            "label": "${context.getString(R.string.workspace_cmd_flutter_analyze)}",
            "command": "flutter analyze",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "flutter_test",
            "label": "${context.getString(R.string.workspace_cmd_flutter_test)}",
            "command": "flutter test",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "flutter_build_apk",
            "label": "${context.getString(R.string.workspace_cmd_flutter_build_apk)}",
            "command": "flutter build apk",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "flutter_build_web",
            "label": "${context.getString(R.string.workspace_cmd_flutter_build_web)}",
            "command": "flutter build web --no-tree-shake-icons",
            "workingDir": ".",
            "shell": true
        }
    ],
    "export": {
        "enabled": false
    }
}
""".trimIndent()
}

/**
 * 生成Web项目配置JSON
 */
private fun generateWebProjectConfig(context: Context): String {
    return """
{
    "projectType": "web",
    "title": "${context.getString(R.string.workspace_project_web_title)}",
    "description": "${context.getString(R.string.workspace_project_web_description)}",
    "server": {
        "enabled": true,
        "port": 8093,
        "autoStart": true
    },
    "preview": {
        "type": "browser",
        "url": "http://localhost:8093"
    },
    "commands": [],
    "export": {
        "enabled": true
    }
}
""".trimIndent()
}

/**
 * 生成Node.js项目配置JSON
 */
private fun generateNodeProjectConfig(context: Context): String {
    return """
{
    "projectType": "node",
    "title": "${context.getString(R.string.workspace_project_node_title)}",
    "description": "${context.getString(R.string.workspace_project_node_description)}",
    "server": {
        "enabled": false,
        "port": 3000,
        "autoStart": false
    },
    "preview": {
        "type": "terminal",
        "url": "http://localhost:3000",
        "showPreviewButton": true,
        "previewButtonLabel": "${context.getString(R.string.workspace_preview_button_label_browser)}"
    },
    "commands": [
        {
            "id": "npm_init",
            "label": "npm init -y",
            "command": "npm init -y",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "npm_install",
            "label": "npm install",
            "command": "npm install",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "npm_start",
            "label": "npm start",
            "command": "npm start",
            "workingDir": ".",
            "shell": true,
            "usesDedicatedSession": true,
            "sessionTitle": "npm start"
        },
        {
            "id": "npm_test",
            "label": "npm test",
            "command": "npm test",
            "workingDir": ".",
            "shell": true
        }
    ],
    "export": {
        "enabled": false
    }
}
""".trimIndent()
}

/**
 * 生成TypeScript项目配置JSON
 */
private fun generateTypeScriptProjectConfig(context: Context): String {
    return """
{
    "projectType": "typescript",
    "title": "${context.getString(R.string.workspace_project_typescript_title)}",
    "description": "${context.getString(R.string.workspace_project_typescript_description)}",
    "server": {
        "enabled": false,
        "port": 3000,
        "autoStart": false
    },
    "preview": {
        "type": "terminal",
        "url": "",
        "showPreviewButton": false
    },
    "commands": [
        {
            "id": "pnpm_install",
            "label": "pnpm install",
            "command": "pnpm install",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "pnpm_build",
            "label": "pnpm build",
            "command": "pnpm build",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "tsc_watch",
            "label": "tsc watch",
            "command": "pnpm exec tsc --watch",
            "workingDir": ".",
            "shell": true,
            "usesDedicatedSession": true,
            "sessionTitle": "TypeScript Watch"
        },
        {
            "id": "pnpm_start",
            "label": "pnpm start",
            "command": "pnpm start",
            "workingDir": ".",
            "shell": true,
            "usesDedicatedSession": true,
            "sessionTitle": "pnpm start"
        },
        {
            "id": "pnpm_list",
            "label": "pnpm list",
            "command": "pnpm list",
            "workingDir": ".",
            "shell": true
        }
    ],
    "export": {
        "enabled": false
    }
}
""".trimIndent()
}

/**
 * 生成Python项目配置JSON
 */
private fun generatePythonProjectConfig(context: Context): String {
    return """
{
    "projectType": "python",
    "title": "${context.getString(R.string.workspace_project_python_title)}",
    "description": "${context.getString(R.string.workspace_project_python_description)}",
    "server": {
        "enabled": false,
        "port": 8000,
        "autoStart": false
    },
    "preview": {
        "type": "terminal",
        "url": "",
        "showPreviewButton": false
    },
    "commands": [
        {
            "id": "venv_create",
            "label": "${context.getString(R.string.workspace_cmd_python_venv_create)}",
            "command": "python -m venv venv",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "venv_activate",
            "label": "${context.getString(R.string.workspace_cmd_python_venv_activate)}",
            "command": "source venv/bin/activate || venv\\Scripts\\activate",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "pip_install",
            "label": "${context.getString(R.string.workspace_cmd_python_pip_install)}",
            "command": "pip install -r requirements.txt",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "pip_list",
            "label": "${context.getString(R.string.workspace_cmd_python_pip_list)}",
            "command": "pip list",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "python_run",
            "label": "${context.getString(R.string.workspace_cmd_python_run)}",
            "command": "python main.py",
            "workingDir": ".",
            "shell": true
        }
    ],
    "export": {
        "enabled": false
    }
}
""".trimIndent()
}

/**
 * 生成Java项目配置JSON
 */
private fun generateJavaProjectConfig(context: Context): String {
    return """
{
    "projectType": "java",
    "title": "${context.getString(R.string.workspace_project_java_title)}",
    "description": "${context.getString(R.string.workspace_project_java_description)}",
    "server": {
        "enabled": false,
        "port": 8080,
        "autoStart": false
    },
    "preview": {
        "type": "terminal",
        "url": "",
        "showPreviewButton": false
    },
    "commands": [
        {
            "id": "gradle_init",
            "label": "${context.getString(R.string.workspace_cmd_java_gradle_init)}",
            "command": "gradle wrapper --gradle-version 8.5",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "gradle_build",
            "label": "${context.getString(R.string.workspace_cmd_java_gradle_build)}",
            "command": "./gradlew build || gradle build",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "gradle_run",
            "label": "${context.getString(R.string.workspace_cmd_java_gradle_run)}",
            "command": "./gradlew run || gradle run",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "gradle_test",
            "label": "${context.getString(R.string.workspace_cmd_java_gradle_test)}",
            "command": "./gradlew test || gradle test",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "gradle_jar",
            "label": "${context.getString(R.string.workspace_cmd_java_gradle_jar)}",
            "command": "./gradlew jar || gradle jar",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "gradle_clean",
            "label": "${context.getString(R.string.workspace_cmd_java_gradle_clean)}",
            "command": "./gradlew clean || gradle clean",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "gradle_tasks",
            "label": "${context.getString(R.string.workspace_cmd_java_gradle_tasks)}",
            "command": "./gradlew tasks || gradle tasks",
            "workingDir": ".",
            "shell": true
        }
    ],
    "export": {
        "enabled": false
    }
}
""".trimIndent()
}

/**
 * 生成Go项目配置JSON
 */
private fun generateGoProjectConfig(context: Context): String {
    return """
{
    "projectType": "go",
    "title": "${context.getString(R.string.workspace_project_go_title)}",
    "description": "${context.getString(R.string.workspace_project_go_description)}",
    "server": {
        "enabled": false,
        "port": 8080,
        "autoStart": false
    },
    "preview": {
        "type": "terminal",
        "url": "",
        "showPreviewButton": false
    },
    "commands": [
        {
            "id": "go_mod_init",
            "label": "go mod init",
            "command": "go mod init myapp",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "go_mod_tidy",
            "label": "go mod tidy",
            "command": "go mod tidy",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "go_run",
            "label": "go run main.go",
            "command": "go run main.go",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "go_build",
            "label": "go build",
            "command": "go build",
            "workingDir": ".",
            "shell": true
        }
    ],
    "export": {
        "enabled": false
    }
}
""".trimIndent()
}

/**
 * 生成办公文档项目配置JSON
 */
private fun generateOfficeProjectConfig(context: Context): String {
    return """
{
    "projectType": "office",
    "title": "${context.getString(R.string.workspace_project_office_title)}",
    "description": "${context.getString(R.string.workspace_project_office_description)}",
    "server": {
        "enabled": false,
        "port": 8080,
        "autoStart": false
    },
    "preview": {
        "type": "terminal",
        "url": "",
        "showPreviewButton": false,
        "previewButtonLabel": ""
    },
    "commands": [],
    "export": {
        "enabled": false
    }
}
""".trimIndent()
}

/** 工程模板由离线资源准备，缺失时抛出明确错误，不能创建半成品工作区。 */
private fun copyTemplateFiles(context: Context, workspaceDir: File, templateName: String) {
    com.ai.assistance.operit.data.resources.packs.PackResources.copyTemplate(context, templateName, workspaceDir)
}
private fun createProjectConfigIfNeeded(context: Context, workspaceDir: File, projectType: ProjectType) {
    // 创建 .operit 目录和 config.json
    val operitDir = File(workspaceDir, ".operit")
    if (!operitDir.exists()) {
        operitDir.mkdirs()
    }

    val configFile = File(operitDir, "config.json")
    if (configFile.exists()) {
        return
    }

    val configContent = when (projectType) {
        ProjectType.WEB -> generateWebProjectConfig(context)
        ProjectType.ANDROID -> generateAndroidProjectConfig(context)
        ProjectType.FLUTTER -> generateFlutterProjectConfig(context)
        ProjectType.NODE -> generateNodeProjectConfig(context)
        ProjectType.TYPESCRIPT -> generateTypeScriptProjectConfig(context)
        ProjectType.PYTHON -> generatePythonProjectConfig(context)
        ProjectType.JAVA -> generateJavaProjectConfig(context)
        ProjectType.GO -> generateGoProjectConfig(context)
        ProjectType.OFFICE -> generateOfficeProjectConfig(context)
        ProjectType.BLANK -> generateBlankProjectConfig(context)
    }

    try {
        configFile.writeText(configContent.trimIndent())
    } catch (_: IOException) {
        // Ignore write errors for now
    }
}
