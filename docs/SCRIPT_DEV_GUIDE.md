# 脚本开发指南

## 1. 简介

本文档旨在为开发者提供关于如何编写、构建和维护自动化脚本的全面指南。**这些脚本旨在作为强大的工具，被导入到 Operit AI 智能助手中，并由 AI 根据用户指令进行调用，从而极大地扩展应用的功能边界。** 这些脚本基于一个强大的框架，提供了一系列用于设备控制、UI自动化、网络请求和文件操作的工具。

脚本主要使用 **TypeScript** 编写，以利用其强大的类型系统，但也可以使用原生 JavaScript (ES6+)。

## 2. 先确定开发场景

不要把“开发插件”和“修改 Operit 主仓”混为一件事。两者使用不同的目录、资料和调试路径。

### 应用内 AI 协作开发

这是普通 Sandbox Package 作者的默认流程。先更新并运行 `SandboxPackage_DEV` 安装脚本，再在 `/sdcard/Download/Operit/dev_package/{packageId}/` 中开发。宿主类型放在同级的 `/sdcard/Download/Operit/dev_package/types/`，具体步骤以安装后的 `SKILL.md` 为准。

### 独立插件作者

独立插件作者可在发布页面选择两条路线：直接上传当前本地包，或引用自己 GitHub Release 中的资产。单独 Git 仓库适合需要独立维护源码、构建脚本、版本与 Release 的插件；它不是市场发布的前提。

### Operit 源码贡献者

只有需要修改 Operit 应用本身、内置示例或 Android 宿主能力时，才进入主仓开发流程。这要求克隆主仓、安装依赖并使用 ADB 调试工具，不能把它当作普通插件作者的默认起点。

---

## 发布到插件市场

市场发布支持两条路线，它们对应不同的开发维护方式：

1. 直接上传当前本地包。适合只发布当前构建产物的作者。完成代码、打包和包内容核对后，在 Operit 发布页面选择本地包并直接上传；不需要创建或维护 Git 仓库。Operit 会在当前 GitHub 账号的 `OperitForge` 仓库创建或更新 Release，并登记市场。
2. 引用 GitHub Release 资产。适合长期维护插件仓库的作者。仓库应持续保存源码、manifest、构建脚本和依赖清单，并维护 `.gitignore` 以排除依赖目录、编辑器文件和设备临时文件。作者自行决定公开程度与许可证；公开仓库可以独立分享、被 clone、获得协作和 star，市场只是其中一个分发入口。每次发布由作者构建资产、更新版本、整理提交与 tag、创建 GitHub Release，再在 Operit 中登记市场。

引用 GitHub Release 资产时，按下面顺序操作：

1. 选择本地已构建的插件文件。
2. 在“发布资源来源”中选择“引用 GitHub Release 资产”。
3. 填写作者仓库链接，加载后选择 Release 和对应资产。
4. 填写插件名称、介绍、分类、版本和支持的软件版本，确认市场登记。

Operit 会核对 Release 资产与本地所选文件是否相同，并由市场服务确认该 Release 的创建者就是当前登录的 GitHub 作者。Release 正文只保留作者自己的发布说明，不需要添加 Operit 标记、校验文本或其他额外内容。

仓库可以只维护一个插件，也可以由作者自行组织多个插件；市场不要求固定的仓库数量。引用 Release 路线的关键是持续维护 Git 项目，直接上传路线的关键是只负责当前包的构建与 UI 上传。

---

### Operit 源码贡献者：在项目中开发

本节只面向需要改动 Operit 主仓的贡献者。普通插件作者请使用前述应用内 Skill 工作流或自己的独立插件仓库。

**步骤 1: 克隆项目并安装依赖**

```bash
# 克隆项目仓库
git clone https://github.com/AAswordman/Operit.git
cd Operit

# 安装项目依赖 (主要是TypeScript编译器)
npm install
```

**步骤 2: 创建你的第一个脚本**

所有的脚本都存放在 `examples/` 目录下。请在该目录中创建一个新文件，例如 `my_first_script.ts`，然后将下面的代码复制进去。

```typescript
/*
METADATA
{
    "name": "MyFirstScript",
    "description": "我的第一个脚本，用于演示。",
    "category": "Utility",
    "tools": [
        {
            "name": "hello_world",
            "description": "向世界问好。",
            "parameters": [
                {
                    "name": "name",
                    "description": "要问好的人名",
                    "type": "string",
                    "required": true
                }
            ]
        }
    ]
}
*/

// 引用核心类型，这会给你带来代码自动补全的奇效
/// <reference path="./types/index.d.ts" />

// 这是一个标准的脚本结构，建议你直接复用
const MyFirstScript = (function () {
    // 包装函数，统一处理成功/失败的返回
    async function wrap(func: (params: any) => Promise<any>, params: any) {
        try {
            const result = await func(params);
            complete(result); // 也可以直接 return result；这里只是统一走显式完成
        } catch (error) {
            complete({ success: false, message: `执行失败: ${error.message}` });
        }
    }

    // 这是你工具的具体实现
    async function hello_world(params: { name: string }): Promise<any> {
        const message = `你好, ${params.name}! 欢迎来到 Operit 脚本世界。`;
        await Tools.System.sleep(500); // 调用内置API
        return { success: true, message: message };
    }

    // 导出你的工具，使其可以被调用
    return {
        hello_world: (params: any) => wrap(hello_world, params),
    };
})();

// 将工具函数暴露给脚本执行引擎
exports.hello_world = MyFirstScript.hello_world;
```

