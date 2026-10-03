---
title: ToolPkg 快速开始
status: draft
---

# ToolPkg 快速开始

以下最小包注册一个聊天输入 Hook。示例只依赖 Operit 注入的 `ToolPkg` 和 `ChatInputHookEvent` 类型，不包含通用 JavaScript 教程。

## 文件布局

```text
hello_toolpkg/
	manifest.json
	tsconfig.json
	src/
		main.ts
	dist/
		main.js
```

`dist/main.js` 是宿主入口。`manifest.main` 必须指向构建后的 JavaScript 文件，而不是 TypeScript 源文件。

## Manifest

```json
{
  "schema_version": 1,
  "toolpkg_id": "com.example.hello",
  "version": "0.1.0",
  "api_version": "1.0.0",
  "main": "dist/main.js",
  "display_name": {
    "zh": "问候插件",
    "en": "Hello ToolPkg"
  },
  "description": {
    "zh": "演示 ToolPkg 注册入口和聊天输入 Hook。",
    "en": "Demonstrates the ToolPkg entry point and chat input hook."
  },
  "subpackages": [],
  "resources": []
}
```

`api_version` 是宿主 API 版本，不是包的 `version`。此示例只使用 `1.0.0` 基线。

## 主入口

```ts
export function registerToolPkg(): void {
  ToolPkg.registerChatInputHook({
    id: 'trim_message',
    function(event) {
      if (event.eventName !== 'submit_requested') return;

      const text = event.eventPayload.text ?? '';
      const normalized = text.trim();
      if (normalized !== text) {
        return { action: 'replace', text: normalized };
      }
    }
  });
}
```

宿主在包注册阶段调用导出的 `registerToolPkg()` 并收集注册声明。Hook 回调会在后续事件中执行；不要在注册函数内启动常驻逻辑。聊天输入 Hook 的完整提交、错误与超时契约见[Hook 页面](../05_hooks/chat_input.md)。

## TypeScript 配置

仓库示例 `examples/template_try/tsconfig.json` 将 `examples/types/**/*.d.ts` 纳入编译输入。独立项目需要把仓库类型声明安装/复制到项目类型路径，并在 `tsconfig.json` 的 `include` 中显式包含它们；否则 TypeScript 不会自动识别全局 `ToolPkg`。

## 构建与安装

从仓库根目录构建仓库内示例：

```bash
npx tsc -p examples/template_try/tsconfig.json
```

该命令按示例配置把 `examples/template_try/src/main.ts` 编译到 `examples/template_try/dist/`。新包需要将 Manifest、编译产物和声明的资源一起组成 ToolPkg 目录/归档。

开发阶段通过 `tools/toolpkg/debug_toolpkg.py` 安装刷新包；参数以脚本 `--help` 输出和[包格式与调试说明](../02_package_model/package_format.md)为准。不要用普通脚本单文件执行器代替 ToolPkg 安装流程，因为 ToolPkg 还需要解析 manifest 并运行注册入口。

## 继续阅读

- [包结构与入口](../02_package_model/package_structure.md)
- [ToolPkg 注册 API](../03_runtime/registry.md)
- [全局运行时 API](../03_runtime/global_api.md)
- [模块 API 索引](../04_modules/index.md)
- [ToolPkg API 版本与兼容](../09_compatibility/api_versions.md)