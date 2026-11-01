/**
 * Cocos Creator entry component. Attach to the Canvas node of Main.scene
 * (already done in assets/scenes/Main.scene). It wires the engine-agnostic
 * core Game to Cocos rendering, audio, storage and input.
 */
import { _decorator, Component, EventKeyboard, EventTouch, game, Game as CCGame, input, Input, JsonAsset, KeyCode, Node, resources, sys, UITransform, view, Layers } from 'cc';
import { Game } from '../core/game';
import { IStorage } from '../core/services';
import { LEVELS } from '../levels/index';
import { CocosRenderer } from './CocosRenderer';
import { CocosAudio } from './CocosAudio';

const { ccclass } = _decorator;

const KEYMAP: Partial<Record<number, string>> = {
  [KeyCode.ARROW_LEFT]: 'ArrowLeft', [KeyCode.ARROW_RIGHT]: 'ArrowRight', [KeyCode.ARROW_UP]: 'ArrowUp',
  [KeyCode.KEY_A]: 'KeyA', [KeyCode.KEY_D]: 'KeyD', [KeyCode.KEY_W]: 'KeyW', [KeyCode.SPACE]: 'Space',
  [KeyCode.KEY_R]: 'KeyR', [KeyCode.ESCAPE]: 'Escape', [KeyCode.KEY_P]: 'KeyP', [KeyCode.ENTER]: 'Enter',
  [KeyCode.MOBILE_BACK]: 'Escape', [KeyCode.KEY_J]: 'KeyJ', [KeyCode.KEY_K]: 'KeyK',
  [KeyCode.BRACKET_LEFT]: 'BracketLeft', [KeyCode.BRACKET_RIGHT]: 'BracketRight', [KeyCode.KEY_O]: 'KeyO',
};

class CocosStorage implements IStorage {
  get(k: string) { return sys.localStorage.getItem(k); }
  set(k: string, v: string) { sys.localStorage.setItem(k, v); }
}

@ccclass('GameRoot')
export class GameRoot extends Component {
  private game: Game | null = null;
  private renderer!: CocosRenderer;
  private drawRoot!: Node;

  onLoad() {
    this.drawRoot = new Node('Draw');
    this.drawRoot.layer = Layers.Enum.UI_2D;
    this.drawRoot.addComponent(UITransform);
    this.node.addChild(this.drawRoot);
    this.renderer = new CocosRenderer(this.drawRoot);
    this.resize();
    view.on('canvas-resize', this.resize, this);

    const audio = new CocosAudio(this.node);
    const boot = (solutions: Record<string, number[]>) => {
      this.game = new Game({
        renderer: this.renderer, audio, storage: new CocosStorage(), levels: LEVELS, solutions,
        platform: 'Cocos ' + (sys.isNative ? 'Native' : sys.platform), version: '1.0.0',
        exitApp: sys.isNative ? () => { this.game?.save.flush(); game.end(); } : undefined,
      });
      this.applySafeArea();
    };
    resources.load('solutions', JsonAsset, (err, asset) => boot(err ? {} : (asset!.json as any)));

    input.on(Input.EventType.TOUCH_START, (e: EventTouch) => this.touch('down', e), this);
    input.on(Input.EventType.TOUCH_MOVE, (e: EventTouch) => this.touch('move', e), this);
    input.on(Input.EventType.TOUCH_END, (e: EventTouch) => this.touch('up', e), this);
    input.on(Input.EventType.TOUCH_CANCEL, (e: EventTouch) => this.touch('up', e), this);
    input.on(Input.EventType.KEY_DOWN, (e: EventKeyboard) => { const k = KEYMAP[e.keyCode]; if (k) this.game?.key(k, true); }, this);
    input.on(Input.EventType.KEY_UP, (e: EventKeyboard) => { const k = KEYMAP[e.keyCode]; if (k) this.game?.key(k, false); }, this);
    game.on(CCGame.EVENT_HIDE, () => { const g = this.game; if (!g) return; g.save.markDirty(); g.save.flush(); if (g.screen === 'play' && g.wonT < 0) g.paused = true; g.touches.clear(); g.keys = { left: false, right: false, jump: false }; });
  }

  private resize() {
    const vs = view.getVisibleSize();
    const w = Math.max(960, Math.round(720 * vs.width / vs.height));
    this.renderer.setSize(w);
    this.drawRoot.getComponent(UITransform)!.setContentSize(w, 720);
    // scale the logical 720-high space into the visible area (Canvas uses FIXED_HEIGHT 720)
    const k = vs.height / 720;
    this.drawRoot.setScale(k, k, 1);
    this.applySafeArea();
  }

  private applySafeArea() {
    if (!this.game) return;
    const vs = view.getVisibleSize();
    const sa = sys.getSafeAreaRect();
    const k = 720 / vs.height;
    this.game.safe = {
      l: Math.max(0, sa.x) * k + 16,
      r: Math.max(0, vs.width - sa.x - sa.width) * k + 16,
      t: 0,
      b: Math.max(0, sa.y) * k + 6,
    };
  }

  private touch(type: 'down' | 'move' | 'up', e: EventTouch) {
    if (!this.game) return;
    const p = e.getUILocation();
    const vs = view.getVisibleSize();
    const k = 720 / vs.height;
    this.game.pointer(type, e.getID(), p.x * k, (vs.height - p.y) * k);
  }

  update(dt: number) {
    this.game?.frame(dt);
  }
}
