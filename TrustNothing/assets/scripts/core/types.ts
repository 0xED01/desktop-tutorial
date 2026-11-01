/**
 * 别信这关 / Trust Nothing — engine-agnostic core types.
 * Nothing in /core may import Cocos, DOM or platform APIs, so the same
 * logic runs on Cocos Creator (Android), HTML5/WebView and WeChat Mini Game.
 */

export const TILE = 40;
export const FIXED_DT = 1 / 60;

export interface Rect { x: number; y: number; w: number; h: number; }

export type Dir = 'u' | 'd' | 'l' | 'r';

/** Visual role of an entity — renderers map this to a style. */
export type Look =
  | 'ground' | 'brick' | 'cloud' | 'red' | 'blue' | 'green' | 'gold' | 'ice' | 'mud' | 'glass'
  | 'spike' | 'paperSpike' | 'saw' | 'door' | 'flag' | 'coin' | 'spring' | 'portal' | 'sign'
  | 'button' | 'laser' | 'boulder' | 'shadow' | 'none';

export type Kind =
  | 'block'      // solid (or fake) rectangle
  | 'spike'      // deadly triangles, direction dir
  | 'saw'        // deadly circle
  | 'goal'       // door; props.real decides real/fake
  | 'spring'     // launches the player
  | 'coin'       // bait coin
  | 'checkpoint' // real or fake flag
  | 'portal'     // teleport to props.to (px)
  | 'sign'       // text board, may lie
  | 'zone'       // invisible area: gravity / reverse / wind / ice / mud / speed / dark / mirror / text
  | 'button'     // pressure switch, emits event when stood on
  | 'laser'      // periodic beam hazard
  | 'shadow';    // replays player history with delay — deadly

/* ---------- components ---------- */
export interface MoveComp {
  /** waypoints relative to spawn, in px */
  path: [number, number][];
  speed: number;          // px/s
  mode: 'loop' | 'pingpong' | 'once';
  active: boolean;
  /** runtime */
  seg?: number; t?: number; fwd?: boolean; done?: boolean;
  /** pause at each waypoint (s) */
  wait?: number; waitT?: number;
}
export interface TrackComp { speed: number; dur: number; active: boolean; t?: number; axis?: 'x' | 'y' | 'xy'; }
export interface FleeComp { spots: [number, number][]; dist: number; speed: number; idx?: number; moving?: boolean; }
export interface CrumbleComp { delay: number; respawn: number; t?: number; state?: 'idle' | 'shaking' | 'gone'; goneT?: number; }
export interface BlinkComp { on: number; off: number; phase: number; }
export interface DropComp { range: number; speed: number; state?: 'idle' | 'fall' | 'done'; vy?: number; delay?: number; t?: number; }
export interface ShyComp { dist: number; dx: number; speed: number; done?: boolean; }

export interface Ent {
  id: string;            // unique
  group: string;         // def char / named group used as action target
  kind: Kind;
  look: Look;
  x: number; y: number; w: number; h: number;
  x0: number; y0: number;
  vis: boolean;          // drawn
  solid: boolean;        // collides
  deadly: boolean;       // kills on touch (spikes, saws…)
  dir: Dir;
  anim: number;          // 0..1 pop-in progress (spikes / walls)
  animDur: number;       // seconds to fully appear
  alpha: number;
  shake: number;         // visual jitter seconds remaining
  dx: number; dy: number; // movement last tick (for carrying)
  props: any;            // kind specific static config
  st: any;               // kind specific runtime state
  move?: MoveComp; track?: TrackComp; flee?: FleeComp; crumble?: CrumbleComp;
  blink?: BlinkComp; drop?: DropComp; shy?: ShyComp;
  /** toggle-block colour; solid only when world.toggle matches */
  phase?: 'red' | 'blue';
  /** for 'reveal' look: original look restored after memory preview */
  looks?: { before?: Look; after?: Look; until?: number };
  /** surface kind for physics */
  surface?: 'ice' | 'mud' | 'normal';
}

