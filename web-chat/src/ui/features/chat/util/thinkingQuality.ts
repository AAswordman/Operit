export const MIN_THINKING_QUALITY_LEVEL = 1;
export const MAX_THINKING_QUALITY_LEVEL = 5;

export function clampThinkingQualityLevel(value: number): number {
  return Math.max(MIN_THINKING_QUALITY_LEVEL, Math.min(MAX_THINKING_QUALITY_LEVEL, value));
}
