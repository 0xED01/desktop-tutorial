/** Abstract 2D renderer — implemented by Canvas2D (web/WebView/WeChat) and cc.Graphics (Cocos). */
export type Align = 'left' | 'center' | 'right';

export interface IRenderer {
  readonly width: number;   // logical width (height is always 720)
  readonly height: number;
  begin(): void;
  end(): void;
  clear(color: string): void;
  save(): void;
  restore(): void;
  translate(x: number, y: number): void;
  scale(sx: number, sy: number): void;
  rotate(rad: number): void;
  /** multiplies current alpha */
  alpha(a: number): void;
  rect(x: number, y: number, w: number, h: number, fill: string | null, stroke?: string | null, lw?: number): void;
  roundRect(x: number, y: number, w: number, h: number, r: number, fill: string | null, stroke?: string | null, lw?: number): void;
  circle(x: number, y: number, r: number, fill: string | null, stroke?: string | null, lw?: number): void;
  /** flat point list [x0,y0,x1,y1,...] */
  poly(pts: number[], fill: string | null, stroke?: string | null, lw?: number): void;
  line(x1: number, y1: number, x2: number, y2: number, color: string, lw: number): void;
  text(s: string, x: number, y: number, size: number, color: string, align?: Align, bold?: boolean): void;
  measure(s: string, size: number, bold?: boolean): number;
}

/** colour helpers (hex only) */
export function mix(a: string, b: string, t: number): string {
  const pa = parse(a), pb = parse(b);
  const r = Math.round(pa[0] + (pb[0] - pa[0]) * t), g = Math.round(pa[1] + (pb[1] - pa[1]) * t), bl = Math.round(pa[2] + (pb[2] - pa[2]) * t);
  return '#' + [r, g, bl].map(v => v.toString(16).padStart(2, '0')).join('');
}
function parse(h: string): number[] {
  const s = h.replace('#', '');
  return [parseInt(s.slice(0, 2), 16), parseInt(s.slice(2, 4), 16), parseInt(s.slice(4, 6), 16)];
}
export function rgba(h: string, a: number): string {
  const p = parse(h);
  return `rgba(${p[0]},${p[1]},${p[2]},${a})`;
}
