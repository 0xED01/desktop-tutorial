/**
 * Trap preset registry — every trap is a reusable, configurable mechanism.
 * A level writes   defs: { a: { t: 'hiddenSpike', near: 2, delay: 0.1 } }
 * and paints `a` on its ASCII map. The preset expands into generic entities
 * (+ optional triggers). Common options supported by (almost) every preset:
 *   at     : trigger columns  (number | [from,to], tile units)
 *   aty    : trigger rows     (number | [from,to])
 *   near   : auto trigger distance in tiles around the entity (if no `at`)
 *   on     : 'enter' | 'jump' | 'land' | 'event' | 'start' | 'stand' | 'touch'
 *   ev     : event name (when on === 'event')
 *   delay  : trigger delay (s)
 *   dur    : appear animation duration (s)
 *   look   : visual override
 *   then   : extra actions (Act[]) fired together with the preset trigger
 */
import { Act, Dir, Ent, Kind, Look, Rect, TILE, Trig, TrigOn } from './types';

export interface PresetCtx {
  char: string;          // def char (group)
  region: Rect;          // px rect of this region
  index: number;         // region index for this char
  spec: any;
  rows: number; cols: number;
  addEnt(e: Partial<Ent> & { kind: Kind }): Ent;
  addTrig(t: Partial<Trig> & { on: TrigOn; actions: Act[] }): void;
  /** build trigger zone from spec.at/spec.aty or near */
  zone(nearDefault?: number): Rect;
}

export interface PresetInfo {
  name: string;
  cn: string;            // Chinese name
  desc: string;
  build(ctx: PresetCtx): void;
}

const R: Record<string, PresetInfo> = {};
function reg(name: string, cn: string, desc: string, build: (ctx: PresetCtx) => void) {
  R[name] = { name, cn, desc, build };
}
export const PRESETS = R;

function popTrig(ctx: PresetCtx, target: string, onDefault: TrigOn, nearDefault: number, extra: Act[] = [], act: Act['a'] = 'pop') {
  const s = ctx.spec;
  const on: TrigOn = s.on || onDefault;
  const acts: Act[] = [{ a: act, t: target, step: s.step }];
  if (s.sfx !== false) acts.push({ a: 'sfx', name: s.sfx || (act === 'pop' ? 'spike' : 'trap') });
  if (s.surprise) acts.push({ a: 'surprise' });
  ctx.addTrig({
    on, zone: on === 'event' || on === 'start' ? undefined : ctx.zone(nearDefault),
    event: s.ev, delay: s.delay || 0, once: true,
    target: s.target, actions: acts.concat(extra, s.then || []),
  });
}

function spikeEnt(ctx: PresetCtx, dir: Dir, hidden: boolean, look: Look = 'spike', idSuffix = '', r?: Rect): Ent {
  const s = ctx.spec;
  const rr = r || ctx.region;
  return ctx.addEnt({
    kind: 'spike', look, x: rr.x, y: rr.y, w: rr.w, h: rr.h, dir,
    vis: !hidden, deadly: look !== 'paperSpike', anim: hidden ? 0 : 1, animDur: s.dur ?? 0.08,
    id: idSuffix ? ctx.char + ctx.index + idSuffix : undefined,
  });
}

/* ================= 1-25: required traps ================= */

reg('hiddenSpike', '隐形地刺', '平时看不见，玩家靠近时从地面突然冒出', ctx => {
  spikeEnt(ctx, ctx.spec.dir || 'u', true);
  if (!ctx.spec.manual && (ctx.index === 0 || !ctx.spec.at)) popTrig(ctx, ctx.spec.at ? ctx.char : ctx.char + ctx.index, 'enter', ctx.spec.near ?? 1);
});

reg('secondSpike', '二段地刺', '躲过第一根刺后，在落点再次冒出', ctx => {
  spikeEnt(ctx, ctx.spec.dir || 'u', true);
  if (ctx.index === 0) popTrig(ctx, ctx.char, ctx.spec.on || 'land', ctx.spec.near ?? 0.3);
});

