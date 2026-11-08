---
For_Agent: ToolPkg AssemblyScript WASM 企业核心模块接入记录
---

# ToolPkg AssemblyScript WASM 模块接入

## 现状
- ToolPkg 主运行时仍由 QuickJS 执行 JavaScript。
- 企业插件核心算法可以声明为 AssemblyScript `.wasm` 模块，随 `.toolpkg` 保护格式加密发布。
- Android 端已接入 native WAMR runtime，通过独立 `toolpkgwasm` so 执行 WASM。

## 意图
- 保持现有 JS 插件入口、`exports` 和外部接口调用方式稳定。
- 允许企业插件把核心算法用 AssemblyScript 编译成 `.wasm`。
- 让 manifest 能显式声明 `.wasm` 模块 ID、路径、导出函数名和 ABI。
- 让 `.wasm` 文件随 `.toolpkg` 资源进入现有保护格式，运行期按模块元数据读取密文资源的明文 bytes。
- 提供 `ToolPkg.wasm.call(moduleId, exportName, args)` JS 桥；JS 只负责入口调度和参数组织，核心逻辑进入 native WAMR 执行。
- 提供 TS-first 示例工程，让作者写 `src/main.ts` 和 `src/wasm/*.ts`，打包阶段生成宿主需要的 `main.js`。

## 作用域
- `ToolPkgManifest.wasmModules` 清单字段
- `ToolPkgWasmModuleRuntime` 运行时元数据
- 包详情中的 WASM 模块数量展示
- ToolPkg 格式文档中的 AssemblyScript 交付规范
- native WAMR runtime、Kotlin module cache、JS bridge 和 TS 声明

## 分步

1. Manifest 与详情模型
   - 解析 `wasm_modules`
   - 校验模块 ID、`.wasm` 路径和导出名
   - 在包详情中展示 WASM 模块数量
   - [DONE]

2. Android WASM runtime 选型
   - 对比 wasm3、WAMR 和 Wasmtime Android 集成成本
   - 确认 QuickJS 后端调用边界、内存传参方式和线程模型
   - 选定 WAMR native so，避免 JVM WASM runtime 执行热路径
   - [DONE]

3. `ToolPkg.wasm` JS 桥
   - 设计 `call` API
   - 只允许访问当前容器 manifest 声明的模块 ID
   - 用 typed JSON 明确定义数值参数和返回值边界
   - 当前 ABI：`i32`、`i64`、`f32`、`f64`
   - [DONE]

4. AssemblyScript + TS-first 模板
   - 提供 `src/wasm/core.as.ts`
   - 提供 `src/wasm/core.ts` typed facade
   - 提供 `src/main.ts`
   - 提供 `asconfig.json`
   - 提供 manifest 示例和打包脚本
   - 示例位置：`examples/toolpkg_wasm_demo`
   - [DONE]