**步骤 3: 编译脚本**

TypeScript (`.ts`) 文件需要被编译成 JavaScript (`.js`) 才能执行。`tsc` 命令会自动读取 `tsconfig.json` 文件并根据其配置来编译。

```bash
# 进入 examples 目录
cd examples

# 编译目录下的所有 TypeScript 脚本
npx tsc
```

执行成功后，你会看到 `my_first_script.ts` 旁边生成了一个 `my_first_script.js` 文件。

**步骤 4: 在设备上运行脚本**

确保你已连接安卓设备并开启了USB调试。然后返回项目根目录，使用我们提供的工具来执行脚本。

```bash
# 回到项目根目录
cd ..

# 执行脚本的 `hello_world` 函数
# Windows:
tools\\adb\\execute_js.bat examples\\my_first_script.js hello_world "{\\"name\\":\\"世界\\"}"
# Linux / macOS:
./tools/adb/execute_js.sh examples/my_first_script.js hello_world '{"name":"世界"}'
```

如果你要直接运行整段 sandbox script，而不是指定某个导出函数：

```bash
# Windows:
tools\\adb\\run_sandbox_script.bat examples\\my_first_script.js "{\\"name\\":\\"世界\\"}"
# Linux / macOS:
./tools/adb/run_sandbox_script.sh examples/my_first_script.js '{"name":"世界"}'
```

**步骤 5: 查看结果**

脚本执行器会等待结构化结果文件返回。如果一切顺利，你会在终端看到包含 `"你好, 世界!..."` 的成功信息，以及日志事件和最终结果。

**恭喜你！**

