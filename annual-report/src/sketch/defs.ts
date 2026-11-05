import { svgEl } from './svg.ts';

/** 全局 SVG 滤镜：墨迹边缘的轻微抖动。只注入一次。 */
export function installSketchDefs(): void {
  if (document.getElementById('sketch-defs') !== null) return;
  const root = svgEl('svg', { id: 'sketch-defs', width: 0, height: 0, 'aria-hidden': 'true' });
  root.style.position = 'absolute';
  root.style.width = '0';
  root.style.height = '0';
  const defs = svgEl('defs');

  const ink = svgEl('filter', { id: 'ink-rough', x: '-5%', y: '-10%', width: '110%', height: '120%' });
  ink.append(
    svgEl('feTurbulence', { type: 'fractalNoise', baseFrequency: '0.045', numOctaves: 2, seed: 7, result: 'noise' }),
    svgEl('feDisplacementMap', { in: 'SourceGraphic', in2: 'noise', scale: 2.2, xChannelSelector: 'R', yChannelSelector: 'G' }),
  );

  const chalk = svgEl('filter', { id: 'chalk-rough', x: '-5%', y: '-10%', width: '110%', height: '120%' });
  chalk.append(
    svgEl('feTurbulence', { type: 'fractalNoise', baseFrequency: '0.9', numOctaves: 1, seed: 3, result: 'grain' }),
    svgEl('feColorMatrix', { in: 'grain', type: 'matrix', values: '0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 -1.6 1.25', result: 'mask' }),
    svgEl('feComposite', { in: 'SourceGraphic', in2: 'mask', operator: 'in', result: 'grained' }),
    svgEl('feTurbulence', { type: 'fractalNoise', baseFrequency: '0.05', numOctaves: 2, seed: 11, result: 'warp' }),
    svgEl('feDisplacementMap', { in: 'grained', in2: 'warp', scale: 1.8, xChannelSelector: 'R', yChannelSelector: 'G' }),
  );

  defs.append(ink, chalk);
  root.append(defs);
  document.body.prepend(root);
}