reg('chainSpike', '连锁地刺', '触发后多处地刺依次弹出', ctx => {
  const r = ctx.region; const n = Math.round(r.w / TILE);
  for (let i = 0; i < n; i++) spikeEnt(ctx, ctx.spec.dir || 'u', true, 'spike', '_' + i, { x: r.x + i * TILE, y: r.y, w: TILE, h: r.h });
  if (ctx.index === 0) {
    const s = ctx.spec;
    const on: TrigOn = s.on || 'enter';
    ctx.addTrig({
      on, zone: on === 'event' ? undefined : ctx.zone(s.near ?? 0.5), event: s.ev, delay: s.delay || 0, once: true,
      actions: [{ a: 'pop', t: ctx.char, step: s.step ?? 0.12, x: s.order === 'rl' ? -1 : 1 }, { a: 'sfx', name: 'spike' }].concat(s.then || []) as Act[],
    });
  }
});

reg('fakeFloor', '假地板', '看起来是地面，踩上去立即坍塌', ctx => {
  const s = ctx.spec;
  ctx.addEnt({ kind: 'block', look: s.look || 'ground', solid: true, vis: true, crumble: { delay: s.delay ?? 0.02, respawn: s.respawn ?? 0 } });
});

reg('crumble', '延迟坍塌', '站立一段时间后才坍塌（会先抖动）', ctx => {
  const s = ctx.spec;
  ctx.addEnt({ kind: 'block', look: s.look || 'brick', solid: true, vis: true, crumble: { delay: s.delay ?? 0.45, respawn: s.respawn ?? 0 } });
});

reg('hiddenWall', '隐形墙壁', '走到指定位置时墙壁突然出现（invis:true 则一直存在但看不见）', ctx => {
  const s = ctx.spec;
  if (s.invis) {
    const e = ctx.addEnt({ kind: 'block', look: s.look || 'brick', solid: true, vis: false });
    e.props.revealOnTouch = true;
    return;
  }
  ctx.addEnt({ kind: 'block', look: s.look || 'brick', solid: false, vis: false, anim: 0, animDur: s.dur ?? 0.12 });
  if (ctx.index === 0 || !s.at) popTrig(ctx, s.at ? ctx.char : ctx.char + ctx.index, 'enter', s.near ?? 2, [], 'pop');
});

reg('fakeSafe', '伪装安全区', '看似普通地面，站上去片刻后整块变成尖刺', ctx => {
  const s = ctx.spec; const r = ctx.region;
  ctx.addEnt({ kind: 'block', look: s.look || 'ground', solid: true, vis: true });
  const sp = ctx.addEnt({ kind: 'spike', look: 'spike', dir: 'u', x: r.x, y: r.y - TILE, w: r.w, h: TILE, vis: false, anim: 0, animDur: s.dur ?? 0.1, deadly: true, id: ctx.char + ctx.index + '_sp' });
  ctx.addTrig({
    on: 'stand', target: ctx.char + ctx.index, delay: s.delay ?? 0.25, once: true,
    actions: [{ a: 'pop', t: sp.id }, { a: 'sfx', name: 'spike' }].concat(s.then || []) as Act[],
  });
});

reg('fleeGoal', '移动终点', '靠近时终点门向前逃跑（按 spots 顺序）', ctx => {
  const s = ctx.spec; const r = ctx.region;
  const e = ctx.addEnt({ kind: 'goal', look: 'door', x: r.x + 2, y: r.y + r.h - 56, w: 36, h: 56 });
  e.props.real = s.real !== false;
  e.flee = { spots: (s.spots || [[4, 0]]).map((p: number[]) => [p[0] * TILE, p[1] * TILE]), dist: (s.dist ?? 2.5) * TILE, speed: (s.speed ?? 8) * TILE };
});

