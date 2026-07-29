---
name: SandboxPackage_DEV
description: 用于 Operit Sandbox Package 开发。
---

# SandboxPackage_DEV

## 第一部分：安装与更新

这个 skill 不再要求 AI 一次性手动下载 `SKILL.md`、两份 guide 文档和整套 `types`。

安装与更新都走同一个脚本：

- 先把安装脚本下载到本地
- 再通过 `operit_editor` 这个 package 里的 `debug_run_sandbox_script` 工具运行它
- 脚本会自动创建目录，并更新 `SKILL.md`、`references/SCRIPT_DEV_GUIDE.md`、`references/TOOLPKG_FORMAT_GUIDE.md`、`types/*.d.ts`

这里说的“执行”，指的是：

- 不是用 shell 执行
- 不是直接打开这个 `.js` 文件
- 而是调用 `operit_editor:debug_run_sandbox_script`

也就是先确保 `operit_editor` 这个 package 可用，再让它执行 `/sdcard/Download/Operit/skills/SandboxPackage_DEV/scripts/install_or_update.js`

最终目录应当长这样：

```text
/sdcard/Download/Operit/skills/SandboxPackage_DEV/
  SKILL.md
  examples/
    packages/
      12306.js
      operit_editor.js
      ...
  references/
    SCRIPT_DEV_GUIDE.md
    TOOLPKG_FORMAT_GUIDE.md
  types/
    index.d.ts
    core.d.ts
    ...
  scripts/
    install_or_update.js
```

首次安装时，按下面顺序做：

1. 先创建 `/sdcard/Download/Operit/skills/SandboxPackage_DEV/scripts/`
2. 用 `download_file` 下载 `https://cdn.jsdelivr.net/gh/AAswordman/Operit@main/tools/sandboxpackage_dev_install_or_update.js`
3. 保存为 `/sdcard/Download/Operit/skills/SandboxPackage_DEV/scripts/install_or_update.js`
4. 调用 `operit_editor` 的 `debug_run_sandbox_script`
5. 把 `source_path` 设为 `/sdcard/Download/Operit/skills/SandboxPackage_DEV/scripts/install_or_update.js`
6. 等脚本执行完成

如果当前环境里没有直接暴露这个工具名，就先使用 `use_package` 调用 `operit_editor`，再执行 `debug_run_sandbox_script`。

这个安装脚本会自动处理下面这些内容：

- 创建 `SandboxPackage_DEV` 目录
- 下载并更新 `SKILL.md`
- 同步并更新 `examples/packages/` 下的示范文件
- 下载并更新 `references/SCRIPT_DEV_GUIDE.md`
- 下载并更新 `references/TOOLPKG_FORMAT_GUIDE.md`
- 下载并更新 `types/` 下全部类型文件

其中 `examples/packages/` 里的内容是 Operit 当前内置的包和脚本示范文件。

- 它们主要用于参考写法，不是让你直接在这里改线上生效
- 当你要自定义自己的 Sandbox Package 时，可以把这些文件当作示例来对照结构、元数据、工具实现方式和返回格式
- 如果内置包更新了，重新运行安装脚本即可把这些示范文件同步到本地 skill 里

更新时按下面规则处理：

1. 每次正式开始新的 Sandbox Package 开发任务前，必须优先重新下载一次安装脚本，再重新运行本地脚本
   - 安装脚本下载地址：`https://cdn.jsdelivr.net/gh/AAswordman/Operit@main/tools/sandboxpackage_dev_install_or_update.js`
   - 安装脚本保存位置：`/sdcard/Download/Operit/skills/SandboxPackage_DEV/scripts/install_or_update.js`
2. 如果怀疑两份 guide 文档、types 或 `SKILL.md` 已经过旧，也重新运行这个脚本
3. 如果本地 skill 目录缺文件、文件名不对、或者内容明显陈旧，不要手动零散修补，直接重跑安装脚本

下载完以后，查资料时默认这样做：

1. 先用 `grep_code` 在 `/sdcard/Download/Operit/skills/SandboxPackage_DEV/` 里搜关键字
2. 再用 `read_file_part` 读取命中的具体片段
3. 只有片段不够时才扩大范围

