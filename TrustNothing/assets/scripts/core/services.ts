/** Platform-agnostic services: storage, save data, achievements, audio & ad interfaces. */

/* ---------------- platform adapters (implemented per platform) ---------------- */
export interface IStorage { get(key: string): string | null; set(key: string, value: string): void; }
export interface IAudio {
  playSfx(name: string): void;
  playMusic(track: string): void;
  stopMusic(): void;
  setVolumes(music: number, sfx: number, musicOn: boolean, sfxOn: boolean): void;
  /** called on first user gesture (web audio unlock) */
  unlock?(): void;
}

/* ---------------- Ad service (rewarded video) ----------------
 * The Android test build uses MockAdService which is clearly labelled as a
 * simulated test ad and only completes after the player actively confirms.
 * WeChat: implement with wx.createRewardedVideoAd (see platform/wx notes).
 * Ads are always optional — closing them never blocks normal retries. */
export interface RewardedAdCallbacks {
  onRewardedAdCompleted(): void;
  onRewardedAdFailed(reason: string): void;
}
export interface AdService {
  readonly name: string;
  readonly isTestMode: boolean;
  isAvailable(placement: string): boolean;
  showRewardedAd(placement: 'hint' | 'shield' | 'revive', cb: RewardedAdCallbacks): void;
}
/** UI host that renders the simulated ad (Game implements it). */
export interface MockAdHost { openMockAd(placement: string, onDone: (completed: boolean) => void): void; }

export class MockAdService implements AdService {
  readonly name = 'MockAdService (测试模拟，无真实广告)';
  readonly isTestMode = true;
  constructor(private host: MockAdHost) {}
  isAvailable(): boolean { return true; }
  showRewardedAd(placement: 'hint' | 'shield' | 'revive', cb: RewardedAdCallbacks): void {
    this.host.openMockAd(placement, (completed) => {
      if (completed) cb.onRewardedAdCompleted(); else cb.onRewardedAdFailed('user_closed');
    });
  }
}

/* ---------------- Save ---------------- */
export interface Settings { musicOn: boolean; sfxOn: boolean; music: number; sfx: number; shake: boolean; debug: boolean; leftHanded: boolean; }
export interface SaveData {
  v: number;
  unlocked: number;                // highest playable level id
  cleared: Record<number, { deaths: number; best: number; perfect: boolean }>;
  levelDeaths: Record<number, number>;
  totalDeaths: number;
  fakeGoals: number;
  baitCoins: number;
  jumps: number;
  playTime: number;                // seconds
  achievements: Record<string, number>; // id -> timestamp
  settings: Settings;
  trueEnding: boolean;
}

const SAVE_KEY = 'trustnothing.save.v1';
export const DEFAULT_SETTINGS: Settings = { musicOn: true, sfxOn: true, music: 0.6, sfx: 0.8, shake: true, debug: false, leftHanded: false };

export function defaultSave(): SaveData {
  return { v: 1, unlocked: 1, cleared: {}, levelDeaths: {}, totalDeaths: 0, fakeGoals: 0, baitCoins: 0, jumps: 0, playTime: 0, achievements: {}, settings: { ...DEFAULT_SETTINGS }, trueEnding: false };
}

export class SaveManager {
  data: SaveData;
  private dirty = false;
  constructor(private store: IStorage) { this.data = this.load(); }
  load(): SaveData {
    const d = defaultSave();
    for (const key of [SAVE_KEY, SAVE_KEY + '.bak']) {
      try {
        const raw = this.store.get(key);
        if (!raw) continue;
        const j = JSON.parse(raw);
        if (typeof j !== 'object' || !j) continue;
        const merged: SaveData = { ...d, ...j, settings: { ...d.settings, ...(j.settings || {}) } };
        merged.unlocked = Math.max(1, Math.min(80, Number(merged.unlocked) || 1));
        return merged;
      } catch { /* try backup */ }
    }
    return d;
  }
  markDirty() { this.dirty = true; }
  flush() {
    if (!this.dirty) return;
    this.dirty = false;
    try {
      const raw = JSON.stringify(this.data);
      this.store.set(SAVE_KEY, raw);
      this.store.set(SAVE_KEY + '.bak', raw);
    } catch { /* storage full or unavailable – keep in memory */ }
  }
  reset() { const s = this.data.settings; this.data = defaultSave(); this.data.settings = s; this.dirty = true; this.flush(); }
}