reg('fakeGoal', '假终点', '进入后并没有通关，还会触发后续机关', ctx => {
  const s = ctx.spec; const r = ctx.region;
  const id = ctx.char + ctx.index;
  ctx.addEnt({ id, kind: 'goal', look: s.look || 'door', x: r.x + 2, y: r.y + r.h - 56, w: 36, h: 56, props: { real: false } });
  const acts: Act[] = [{ a: 'sfx', name: 'fake' }, { a: 'hide', t: id, d: s.keep ? 999 : 0.05 }];
  if (s.text !== '') acts.push({ a: 'say', text: s.text || '骗你的，这门是画上去的。' });
  acts.push({ a: 'emit', ev: 'fakegoal' });
  ctx.addTrig({ on: 'touch', target: id, delay: 0, once: true, actions: acts.concat(s.then || []) });
});

reg('ceilSpike', '倒刺天花板', '玩家起跳后上方突然出现尖刺', ctx => {
  spikeEnt(ctx, 'd', true);
  if (ctx.index === 0 || !ctx.spec.at) popTrig(ctx, ctx.spec.at ? ctx.char : ctx.char + ctx.index, ctx.spec.on || 'jump', ctx.spec.near ?? 1.5);
});

reg('ghost', '假平台', '看起来能站，实际上会直接穿过去', ctx => {
  const s = ctx.spec;
  ctx.addEnt({ kind: 'block', look: s.look || 'ground', solid: false, vis: true, props: { ghost: true, tell: !!s.tell } });
});

reg('mover', '移动平台陷阱', '沿路径移动，可配置站上后加速/停止/反向（onStand）', ctx => {
  const s = ctx.spec;
  const id = ctx.char + ctx.index;
  const e = ctx.addEnt({
    id, kind: 'block', look: s.look || 'cloud', solid: true, vis: true,
    move: {
      path: (s.path || [[0, 0], [4, 0]]).map((p: number[]) => [p[0] * TILE, p[1] * TILE]),
      speed: (s.speed ?? 2.5) * TILE, mode: s.mode || 'pingpong', active: s.active !== false, wait: s.wait ?? 0,
    },
  });
  if (s.deadly) { e.deadly = true; e.look = 'saw'; e.kind = 'saw'; e.solid = false; }
  if (s.sink) e.move!.active = false;
  const stand: Act[] = (s.onStand || []).concat(s.sink ? [{ a: 'move', t: id }] : []);
  if (stand.length) ctx.addTrig({ on: 'stand', target: id, delay: s.standDelay ?? 0, once: true, actions: stand });
  if (s.at !== undefined) ctx.addTrig({ on: s.on || 'enter', zone: ctx.zone(0), delay: s.delay || 0, once: true, actions: [{ a: 'move', t: id }].concat(s.then || []) as Act[] });
});

reg('gravity', '重力反转区', '进入区域后上下颠倒（v: -1 反转, 1 恢复, 0 切换）', ctx => {
  const s = ctx.spec;
  ctx.addEnt({ kind: 'zone', look: 'none', vis: true, props: { zone: 'gravity', v: s.v ?? 0 } });
  ctx.addTrig({ on: 'enter', zone: ctx.region, delay: s.delay || 0, once: false, actions: [{ a: 'gravity', v: s.v ?? 0 }, { a: 'sfx', name: 'warp' }].concat(s.then || []) as Act[] });
});

reg('reverse', '操作反转区', '短时间交换左右键效果', ctx => {
  const s = ctx.spec;
  ctx.addEnt({ kind: 'zone', look: 'none', vis: !s.hidden, props: { zone: 'reverse' } });
  ctx.addTrig({ on: 'enter', zone: ctx.region, delay: s.delay || 0, once: !!s.once, actions: [{ a: 'reverse', dur: s.dur ?? 3 }, { a: 'sfx', name: 'trap' }, { a: 'surprise' }].concat(s.then || []) as Act[] });
});

