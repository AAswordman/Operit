/*
    欢迎来到 TypeScript/JavaScript AI 工具开发终极入门教程 (v2.0 详细版)
    ====================================================================

    本教程为初学者设计，特别是那些熟悉其他脚本语言（如Python）但对TypeScript/JavaScript不熟悉的开发者。
    我们将从最基础的语法开始，一步步带你构建一个功能完整、结构优雅的AI工具，并详细解释每一步背后的“为什么”。
*/


// =================================================================
// Part 0: 关于运行环境 (A Note on the Environment)
// =================================================================
/*
    在开始之前，你需要知道，你的代码并不是在真空中运行。它在一个特殊的“沙箱”环境中执行。
    这个环境为你预先提供了一些全局可用的函数和对象，最重要的有：

    1. `complete(result: object)`:
       - 这是你的工具与AI系统沟通的【唯一】桥梁。
       - 当你的工具执行完毕，无论成功还是失败，都必须调用这个函数来返回结果。
       - AI会根据你传入这个函数的对象内容，来决定下一步的行为。

    2. `exports`:
       - 这是一个特殊的对象，你可以把它看作是你代码文件的“公开接口”。
       - 你需要将你的工具函数“挂载”到 `exports` 对象上，AI才能找到并调用它们。
       - 示例: `exports.myToolName = myToolFunction;`

    3. `Tools`:
       - 这是一个内置的工具集，提供了很多实用的辅助功能，比如文件读写、网络请求、系统命令等。
       - 我们在本教程中会用到 `Tools.System.sleep()` 来演示异步操作。

    本教程的所有代码都在一个TypeScript文件（.ts）中。TypeScript是JavaScript的超集，它增加了“类型系统”，
    能帮助我们编写更健壮、更不容易出错的代码。
*/


// =================================================================
// Part 1: JavaScript / TypeScript 核心语法快速入门
// =================================================================
/*
    本部分将详细介绍编写AI工具必需的JS/TS语法，并为每个知识点提供代码示例。
*/

// --- 1.1 变量声明 (Variables) ---
/*
    - `const` (常量): 用于声明一个值不会再改变的变量。这是首选的声明方式，能让代码更可预测。
    - `let` (变量): 用于声明一个值将来可能需要被修改的变量。
*/
function variable_example() {
    const toolName = "My Greeter Tool"; // 一旦设定，toolName就不能再被赋值
    let executionCount = 0;           // executionCount 的值可以改变
    console.log(`工具名称: ${toolName}`);

    executionCount = executionCount + 1;
    console.log(`这是第 ${executionCount} 次执行。`);

    // 下面这行代码会报错，因为 toolName 是一个常量
    // toolName = "New Name";
}
// variable_example(); // 你可以取消注释来在本地测试这些函数


// --- 1.2 数据类型 (Data Types) ---
/*
    TypeScript 强制我们思考数据的类型，这极大地减少了运行时错误。
    - `string`: 文本字符串。
    - `number`: 数字（整数或浮点数）。
    - `boolean`: 布尔值 (`true` 或 `false`)。
    - `object`: 对象，键值对的集合，类似于Python的字典。
    - `array`: 数组，有序的数据列表，类似于Python的列表。
    - `any`: 特殊类型，表示“任何类型都可以”。应尽量避免使用，因为它会削弱TypeScript的优势。
*/
function data_types_example() {
    // 我们可以明确地为变量注解类型
    const userName: string = "Alice";
    const userAge: number = 30;
    const isActive: boolean = true;

    // 对象类型注解，描述了一个对象的“形状”
    const userInfo: { name: string; age: number; premium: boolean } = {
        name: "Bob",
        age: 25,
        premium: false
    };
    console.log(`${userInfo.name} 的年龄是 ${userInfo.age}`);

    // 数组类型注解，`string[]` 表示一个“只包含字符串的数组”
    const permissions: string[] = ["read", "write", "execute"];
    console.log(`用户的第一个权限是: ${permissions[0]}`);

    // 遍历数组
    permissions.forEach(permission => {
        console.log(`权限: ${permission}`);
    });
}
// data_types_example();