/* ---------------- Achievements ---------------- */
export interface AchDef { id: string; name: string; desc: string; hidden?: boolean; }
export const ACHIEVEMENTS: AchDef[] = [
  { id: 'first_death', name: '欢迎光临', desc: '第一次死亡。这只是开始。' },
  { id: 'first_clear', name: '原来能过', desc: '通关第 1 关。' },
  { id: 'ch1', name: '你以为很简单', desc: '完成第一章。' },
  { id: 'ch2', name: '地板恐惧症', desc: '完成第二章。' },
  { id: 'ch3', name: '恐高症', desc: '完成第三章。' },
  { id: 'ch4', name: '门都是骗子', desc: '完成第四章。' },
  { id: 'ch5', name: '身体不听使唤', desc: '完成第五章。' },
  { id: 'ch6', name: '重新学习', desc: '完成第六章。' },
  { id: 'ch7', name: '受虐爱好者', desc: '完成第七章。' },
  { id: 'ch8', name: '骗局终结者', desc: '完成第八章。' },
  { id: 'perfect', name: '一次过', desc: '不死亡通过任意一关。' },
  { id: 'perfect10', name: '读心术', desc: '累计 10 关不死亡通过。' },
  { id: 'streak10', name: '有点上头', desc: '同一关连续死亡 10 次。' },
  { id: 'streak30', name: '我不信过不去', desc: '同一关连续死亡 30 次（隐藏成就）。', hidden: true },
  { id: 'deaths100', name: '百死不悔', desc: '累计死亡 100 次。' },
  { id: 'deaths500', name: '死亡专家', desc: '累计死亡 500 次。' },
  { id: 'deaths1000', name: '不死鸟（并不）', desc: '累计死亡 1000 次。' },
  { id: 'fooled5', name: '门的信徒', desc: '被假终点骗 5 次。' },
  { id: 'fooled20', name: '门都信', desc: '被假终点骗 20 次。' },
  { id: 'greedy', name: '财迷', desc: '吃下 10 个诱饵金币。' },
  { id: 'true_end', name: '真相只有一个', desc: '发现第 80 关的隐藏结局。', hidden: true },
  { id: 'all80', name: '别信这关·全通', desc: '完成全部 80 关，解锁纪念页。', hidden: true },
];

export class AchievementManager {
  queue: AchDef[] = [];
  constructor(private save: SaveManager) {}
  has(id: string) { return !!this.save.data.achievements[id]; }
  unlock(id: string) {
    if (this.has(id)) return;
    const def = ACHIEVEMENTS.find(a => a.id === id);
    if (!def) return;
    this.save.data.achievements[id] = Date.now();
    this.save.markDirty();
    this.queue.push(def);
  }
}

/* ---------------- Copy ---------------- */
export const DEATH_QUOTES = [
  '就差一点点。', '你真的以为这里安全？', '这次不能怪我吧？', '已经很努力了，下次继续。', '恭喜，你又发现了一种死法。',
  '地板：我只是开个玩笑。', '刚才那个……是你自己跳的。', '别生气，深呼吸。', '这关的设计师正在偷笑。', '你的反应很快，可惜方向错了。',
  '再来一次，这次肯定行。（大概）', '看见了吗？没看见就对了。', '尖刺表示：好久不见。', '你离成功只有一个死亡的距离。', '方块也会疼的。',
  '失败是成功他妈，但这关没有他爸。', '这不是 bug，这是特性。', '你学到了什么？对，别信。', '胜利就在前方，大概三十次之后。', '冷静，它们只是像素。',
  '你的勇气值得一块墓碑。', '下次试试别那么相信世界。', '诚实的游戏可不好玩。', '记住它，下次它就骗不到你了。', '小方块已经原地复活了，快上！',
  '欢迎回到起点。', '这一下很有艺术感。', '我宣布你是这里最勇敢的方块。', '这条路线已被尖刺承包。', '嗯，这里确实不安全。',
  '你是第 1 个这样死的。（才怪）', '已为你保存死亡录像。（并没有）', '要不……换个思路？', '笑一笑，重来吧。', '它就在那里等你，从未离开。',
];
export const STREAK10_QUOTES = ['已经第 10 次了，要不要看看提示？', '十连死！你的毅力令人感动。', '这关跟你有仇吗？'];
export const STREAK30_QUOTES = ['30 次！你已经和这关的每一块地板都很熟了。', '解锁隐藏成就：我不信过不去。'];
export const FAKE_GOAL_QUOTES = ['骗你的～', '又信了？', '你对门的信任让人感动。', '下一扇门一定是真的。（大概）', '门：抱歉，我习惯了。', '你已经被门骗了很多次了，自己数。'];

export const CHAPTERS = [
  { name: '你以为很简单', sub: '1 - 10' },
  { name: '不要相信地板', sub: '11 - 20' },
  { name: '跳跃也是错误', sub: '21 - 30' },
  { name: '终点在骗你', sub: '31 - 40' },
  { name: '操作开始背叛你', sub: '41 - 50' },
  { name: '你学到的都是错的', sub: '51 - 60' },
  { name: '极限折磨', sub: '61 - 70' },
  { name: '最后的骗局', sub: '71 - 80' },
];

export function musicForLevel(id: number): string {
  const ch = Math.floor((id - 1) / 10);
  if (ch <= 1) return 'level_a';
  if (ch <= 4) return 'level_b';
  if (ch <= 6) return 'hard';
  return 'final';
}
