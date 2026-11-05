---
fork: https://github.com/luojiaping/Operit
status: device-smoke-passed
related_pr: https://github.com/AAswordman/Operit/pull/1420
---

# 浏览器点击命中与导航结果

[返回 TODO 说明](../README.md)

## 原状与意图

上游 PR 的 ref 点击修复将 WebView 尺寸读取移回主线程。后续真机反馈还发现 selector 重复点击、滚动后触摸点越出视口、链接点击未导航，以及展示地址早于文档提交变化。

静态检查确认 selector 的事件派发后又执行 `target.click()`，一次调用可能多次激活目标；ref 与 selector 的普通单击使用不同路径。WebView 请求焦点晚于坐标计算也可能再次滚动已聚焦元素，使先前的目标几何信息失效。

普通 HTTP/HTTPS 的 `shouldOverrideUrlLoading` 已返回 false。这里修正目标准备、触摸派发和结果观测，沿用既有工具名、参数和返回结构。

## 最终行为

1. ref 与 selector 共用目标解析入口，普通左键单击使用同一原生触摸路径。特殊鼠标手势共用 JS 派发实现，双击与修饰键点击只产生对应的激活次数。
2. 先请求 WebView 焦点，再瞬时滚动并等待目标几何稳定。点击点必须处于可见视口内并通过命中测试；CSS 视口坐标只换算一次，不叠加文档 scrollY，不把越界点压到边缘。
3. 原生事件使用 TOUCHSCREEN 来源与 FINGER 工具类型，DOWN、UP 分开发送，使用真实事件时间，并要求两者均被接受。派发失败直接报告错误。
4. 原生 JavaScript bridge 记录真实 DOM click 的目标、次数、trusted 状态、坐标与取消状态。观察器由顶层文档管理到期清理，避免被移除 iframe 的计时器继续持有目标。
5. 未取消默认行为且目标 URL 不同的 HTTP/HTTPS 链接需要观察到导航；未导航返回 `clicked_but_not_navigated`，主框架加载失败返回 `navigation_failed`，等待文档就绪超时明确报告失败。按钮、下载、弹窗与页面主动取消的行为按其实际结果判定。
6. `pendingNavigationUrl` 与已提交的 `currentUrl` 分离。展示地址在文档提交或完成时更新，保留同页 history 更新；上一份文档的 complete 状态不能满足新导航的就绪判断。

iframe 的命中测试包含边框与缩放映射，并在目标浏览上下文观测导航。跨域后使用 frame load 事件作为生命周期证据，不声称读取了受限的文档 URL。

## 复验步骤

- 用 selector 和新 snapshot 的 ref 分别点击同一计数按钮：普通单击各增加 1，双击增加 2，修饰键点击不再额外派发无修饰点击。
- 点击视口外元素，核对最终触摸点与 DOM 事件坐标。720 高的视口应拒绝 818 坐标，覆盖层挡住目标时报告 `element_obscured`。
- Learn more、About、Contact Us 各从一次新的页面与 snapshot 开始，核对点击回执、展示地址和 `location.href`。
- 检查 preventDefault、普通按钮、下载和 alert/confirm/prompt，以及 hash、pushState、同源 iframe 和跨域 iframe 的结果。

下面的临时探针保留页面的 smooth-scroll 设置，用来检查工具是否完成瞬时滚动并准确命中。重新导航即可清除探针。

```javascript
() => {
    document.documentElement.style.scrollBehavior = "smooth";
    const button = document.createElement("button");
    button.id = "operit-offscreen-probe";
    button.textContent = "Offscreen click probe";
    button.style.position = "absolute";
    button.style.top = "1600px";
    button.style.left = "40px";
    const events = [];
    button.addEventListener("click", event => {
        const rect = button.getBoundingClientRect();
        events.push({
            trusted: event.isTrusted,
            x: event.clientX,
            y: event.clientY,
            centerX: (rect.left + rect.right) / 2,
            centerY: (rect.top + rect.bottom) / 2,
            viewportHeight: window.visualViewport.height
        });
    });
    document.body.appendChild(button);
    window.__operitOffscreenClicks = events;
    return { selector: "#operit-offscreen-probe" };
}
```

## 日志判读

日志标签为 `BrowserNavigation`，记录 session、WebView 实例、导航序号和点击编号。JSON 顶层 `lastClickId` 是最近发起的点击；DOM 记录中的 `clickId` 才是观察器绑定的编号。结合 `clickAgeMs` 和生命周期记录关联动作。

- `native-tap`：触摸派发记录；`dom-click-capture` 与 `dom-click-final`：实际命中与最终取消状态
- `matchesTarget=false`：接收事件的元素与预期目标不同
- `defaultPrevented=true`：页面取消默认行为；页面销毁可能早于延后记录
- `page-started`、`page-commit-visible`、`page-finished`：请求开始、文档提交与完成
- `main-frame-error`、`main-frame-http-error`：主框架加载错误
- `popup-created`、`frame-load`：新浏览上下文或 iframe 的生命周期证据

## 验证记录

2026-11-04，用户反馈：“除了waifu和外部http没测试，其它都通过测试了”。浏览器修复按用户已执行的真机场景记录为通过；上面的复验步骤用于复现同类问题，不扩大已测范围。

`NativeTapCoordinatesTest` 包含 10 项坐标回归用例，覆盖视口外点、非整数密度、无效尺寸及非有限坐标。主应用与全部 JVM 单测源码已在本地集成版本编译通过；上一轮筛选命令未执行这个测试类。本次提交整理未重新执行构建或测试。

[DONE]