reg('sign', '提示牌', '提示牌（有时故意误导）', ctx => {
  const s = ctx.spec; const r = ctx.region;
  ctx.addEnt({ kind: 'sign', look: 'sign', x: r.x, y: r.y + r.h - TILE, w: TILE, h: TILE, props: { text: s.text || '?', lie: !!s.lie } });
});

reg('coin', '诱饵金币', '碰到金币后触发隐藏机关（do: 动作列表）', ctx => {
  const s = ctx.spec; const r = ctx.region;
  const id = ctx.char + ctx.index;
  ctx.addEnt({ id, kind: 'coin', look: 'coin', x: r.x + 10, y: r.y + 10, w: 20, h: 20 });
  ctx.addTrig({ on: 'touch', target: id, delay: s.delay || 0, once: true, actions: [{ a: 'hide', t: id }, { a: 'sfx', name: 'coin' } as Act].concat(s.do || []) });
});

reg('tracker', '追踪尖刺', '触发后沿 axis 追踪玩家一段时间', ctx => {
  const s = ctx.spec;
  const id = ctx.char + ctx.index;
  const r = ctx.region;
  const size = s.size ? s.size * TILE : Math.min(r.w, r.h);
  const e = ctx.addEnt({
    id, kind: 'saw', look: 'saw', x: r.x + (r.w - size) / 2, y: r.y + (r.h - size) / 2, w: size, h: size, deadly: true, vis: s.hidden ? false : true,
    track: { speed: (s.speed ?? 3) * TILE, dur: s.dur ?? 2.5, active: false, axis: s.axis || 'x' },
  });
  e.props.spin = true;
  ctx.addTrig({ on: s.on || 'enter', zone: s.on === 'event' ? undefined : ctx.zone(s.near ?? 3), event: s.ev, delay: s.delay || 0, once: true, actions: [{ a: 'show', t: id }, { a: 'track', t: id }, { a: 'sfx', name: 'trap' } as Act].concat(s.then || []) });
});

reg('spring', '弹射地板', '踩上后被弹飞（vy 向上力度，vx 水平力度，单位：格/秒）', ctx => {
  const s = ctx.spec; const r = ctx.region;
  const dir: Dir = s.dir || 'u';
  const h = dir === 'u' || dir === 'd' ? 16 : r.h;
  const w = dir === 'u' || dir === 'd' ? r.w : 16;
  const x = dir === 'l' ? r.x + r.w - 16 : r.x;
  const y = dir === 'u' ? r.y + r.h - 16 : r.y;
  ctx.addEnt({ kind: 'spring', look: s.look || 'spring', x, y, w, h, dir, vis: !s.hidden, props: { vy: (s.vy ?? 28) * TILE, vx: (s.vx ?? 0) * TILE, hidden: !!s.hidden } });
});

reg('riseWall', '突然升起的墙', '原本畅通的路被地下升起的墙封住', ctx => {
  const s = ctx.spec; const r = ctx.region;
  const id = ctx.char + ctx.index;
  const down = s.from === 'top' ? -r.h : r.h;
  ctx.addEnt({ id, kind: 'block', look: s.look || 'brick', x: r.x, y: r.y + down, solid: true, vis: true, props: { clip: true } });
  ctx.addTrig({
    on: s.on || 'enter', zone: s.on === 'event' ? undefined : ctx.zone(s.near ?? 2), event: s.ev, delay: s.delay || 0, once: true,
    actions: [{ a: 'moveBy', t: id, x: 0, y: -down / TILE, dur: s.dur ?? 0.15 }, { a: 'sfx', name: 'wall' }, { a: 'shake', v: 4 } as Act].concat(s.then || []),
  });
});

reg('fakeCheckpoint', '假检查点', '看起来是存档点，碰到后触发机关', ctx => {
  const s = ctx.spec; const r = ctx.region;
  const id = ctx.char + ctx.index;
  ctx.addEnt({ id, kind: 'checkpoint', look: 'flag', x: r.x + 8, y: r.y + r.h - 60, w: 24, h: 60, props: { fake: true } });
  ctx.addTrig({ on: 'touch', target: id, delay: s.delay || 0, once: true, actions: [{ a: 'sfx', name: 'fake' }, { a: 'say', text: s.text || '存档？想多了。' }, { a: 'look', t: id, look: 'spike' } as Act].concat(s.do || []) });
});