你已经完成了第一个脚本的开发和运行。现在你已经掌握了基础，可以继续深入探索 **[第 3 章：核心概念](#3-核心概念)** 来学习 `METADATA` 的详细配置和 `Tools` API 的更多用法，或者直接跳到 **[第 6 章：UI自动化详解](#6-ui自动化详解)** 编写更强大的自动化脚本。

---

### 独立插件仓库

如果你希望独立管理你的脚本，可以按照以下步骤从零开始搭建一个开发环境。

#### 2.1. 初始化项目与依赖 (`package.json`)

`package.json` 文件是 Node.js 项目的清单，用于管理项目的元数据和依赖项。

**步骤 1: 创建 `package.json`**

在你的空项目文件夹中，创建一个名为 `package.json` 的文件，并填入以下基础内容：

```json
{
    "name": "my-script-project",
    "version": "1.0.0",
    "description": "My new script project.",
    "scripts": {
        "build": "tsc"
    },
    "devDependencies": {
        "@types/node": "^22.0.0",
        "typescript": "^5.4.5"
    }
}
```
*注意: 我们推荐使用较新版本的 `typescript` 和 `@types/node`，你可以根据需要调整版本号。*

**步骤 2: 安装依赖**

打开终端，在项目根目录下运行以下命令来安装开发依赖：

```bash
npm install
```

此命令会根据 `package.json` 中的 `devDependencies` 下载 `typescript` 和 Node.js 的类型定义。

#### 2.2. 配置 TypeScript (`tsconfig.json`)

`tsconfig.json` 文件用于指定 TypeScript 编译器的选项，告诉它如何将 `.ts` 文件编译成 `.js` 文件。

**步骤: 创建 `tsconfig.json`**

在项目根目录创建 `tsconfig.json` 文件，并复制以下推荐配置。这个配置与本项目使用的标准配置 (`examples/tsconfig.json`) 保持一致，确保了兼容性。

```json
{
  "compilerOptions": {
    "target": "es2020",
    "module": "commonjs",
    "lib": [
      "es2020"
    ],
    "declaration": false,
    "strict": false,
    "noImplicitAny": false,
    "strictNullChecks": true,
    "noImplicitThis": true,
    "alwaysStrict": false,
    "noUnusedLocals": false,
    "noUnusedParameters": false,
    "noImplicitReturns": true,
    "moduleResolution": "node",
    "allowSyntheticDefaultImports": true,
    "esModuleInterop": true,
    "experimentalDecorators": true,
    "emitDecoratorMetadata": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true,
    "typeRoots": [
      "./types"
    ]
  },
  "include": [
    "**/*.ts",
    "**/*.d.ts"
  ],
  "exclude": [
    "node_modules"
  ]
}
```

**关键配置解释:**
-   `"target": "es2020"`: 将代码编译为 ES2020 版本的 JavaScript，以匹配 QuickJS 运行时可用的现代语法能力。
-   `"module": "commonjs"`: 使用 CommonJS 模块系统，这是脚本执行环境所要求的。
-   `"lib": ["es2020"]`: 以 ECMAScript 标准库为基础，不默认引入浏览器 DOM 类型。Operit 的脚本运行在 QuickJS 宿主中，不是网页环境；`console`、timer 等宿主能力由平台兼容层单独提供。
-   `"typeRoots": ["./types"]`: 指定了类型定义文件 (`.d.ts`) 的存放目录。你需要手动在项目中创建一个 `types` 文件夹，并将平台提供的核心类型定义文件 (`index.d.ts`, `files.d.ts` 等) 放进去，这样才能获得 `Tools` 等全局对象的智能提示。
-   `"include": ["**/*.ts", "**/*.d.ts"]`: 告诉编译器同时纳入脚本源码和补充的宿主类型声明文件。

完成以上步骤后，你的项目就搭建好了。现在你可以继续阅读后续章节，了解项目的具体结构和脚本的编写方法。

#### 2.3. 复制平台核心文件

为了让你的脚本能与平台交互，并能在设备上执行，你需要从本开发指南所在的源项目（即 `assistance` 项目）中复制一些核心文件到你的新项目里。

**需要复制的内容：**

1.  **类型定义文件**: 将源项目中的 `examples/types/` 整个文件夹复制到你的新项目根目录，并确保文件夹名称就是 `types`。这与 `tsconfig.json` 中的 `"typeRoots": ["./types"]` 配置相对应。这些文件定义了 `Tools` 等全局对象的类型，是获得代码智能提示的关键。
2.  **执行工具**: 将源项目根目录下的 `tools/` 整个文件夹复制到你的新项目根目录。这些脚本（如 `execute_js.bat`）是用来在设备上运行和测试你的代码的。

**复制后的项目结构**

完成以上所有步骤后，你的新项目文件夹看起来应该是这样的：

```plaintext
my-script-project/
├── node_modules/
├── tools/
│   ├── execute_js.bat
│   └── execute_js.sh
├── types/
│   ├── core.d.ts
│   ├── files.d.ts
│   ├── index.d.ts
│   ├── network.d.ts
│   ├── system.d.ts
│   ├── ui.d.ts
│   └── ... (其他核心类型定义文件)
├── my_first_script.ts  // 这是你将要创建的脚本文件
├── package.json
└── tsconfig.json
```

现在，你的开发环境已经完全准备就绪。

## 3. 核心概念

在开始编写脚本之前，理解以下几个核心概念非常重要：

### 3.1. 脚本元数据 (METADATA)

每个脚本文件的开头都必须包含一个 `/* METADATA ... */` 注释块。这个块定义了脚本的名称、描述、分类以及最重要的——它所提供的工具。**这块元数据是 Operit AI 理解并调用你所编写功能的唯一途径。AI 会解析 `METADATA` 中的信息，将其作为可用的“工具”呈现给大语言模型（LLM），从而实现通过自然语言指令来执行复杂脚本的能力。**

**示例：** (`examples/automatic_bilibili_assistant.ts`)

```typescript
/*
METADATA
{
    "name": "Automatic_bilibili_assistant",
    "display_name": {
        "zh": "B站智能助手",
        "en": "Bilibili Assistant"
    },
    "description": "高级B站智能助手，通过UI自动化技术实现B站应用交互...",
    "author": ["Operit Team"],
    "category": "UI_AUTOMATION",
    "env": ["BILIBILI_SESSDATA"],
    "tools": [
        {
            "name": "search_video",
            "description": "在B站搜索视频内容",
            "parameters": [
                {
                    "name": "keyword",
                    "description": "搜索关键词",
                    "type": "string",
                    "required": true
                },
                // ... more parameters
            ]
        },
        // ... more tools
    ]
}
*/
```

-   `name`: 脚本的唯一标识符。
-   `display_name`: （可选，推荐）用于界面显示的名称。不会影响脚本 ID；脚本 ID 仍由 `name` 决定。支持字符串或多语言对象（见 3.1.2）。
-   `description`: 对脚本功能的详细描述。
-   `author`: （可选）作者信息，支持单个字符串或字符串数组。
-   `category`: （可选）脚本分类，用于在工具列表中分组和检索。未填写或为空时，系统会自动归类为 `Other`（见 3.1.3）。
-   `env`: （可选）字符串数组，声明该脚本/包运行时依赖的环境变量名称，例如各类 API Key。应用会根据这里列出的键在“环境配置”界面中展示对应的输入项，并在激活包前校验这些变量是否已经配置。
-   `tools`: 一个数组，定义了该脚本暴露给外部调用的所有工具（函数）。
    -   `name`: 工具的函数名。
    -   `description`: 工具功能的描述。
    -   `parameters`: 工具接受的参数列表，每个参数都应定义 `name`, `description`, `type`, 和 `required`。
    -   `advice`: （可选）标记“仅提示/说明”的工具（如 usage_advice / workflow_guide）。
        -   `advice: true` 时，该工具不要求在脚本中有同名函数实现。
        -   应用于纯提示/规则说明场景，运行时会跳过“工具不存在”的校验。

### 3.1.1. 动态工具集：`states`

当同一个脚本在不同设备能力/权限等级下需要暴露不同工具集（或同名工具需要不同的说明/参数约束）时，可以在 `METADATA` 中使用 `states` 字段。

`states` 为一个数组，每个元素是一个“状态”（State）。系统会根据运行时能力（capabilities）按顺序评估每个 state 的 `condition`，选择第一个为 `true` 的 state 并激活。

#### State 结构

-   `id`: 状态 ID（字符串），用于脚本侧识别当前状态。
-   `condition`: 条件表达式（字符串），使用“类 JS”布尔表达式语法。
-   `inheritTools`: 是否继承顶层 `tools`（布尔值）。
-   `excludeTools`: 需要从继承工具中排除的工具名列表（字符串数组）。
-   `tools`: 该 state 额外提供的工具列表（同 `tools` 的元素结构）。如果与继承工具同名，则覆盖其定义。

#### 选择规则

-   若 `states` 为空：使用顶层 `tools`。
-   若 `states` 非空：从上到下找第一个满足 `condition` 的 state。
    -   找不到任何匹配：回退到顶层 `tools`。

#### 合并规则

-   当 `inheritTools=true`：以顶层 `tools` 为基底。
-   先应用 `excludeTools` 删除指定工具。
-   再将 state 的 `tools` 合并进来（同名覆盖，不同名新增）。

#### Condition 语法（简述）

-   字面量：`true` / `false` / `null`
-   逻辑：`!` / `&&` / `||`
-   比较：`==` / `!=` / `>` / `>=` / `<` / `<=`
-   成员测试：`in`（示例：`android.permission_level in ['ADMIN','ROOT']`）
-   括号：`(...)`
-   数组字面量：`[...]`

#### 可用 capability key（内置）

-   `ui.virtual_display`: 是否具备虚拟屏能力（boolean）
-   `android.permission_level`: 权限等级（enum，会以字符串形式参与比较）
-   `android.shizuku_available`: Shizuku 是否可用（boolean）
-   `ui.shower_display`: Shower 虚拟屏是否可用（boolean）

#### 脚本侧获取当前 state

运行时会向脚本环境提供全局函数 `getState(): string`，返回当前激活的 state 的 `id`。

#### 脚本侧获取当前语言

运行时会向脚本环境提供全局函数 `getLang(): string`，返回当前使用的语言代码（如 `zh` / `en`）。若语言不可用则返回 `en`。

### 3.1.2. 文本字段的双语/多语（LocalizedText）

在 `METADATA` 中，以下文本字段都支持“单语字符串”或“多语对象”两种写法：

-   包级：`display_name`
-   包级：`description`
-   工具级：`tools[].description`
-   参数级：`tools[].parameters[].description`
-   环境变量（若使用对象格式 env 声明）：`env[].description`

#### 两种写法

1) **单语（字符串）**

