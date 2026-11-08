---
For_Agent: Restore DragonBones archive preparation after the avatar module relocation
---

# DragonBones CI 依赖路径

## 原本状况

角色模型模块迁移到 `avator/dragonbones` 后，本地忽略的 DragonBones runtime、RapidJSON 与 stb 仍然存在，因此本地编译正常。干净 CI 工作区没有这些目录，完整 Android 构建从迁移提交开始因缺少头文件失败。PR 路径分类也仍使用迁移前的模块根目录。

## 改动

- 在 `avator/dragonbones/CMakeLists.txt` 接入仓库现有的 GitHub archive 获取帮助函数
- 固定到与迁移前源码一致的 DragonBonesCPP 提交，不引入 submodule 或独立下载脚本
- 将 PR Android 模块分类改为迁移后的 `avator/` 与 `llm/` 路径
- 增加范围分类回归测试

## 预期结果

本地构建与干净 Candidate 工作区都通过现有 CMake archive 机制获得 DragonBones 原生源码，完整 Android 构建可以继续执行。

[DONE]