reg('portal', '传送门骗局', '传送到意想不到（但可逃脱）的位置（to: [列,行]）', ctx => {
  const s = ctx.spec; const r = ctx.region;
  const to = s.to || [0, 0];
  ctx.addEnt({ kind: 'portal', look: 'portal', x: r.x + 4, y: r.y + r.h - 64, w: 32, h: 64, props: { to: [to[0] * TILE + 5, to[1] * TILE + 10], hue: s.hue ?? 0, gravity: s.gravity } });
});

reg('lastStep', '终点前陷阱', '距离终点一步之遥时冒出障碍（地刺）', ctx => {
  spikeEnt(ctx, ctx.spec.dir || 'u', true);
  if (ctx.index === 0) popTrig(ctx, ctx.char, ctx.spec.on || 'enter', ctx.spec.near ?? 0.7);
});

reg('fakeWin', '连环假通关', '进入区域播放通关动画，随后揭示隐藏挑战（then: 后续动作）', ctx => {
  const s = ctx.spec;
  ctx.addTrig({ on: s.on || 'enter', zone: ctx.region, target: s.target, event: s.ev, delay: s.delay || 0, once: true, actions: [{ a: s.ending ? 'fakeEnding' : 'fakeWin' } as Act].concat(s.then || []) });
});

reg('jumpTrap', '跳跃触发陷阱', '起跳而非落地时触发（区域内起跳即触发 do 动作）', ctx => {
  const s = ctx.spec;
  ctx.addTrig({ on: 'jump', zone: ctx.region, delay: s.delay || 0, once: s.once !== false, actions: s.do || [] });
});

reg('memory', '记忆陷阱', '开局短暂显示真假（绿色=真），之后全部看起来一样', ctx => {
  const s = ctx.spec;
  const real = s.real !== false;
  ctx.addEnt({ kind: 'block', look: real ? 'green' : 'red', solid: real, vis: true, looks: { before: real ? 'green' : 'red', after: s.look || 'ground', until: s.show ?? 2.5 }, props: { ghost: !real } });
});

/* ================= 26+: original mechanisms ================= */

reg('shy', '害羞平台', '玩家靠近时平台害羞地滑走（dx 格）', ctx => {
  const s = ctx.spec;
  ctx.addEnt({ kind: 'block', look: s.look || 'cloud', solid: true, vis: true, shy: { dist: (s.dist ?? 2) * TILE, dx: (s.dx ?? 3) * TILE, speed: (s.speed ?? 6) * TILE } });
});

reg('drop', '坠落砸块', '玩家经过下方时砸下（可带刺）', ctx => {
  const s = ctx.spec;
  const e = ctx.addEnt({ kind: s.spiky ? 'spike' : 'block', look: s.spiky ? 'spike' : (s.look || 'brick'), dir: 'd', solid: !s.spiky, deadly: !!s.spiky, vis: true,
    drop: { range: (s.range ?? 1) * TILE, speed: (s.speed ?? 18) * TILE, delay: s.delay ?? 0.05 } });
  e.props.crush = true;
});

reg('blink', '闪烁平台', '周期性出现/消失（on/off 秒，phase 偏移）', ctx => {
  const s = ctx.spec;
  ctx.addEnt({ kind: 'block', look: s.look || 'glass', solid: true, vis: true, blink: { on: s.onT ?? 1.4, off: s.offT ?? 1.0, phase: s.phase ?? 0 } });
});

