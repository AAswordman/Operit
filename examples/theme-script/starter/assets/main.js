"use strict";

// 此脚本与同目录的 theme.json 配套，读取其中已定义的两个区域。
// 只混合 RGB 通道，明确输出不透明颜色；输入配色值由主题宿主提供。
function mixRgb(left, right, amount) {
  const a = parseInt(left.slice(-6), 16);
  const b = parseInt(right.slice(-6), 16);
  const channels = [16, 8, 0].map(function (shift) {
    const start = (a >>> shift) & 255;
    const end = (b >>> shift) & 255;
    return Math.round(start * (1 - amount) + end * amount)
      .toString(16).padStart(2, "0");
  });
  return "#" + channels.join("");
}

// 固定生成十二个点，坐标均留在部件内部；它们是静态图案，不是动画。
function makeDots(color, dark) {
  return Array.from({ length: 12 }, function (_, index) {
    return {
      id: "starter_dot_" + index,
      type: "ellipse",
      color: color,
      opacity: dark ? 0.35 : 0.22,
      placement: "background",
      bounds: {
        x: 0.05 + (index % 4) * 0.24,
        y: 0.08 + Math.floor(index / 4) * 0.35,
        width: 0.018,
        height: 0.012
      }
    };
  });
}

module.exports = {
  apiVersion: 1,
  render(context) {
    const base = context.appearance;
    const background = base.surfaces.background;
    const composer = base.surfaces.composer;
    const dark = context.mode === "dark";
    const accent = context.colors.primary;
    const border = mixRgb(accent, context.colors.surface, dark ? 0.35 : 0.6);

    // 读取原有设置作为条件，只在返回外观里改变最小高度。
    const inputHeight = context.settings.strings.input_style === "classic" ? 48 : 56;
    return {
      ...base,
      apiVersion: 1,
      surfaces: {
        ...base.surfaces,
        background: {
          ...background,
          layers: [...background.layers, ...makeDots(accent, dark)]
        },
        composer: {
          ...composer,
          minHeight: inputHeight,
          strokeColor: border,
          states: {
            ...composer.states,
            focused: {
              ...composer.states.focused,
              strokeColor: accent,
              strokeWidth: 2
            }
          }
        }
      }
    };
  }
};
