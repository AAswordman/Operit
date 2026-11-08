---
For_Agent: 将本地大模型模块集中到 llm 目录
---

# LLM 模块目录迁移

## 旧状况

- `llama`、`mnn` 分散在仓库根目录
- Gradle、CMake、CI 和架构文档直接使用根目录路径

## 修改意图

- 将两个模块整体移动到 `llm/llama`、`llm/mnn`
- 保留 Gradle 模块名 `:llama`、`:mnn`，不改变应用依赖接口
- 同步 CMake 公共脚本路径、CI 触发路径和仓库架构文档

## 作用域

- `llm/llama/`
- `llm/mnn/`
- `settings.gradle.kts`
- `.github/workflows/android-build.yml`
- `Repo_Arch_Basic.md`
- `docs/TODO/ci/native_dependency_fetchcontent/index.md`

## 状态

[DONE]
