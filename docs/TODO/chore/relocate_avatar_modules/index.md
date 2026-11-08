---
For_Agent: 将模型模块集中到 avator 目录
---

# Avatar 模块目录迁移

## 旧状况

- `dragonbones`、`fbx`、`mmd` 分散在仓库根目录
- Gradle、CMake、CI 和架构文档直接使用根目录路径

## 修改意图

- 将三个模块整体移动到 `avator/dragonbones`、`avator/fbx`、`avator/mmd`
- 保留 Gradle 模块名 `:dragonbones`、`:fbx`、`:mmd`，不改变应用依赖接口
- 同步 CMake 公共脚本路径、CI 触发路径和仓库架构文档

## 作用域

- `avator/dragonbones/`
- `avator/fbx/`
- `avator/mmd/`
- `settings.gradle.kts`
- `.github/workflows/android-build.yml`
- `Repo_Arch_Basic.md`
- `docs/TODO/ci/native_dependency_fetchcontent/index.md`

## 状态

[DONE]