/* ---------- triggers & actions ---------- */
export type TrigOn = 'enter' | 'inside' | 'leave' | 'jump' | 'land' | 'event' | 'start' | 'touch' | 'stand' | 'death';

export interface Act {
  /** action name (validated by LevelValidator) */
  a: ActName | (string & {});
  t?: string;          // target group or id (comma separated allowed)
  d?: number;          // delay in seconds
  /** generic numeric/string params */
  v?: number; x?: number; y?: number; dur?: number; text?: string; ev?: string; look?: Look | (string & {}); name?: string;
  /** chain: for 'pop' on a group with interval, each member delayed i*step */
  step?: number;
}
export type ActName = 'show' | 'hide' | 'pop' | 'solid' | 'unsolid' | 'deadly' | 'safe' | 'move' | 'stop' | 'moveBy' | 'track'
    | 'emit' | 'shake' | 'say' | 'sfx' | 'gravity' | 'reverse' | 'fakeWin' | 'fakeEnding' | 'speed'
    | 'teleport' | 'look' | 'wind' | 'dark' | 'mirror' | 'surprise' | 'crumble' | 'goalReal' | 'freeze' | 'mspeed' | 'mrev';

export interface Trig {
  id: string;
  on: TrigOn;
  zone?: Rect;           // px
  target?: string;       // entity group for touch/stand
  event?: string;
  delay: number;         // added before every action
  once: boolean;
  actions: Act[];
  /** runtime */
  fired: boolean;
  inside: boolean;
  standT?: number;
}

/* ---------- level data ---------- */
export interface LevelDef {
  id: number;            // 1..80
  name: string;          // display title
  hint: string;          // honest hint (ad/hint system)
  /** ASCII map rows. Legend in levelLoader.ts */
  map: string[];
  /** custom char definitions */
  defs?: Record<string, DefSpec>;
  /** extra triggers */
  triggers?: TrigSpec[];
  theme?: number;        // palette index override
  /** opening speech bubble */
  intro?: string;
  /** music override */
  music?: string;
  /** special flags */
  final?: boolean;
  /** optional solver waypoints [col,row] — documents the intended route for backtracking levels */
  route?: [number, number][];
}

/** Def spec written in levels. `t` is the preset name (see presets.ts). */
export interface DefSpec { t: string; [k: string]: any; }

/** Trigger spec in tile coordinates. x/y are columns/rows (inclusive ranges). */
export interface TrigSpec {
  on: TrigOn;
  x?: number | [number, number];
  y?: number | [number, number];
  target?: string;
  event?: string;
  delay?: number;
  once?: boolean;
  do: Act[];
}

export interface Player {
  x: number; y: number; w: number; h: number;
  vx: number; vy: number;
  grounded: boolean; wasGrounded: boolean;
  coyote: number; buffer: number;
  jumpHeld: boolean; jumping: boolean;
  facing: number;
  groundEnt: string | null;
  launchT: number;       // reduced control after spring
  alive: boolean;
  deathT: number;
  deathCause: string;
  landT: number;         // squash anim timer
  jumpT: number;         // stretch anim timer
  blinkT: number;
  surpriseT: number;
  winT: number;
  surface: 'ice' | 'mud' | 'normal';
}

export interface InputState { left: boolean; right: boolean; jump: boolean; }

/** Presentation events produced by the simulation, consumed by the game layer. */
export type Fx =
  | { type: 'sfx'; name: string }
  | { type: 'shake'; v: number }
  | { type: 'burst'; x: number; y: number; color: string; n: number }
  | { type: 'say'; text: string; dur: number }
  | { type: 'fakeWin' }
  | { type: 'fakeEnding' }
  | { type: 'death'; cause: string }
  | { type: 'win' }
  | { type: 'checkpoint' }
  | { type: 'fakeGoal' }
  | { type: 'surprise' };
