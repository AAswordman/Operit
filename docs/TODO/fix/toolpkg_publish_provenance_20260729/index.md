---
For_Agent: ToolPkg 发布来源注入与受保护归档
---

# ToolPkg 发布处理

## 现状

直接上传过去只在开启混淆时处理脚本；关闭混淆会原样上传，因此不会写入市场来源。ToolPkg 归档中的 `src/` 和 source map 也会随包保留。

## 变更

- 直接上传始终写入当前登录用户的市场来源。
- 关闭混淆时保留可执行源码，只改写脚本 `METADATA` 或 ToolPkg 主入口。
- 开启混淆时沿着 manifest 入口和静态相对模块引用构建可达条目集合，只保留 manifest、主入口、子包入口、声明资源/WASM 和这些入口依赖的模块；未到达的 `src/`、source map、测试及构建文件都会被排除。
- 多子包仍分别处理，市场来源是 ToolPkg 容器级信息，由主入口承载。

已有 GitHub Release 资产是外部不可变引用，加载该模式不会改写远端文件；要获得来源注入，需要通过直接上传发布处理后的资产。

## 作用域

- `app/src/main/java/com/ai/assistance/operit/util/ToolPkgArtifactMinifier.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/features/packages/market/GitHubForgePublishService.kt`

[DONE]