reg('laser', '定时激光', '周期性发射激光，发射前闪烁预警', ctx => {
  const s = ctx.spec;
  ctx.addEnt({ kind: 'laser', look: 'laser', deadly: false, vis: true, blink: { on: s.onT ?? 1.0, off: s.offT ?? 1.4, phase: s.phase ?? 0 }, props: { vertical: ctx.region.h > ctx.region.w } });
});

reg('button', '压力开关', '踩下后发出事件 ev（可开门/关墙/重置机关）', ctx => {
  const s = ctx.spec; const r = ctx.region;
  const id = ctx.char + ctx.index;
  ctx.addEnt({ id, kind: 'button', look: 'button', x: r.x + 6, y: r.y + r.h - 10, w: r.w - 12, h: 10, solid: false });
  ctx.addTrig({ on: 'touch', target: id, delay: 0, once: true, actions: [{ a: 'sfx', name: 'click' }, { a: 'look', t: id, look: 'none' }, { a: 'emit', ev: s.ev || 'button' } as Act].concat(s.do || []) });
});

reg('wind', '风区', '持续把玩家往一侧推（fx 格/秒²）', ctx => {
  const s = ctx.spec;
  ctx.addEnt({ kind: 'zone', look: 'none', vis: true, props: { zone: 'wind', fx: (s.fx ?? -10) * TILE, fy: (s.fy ?? 0) * TILE } });
});

reg('ice', '冰面', '极滑的地面，难以停下', ctx => {
  ctx.addEnt({ kind: 'block', look: 'ice', solid: true, vis: true, surface: 'ice' });
});

reg('mud', '泥潭', '走得慢、跳得低', ctx => {
  ctx.addEnt({ kind: 'block', look: 'mud', solid: true, vis: true, surface: 'mud' });
});

reg('shadow', '影子追击', '触发后影子沿你走过的路线延迟追来，碰到即死', ctx => {
  const s = ctx.spec; const r = ctx.region;
  const id = ctx.char + ctx.index;
  ctx.addEnt({ id, kind: 'shadow', look: 'shadow', x: r.x + 5, y: r.y + r.h - 30, w: 30, h: 30, vis: false, deadly: false, props: { lag: s.lag ?? 1.6 } });
  ctx.addTrig({ on: s.on || 'enter', zone: s.on === 'event' ? undefined : ctx.zone(s.near ?? 0), event: s.ev, delay: s.delay || 0, once: true, actions: [{ a: 'show', t: id }, { a: 'deadly', t: id }, { a: 'say', text: s.text || '有东西在跟着你……' }, { a: 'sfx', name: 'trap' } as Act] });
});

reg('paperSpike', '纸老虎尖刺', '看起来是尖刺，其实是纸做的（虚线边缘），可以放心走', ctx => {
  spikeEnt(ctx, ctx.spec.dir || 'u', false, 'paperSpike');
});

reg('retract', '缩回尖刺', '可见的尖刺，玩家靠近时会缩回地下', ctx => {
  spikeEnt(ctx, ctx.spec.dir || 'u', false);
  if (ctx.index === 0 || !ctx.spec.at) popTrig(ctx, ctx.spec.at ? ctx.char : ctx.char + ctx.index, 'enter', ctx.spec.near ?? 1.5, [], 'hide');
});

reg('speed', '加速带', '进入后奔跑速度翻倍一段时间', ctx => {
  const s = ctx.spec;
  ctx.addEnt({ kind: 'zone', look: 'none', vis: true, props: { zone: 'speed' } });
  ctx.addTrig({ on: 'enter', zone: ctx.region, delay: 0, once: false, actions: [{ a: 'speed', v: s.v ?? 1.8, dur: s.dur ?? 2 }, { a: 'sfx', name: 'warp' }] });
});

reg('mirror', '镜像世界', '进入后画面左右翻转（操作不变）', ctx => {
  const s = ctx.spec;
  ctx.addEnt({ kind: 'zone', look: 'none', vis: false, props: { zone: 'mirror' } });
  ctx.addTrig({ on: 'enter', zone: ctx.region, delay: 0, once: true, actions: [{ a: 'mirror', v: s.v ?? 1 }, { a: 'sfx', name: 'warp' }, { a: 'say', text: s.text || '咦？世界反过来了。' }] });
});