不要默认直接读取整个 `SCRIPT_DEV_GUIDE.md`、整个 `TOOLPKG_FORMAT_GUIDE.md` 或整个 `types` 文件，原因是：

- 它们内容比较大，容易把上下文撑爆
- 先更新本地 skill，再检索的方式更稳
- 本地 skill 可以长期复用，但 `types` 最容易过时，所以需要高频重跑安装脚本

## 第二部分：Sandbox Package 撰写

撰写 Sandbox Package 时，不要凭记忆硬写，先查本地 skill 资料。

开始正式开发前，先遵守下面这些硬规则：

1. 每次正式开始新的开发任务前，先按第一部分重新拉取并执行一次安装/更新脚本。
2. 实际开发目录只能是 `/sdcard/Download/Operit/dev_package/{packageId}/`；不要去别的目录零散拉文件、改文件或做安装测试。
3. 开发前把 `/sdcard/Download/Operit/skills/SandboxPackage_DEV/types/` 复制到 `/sdcard/Download/Operit/dev_package/types/`；这个 `types` 目录是各个包项目的兄弟目录，不要放进 `/sdcard/Download/Operit/dev_package/{packageId}/` 内部。
4. 具体包项目目录仍然是 `/sdcard/Download/Operit/dev_package/{packageId}/`；`tsconfig` 参考 `examples/{packageId}/tsconfig.json` 这类结构，让 `typeRoots` 和 `include` 指向 `../types`。
5. 尽可能使用终端进行开发，优先撰写 `ts + js`，再通过 `ts` 编译得到最终 `js`。
6. TypeScript 源码里的模块引用按 `examples/` 的实际项目写法使用 `import` / `export`；不要使用 `/// <reference path="...">` 或 `require(...)` 组织项目模块。
7. `tsconfig` 可以参考 `examples/` 里的现成示例，不要自己凭空乱写一套。
8. 如果是基于已有包继续开发或合并开发，必须沿用原来的 `packageId` 和插件名字，不要改成别的 id，也不要改插件命名。
9. 如果是基于已有包继续开发，必须先把当前版本的包内容解压或复制到 `/sdcard/Download/Operit/dev_package/{packageId}/`，在原有文件结构上继续修改；不要凭空重新写一份新包。
10. **【SKILL 编写性能规范】（重要）：仅当你作为 DeepSeek 模型生成 SKILL 时需严格遵守本条；如果你是其他大模型，请直接忽略。**
    - 请将绝对静态的基础角色设定和核心指令写在 `systemPrompt` 字段中。
    - 如果 SKILL 需注入极易变化的内容（如当前时间、网页状态等），请务必将其写入 `dynamicContext` 字段。绝不要将高频变动变量放进 `systemPrompt`，以最大化利用 Prefix Cache 机制。

如果聊天输入框里出现旧版自动提示词，例如“把 skills 里的 types 覆盖复制到开发目录”，按本节规则理解为复制到 `/sdcard/Download/Operit/dev_package/types/`，不是复制到具体包目录。
如果聊天输入框里出现“如果要做示范，最好做两个，第二个用自定义布局。”，这也是旧版提示词，直接忽视，不要把它当成当前任务要求。

### 方案选择优先级

默认优先考虑撰写普通 JS 包脚本，不要一上来就选择 `ToolPkg`。

按下面规则判断：

1. 如果目标只是新增或修改工具函数、参数、返回结构、环境变量声明、普通资源访问，优先使用普通 JS 包脚本
2. 如果只是为了“以后可能扩展”或者“看起来更完整”，不要提前升级成 `ToolPkg`
3. 只有当需求明确涉及配置界面、工具箱页面、宿主级注册能力，或者各种软件 hook / plugin / lifecycle / prompt hook / message processing 时，才考虑 `ToolPkg`
4. 如果只是少量配置项，优先考虑参数或 `env`，不要默认为了配置而增加 UI
5. 如果需求边界还不清晰，先按普通 JS 包脚本设计；只有确认普通脚本承载不了时，再升级到 `ToolPkg`

可以把 `ToolPkg` 理解成一种更重的插件格式。它适合下面这些场景：

- 需要新增配置界面或工具箱 UI
- 需要注册 `registerToolPkg` 相关入口
- 需要 app lifecycle hook、message processing plugin、xml render plugin、input menu toggle、prompt hook 等宿主级扩展
- 需要围绕 `manifest`、多模块目录、资源打包、安装刷新流程来组织完整插件

