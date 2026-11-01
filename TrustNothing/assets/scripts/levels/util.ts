import { LevelDef } from '../core/types';
/** Pads the map on top with empty rows so every level is at least `rows` tall. */
export function M(bottom: string[], rows = 18): string[] {
  const out = bottom.slice();
  while (out.length < rows) out.unshift('.');
  return out;
}
/** Repeats a char n times (handy inside template literals). */
export const rep = (c: string, n: number) => c.repeat(n);
export type L = LevelDef;
