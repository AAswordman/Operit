# Android 滑块

## 旧实现

Classic 使用带数字输入框的 `SettingSliderItem`，Agent 使用默认 Material `Slider` 并在右侧显示数字。两套控件没有真实映射标签，也没有共享视觉实现。

## 新实现

共享 `ThinkingQualitySlider` 使用 Material 3 `Slider` 的自定义 track 和 thumb：

- 五个离散位置，内部仍对应全局 level
- 20dp 透明外框内绘制 16dp active track、12dp 圆形 thumb 和 4dp stop indicator
- provider 映射文本显示在 track 下方
- 档位标签与 stop indicator 共用 10dp 端点内距和等距锚点，最大宽度随相邻档位间距调整
- 标签最多显示两行，窄屏和放大字体下保持在容器边界内
- 当前 provider 值显示在标题右侧
- 不在用户界面显示 provider 参数名
- active track 仅从起点绘制到当前档位，右侧保持透明并只显示未选档位点
- 渐变和 thumb 高亮均由当前自定义主题色派生，不固定具体色值
- thumb 完整嵌入 active track，不绘制外圈和阴影
- 只有拖动期间在 active track 内播放流光，松手后停止
- 关闭思考模式或减少动态效果时停止动画