```json
"description": "一个简短描述"
```

2) **双语/多语（对象）**

```json
"description": {
  "zh": "中文描述",
  "en": "English description",
  "default": "Fallback description"
}
```

#### 语言 Key 的选择与回退

运行时会根据系统语言做选择，优先级大致为：

-   优先匹配完整语言标签（如 `zh-CN`、`en-US`，含大小写变体）
-   再匹配语言代码（如 `zh`、`en`）
-   再回退到 `default`
-   若仍未匹配到，则回退为对象中的任意一个值

#### 示例（包/工具/参数/环境变量同时双语）

```typescript
/*
METADATA
{
  "name": "MyBilingualPackage",
  "category": "Utility",
  "display_name": {
    "zh": "双语示例包",
    "en": "Bilingual Demo Package",
    "default": "Bilingual Demo Package"
  },
  "description": {
    "zh": "演示双语元数据",
    "en": "Bilingual metadata demo",
    "default": "Bilingual metadata demo"
  },
  "env": [
    {
      "name": "MY_API_KEY",
      "description": {
        "zh": "用于访问某 API 的密钥",
        "en": "API key for accessing a service",
        "default": "API key"
      },
      "required": true
    }
  ],
  "tools": [
    {
      "name": "hello",
      "description": {
        "zh": "向指定的人问好",
        "en": "Say hello to someone",
        "default": "Say hello"
      },
      "parameters": [
        {
          "name": "name",
          "description": {
            "zh": "要问好的人名",
            "en": "Name to greet",
            "default": "Name"
          },
          "type": "string",
          "required": true
        }
      ]
    }
  ]
}
*/
```

### 3.1.3. `category` 字段规范

`category` 为 **可选字段**，类型为字符串，用于脚本分类展示与检索。建议直接复用 `examples/` 中已使用的分类，避免创建语义重复的新分类。

```json
"category": "Utility"
```

当 `category` 缺失、为空字符串或仅包含空白字符时，解析阶段会自动归类为：

```json
"category": "Other"
```

当前 `examples/` 中可参考的分类值：

-   `Automatic`
-   `Chat`
-   `Development`
-   `Draw`
-   `File`
-   `Life`
-   `Map`
-   `Media`
-   `Memory`
-   `Network`
-   `Search`
-   `System`
-   `Utility`
-   `Workflow`
-   `Other`（系统默认兜底分类）

### 3.3. 使用内置工具 (Tools)

平台提供了一个全局的 `Tools` 对象，它包含了所有与底层系统交互的API。这些API被分类到不同的命名空间下:

-   `Tools.System`: 系统级操作，如 `sleep()`, `startApp()`, `stopApp()`。
-   `Tools.UI`: UI自动化操作，如 `getPageInfo()`, `pressKey()`, `swipe()`, `setText()`。
-   `Tools.Files`: 文件系统操作，如 `read()`, `write()`, `list()`。
-   `Tools.Network`: 网络请求，如 `httpGet()`, `httpPost()`。
-   `UINode`: 用于表示和操作UI元素的类。

所有这些工具函数都是**异步**的，调用时必须使用 `await`。

**示例：**
```typescript
// 等待3秒
await Tools.System.sleep(3000);

// 获取当前页面信息
const pageInfo = await Tools.UI.getPageInfo();

// 在屏幕上滑动
await Tools.UI.swipe(540, 1800, 540, 900);
```

### 3.4. Java/Kotlin 类桥接 (Java Bridge)

除了 `Tools` 外，脚本运行时还注入了 `Java` / `Kotlin` 全局对象，用于直接调用 Java/Kotlin/Android 类。

这套能力适用于：

