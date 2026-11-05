import type {
  ComposeDslContext,
  ComposeNode,
} from "../../../types/compose-dsl";

/**
 * 悬浮窗内容页。count 的初值来自 open({ state: { count } }) 注入的
 * 窗口 state；update(id, options, state) 浅合并后重渲染会反映到本页。
 */
export default function Screen(ctx: ComposeDslContext): ComposeNode {
  const { UI } = ctx;
  const colors = ctx.MaterialTheme.colorScheme;
  const [count, setCount] = ctx.useState("count", 0);

  return UI.Column(
    {
      fillMaxSize: true,
      padding: 12,
      spacing: 8,
    },
    [
      UI.Text({
        text: "悬浮窗示例",
        style: "titleMedium",
        color: colors.onSurface,
      }),
      UI.Text({
        text: `count = ${String(count)}`,
        style: "headlineSmall",
        color: colors.primary,
      }),
      UI.Button({
        text: "窗口内自增 +1",
        onClick: () => {
          setCount(count + 1);
        },
      }),
    ],
  );
}
