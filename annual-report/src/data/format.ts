// 数字统一格式化成手写字形能写出的字符：数字、逗号、小数点、万、亿。

export function groupDigits(value: number): string {
  const rounded = Math.round(value);
  const sign = rounded < 0 ? '-' : '';
  const digits = Math.abs(rounded).toString();
  let result = '';
  for (let index = 0; index < digits.length; index += 1) {
    const fromEnd = digits.length - index;
    result += digits.charAt(index);
    if (fromEnd > 1 && fromEnd % 3 === 1) result += ',';
  }
  return sign + result;
}

function trimDecimal(value: number, digits: number): string {
  return value.toFixed(digits).replace(/\.?0+$/, '');
}

/** 十万以下按千分位；一百万以下带一位小数的"万"；一亿以下取整的"万"；再往上用"亿"。 */
export function compactCount(value: number): string {
  const abs = Math.abs(value);
  if (abs < 100_000) return groupDigits(value);
  if (abs < 1_000_000) return `${trimDecimal(value / 10_000, 1)}万`;
  if (abs < 100_000_000) return `${groupDigits(value / 10_000)}万`;
  return `${trimDecimal(value / 100_000_000, 2)}亿`;
}

export interface LocalDate {
  year: number;
  month: number;
  day: number;
}

/** 解析 YYYY-MM-DD 或带偏移的 ISO 时间里的本地日期部分，不经过设备时区换算。 */
export function localDate(text: string): LocalDate {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(text);
  if (match === null) throw new Error(`无法解析日期：${text}`);
  return { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) };
}

export interface LocalTime {
  hour: number;
  minute: number;
}

export function localTime(text: string): LocalTime {
  const match = /T(\d{2}):(\d{2})/.exec(text);
  if (match === null) throw new Error(`无法解析时间：${text}`);
  return { hour: Number(match[1]), minute: Number(match[2]) };
}

export function pad2(value: number): string {
  return value.toString().padStart(2, '0');
}

export function dateKey(date: LocalDate): string {
  return `${date.year}-${pad2(date.month)}-${pad2(date.day)}`;
}

/** 两个本地日期之间相差的天数。 */
export function daysBetween(from: LocalDate, to: LocalDate): number {
  const a = Date.UTC(from.year, from.month - 1, from.day);
  const b = Date.UTC(to.year, to.month - 1, to.day);
  return Math.round((b - a) / 86_400_000);
}

export function durationParts(ms: number): { hours: number; minutes: number } {
  const totalMinutes = Math.round(ms / 60_000);
  return { hours: Math.floor(totalMinutes / 60), minutes: totalMinutes % 60 };
}

export function share(part: number, total: number): number {
  if (total <= 0) return 0;
  return part / total;
}

export function percentText(part: number, total: number): string {
  return `${Math.round(share(part, total) * 100)}%`;
}

export function money(value: number, currency: 'CNY' | 'USD'): string {
  const symbol = currency === 'CNY' ? '¥' : '$';
  return `${symbol}${value.toFixed(2)}`;
}
