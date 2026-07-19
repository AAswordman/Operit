---
For_Agent: 插件市场保护链路 JS 语法树压缩改造记录
---

# 插件市场保护链路保护格式改造

## 现状
- 市场加密发布会把插件资源写成 Operit 保护格式
- `.toolpkg` 内部 manifest 需要保持明文，以便安装和加载时定位入口与资源表
- 保护格式由 native 层读取构建期密钥并完成 AEAD 加解密

## 意图
- 保护格式只保留一套当前实现，不做 v1、v2 分流
- 写入链路改为 JS 源码先经过 AST minifier，再进入 native 加密
- AST minifier 只去除注释和空白，不启用 mangle 或 compress，避免改变外部调用接口和 export 名
- `.toolpkg` 内除 manifest 外的 entry 都进入保护格式，脚本做 AST minify，资源保持原始 bytes 后加密
- native 保护容器使用 ChaCha20-Poly1305 结构，密钥由构建期 secret 通过 HKDF-SHA256 派生
- 加密保护逻辑拆到独立 `toolpkgprotect` native 库，单独启用隐藏符号、section GC、strip 和链接加固
- 构建期 secret 不以连续字符串宏写入 C++，改为生成异或 byte array 头文件供 `toolpkgprotect` 运行时组装
- 读取链路只负责识别 Operit 保护容器并解密，解密结果就是可执行 JS 文本
- 市场 metadata 使用无版本后缀的 `operit-protected`

## 作用域
- `ToolPkgProtection` Kotlin 包装层
- `ToolPkgJsAstMinifier` 发布期 AST minifier
- `native_toolpkg_protection.cpp` native 保护格式实现
- `CMakeLists.txt` 中的 `toolpkgprotect` 独立 native target
- `PackageManager` 资源读取、导出和工作区模板复制
- 插件市场加密发布说明文案

## 完成标准
- 新写入的保护 payload 内部是 AST 压缩后的 JS 文本
- 外部调用接口、CommonJS `exports.*` 和 ES module export 名保持不变
- `.toolpkg` 资源 entry 在包内保持密文，读取、导出、模板复制时得到明文内容
- 保护 JNI 加载独立 `toolpkgprotect`，不再挂在通用 `streamnative` 库里
- 编译产物不保留完整连续的 `OPERIT_TOOLPKG_PROTECTION_SECRET` 字符串
- 读取保护 payload 时无需额外展开步骤
- 旧的版本化保护标识从代码路径移除

[DONE]
