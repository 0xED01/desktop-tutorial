using UnityEngine;

namespace OfficeExplorer
{
    /// <summary>
    /// 全部音效由代码合成（不含任何录音素材，也不使用原视频中的声音）：
    /// 地毯脚步声、起跳 / 落地、空调与办公室底噪、灯光开关声、界面按键音。
    /// </summary>
    public class AudioManager : MonoBehaviour
    {
        public enum Sfx { Step, Land, Jump, Switch, Click }

        static AudioManager inst;
        AudioSource sfx, ambient;
        AudioClip[] steps;
        AudioClip land, jump, sw, click;
        const int Rate = 44100;
        System.Random rnd = new System.Random(7);

        public static AudioManager Create()
        {
            var go = new GameObject("Audio");
            inst = go.AddComponent<AudioManager>();
            inst.Build();
            return inst;
        }

        public static void Play(Sfx s, float volume = 1f)
        {
            if (inst == null) return;
            AudioClip c;
            switch (s)
            {
                case Sfx.Step: c = inst.steps[inst.rnd.Next(inst.steps.Length)]; inst.sfx.pitch = 0.92f + (float)inst.rnd.NextDouble() * 0.16f; break;
                case Sfx.Land: c = inst.land; inst.sfx.pitch = 1f; break;
                case Sfx.Jump: c = inst.jump; inst.sfx.pitch = 1f; break;
                case Sfx.Switch: c = inst.sw; inst.sfx.pitch = 1f; break;
                default: c = inst.click; inst.sfx.pitch = 1f; break;
            }
            inst.sfx.PlayOneShot(c, volume);
        }

        public static void SetAmbient(bool playing, float volume)
        {
            if (inst == null) return;
            inst.ambient.volume = volume;
            if (playing && !inst.ambient.isPlaying) inst.ambient.Play();
            if (!playing && inst.ambient.isPlaying) inst.ambient.Pause();
        }

        void Build()
        {
            sfx = gameObject.AddComponent<AudioSource>();
            sfx.playOnAwake = false; sfx.spatialBlend = 0f; sfx.ignoreListenerPause = true;
            ambient = gameObject.AddComponent<AudioSource>();
            ambient.playOnAwake = false; ambient.loop = true; ambient.spatialBlend = 0f; ambient.volume = 0.22f;

            steps = new AudioClip[4];
            for (int i = 0; i < steps.Length; i++) steps[i] = Thud("Step" + i, 0.13f, 620f + i * 70f, 0.55f, 30f, 0.25f);
            land = Thud("Land", 0.24f, 300f, 0.9f, 16f, 0.5f);
            jump = Thud("Jump", 0.10f, 900f, 0.35f, 38f, 0.1f);
            sw = SwitchClick();
            click = Blip();
            ambient.clip = AmbientLoop();
        }

        AudioClip Make(string name, float[] data)
        {
            var c = AudioClip.Create(name, data.Length, 1, Rate, false);
            c.SetData(data, 0);
            return c;
        }

        /// <summary>低通噪声脉冲：模拟鞋底踩在地毯上的闷响。</summary>
        AudioClip Thud(string name, float len, float cutoff, float gain, float decay, float body)
        {
            int n = (int)(len * Rate);
            var d = new float[n];
            float lp = 0f, a = 1f - Mathf.Exp(-2f * Mathf.PI * cutoff / Rate);
            for (int i = 0; i < n; i++)
            {
                float t = (float)i / Rate;
                float env = Mathf.Min(1f, t * 400f) * Mathf.Exp(-decay * t);
                float noise = (float)rnd.NextDouble() * 2f - 1f;
                lp += (noise - lp) * a;
                float low = Mathf.Sin(2f * Mathf.PI * (95f - 60f * t) * t) * body;
                d[i] = (lp * 2.2f + low) * env * gain;
            }
            Fade(d);
            return Make(name, d);
        }

        AudioClip SwitchClick()
        {
            int n = (int)(0.09f * Rate);
            var d = new float[n];
            for (int i = 0; i < n; i++)
            {
                float t = (float)i / Rate;
                float a = Mathf.Exp(-900f * t) * Mathf.Sin(2f * Mathf.PI * 2600f * t);
                float t2 = t - 0.045f;
                float b = t2 > 0 ? Mathf.Exp(-700f * t2) * Mathf.Sin(2f * Mathf.PI * 1900f * t2) * 0.8f : 0f;
                d[i] = (a + b + ((float)rnd.NextDouble() - 0.5f) * 0.25f * Mathf.Exp(-500f * t)) * 0.6f;
            }
            Fade(d);
            return Make("Switch", d);
        }

        AudioClip Blip()
        {
            int n = (int)(0.06f * Rate);
            var d = new float[n];
            for (int i = 0; i < n; i++)
            {
                float t = (float)i / Rate;
                d[i] = Mathf.Sin(2f * Mathf.PI * (820f + 500f * t) * t) * Mathf.Exp(-60f * t) * Mathf.Min(1f, t * 600f) * 0.35f;
            }
            Fade(d);
            return Make("Click", d);
        }

        /// <summary>6 秒无缝循环：空调低频气流声 + 轻微电流哼声。</summary>
        AudioClip AmbientLoop()
        {
            int n = 6 * Rate, xf = Rate / 2;
            var raw = new float[n + xf];
            float lp1 = 0f, lp2 = 0f, slow = 0f;
            for (int i = 0; i < raw.Length; i++)
            {
                float t = (float)i / Rate;
                float noise = (float)rnd.NextDouble() * 2f - 1f;
                lp1 += (noise - lp1) * 0.035f;            // ≈250Hz 低通：气流
                lp2 += (noise - lp2) * 0.004f;            // ≈30Hz：低频隆隆
                slow += ((float)rnd.NextDouble() - 0.5f - slow) * 0.0002f;
                float hum = Mathf.Sin(2f * Mathf.PI * 100f * t) * 0.012f + Mathf.Sin(2f * Mathf.PI * 50f * t) * 0.01f;
                raw[i] = lp1 * (1.1f + slow * 30f) + lp2 * 3.0f + hum;
            }
            var d = new float[n];
            for (int i = 0; i < n; i++)
            {
                // 首尾交叉淡化 → 循环无接缝
                if (i < xf) { float k = (float)i / xf; d[i] = raw[i] * k + raw[n + i] * (1f - k); }
                else d[i] = raw[i];
                d[i] *= 0.55f;
            }
            return Make("Ambient", d);
        }

        static void Fade(float[] d)
        {
            int f = Mathf.Min(200, d.Length / 4);
            for (int i = 0; i < f; i++) d[d.Length - 1 - i] *= (float)i / f;
        }
    }
}