// --- 1.3 函数 (Functions) 与 异步操作 (Async Operations) ---
/*
    - `async function`: 这是定义AI工具函数的标准方式。`async` 关键字表明该函数内部可能包含需要“等待”的操作。
    - `await`: 必须在 `async` 函数内部使用。它会暂停函数的执行，直到其后的异步操作（通常是一个`Promise`）完成，然后返回结果。
    - `Promise<T>`: 代表一个异步操作最终完成（或失败）的结果。`async` 函数的返回值总是一个`Promise`。
      例如 `Promise<string>` 意味着这个异步函数最终会返回一个字符串。
*/
async function async_function_example(name: string): Promise<string> {
    console.log(`开始向 ${name} 发送问候...`);

    // `await` 在这里暂停函数，等待 Tools.System.sleep(1500) 完成
    // 这模拟了一个耗时1.5秒的网络请求或文件操作
    await Tools.System.sleep(1500);

    const greetingMessage = `你好, ${name}! 异步问候已送达。`;
    console.log(greetingMessage);

    // async 函数通过 return 返回的值，会被包装在 Promise 中
    return greetingMessage;
}
// async_function_example("Charlie");


// --- 1.4 错误处理 (Error Handling) ---
/*
    - `try...catch`: 这是处理潜在错误的标准方式，对于健壮的工具至关重要。
    - `throw new Error(...)`: 当函数执行不下去时，主动“抛出”一个错误，中断当前`try`块的执行，并被`catch`块捕获。
*/
function error_handling_example(input: any) {
    try {
        console.log("尝试处理输入...");

        // 步骤1: 检查输入是否为非空字符串
        if (typeof input !== 'string' || input.trim() === '') {
            // 如果检查失败，就“抛出”一个错误对象
            throw new Error("输入必须是一个非空的字符串。");
        }

        // 步骤2: 如果代码能执行到这里，说明没有错误发生
        console.log(`输入有效，内容是: "${input}"`);
        return { success: true, data: input };

    } catch (error) {
        // 步骤3: 如果`try`块中任何地方抛出了错误，程序会立即跳转到这里
        console.error("发生了一个错误!");
        console.error(`错误详情: ${error.message}`); // error.message 包含了我们抛出时提供的信息

        // 在实际工具中，这里我们会调用 complete() 来报告失败
        return { success: false, message: error.message };
    }
}
// error_handling_example("这是一个有效的输入");
// error_handling_example(""); // 这个会触发错误
// error_handling_example(123); // 这个也会触发错误


// =================================================================
// Part 2: AI工具的本质 与 `complete()` 函数
// =================================================================
/*
    如前所述，一个AI工具就是一个函数，它通过 `complete()` 函数与AI系统交互。
    下面是一个最基础的、结构完整的AI工具示例，我们为其中的关键部分添加了注释。
*/
async function simple_greeter_tool(params: { user_name: string }) {
    // 典型的工具函数总是在一个大的 try...catch 块中
    try {
        // 1. 从 AI 接收到的参数中解构出需要的值
        const { user_name } = params;

        // 2. 参数校验：永远不要相信输入！
        if (!user_name) {
            throw new Error("参数 'user_name' 缺失，无法生成问候语。");
        }

        // 3. 执行核心业务逻辑
        const greeting = `你好, ${user_name}! 欢迎来到AI工具的世界。`;

        // 4. 调用 complete()，报告成功
        //    - success: true 表示工具成功完成了它的任务。
        //    - data: 存放工具的执行结果，这是AI最关心的部分。
        complete({
            success: true,
            data: greeting
        });

    } catch (error) {
        // 5. 如果 try 块中任何地方出错（无论是参数校验还是业务逻辑），都会进入这里
        console.error("simple_greeter_tool 执行失败:", error);

        // 6. 调用 complete()，报告失败
        //    - success: false 告诉AI任务失败了。
        //    - message: 存放清晰的错误信息，帮助AI理解失败原因。
        complete({
            success: false,
            message: `工具执行失败: ${error.message}`
        });
    }
}


