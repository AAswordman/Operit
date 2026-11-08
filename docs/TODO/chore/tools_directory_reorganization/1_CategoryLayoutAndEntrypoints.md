---
scope: tools
---

# 分类布局与入口迁移

## 旧实现

多个独立工具直接位于 `tools/` 根目录。部分脚本从自身位置推导项目根目录，移动后会指向错误位置；文档、测试和应用内开发脚本下载地址也引用旧入口。

## 修改意图

- 将 ADB、ToolPkg、示例包、Shower、Compose DSL 与 native-ripgrep 入口分别归位
- 让每个移动后的脚本继续正确定位仓库根目录与同目录资源
- 将所有仓库内调用切换到新入口

## 新布局

```
tools/
	adb/
	compose_dsl/
	example_packages/
	ffmpeg/
	github/
	hotbuild/
	mcp_bridge/
	native_ripgrep/
	shell_identity_launcher/
	shower/
	string/
	toolpkg/
	sandboxpackage_dev_install_or_update.js
```

## 预期结果

除固定公开安装脚本外，根目录不再存放分散的可执行工具文件。每个新入口具有单一、可追踪的位置，且不存在旧路径兼容脚本。

[DONE]
