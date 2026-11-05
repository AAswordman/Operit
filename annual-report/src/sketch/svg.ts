export const SVG_NS = 'http://www.w3.org/2000/svg';

type AttributeValue = string | number;

export function svgEl<K extends keyof SVGElementTagNameMap>(
  tag: K,
  attributes: Record<string, AttributeValue> = {},
): SVGElementTagNameMap[K] {
  const element = document.createElementNS(SVG_NS, tag);
  for (const [name, value] of Object.entries(attributes)) {
    element.setAttribute(name, String(value));
  }
  return element;
}

export function svgRoot(width: number, height: number, className?: string): SVGSVGElement {
  const root = svgEl('svg', { viewBox: `0 0 ${width} ${height}`, width, height });
  if (className !== undefined) root.setAttribute('class', className);
  root.setAttribute('aria-hidden', 'true');
  return root;
}

export function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  className?: string,
  children: (Node | string)[] = [],
): HTMLElementTagNameMap[K] {
  const element = document.createElement(tag);
  if (className !== undefined && className !== '') element.className = className;
  for (const child of children) {
    element.append(child);
  }
  return element;
}

export function pathsOf(root: Element): SVGPathElement[] {
  return Array.from(root.querySelectorAll('path'));
}