// =================================================================
// Part 3: Metadata 和 函数导出 - 连接AI与代码的桥梁
// =================================================================
/*
    我们已经写好了工具函数，但AI如何知道它的存在、功能和用法呢？
    这需要两样东西：
    1. `METADATA` (元数据): 就像是工具的“说明书”。
    2. `exports` (导出): 像是把工具函数“注册”到系统里。

    --- 3.1 详解 METADATA ---
    文件顶部的 ` METADATA ... ` 注释块就是这份说明书。AI在加载时会解析它。
    - `name`: (string) 工具集的唯一ID。
    - `description`: (string) 对整个工具集的详细描述。
    - `tools`: (array) 一个数组，包含此文件中所有可用的工具。
      - `name`: (string) 单个工具的函数名。这个名字【必须】和后面 `exports` 中使用的名字完全一致。
      - `description`: (string) 对这个工具能做什么的清晰描述。AI会根据这个描述来决定何时调用它。
      - `parameters`: (array) 定义了此工具需要哪些参数。
        - `name`: (string) 参数名。
        - `description`: (string) 对参数用途的描述。
        - `type`: (string) 参数的类型 (e.g., 'string', 'number', 'boolean')。
        - `required`: (boolean) 这个参数是否是必需的。
*/

/* METADATA
{
    "name": "greeter_tool_v2",

    "display_name": {
        "zh": "问候工具 V2",
        "en": "Greeting Tool V2"
    },
    "description": { "zh": "一个提供多种问候方式的教学工具集。", "en": "A tutorial tool package that demonstrates multiple greeting methods." },
    "category": "Utility",
    "tools": [
        {
            "name": "greet",
            "description": { "zh": "向指定的人发送一个标准的问候。", "en": "Send a standard greeting to a specified person." },
            "parameters": [
                {
                    "name": "user_name",
                    "description": { "zh": "要问候的人的名字。", "en": "Name of the person to greet." },
                    "type": "string",
                    "required": true
                }
            ]
        },
        {
            "name": "system_inspector",
            "description": { "zh": "获取设备摘要信息，并在用户的HOME目录下查找符合特定模式的文件。", "en": "Get device summary information and find files matching a pattern under the user's HOME directory." },
            "parameters": [
                {
                    "name": "search_pattern",
                    "description": { "zh": "用于文件搜索的通配符模式，例如：'*.txt' 或 'documents/*.pdf'。", "en": "Glob pattern for file search, e.g. '*.txt' or 'documents/*.pdf'." },
                    "type": "string",
                    "required": true
                }
            ]
        }
    ]
}*/

// --- 3.2 详解 exports ---
/*
    `exports` 是一个空对象，等待你来填充。
    你必须将你的工具函数赋值给 `exports` 对象的一个属性，属性名必须和 `METADATA` 中定义的工具 `name` 完全一致。
    只有这样，AI才能把“说明书”和“实际的函数实现”关联起来。

    `exports.greet = simple_greeter_tool;`

    上面这行代码的意思是：
    “当AI想要调用名为 'greet' 的工具时，请执行 `simple_greeter_tool` 这个函数。”
    (我们将在教程的最后进行统一导出，所以暂时注释掉这行)
*/


// =================================================================
// Part 4: IIFE模式 - 隔离作用域，避免代码冲突
// =================================================================
/*
    当项目变大，你可能会定义很多辅助函数、变量。如果都放在全局（文件的顶层），很容易与其他工具或系统库产生命名冲突。
    IIFE (Immediately Invoked Function Expression - 立即调用函数表达式) 是解决这个问题的经典JS模式。

    它的写法是 `(function() { ... })();`
    - `(function() { ... })` 定义了一个匿名函数。
    - 最后的 `()` 表示“立即执行”这个刚刚定义的匿名函数。

    这样做的好处是创建了一个“私有作用域”。在IIFE内部声明的任何变量或函数，在外部都是不可见的，从而避免了污染全局命名空间。
    我们可以通过 `return` 语句，有选择性地将需要公开的函数暴露出来。
*/

// 使用IIFE模式重构我们的问候工具
const greeterModule = (function () {
    // --- 这里是私有作用域 ---

    // `version` 和 `logWithTimestamp` 在IIFE外部无法被访问，是我们的内部实现细节。
    const version = "2.0";

    function logWithTimestamp(message: string) {
        const timestamp = new Date().toLocaleTimeString();
        console.log(`[${timestamp}][v${version}] ${message}`);
    }

    // 这是我们真正的业务逻辑实现
    async function performGreeting(params: { user_name: string }) {
        try {
            logWithTimestamp(`开始执行问候工具，参数: ${JSON.stringify(params)}`);
            const { user_name } = params;
            if (!user_name) {
                throw new Error("参数 'user_name' 缺失。");
            }
            const greeting = `你好, ${user_name}! (来自IIFE模式 v${version})`;
            complete({ success: true, data: greeting });
        } catch (error) {
            logWithTimestamp(`问候工具失败: ${error.message}`);
            complete({ success: false, message: error.message });
        }
    }

    // --- 公开接口 ---
    // 通过 return 一个对象，我们将 `performGreeting` 函数以 `greet` 的名字暴露出去。
    return {
        greet: performGreeting
        // 如果有其他工具，可以继续在这里暴露: anotherTool: someInternalFunction
    };

})(); // 注意这里，函数被立即执行了

