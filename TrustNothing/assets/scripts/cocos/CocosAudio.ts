/** IAudio implementation for Cocos Creator (AudioSource + AudioClip from resources/audio). */
import { AudioClip, AudioSource, Node, resources } from 'cc';
import { IAudio } from '../core/services';

export class CocosAudio implements IAudio {
  private music: AudioSource;
  private sfx: AudioSource;
  private clips = new Map<string, AudioClip>();
  private wanted = '';
  private playing = '';
  private vol = { music: 0.6, sfx: 0.8, mOn: true, sOn: true };

  constructor(host: Node) {
    this.music = host.addComponent(AudioSource);
    this.sfx = host.addComponent(AudioSource);
    this.music.loop = true;
    resources.loadDir('audio/sfx', AudioClip, (err, list) => { if (!err) for (const c of list) this.clips.set('sfx_' + c.name, c); });
    resources.loadDir('audio/music', AudioClip, (err, list) => {
      if (!err) for (const c of list) this.clips.set('mus_' + c.name, c);
      if (this.wanted) { const w = this.wanted; this.playing = ''; this.playMusic(w); }
    });
  }
  playSfx(name: string) {
    if (!this.vol.sOn) return;
    const c = this.clips.get('sfx_' + name);
    if (c) this.sfx.playOneShot(c, this.vol.sfx);
  }
  playMusic(track: string) {
    this.wanted = track;
    if (this.playing === track) return;
    const c = this.clips.get('mus_' + track);
    if (!c) return;
    this.music.stop();
    this.music.clip = c;
    this.music.loop = track !== 'victory_short';
    this.music.volume = this.vol.music * 0.55;
    this.playing = track;
    if (this.vol.mOn) this.music.play();
  }
  stopMusic() { this.music.stop(); this.playing = ''; this.wanted = ''; }
  setVolumes(music: number, sfx: number, musicOn: boolean, sfxOn: boolean) {
    this.vol = { music, sfx, mOn: musicOn, sOn: sfxOn };
    this.music.volume = music * 0.55;
    if (!musicOn) this.music.pause();
    else if (this.playing && !this.music.playing) this.music.play();
    else if (!this.playing && this.wanted) this.playMusic(this.wanted);
  }
}
