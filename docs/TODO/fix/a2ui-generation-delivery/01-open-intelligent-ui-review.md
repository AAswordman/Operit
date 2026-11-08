# Open Intelligent UI 接入评估

核对日期：2026-10-23。核对来源为 OpenAI 官方文章，以及 thesysdev/open-intelligent-ui 的 README、package.json、聊天路由、组件目录、提示选项、HTML 组件和消息存储源码；没有安装、构建或运行该项目，也没有核验其托管网关内部实现。

OpenAI 的 [GPT-6 与智能界面官方文章](https://openai.com/index/gpt-6-for-everyone/) 日期为 2026-10-07，描述流式原生组件库、编译器与针对界面设计的模型训练。文章描述 ChatGPT 产品体验，没有在该文公布可直接移植到 Operit 的官方渲染器源码。

[第三方仓库](https://github.com/thesysdev/open-intelligent-ui) 创建于 2026-10-08，采用 MIT 许可，README 明确说明独立于 OpenAI。它基于 OpenUI Lang 1.0、React 19、Next.js 16 和一套自定义组件重现体验，不是 GPT 模型权重或 OpenAI 内部 UI 运行时的公开版本。

[聊天路由](https://github.com/thesysdev/open-intelligent-ui/blob/main/src/app/api/chat/route.ts) 支持可选 Thesys Gateway 和任意 OpenAI 兼容接口直连。README 声明网关会校正输出；直连路径只生成提示并转发文本，没有该校正步骤。模型通用可接入，并不意味着较弱模型无需遵循语法。README 给出的完整提示约 8000 tokens，界面规则和示例也是其效果的重要组成部分。

其组件使用 React、DOM、MapLibre、KaTeX 及沙盒 iframe，消息填写状态使用浏览器 localStorage。接入 Operit Android 有两条实际路径：引入 WebView 内的完整前端并实现原生动作、权限和聊天状态桥接；或移植其语言解析和表达式求值，再实现 Compose 组件映射。两者都需要继续支持已经保存的 A2UI 卡片，不能只替换一个依赖。

结论：当前不整体替换。现有问题主要在消息投递、参数约定和围栏一致性，这个仓库的直连路径不会自动消除这些问题。先完成本轮 A2UI 衔接修复；后续可参考其从组件定义生成提示、完整工作示例和本地状态交互设计，若要引入 WebView 游戏等能力，应作为独立功能评估。