// 现在，IIFE已经执行完毕，`greeterModule` 变量包含了我们返回的对象。
// 我们可以这样导出它：
// `exports.greet = greeterModule.greet;`


// =================================================================
// Part 5: Wrapper模式 - 终极形态，让代码更健壮、更简洁
// =================================================================
/*
    观察上面的代码，即使使用了IIFE，`performGreeting` 函数内部仍然有重复的 `try/catch` 和 `complete()` 调用。
    在软件工程中，重复的代码（boilerplate）是维护的噩梦。

    我们可以创建一个 `wrapper` (包装器) 函数来终结这种重复。
    Wrapper 的职责非常清晰：
    1. 接收一个“核心业务逻辑函数”作为输入。
    2. 在内部建立 `try/catch` 安全网。
    3. 负责调用核心函数，并等待其结果。
    4. 无论成功还是失败，都由它来调用 `complete()` 并返回标准格式的结果。

    这样，我们编写的工具函数就可以变得非常纯粹：只关心输入、业务处理和输出（通过`return`和`throw`），
    完全不用理会 `try/catch` 和 `complete` 这些模板代码。
*/

// --- 最终的、最推荐的开发模式 ---

const finalTool = (function () {

    // 1. 定义我们的通用 Wrapper 函数
    //    它接收一个核心逻辑函数和其所需的参数。
    async function wrap(coreFunction: (params: any) => Promise<any>, params: any) {
        try {
            // 调用核心业务逻辑，并使用 await 等待其完成
            const result = await coreFunction(params);

            // 核心逻辑成功返回，Wrapper负责包装成标准成功对象并调用complete
            complete({
                success: true,
                message: "工具执行成功。",
                data: result // 将核心函数的返回值放入data字段
            });

        } catch (error: any) {
            console.error(`工具 [${coreFunction.name}] 执行时捕获到错误:`, error);

            // 核心逻辑抛出异常，Wrapper负责捕获并包装成标准失败对象
            complete({
                success: false,
                message: `错误: ${error.message}`, // 将错误信息放入message字段
                error_stack: error.stack // 附带完整的错误堆栈以供调试
            });
        }
    }

    // =================================================
    //  核心业务逻辑区 (Core Logic Functions)
    // =================================================

    // 2a. “问候”工具的核心逻辑
    async function greet_core_logic(params: { user_name: string }): Promise<string> {
        if (!params.user_name || params.user_name.trim() === '') {
            throw new Error("user_name 不能为空。");
        }
        const greeting = `你好, ${params.user_name}! 这是一个采用终极Wrapper模式的工具。`;
        return greeting;
    }

    // 2b. “系统检查器”工具的核心逻辑
    async function inspect_system_core_logic(params: { search_pattern: string }): Promise<object> {
        if (!params.search_pattern) {
            throw new Error("search_pattern 不能为空。");
        }

        console.log(`开始系统检查，搜索模式: '${params.search_pattern}'`);

        // 为了让逻辑更清晰，我们一步一步地、顺序地调用工具
        // 1. 首先，获取设备信息
        console.log("正在获取设备信息...");
        const deviceInfo = await Tools.System.getDeviceInfo();
        console.log("设备信息获取成功。");

        // 2. 然后，查找文件
        console.log(`正在主目录 ('~') 下查找文件，模式: '${params.search_pattern}'`);
        const foundFiles = await Tools.Files.find('~', params.search_pattern);
        console.log("文件查找成功。");

        // 组合结果并返回一个结构化的对象
        return {
            device: deviceInfo,
            files: foundFiles
        };
    }


    // =================================================
    //  导出函数封装区 (Exported Functions)
    // =================================================

    // 3a. 暴露给AI的“问候”函数
    async function greet_exported_function(params: { user_name: string }) {
        await wrap(greet_core_logic, params);
    }

    // 3b. 暴露给AI的“系统检查器”函数
    async function inspect_system_exported_function(params: { search_pattern: string }) {
        await wrap(inspect_system_core_logic, params);
    }


    // 4. 从IIFE中返回所有需要导出的工具
    return {
        greet: greet_exported_function,
        system_inspector: inspect_system_exported_function
    };

})();