-   直接访问 Android SDK 类（如 `android.os.Build`、`android.os.SystemClock`）。
-   调用宿主应用暴露的类与单例对象。
-   在脚本中实现 Java 接口回调（如 `Runnable`、`Callable`、Listener）。

#### 3.4.1. 两层 API

1.  **高层 API（推荐）**：`Java` / `Kotlin`（Rhino 风格）
2.  **底层 API（调试/底层控制）**：`NativeInterface.java*`

`Kotlin` 是 `Java` 的同义别名，API 完全一致。

#### 3.4.2. 高层 API 常用能力

-   类获取：
    -   语法糖（推荐）：`Java.java.lang.StringBuilder`、`Java.android.os.Build.VERSION`、`Java.android.app.AlertDialog.Builder`
    -   兼容写法：`Java.type("java.lang.StringBuilder")`
    -   别名：`Java.use(...)` / `Java.importClass(...)`
-   包链访问：`Java.java.lang.System.currentTimeMillis()`
-   静态调用：`Java.callStatic(className, methodName, ...args)`
-   异步挂起调用：`Java.callSuspend(className, methodName, ...args)`（返回 Promise）
-   构造实例：`Java.newInstance(className, ...args)` 或 `new Java.java.util.ArrayList()`
-   接口实现：
    -   `Java.implement(interfaceNameOrNames, impl)`（支持字符串接口名或 `Java.xxx` 类代理）
    -   `Java.proxy(interfaceNameOrNames, impl)`（`implement` 别名）
    -   **语法糖**：当 Java/Kotlin 方法参数为接口类型时，可直接传 JS 函数/对象，桥接会自动推断接口并创建代理（无需显式 `implement`）
-   生命周期管理：
    -   Java 实例 handle 与 JS 接口回调都由运行时自动管理，无需手动释放脚本侧回调标记

#### 3.4.3. 句柄与生命周期建议

Java 复杂对象跨桥接会被包装成“实例代理”（内部是 handle 句柄），建议：

-   `Java.implement` / `Java.proxy` 产生的回调标记不再需要手动释放；对应 Java 代理被 GC 后，运行时会自动解除当前 JS 回调注册。
-   Java 实例代理的 handle 解绑由运行时自动处理；业务脚本不再暴露 `obj.release()` / `Java.release(...)` / `Java.releaseAll()`。
-   运行时会在代理对象被 GC 后尝试解绑对应 handle，也会在引擎销毁时清理剩余句柄；GC 时机本身仍然是不确定的。
-   Java Bridge 实例默认推荐 `obj.methodName()` 动态语法糖；运行时会优先把实例成员按方法解释，尽量保证这种写法可用。
-   `obj.call('methodName', ...)` 仍可用于极少数字段/方法同名冲突，或在调试桥接问题时做显式调用。
-   `Java.implement(...)` / `Java.proxy(...)` 的 JS 回调会被调度回 QuickJS 运行时线程执行，而不是直接在 Java 调用方线程里执行；不要把它当成 JS 侧真正的多线程执行模型。
-   “Java 返回值会被归一化” 与 “显式构造 Java 对象” 是两件事：例如 Java 方法返回的 `JSONArray` / `JSONObject` / `Map` / `List` 会在 JS 侧按数组或 plain object 使用；但 `new Java.java.lang.StringBuilder()`、`new Java.java.util.ArrayList()` 这种显式构造结果本身仍然是 Java 实例代理。

#### 3.4.4. 示例：包链语法 + 回调

```typescript
const Thread = Java.java.lang.Thread;
const Runnable = Java.java.lang.Runnable;

let runCount = 0;
const runnable = Java.implement(Runnable, () => {
    runCount += 1;
});

const worker = new Thread(runnable);
worker.start();
worker.join(2000);
console.log("runCount=", runCount);
```

#### 3.4.5. 示例：Android 类与内部类

```typescript
const Build = Java.android.os.Build;
const Version = Java.android.os.Build.VERSION;
const AlertDialogBuilder = Java.android.app.AlertDialog.Builder;

console.log("brand=", String(Build.BRAND || ""));
console.log("sdk=", Number(Version.SDK_INT));
```

#### 3.4.6. 底层 `NativeInterface.java*`（仅在必要时使用）

高层 API 会自动处理参数转换、异常抛出与句柄包装。只有在调试底层行为时才建议直接使用 `NativeInterface.java*`：

```typescript
const raw = NativeInterface.javaCallStatic(
    "java.lang.Integer",
    "parseInt",
    JSON.stringify(["42"])
);
const parsed = JSON.parse(raw);
if (!parsed.success) throw new Error(parsed.error);
console.log(parsed.data); // 42
```

如果你在 TypeScript 中开发，建议先引用 `examples/types/index.d.ts`，即可获得 Java Bridge 的类型提示（含 `Java`、`Kotlin`、`NativeInterface`）。

#### 3.4.7. 示例：suspend 调用（callback / Promise）

`callSuspend` 用于调用 Kotlin `suspend` 方法，始终返回 `Promise`。

```typescript
const EnhancedAIService = Java.com.ai.assistance.operit.api.chat.EnhancedAIService;

// Promise 形式
const service = await EnhancedAIService.callSuspend(
    "getAIServiceForFunction",
    ctx,
    FunctionType.CHAT
);
```

## 4. 编写第一个脚本 (TypeScript)

推荐使用 TypeScript 来编写脚本，这样可以充分利用 `types/` 目录中提供的类型定义，获得更好的开发体验。

### 步骤 1: 创建 `.ts` 文件

