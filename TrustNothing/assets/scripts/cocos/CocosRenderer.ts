/**
 * IRenderer implementation on top of cc.Graphics + pooled cc.Label nodes.
 * Draw order is preserved with "layers": every time a shape is drawn after text,
 * a new layer (Graphics + label container) is started.
 */
import { Color, Graphics, HorizontalTextAlignment, Label, Layers, Node, UITransform, Vec3, VerticalTextAlignment } from 'cc';
import { Align, IRenderer } from '../core/render';

type M = [number, number, number, number, number, number]; // a b c d e f  (x' = a x + c y + e)

interface Layer { node: Node; g: Graphics; labels: Node; used: number; pool: Label[]; }

const colorCache = new Map<string, [number, number, number, number]>();
function parseColor(s: string): [number, number, number, number] {
  let c = colorCache.get(s);
  if (c) return c;
  if (s[0] === '#') {
    const h = s.slice(1);
    c = [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16), h.length >= 8 ? parseInt(h.slice(6, 8), 16) / 255 : 1];
  } else {
    const m = s.match(/rgba?\(([^)]+)\)/);
    const p = m ? m[1].split(',').map(v => parseFloat(v)) : [0, 0, 0, 1];
    c = [p[0], p[1], p[2], p[3] ?? 1];
  }
  if (colorCache.size > 512) colorCache.clear();
  colorCache.set(s, c);
  return c;
}

export class CocosRenderer implements IRenderer {
  width = 1280; height = 720;
  private stack: { m: M; a: number }[] = [];
  private m: M = [1, 0, 0, 1, 0, 0];
  private a = 1;
  private layers: Layer[] = [];
  private cur = 0;
  private lastWasText = false;
  private tmpColor = new Color();

  constructor(private root: Node) {}

  setSize(w: number) { this.width = w; }

  private layer(i: number): Layer {
    let L = this.layers[i];
    if (!L) {
      const node = new Node('layer' + i);
      node.layer = Layers.Enum.UI_2D;
      node.addComponent(UITransform);
      this.root.addChild(node);
      const gn = new Node('g'); gn.layer = Layers.Enum.UI_2D; gn.addComponent(UITransform); node.addChild(gn);
      const g = gn.addComponent(Graphics);
      const labels = new Node('labels'); labels.layer = Layers.Enum.UI_2D; labels.addComponent(UITransform); node.addChild(labels);
      L = { node, g, labels, used: 0, pool: [] };
      this.layers[i] = L;
    }
    return L;
  }

  begin() {
    this.cur = 0; this.lastWasText = false;
    this.m = [1, 0, 0, 1, 0, 0]; this.a = 1; this.stack.length = 0;
    for (const L of this.layers) { L.g.clear(); for (let k = 0; k < L.used; k++) L.pool[k].node.active = false; L.used = 0; L.node.active = false; }
    this.layer(0).node.active = true;
  }
  end() { /* graphics are submitted automatically */ }

  private g(): Graphics {
    if (this.lastWasText) { this.cur++; this.lastWasText = false; }
    const L = this.layer(this.cur);
    L.node.active = true;
    return L.g;
  }

  // logical (top-left origin, y down) → node space (center origin, y up)
  private tx(x: number, y: number): [number, number] {
    const m = this.m;
    const lx = m[0] * x + m[2] * y + m[4], ly = m[1] * x + m[3] * y + m[5];
    return [lx - this.width / 2, this.height / 2 - ly];
  }

  clear(color: string) { this.save(); this.m = [1, 0, 0, 1, 0, 0]; this.rect(0, 0, this.width, this.height, color); this.restore(); }
  save() { this.stack.push({ m: this.m.slice() as M, a: this.a }); }
  restore() { const s = this.stack.pop(); if (s) { this.m = s.m; this.a = s.a; } }
  translate(x: number, y: number) { const m = this.m; m[4] += m[0] * x + m[2] * y; m[5] += m[1] * x + m[3] * y; }
  scale(sx: number, sy: number) { const m = this.m; m[0] *= sx; m[1] *= sx; m[2] *= sy; m[3] *= sy; }
  rotate(r: number) {
    const m = this.m, c = Math.cos(r), s = Math.sin(r);
    const a = m[0] * c + m[2] * s, b = m[1] * c + m[3] * s, cc = m[0] * -s + m[2] * c, d = m[1] * -s + m[3] * c;
    m[0] = a; m[1] = b; m[2] = cc; m[3] = d;
  }
  alpha(a: number) { this.a *= a; }