// =================================================================
// Part 6: 使用内置工具集 (Using the Built-in `Tools`)
// =================================================================
/*
    沙箱环境提供了一个强大的全局对象 `Tools`，它是你与外部世界交互的主要入口。
    `Tools` 对象被组织成多个模块，每个模块负责一类特定的功能。

    主要的模块包括：
    - `Tools.System`: 系统级操作。
      - `getDeviceInfo()`: 获取设备信息（型号、操作系统版本等）。
      - `sleep(ms)`: 让工具暂停指定的毫秒数。
      - `exec(command)`: 执行shell命令。
    - `Tools.Files`: 文件系统操作。
      - `read(path)`: 读取文件内容。
      - `write(path, content)`: 写入内容到文件。
      - `find(directory, pattern)`: 在指定目录中查找匹配模式的文件。
      - `remove(path)`: 删除文件或目录。
    - `Tools.Net`: 网络操作。
      - `fetch(url, options)`: 发起HTTP请求，类似于浏览器中的`fetch`。
    - `Tools.UI`: 与设备UI交互（高级功能）。
      - `getScreenshot()`: 获取当前屏幕截图。
      - `findAndClick(text)`: 查找并点击包含特定文本的UI元素。
    - `Tools.Memory`: 记忆管理功能。
      - `query(query)`: 查询记忆库。
      - `create(title, content, ...)`: 创建新记忆。
      - `update(oldTitle, updates)`: 更新记忆。
      - `deleteMemory(title)`: 删除记忆。
      - `link(sourceTitle, targetTitle, ...)`: 链接记忆。
    - `Tools.FFmpeg`: 音视频处理（高级功能）。

    所有的 `Tools` 函数都是【异步】的，意味着你必须使用 `await` 来调用它们，
    并且调用它们的代码必须位于 `async` 函数内部。

    在上面的 `Part 5` 中，我们已经为你演示了如何实现一个名为 `system_inspector` 的新工具。
    它完美地展示了：
    1. 如何在核心逻辑函数中调用 `Tools.System.getDeviceInfo()` 和 `Tools.Files.find()`。
    2. 如何按顺序地调用多个异步工具，等待每一个操作完成后再进行下一步。
    3. 如何将这些调用集成到我们健壮的 `Wrapper` 模式中。

    请回头仔细阅读 `Part 5` 中 `inspect_system_core_logic` 函数的实现，
    它是你学习使用内置 `Tools` 的最佳起点。
*/


// ==========================================
// 最终导出 (Final Export)
// ==========================================
// 将我们在最终IIFE模式中返回的函数，挂载到exports上。
// 这里的 `greet` 和 `system_inspector` 必须和METADATA中定义的工具 `name` 完全一致。
exports.greet = finalTool.greet;
exports.system_inspector = finalTool.system_inspector;

/*
    🎓 恭喜! 你已经学完了从零到一的完整开发流程。

    现在回顾一下我们构建最终代码形态的旅程：
    1. **基础语法**: 掌握了编写代码的基本工具。
    2. **基础工具+complete()**: 理解了AI工具的本质和与系统的交互方式。
    3. **Metadata+exports**: 学会了如何让AI“发现”并“使用”你的工具。
    4. **IIFE模式**: 学会了如何通过作用域隔离来组织代码，避免命名冲突，使代码更模块化。
    5. **Wrapper模式**: 学会了如何通过分离“业务逻辑”和“模板代码”来编写更简洁、更健壮、更易于维护的工具代码。
    6. **内置工具 `Tools`**: 学会了如何利用系统提供的强大工具与文件、网络和操作系统进行交互。

    这套“Wrapper + IIFE + Tools”的组合是经过实践检验的最佳实践，强烈建议你在开发自己的工具时采用它。
*/ 