在你选择的脚本目录下创建一个新的 `.ts` 文件，例如 `my_new_script.ts`。

### 步骤 2: 添加元数据

在文件顶部添加 `METADATA` 块，定义你的脚本和工具。

```typescript
/*
METADATA
{
    "name": "MyNewScript",
    "description": "一个用于演示脚本开发的新脚本。",
    "category": "Utility",
    "tools": [
        {
            "name": "hello_world",
            "description": "向指定的人问好。",
            "parameters": [
                {
                    "name": "name",
                    "description": "要问好的人名",
                    "type": "string",
                    "required": true
                }
            ]
        }
    ]
}
*/
```

### 步骤 3: 编写主体逻辑

使用一个立即执行函数表达式 (IIFE) 来封装脚本逻辑，避免污染全局作用域。

```typescript
// 引用核心类型，以获得代码提示
/// <reference path="./types/index.d.ts" />

const MyNewScript = (function () {
    // 辅助函数，用于统一返回结果
    async function wrapToolExecution(func: (params: any) => Promise<any>, params: any) {
        try {
            const result = await func(params);
            return result;
        } catch (error) {
            console.error(`工具 ${func.name} 执行失败`, error);
            return {
                success: false,
                message: `工具执行时发生意外错误: ${error.message}`,
            };
        }
    }

    // 实现 `hello_world` 工具
    async function hello_world(params: { name: string }): Promise<any> {
        const { name } = params;
        
        // 使用内置工具
        await Tools.System.sleep(500);
        
        const message = `你好, ${name}! 欢迎使用脚本。`;
        
        // 返回成功结果
        return { success: true, message: message };
    }

    // 导出工具
    return {
        hello_world: (params: any) => wrapToolExecution(hello_world, params),
    };
})();

// 导出模块，使其可以被系统加载
exports.hello_world = MyNewScript.hello_world;
```

### 步骤 4: 使用类型

-   在文件顶部添加 `/// <reference path="./types/index.d.ts" />` 可以让 TypeScript 编译器和你的IDE（如 VS Code）找到全局的类型定义。
-   `types/` 目录下的 `.d.ts` 文件详细定义了所有可用工具的签名和返回类型。Java Bridge 的契约说明优先以 [dev-core/JAVA_BRIDGE_INTERFACE.md](doc-src/dev-core/JAVA_BRIDGE_INTERFACE.md) 为准。

## 5. 深入学习：示例脚本解析

当你掌握了基础脚本的写法后，最好的学习方式就是阅读现有的优秀代码。`examples/` 目录下包含了许多功能各异的脚本，我们精选了以下几个作为推荐的进阶学习材料：

### 5.1. 终极入门教程: `examples/quick_start.ts`

这个脚本是一部从零开始的、带有超详细注释的教科书。它不仅仅是一个工具，更是一份动态的开发文档。

*   **学习重点**:
    *   **语法基础**: 详细解释了变量、数据类型、`async/await` 和 `try/catch` 错误处理。
    *   **设计模式演进**: 从一个简单的全局函数开始，逐步重构为使用 `IIFE` 模式隔离作用域，最终演进到我们强烈推荐的 `Wrapper` 模式，让你深刻理解为什么我们的代码要这样组织。
    *   **`Tools` API 调用**: 演示了如何调用 `Tools.System` 和 `Tools.Files` 来与系统交互。
*   **建议**: 如果你对 TypeScript/JavaScript 不熟悉，或者对 `IIFE` 和 `Wrapper` 模式感到困惑，请务必完整阅读一遍此文件。

### 5.2. 网络请求与并发: `examples/various_search.ts`

这个脚本实现了一个实用的多平台搜索引擎聚合工具，是学习网络操作的最佳范例。

*   **学习重点**:
    *   **网络请求**: 演示了如何使用 `Tools.Net.visit` 来访问网页并获取可读内容。它不等价于原始 HTTP GET/POST；如果你需要接口响应体，请改用 `httpGet` / `httpPost` / `http`。
    *   **多工具聚合**: `combined_search` 函数展示了如何在一个工具内部调用其他多个工具的逻辑，并使用 `Promise.all` 来同时等待多个 `toolCall()`。注意：这不等于底层工具一定真正并行，实际并发度仍取决于宿主实现与具体工具执行器。
    *   **代码复用**: `performSearch` 作为一个内部辅助函数，被多个导出的工具复用，展示了良好的代码组织。

### 5.3. 纯粹的工具集: `examples/time.ts`

这是一个非常简洁的工具包，提供了多种时间格式化功能。

*   **学习重点**:
    *   **IIFE 的标准用法**: 完美展示了如何使用 `IIFE` 模式来封装一组相关的、无副作用的纯函数。
    *   **多函数导出**: 演示了如何在一个文件中定义并导出多个小而美的功能函数。
    *   **无外部依赖**: 这个脚本完全不依赖 `Tools` API，是一个纯粹的逻辑计算示例。

### 5.4. “元数据”的高级技巧: `examples/various_output.ts`

这个脚本看起来很简单，但它展示了一种与AI交互的特殊技巧。

*   **学习重点**:
    *   **利用描述(Description)**: `output_image` 工具的 `description` 字段并非简单描述功能，而是直接给出了一段提示(prompt)，指导AI如何通过输出特定格式的Markdown文本来展示图片。
    *   **另类工具**: 这表明了 `METADATA` 不仅可以用来定义可执行的函数，还可以作为一种向AI传递“指令”或“知识”的灵活机制。

