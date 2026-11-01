// Minimal type stub of the Cocos Creator 3.8 'cc' API surface used by assets/scripts/cocos.
// Only for CI type-checking outside the editor; the real types come from the editor (temp/declarations).
declare module 'cc' {
  export class Vec3 { constructor(x?: number, y?: number, z?: number); x: number; y: number; z: number; }
  export class Color { constructor(r?: number, g?: number, b?: number, a?: number); set(r: number, g: number, b: number, a: number): Color; }
  export class Node { constructor(name?: string); layer: number; active: boolean; addComponent<T>(c: new () => T): T; getComponent<T>(c: new () => T): T | null; addChild(n: Node): void; setPosition(v: Vec3): void; setScale(x: number, y: number, z?: number): void; }
  export class Component { node: Node; }
  export class UITransform extends Component { setAnchorPoint(x: number, y: number): void; setContentSize(w: number, h: number): void; }
  export class Graphics extends Component { clear(): void; moveTo(x: number, y: number): void; lineTo(x: number, y: number): void; close(): void; fill(): void; stroke(): void; fillColor: Color; strokeColor: Color; lineWidth: number; }
  export enum HorizontalTextAlignment { LEFT, CENTER, RIGHT }
  export enum VerticalTextAlignment { TOP, CENTER, BOTTOM }
  export class Label extends Component {
    static Overflow: { NONE: number }; static CacheMode: { CHAR: number };
    string: string; fontSize: number; lineHeight: number; isBold: boolean; color: Color; overflow: number; cacheMode: number;
    horizontalAlign: HorizontalTextAlignment; verticalAlign: VerticalTextAlignment;
  }
  export const Layers: { Enum: { UI_2D: number; DEFAULT: number } };
  export class AudioClip { name: string; }
  export class AudioSource extends Component { loop: boolean; clip: AudioClip | null; volume: number; playing: boolean; play(): void; pause(): void; stop(): void; playOneShot(c: AudioClip, v?: number): void; }
  export class JsonAsset { json: unknown; }
  export const resources: { loadDir<T>(p: string, t: new () => T, cb: (err: Error | null, list: T[]) => void): void; load<T>(p: string, t: new () => T, cb: (err: Error | null, a: T | null) => void): void };
  export const sys: { localStorage: { getItem(k: string): string | null; setItem(k: string, v: string): void }; isNative: boolean; platform: string; getSafeAreaRect(): { x: number; y: number; width: number; height: number } };
  export const view: { getVisibleSize(): { width: number; height: number }; on(ev: string, cb: () => void, t?: unknown): void };
  export class Game { static EVENT_HIDE: string; on(ev: string, cb: () => void): void; end(): void; }
  export const game: Game;
  export class EventTouch { getUILocation(): { x: number; y: number }; getID(): number; }
  export class EventKeyboard { keyCode: number; }
  export const input: { on(ev: string, cb: (e: any) => void, t?: unknown): void };
  export const Input: { EventType: { TOUCH_START: string; TOUCH_MOVE: string; TOUCH_END: string; TOUCH_CANCEL: string; KEY_DOWN: string; KEY_UP: string } };
  export enum KeyCode { ARROW_LEFT, ARROW_RIGHT, ARROW_UP, KEY_A, KEY_D, KEY_W, SPACE, KEY_R, ESCAPE, KEY_P, ENTER, MOBILE_BACK, KEY_J, KEY_K, BRACKET_LEFT, BRACKET_RIGHT, KEY_O }
  export const _decorator: { ccclass(name: string): ClassDecorator };
}