reg('dark', '熄灯区', '进入后只能看见身边一小圈', ctx => {
  const s = ctx.spec;
  ctx.addEnt({ kind: 'zone', look: 'none', vis: false, props: { zone: 'dark' } });
  ctx.addTrig({ on: 'enter', zone: ctx.region, delay: 0, once: true, actions: [{ a: 'dark', v: (s.r ?? 3.5) * TILE }, { a: 'sfx', name: 'trap' }] });
});

reg('boulder', '滚石追击', '触发后巨大滚石从后方滚来', ctx => {
  const s = ctx.spec; const r = ctx.region;
  const id = ctx.char + ctx.index;
  const size = (s.size ?? 2) * TILE;
  ctx.addEnt({
    id, kind: 'saw', look: 'boulder', x: r.x, y: r.y + r.h - size, w: size, h: size, deadly: true, vis: true,
    move: { path: [[0, 0], [(s.dist ?? 30) * TILE, 0]], speed: (s.speed ?? 4.5) * TILE, mode: 'once', active: false },
  });
  ctx.addTrig({ on: s.on || 'enter', zone: s.on === 'event' ? undefined : ctx.zone(0), event: s.ev, delay: s.delay ?? 0.3, once: true, actions: [{ a: 'move', t: id }, { a: 'shake', v: 6 }, { a: 'sfx', name: 'wall' }, { a: 'surprise' }] });
});

reg('toggle', '红蓝切换块', '每次跳跃都会切换红/蓝方块的实体状态（开局红色为实体）', ctx => {
  const s = ctx.spec;
  ctx.addEnt({ kind: 'block', look: s.color === 'blue' ? 'blue' : 'red', solid: true, vis: true, phase: s.color === 'blue' ? 'blue' : 'red' });
});

reg('saw', '巡逻锯片', '沿路径来回移动的锯片', ctx => {
  const s = ctx.spec; const r = ctx.region;
  const size = (s.size ?? 1) * TILE;
  const e = ctx.addEnt({
    kind: 'saw', look: 'saw', x: r.x + (r.w - size) / 2, y: r.y + (r.h - size) / 2, w: size, h: size, deadly: true, vis: true,
    move: { path: (s.path || [[0, 0], [0, -3]]).map((p: number[]) => [p[0] * TILE, p[1] * TILE]), speed: (s.speed ?? 3) * TILE, mode: s.mode || 'pingpong', active: s.active !== false, wait: s.wait ?? 0 },
  });
  e.props.spin = true;
  if (s.at !== undefined) ctx.addTrig({ on: s.on || 'enter', zone: ctx.zone(0), delay: s.delay || 0, once: true, actions: [{ a: 'move', t: e.id }] });
});

reg('fallSpike', '坠落钟乳石', '天花板尖刺在玩家经过时掉落', ctx => {
  const s = ctx.spec;
  ctx.addEnt({ kind: 'spike', look: 'spike', dir: 'd', deadly: true, vis: true, drop: { range: (s.range ?? 0.6) * TILE, speed: (s.speed ?? 14) * TILE, delay: s.delay ?? 0.12 } });
});

reg('fakeSpikeWall', '尖刺门帘', '看似尖刺墙，其实一碰就散（纸老虎变体，用于“学到的都是错的”）', ctx => {
  spikeEnt(ctx, ctx.spec.dir || 'l', false, 'paperSpike');
});

reg('solid', '普通方块', '普通实心方块（可指定外观，可被动作控制）', ctx => {
  const s = ctx.spec;
  ctx.addEnt({ kind: 'block', look: s.look || 'ground', solid: s.hidden ? false : true, vis: !s.hidden, anim: s.hidden ? 0 : 1, animDur: s.dur ?? 0.12, phase: undefined });
});