通过研究以上这些脚本，你可以更快地掌握脚本开发的核心思想和最佳实践。

## 6. UI自动化详解

UI自动化是许多脚本的核心。

### `UINode` 对象

`Tools.UI.getPageInfo()` 返回的页面结构是一个 `UINode` 对象树。每个 `UINode` 代表一个屏幕上的UI元素，你可以通过它来：
-   查找子元素 (`findById`, `findByText`, `findByClass`, `findAllBy...`)
-   获取元素属性 (`text`, `contentDesc`, `bounds`, `resourceId`)
-   执行操作 (`click()`)

### 查找元素

查找元素是自动化的第一步。

```typescript
// 获取当前页面的根节点
const page = await UINode.getCurrentPage();

// 1. 通过资源ID查找 (最稳定)
const searchBox = page.findById('com.example:id/search_box');

// 2. 通过文本内容查找
const loginButton = page.findByText("登录");

// 3. 通过类名查找
const allTextViews = page.findAllByClass('TextView');

// 4. 通过内容描述 (contentDescription) 查找
const backButton = page.findByContentDesc("返回");
```

### 与元素交互

找到元素后，可以对其进行操作。

```typescript
if (loginButton) {
    await loginButton.click();
    await Tools.System.sleep(2000); // 等待页面跳转
}
```

## 7. 调试

-   使用 `console.log()`、`console.error()` 等函数输出日志。日志信息可以在执行环境中查看。
-   将复杂的逻辑拆分成小函数，并为每个函数添加清晰的日志输出。
-   在执行UI操作后，加入适当的 `Tools.System.sleep()` 来等待UI更新，避免操作过快导致失败。
-   如果你对某段代码本身是否可运行、某个 API 的返回值、Java bridge 行为或小片段语法不够确定，不要直接把猜测写进正式包里；可以先用 `operit_editor` 包里的 `debug_run_sandbox_script` 工具执行一小段 sandbox script 做快速验证。
-   这种小片段测试特别适合验证：
    -   某个 `Tools.*` 接口是否存在、参数怎么传
    -   某个 Java 类型/方法在当前宿主里能否正常调用
    -   某段 JSON / 返回结构 / 文件路径逻辑是否符合预期
-   建议先把不确定的逻辑缩成最小可运行片段，验证通过后再合并进正式脚本或 ToolPkg。

## 8. 编译

TypeScript 脚本 (`.ts`) 需要被编译成 JavaScript (`.js`)才能被执行。项目已配置好 `tsconfig.json`，通常可以使用 `tsc` 命令来编译所有脚本。 

## 9. 在设备上运行和测试脚本

当你编写完脚本并将其编译成 JavaScript 后，可以使用 `tools/` 目录下的辅助脚本通过 ADB (Android Debug Bridge) 在连接的安卓设备上运行它。

### 9.1. 前提条件

- **Android SDK (ADB)**: 确保你已经安装了 Android SDK，并且 `adb` 命令在你的系统路径中可用。
- **安卓设备**: 连接一台开启了“USB调试”功能的安卓设备，并已授权电脑进行调试。
- **Operit 应用程序**: 确保 `com.ai.assistance.operit.debug` 或 `com.ai.assistance.operit` 已经安装并在目标设备上运行。脚本会优先选择已安装的 Debug 包；也可以通过 `OPERIT_APP_PACKAGE` 指定其中一个。脚本的执行依赖于应用内的 `ScriptExecutionReceiver` 来接收和处理来自 ADB 的命令。

如果 Debug 和 Release APK 同时安装，默认使用 `com.ai.assistance.operit.debug`。想调试 Release APK 时，在启动脚本前设置：

```cmd
set OPERIT_APP_PACKAGE=com.ai.assistance.operit
```

Linux/macOS 使用：

```bash
export OPERIT_APP_PACKAGE=com.ai.assistance.operit
```

脚本会输出实际选择的 applicationId，并据此生成临时目录、广播 action 和 receiver。

### 9.2. 执行脚本函数

`tools` 目录下提供了 `execute_js.bat` (Windows) 和 `execute_js.sh` (Linux/macOS) 脚本来简化执行流程。

**使用方法 (以 Windows 为例):**

打开命令行工具，执行以下命令：

```cmd
tools\\adb\\execute_js.bat <JS文件路径> <要调用的函数名> [JSON格式的参数]
```

**示例：**

假设我们要执行 `my_new_script.js` 中的 `hello_world` 函数，并传入参数 `{ "name": "世界" }`：

```cmd
tools\\adb\\execute_js.bat examples\\my_new_script.js hello_world "{\\"name\\":\\"世界\\"}"
```

