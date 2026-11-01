"use strict";
// 可视化编辑保存在 context.appearance；可在此按明暗模式计算额外样式。
module.exports = {
  apiVersion: 1,
  render(context) {
    return context.appearance;
  }
};