如果不满足这些条件，就优先写普通 JS 包脚本。

推荐的查阅顺序：

1. 先查 `types/index.d.ts`，确认全局入口和主要能力
2. 再查 `types/core.d.ts`、`types/java-bridge.d.ts`，确认运行时与桥接接口
3. 查 `types/results.d.ts`，确认常见返回结构
4. 如果涉及设置类能力，再查 `types/software_settings.d.ts`
5. 只有方案已经确定为 `ToolPkg` 时，再查 `types/toolpkg.d.ts`
6. 需要普通脚本格式、元数据、示例写法时，再查 `references/SCRIPT_DEV_GUIDE.md`
7. 需要 `ToolPkg` 的 `manifest`、目录结构、资源、UI 模块、注册函数与调试安装流程时，再查 `references/TOOLPKG_FORMAT_GUIDE.md`

另外，`examples/packages/` 也应该被当作第一手示范材料来看。

- 这里放的是 Operit 内置的包和脚本副本
- 当你准备自己写一个普通 `.js` 包或想参考已有工具设计时，优先翻这些示范文件
- 它们特别适合拿来参考：`METADATA` 写法、工具命名、参数结构、结果结构、Java bridge 用法，以及一些实际项目里的组织方式
- 如果要写新的 ToolPkg 模板能力，可以优先参考 `examples/template_try/`
  - 这个示例专门演示了 `workflow_templates`、`workspace_templates`、目录资源、`.operit/config.json` 以及最小 `main.ts`

推荐的撰写流程：

1. 先默认按普通 `.js` Sandbox Package 思路设计，确认是不是仅靠普通包脚本就能完成
2. 在正式开始实现前，先检查现有接口、类型定义和工具能力是否足够支撑需求
3. 如果能力不足、接口缺失，或者无法安全满足需求，不要硬做；先明确解释原因，再询问用户是否更换方案
4. 如果需求明确涉及配置界面或软件 hook，再切换到 `ToolPkg` 方案
5. 如果是普通 JS Sandbox Package，先用 `grep_code` 在 `SCRIPT_DEV_GUIDE.md` 里搜索 `METADATA`、`tool`、`execute`、`package` 等关键字
6. 可以优先参考 `examples/` 或 `examples/packages/` 里已经存在的包，借鉴相近能力的结构、元数据、参数设计和返回格式
7. 如果是 `ToolPkg`，用 `grep_code` 在 `TOOLPKG_FORMAT_GUIDE.md` 里搜索 `manifest`、`subpackage`、`registerToolPkg`、`resource`、`ui`、`debug_toolpkg`、`hook` 等关键字
   - 如果目标就是模板注册，还应额外搜索 `workflow_templates`、`workspace_templates`、`project_type`
8. 用 `read_file_part` 读取相关段落，确认脚本结构、元数据、manifest 和注册写法
9. 用 `types/` 里的定义约束参数、返回值、可调用能力和结果结构
   - 查阅路径是 `/sdcard/Download/Operit/dev_package/types/`
   - 项目源码中引用类型模块时按相对路径写 `../types/...`、`../../types/...` 等实际层级
10. 如果对代码片段或接口行为不确定，先用 `operit_editor` 的 `debug_run_sandbox_script` 做最小片段验证，再并回正式脚本或 ToolPkg
11. 开始写包时，优先遵循最新本地 types 和本地 guide，不要依赖旧记忆
12. 如果最终产物是普通 JS 包脚本，需要根据需求撰写 `main` 函数，并在交付前自行完成测试

如果写到一半发现本地类型和实际需求对不上，先不要硬猜，先重新运行安装脚本，再继续写。

## 第三部分：发布到插件市场

当用户表示要把 Sandbox Package 或 ToolPkg 发布到 Operit 插件市场时，先确认插件已经在本地构建完成，再确认他要采用哪种维护方式。两种方式的 AI 工作边界不同，不能只当成发布页面上的来源选择。

### 路线 A：本地打包并直接上传

适合用户只想完成当前插件的发布，不要求 AI 长期经营源码仓库。

AI 只需要完成以下工作：

