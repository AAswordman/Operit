# Web 同步

## 旧实现

Web Classic 和 Agent 使用原生 `select` 显示全局数字。模型切换接口只刷新模型选择器，思考映射没有随当前模型返回。

## 新实现

`WebModelSelectorState` 携带当前 provider/model 的 `thinking_quality_mapping`。共享 Web 滑块根据 mapping 渲染标签，输入设置仍只负责保存当前内部 level。

Web 使用 20px 透明外框、16px active track、12px 圆形 thumb 和 4px stop indicator。active track 仅填充到当前档位，右侧保持透明；渐变和 thumb 高亮均由当前自定义主题变量派生。CSS shimmer 只在拖动时启用，松手后停止；`prefers-reduced-motion` 时禁用动画。

档位标签使用与 stop indicator 相同的 10px 端点内距和百分比锚点，不再使用等宽 Grid。标签最大宽度随档位间距计算，并跟随 `--chat-font-scale` 保留两行高度。

provider 参数名保留在 mapping 数据中，但不作为用户界面内容渲染。