  private setFill(g: Graphics, c: string) { const p = parseColor(c); this.tmpColor.set(p[0], p[1], p[2], Math.round(255 * p[3] * this.a)); g.fillColor = this.tmpColor; }
  private setStroke(g: Graphics, c: string, lw: number) { const p = parseColor(c); this.tmpColor.set(p[0], p[1], p[2], Math.round(255 * p[3] * this.a)); g.strokeColor = this.tmpColor; g.lineWidth = lw * Math.hypot(this.m[0], this.m[1]); }

  private path(pts: number[], fill: string | null, stroke?: string | null, lw = 2, close = true) {
    if (this.a <= 0.003) return;
    const g = this.g();
    let p = this.tx(pts[0], pts[1]);
    g.moveTo(p[0], p[1]);
    for (let i = 2; i < pts.length; i += 2) { p = this.tx(pts[i], pts[i + 1]); g.lineTo(p[0], p[1]); }
    if (close) g.close();
    if (fill) { this.setFill(g, fill); g.fill(); }
    if (stroke) { this.setStroke(g, stroke, lw); g.stroke(); }
  }

  rect(x: number, y: number, w: number, h: number, fill: string | null, stroke?: string | null, lw?: number) {
    this.path([x, y, x + w, y, x + w, y + h, x, y + h], fill, stroke, lw);
  }
  roundRect(x: number, y: number, w: number, h: number, r: number, fill: string | null, stroke?: string | null, lw?: number) {
    r = Math.min(r, w / 2, h / 2);
    if (r < 1) return this.rect(x, y, w, h, fill, stroke, lw);
    const pts: number[] = [];
    const corner = (cx: number, cy: number, a0: number) => { for (let i = 0; i <= 4; i++) { const a = a0 + (i / 4) * Math.PI / 2; pts.push(cx + Math.cos(a) * r, cy + Math.sin(a) * r); } };
    corner(x + w - r, y + r, -Math.PI / 2); corner(x + w - r, y + h - r, 0); corner(x + r, y + h - r, Math.PI / 2); corner(x + r, y + r, Math.PI);
    this.path(pts, fill, stroke, lw);
  }
  circle(x: number, y: number, r: number, fill: string | null, stroke?: string | null, lw?: number) {
    const n = r > 40 ? 32 : 18, pts: number[] = [];
    for (let i = 0; i < n; i++) { const a = (i / n) * Math.PI * 2; pts.push(x + Math.cos(a) * r, y + Math.sin(a) * r); }
    this.path(pts, fill, stroke, lw);
  }
  poly(pts: number[], fill: string | null, stroke?: string | null, lw?: number) { this.path(pts, fill, stroke, lw); }
  line(x1: number, y1: number, x2: number, y2: number, color: string, lw: number) { this.path([x1, y1, x2, y2], null, color, lw, false); }

  text(s: string, x: number, y: number, size: number, color: string, align: Align = 'left', bold?: boolean) {
    if (this.a <= 0.003 || !s) return;
    const L = this.layer(this.cur);
    L.node.active = true;
    this.lastWasText = true;
    let lab = L.pool[L.used];
    if (!lab) {
      const n = new Node('t'); n.layer = Layers.Enum.UI_2D; n.addComponent(UITransform);
      lab = n.addComponent(Label);
      lab.overflow = Label.Overflow.NONE; lab.verticalAlign = VerticalTextAlignment.CENTER; lab.cacheMode = Label.CacheMode.CHAR;
      L.labels.addChild(n); L.pool.push(lab);
    }
    L.used++;
    lab.node.active = true;
    const sc = Math.hypot(this.m[0], this.m[1]);
    const flip = this.m[0] < 0 ? -1 : 1;
    const fs = Math.max(6, Math.round(size));
    if (lab.string !== s) lab.string = s;
    if (lab.fontSize !== fs) { lab.fontSize = fs; lab.lineHeight = fs * 1.2; }
    lab.isBold = !!bold;
    const p = parseColor(color);
    this.tmpColor.set(p[0], p[1], p[2], Math.round(255 * p[3] * this.a));
    lab.color = this.tmpColor;
    lab.horizontalAlign = align === 'center' ? HorizontalTextAlignment.CENTER : align === 'right' ? HorizontalTextAlignment.RIGHT : HorizontalTextAlignment.LEFT;
    const ui = lab.node.getComponent(UITransform)!;
    ui.setAnchorPoint(align === 'center' ? 0.5 : align === 'right' ? 1 : 0, 0.5);
    const [nx, ny] = this.tx(x, y);
    lab.node.setPosition(new Vec3(nx, ny, 0));
    lab.node.setScale(sc * flip, sc, 1);
  }

  measure(s: string, size: number): number {
    let w = 0;
    for (const ch of s) w += ch.charCodeAt(0) > 0x2e80 ? size : size * 0.56;
    return w;
  }
}