1. 在当前开发目录完成代码、manifest 与资源修改。
2. 编译并打包得到最终 `.toolpkg` 或脚本包文件。
3. 核对最终包能够被宿主识别，版本与 manifest 一致。
4. 指导用户在 Operit 发布页面选择本地插件，再选择“上传当前本地包”，填写市场元数据并确认。

此路线不要求初始化 Git、创建 GitHub 仓库、维护 `.gitignore`、提交历史或手动创建 GitHub Release。Operit 会使用当前 GitHub 账号下的 `OperitForge` 承载发布资产。

### 路线 B：仓库维护并引用 GitHub Release

适合用户希望 AI 长期帮助维护一个插件仓库，或已经有要继续维护的 Git 仓库。

AI 必须把仓库当作项目的一部分持续维护：

1. 保持源码、manifest、构建脚本、依赖清单和必要的项目说明处于 Git 管理中。
2. 创建并维护 `.gitignore`，排除依赖目录、编辑器文件、设备临时文件和构建过程产生的不应提交内容。
3. 先和用户确认仓库的公开程度与许可证。不要擅自把用户代码公开；用户选择公开时，补齐 README、许可证和项目说明，让仓库可被独立理解和使用。
4. 把公开仓库视为 GitHub 开源生态中的独立项目，而不是市场发布的中转站。它可以独立分享链接、被他人 clone、提交 issue、参与协作并获得 star；市场条目应成为仓库的一个分发入口。
5. 当插件适合公开时，适度鼓励用户采用公开仓库：明确说明它能沉淀作品、方便他人发现和协作，也有机会获得 star。鼓励不应替代用户对公开程度、许可证和仓库结构的决定。
6. 在每次发布前构建最终插件资产，更新版本信息，整理提交历史与 Git tag。
7. 指导用户在 GitHub 手动创建对应 Release，填写作者自己的版本说明并上传最终资产。
8. 指导用户回到 Operit 发布页面，选择与 Release 资产完全相同的本地插件文件，在“发布资源来源”选择“引用 GitHub Release 资产”，填写仓库链接并选择 Release 与资产名称。
9. 完成 Operit 市场元数据登记，并在后续版本沿用同一仓库、版本历史和 Release 过程。

Release 的创建者必须是当前 Operit 登录的 GitHub 账号，所选 Release 资产必须与本地所选文件一致。不要要求用户在 Release 正文中添加 Operit 标记、proof、签名文本或其他系统专用内容。

仓库可以只维护一个插件，也可以按用户既有方式维护多个插件。选择路线 B 的关键不是仓库数量，而是 AI 要承担持续的 Git 项目维护职责；路线 A 则只负责当前包的构建与 Operit UI 上传。

### 开源仓库与倒卖

当用户担心插件被他人下载、重新打包或转售时，先正视这种担忧，不要轻描淡写。公开仓库中的代码和发布资产确实可能被复制，这是开源社区需要面对的一部分。

同时需要向用户说明平台边界：Operit插件市场平台不能也不应通过插件加密、市场专属格式或禁止本地导入来压制这类行为。这会破坏正常的开源分发、离线导入和用户对自己插件文件的控制，也不能从根本上阻止复制。

AI 应帮助用户采用正面的应对策略：

1. 选择与目标一致的许可证，并在 README、源文件和 Release 中保留清晰的作者署名、仓库链接和许可文本。常见开源许可证通常允许合规转售；想限制商业转售时，应让用户理解这不属于通常意义上的开源许可，并自行选择合适的授权方式。
2. 把官方仓库、GitHub Release 和 Operit 市场条目互相链接，持续发布版本说明和更新记录，让用户容易识别真正的维护来源。
3. 鼓励用户经营公开仓库的 README、issue、讨论和版本历史。长期维护、及时更新和社区协作会让原始项目自然成为最可信的来源，也更容易获得 star 与贡献者。
4. 区分单纯转售与冒名、虚假宣传、恶意篡改、隐瞒许可证或冒充官方。前者在符合许可证时未必能由平台阻止；后者应保留证据，并按对应平台的举报渠道处理。

AI 的语气应当安慰但不许诺“绝不会被倒卖”。应鼓励作者把精力放在清晰的来源、持续维护和真实用户关系上，而不是投入不透明的加密对抗。