reg('spikes', '可控尖刺', '普通可见尖刺，可被动作控制（hide/move）', ctx => {
  const s = ctx.spec;
  const e = spikeEnt(ctx, s.dir || 'u', !!s.hidden);
  if (s.path) e.move = { path: s.path.map((p: number[]) => [p[0] * TILE, p[1] * TILE]), speed: (s.speed ?? 3) * TILE, mode: s.mode || 'pingpong', active: s.active !== false, wait: s.wait ?? 0 };
});

reg('goal', '真终点', '真正的出口（可隐藏，hidden:true 时需要事件 show）', ctx => {
  const s = ctx.spec; const r = ctx.region;
  const id = ctx.char + ctx.index;
  const e = ctx.addEnt({ id, kind: 'goal', look: 'door', x: r.x + 2, y: r.y + r.h - 56, w: 36, h: 56, vis: !s.hidden, props: { real: true } });
  if (s.flip) { e.y = r.y; e.props.flip = true; }
  if (s.path) e.move = { path: s.path.map((p: number[]) => [p[0] * TILE, p[1] * TILE]), speed: (s.speed ?? 3) * TILE, mode: s.mode || 'once', active: !!s.active };
});

reg('checkpoint', '检查点', '真正的检查点', ctx => {
  const r = ctx.region;
  ctx.addEnt({ kind: 'checkpoint', look: 'flag', x: r.x + 8, y: r.y + r.h - 60, w: 24, h: 60, props: { fake: false, flip: !!ctx.spec.flip } });
});

reg('text', '剧情文字', '进入区域显示一句话', ctx => {
  const s = ctx.spec;
  ctx.addTrig({ on: s.on || 'enter', zone: ctx.region, event: s.ev, delay: s.delay || 0, once: true, actions: [{ a: 'say', text: s.text || '', dur: s.dur } as Act].concat(s.then || []) });
});

reg('mimic', '拟态门', '看起来是终点门，靠近后长出牙齿追着你跑（矩形判定，可跳过）', ctx => {
  const s = ctx.spec; const r = ctx.region;
  const id = ctx.char + ctx.index;
  ctx.addEnt({
    id, kind: 'saw', look: 'door', x: r.x + 2, y: r.y + r.h - 56, w: 36, h: 56, deadly: false, vis: true,
    track: { speed: (s.speed ?? 4) * TILE, dur: s.chase ?? 6, active: false, axis: 'x' }, props: { rect: true, mimic: true },
  });
  ctx.addTrig({
    on: s.on || 'enter', zone: s.on === 'event' ? undefined : ctx.zone(s.near ?? 4), event: s.ev, delay: s.delay ?? 0.4, once: true,
    actions: [{ a: 'deadly', t: id }, { a: 'look', t: id, look: 'saw' }, { a: 'track', t: id }, { a: 'say', text: s.text || '这扇门……有牙齿？！' }, { a: 'sfx', name: 'trap' }, { a: 'shake', v: 4 }, { a: 'surprise' }, { a: 'emit', ev: s.emit || 'mimic' } as Act].concat(s.then || []),
  });
});

export const REQUIRED_TRAPS = [
  'hiddenSpike', 'secondSpike', 'chainSpike', 'fakeFloor', 'crumble', 'hiddenWall', 'fakeSafe', 'fleeGoal', 'fakeGoal', 'ceilSpike',
  'ghost', 'mover', 'gravity', 'reverse', 'sign', 'coin', 'tracker', 'spring', 'riseWall', 'fakeCheckpoint',
  'portal', 'lastStep', 'fakeWin', 'jumpTrap', 'memory',
];
export const ORIGINAL_TRAPS = [
  'shy', 'drop', 'blink', 'laser', 'button', 'wind', 'ice', 'mud', 'shadow', 'paperSpike',
  'retract', 'speed', 'mirror', 'dark', 'boulder', 'toggle', 'saw', 'fallSpike', 'fakeSpikeWall', 'mimic',
];