- 如果连接了多台设备，脚本会提示你选择要操作的设备。
- 注意：在 Windows `cmd` 中，JSON 字符串中的双引号需要使用 `\` 来转义。

### 9.3. 查看输出和调试

脚本执行后，`tools/adb/execute_js.*` 不再依赖 `adb logcat` 抓输出，而是会等待应用写出一份**结构化结果 JSON**。终端里会直接打印这份 JSON，其中包含：

- `success`
- `result`
- `error`
- `events`
- `durationMs`

`events` 里会带上脚本运行过程中的 `console.log/info/warn/error` 输出，以及中间过程产生的 intermediate result，后续调试时不需要再先清空 logcat 再碰运气抓日志。

如果脚本执行时间比较长，可以通过环境变量调整等待时间：

```bash
OPERIT_RESULT_WAIT_SECONDS=30 ./tools/adb/execute_js.sh examples/my_script.js main '{}'
```

这样就完成了一个从编写到设备上测试的完整开发循环。 

### 9.4. VS Code 集成 (推荐)

为了进一步简化开发流程，项目预置了 VS Code 的启动配置，让你可以直接在编辑器内一键运行和测试脚本。

**如何使用：**

1.  在 VS Code 中打开你要测试的脚本文件（`.ts` 或 `.js`）。
2.  切换到“运行和调试”视图 (快捷键 `Ctrl+Shift+D`)。
3.  在顶部的下拉菜单中，你会看到两个可用的配置：
    -   **`在Android设备上运行TS (编译+运行)`**: **推荐用法**。当你正在编辑一个 `.ts` 文件时，选择此项。它会自动编译你的代码，然后提示你输入要执行的函数名和参数，最后在设备上运行。
    -   **`在Android设备上运行JS`**: 用于直接运行一个已经编译好的 `.js` 文件。流程与上面类似，但不会执行编译步骤。
4.  点击绿色的“启动调试”按钮 (F5)。
5.  根据顶部弹出的提示框，依次输入**函数名**和 **JSON 格式的参数**，然后按回车。

VS Code 会自动打开一个新的终端面板，并执行相应的脚本，你可以在该面板中直接看到结构化 JSON 执行结果。这个集成化的工作流极大地提升了开发和调试的效率。 

## 10. VS Code 配置 (可选)

为了安全和避免不同开发者环境的冲突，`.vscode` 文件夹通常会被添加到 `.gitignore` 中，本项目也不例外。这意味着 `launch.json` 和 `tasks.json` 这两个配置文件不会被提交到版本控制中。

如果你想使用上一章节描述的 VS Code 一键启动功能，你需要手动在项目根目录创建这些文件。

### 10.1. 创建 tasks.json

1.  在项目根目录创建一个名为 `.vscode` 的文件夹。
2.  在 `.vscode` 文件夹内，创建一个名为 `tasks.json` 的文件。
3.  将以下内容复制并粘贴到 `tasks.json` 文件中：

```json
{
    "version": "2.0.0",
    "tasks": [
        {
            "label": "运行JavaScript到Android设备",
            "type": "shell",
            "command": ".\\tools\\adb\\execute_js.bat",
            "args": [
                "${fileDirname}\\${fileBasenameNoExtension}.js",
                "${input:jsFunction}",
                "${input:jsParameters}"
            ],
            "windows": {
                "command": ".\\tools\\adb\\execute_js.bat"
            },
            "linux": {
                "command": "./tools/adb/execute_js.sh"
            },
            "osx": {
                "command": "./tools/adb/execute_js.sh"
            },
            "problemMatcher": [],
            "group": {
                "kind": "build",
                "isDefault": true
            },
            "presentation": {
                "reveal": "always",
                "panel": "new",
                "focus": true
            }
        },
        {
            "label": "tsc-watch",
            "type": "shell",
            "command": "tsc",
            "args": [
                "--watch",
                "--project",
                "."
            ],
            "isBackground": true,
            "problemMatcher": "$tsc-watch",
            "group": "build",
            "presentation": {
                "reveal": "always",
                "panel": "dedicated",
                "focus": false
            }
        },
        {
            "label": "运行TypeScript到Android设备",
            "dependsOn": [
                "tsc-watch"
            ],
            "dependsOrder": "sequence",
            "type": "shell",
            "command": ".\\tools\\adb\\execute_js.bat",
            "args": [
                "${fileDirname}\\${fileBasenameNoExtension}.js",
                "${input:jsFunction}",
                "${input:jsParameters}"
            ],
            "windows": {
                "command": ".\\tools\\adb\\execute_js.bat"
            },
            "linux": {
                "command": "./tools/adb/execute_js.sh"
            },
            "osx": {
                "command": "./tools/adb/execute_js.sh"
            },
            "problemMatcher": [],
            "group": "test",
            "presentation": {
                "reveal": "always",
                "panel": "new",
                "focus": true
            }
        }
    ],
    "inputs": [
        {
            "id": "jsFunction",
            "description": "要执行的JavaScript函数名",
            "default": "main",
            "type": "promptString"
        },
        {
            "id": "jsParameters",
            "description": "函数参数(JSON格式)",
            "default": "{}",
            "type": "promptString"
        }
    ]
}
```

### 10.2. 创建 launch.json

1.  同样在 `.vscode` 文件夹内，创建一个名为 `launch.json` 的文件。
2.  将以下内容复制并粘贴到 `launch.json` 文件中：

```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "name": "Python Debugger: Current File",
            "type": "debugpy",
            "request": "launch",
            "program": "${file}",
            "console": "integratedTerminal"
        },
        {
            "name": "在Android设备上运行JS",
            "type": "node",
            "request": "launch",
            "preLaunchTask": "运行JavaScript到Android设备",
            "presentation": {
                "hidden": false,
                "group": "",
                "order": 1
            }
        },
        {
            "name": "在Android设备上运行TS (编译+运行)",
            "type": "node",
            "request": "launch",
            "preLaunchTask": "运行TypeScript到Android设备",
            "presentation": {
                "hidden": false,
                "group": "",
                "order": 2
            }
        }
    ]
}
```

完成这两个文件的创建后，重启 VS Code，“运行和调试”中的配置项就应该可用了。 
