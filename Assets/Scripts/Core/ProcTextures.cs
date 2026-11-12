using UnityEngine;

namespace OfficeExplorer
{
    /// <summary>
    /// 全部贴图由代码生成（地毯、木纹、布料、屏幕画面、挂画、窗外景色、UI 图元），
    /// 因此工程不依赖任何第三方贴图资源。
    /// </summary>
    public static class ProcTextures
    {
        // ---------- 基础噪声 ----------
        public static float Hash(int x, int y, int s = 0)
        {
            unchecked
            {
                uint h = (uint)(x * 374761393 + y * 668265263 + s * 2147483647);
                h = (h ^ (h >> 13)) * 1274126177u;
                h ^= h >> 16;
                return (h & 0xFFFFFF) / 16777215f;
            }
        }

        static float ValueNoise(float x, float y, int seed, int wrap)
        {
            int x0 = Mathf.FloorToInt(x), y0 = Mathf.FloorToInt(y);
            float fx = x - x0, fy = y - y0;
            fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
            int xa = Mod(x0, wrap), xb = Mod(x0 + 1, wrap), ya = Mod(y0, wrap), yb = Mod(y0 + 1, wrap);
            float a = Hash(xa, ya, seed), b = Hash(xb, ya, seed), c = Hash(xa, yb, seed), d = Hash(xb, yb, seed);
            return Mathf.Lerp(Mathf.Lerp(a, b, fx), Mathf.Lerp(c, d, fx), fy);
        }

        static int Mod(int a, int m) { int r = a % m; return r < 0 ? r + m : r; }

        /// <summary>可无缝平铺的分形噪声，u、v ∈ [0,1)。</summary>
        public static float Fbm(float u, float v, int baseFreqX, int baseFreqY, int octaves, int seed)
        {
            float sum = 0, amp = 0.5f, norm = 0;
            int fx = baseFreqX, fy = baseFreqY;
            for (int o = 0; o < octaves; o++)
            {
                // 非正方形频率时分别对两个方向取模以保持无缝
                sum += amp * ValueNoise2(u * fx, v * fy, seed + o * 17, fx, fy);
                norm += amp; amp *= 0.5f; fx *= 2; fy *= 2;
            }
            return sum / norm;
        }

        static float ValueNoise2(float x, float y, int seed, int wrapX, int wrapY)
        {
            int x0 = Mathf.FloorToInt(x), y0 = Mathf.FloorToInt(y);
            float fx = x - x0, fy = y - y0;
            fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
            int xa = Mod(x0, wrapX), xb = Mod(x0 + 1, wrapX), ya = Mod(y0, wrapY), yb = Mod(y0 + 1, wrapY);
            float a = Hash(xa, ya, seed), b = Hash(xb, ya, seed), c = Hash(xa, yb, seed), d = Hash(xb, yb, seed);
            return Mathf.Lerp(Mathf.Lerp(a, b, fx), Mathf.Lerp(c, d, fx), fy);
        }

        // ---------- 贴图封装 ----------
        public static Texture2D Make(int w, int h, Color32[] px, bool linear, bool repeat, string name, bool mips = true)
        {
            var t = new Texture2D(w, h, TextureFormat.RGBA32, mips, linear)
            {
                name = name,
                wrapMode = repeat ? TextureWrapMode.Repeat : TextureWrapMode.Clamp,
                filterMode = FilterMode.Trilinear,
                anisoLevel = repeat ? 8 : 4
            };
            t.SetPixels32(px);
            t.Apply(mips, true);
            return t;
        }

        static Color32 C(float r, float g, float b, float a = 1f)
        {
            return new Color32((byte)(Mathf.Clamp01(r) * 255f), (byte)(Mathf.Clamp01(g) * 255f), (byte)(Mathf.Clamp01(b) * 255f), (byte)(Mathf.Clamp01(a) * 255f));
        }

        /// <summary>由高度场生成法线贴图（RGB 编码，移动端与编辑器均可正确解码）。</summary>
        public static Texture2D NormalFromHeight(float[] hgt, int w, int h, float strength, string name)
        {
            var px = new Color32[w * h];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                {
                    float l = hgt[y * w + Mod(x - 1, w)], r = hgt[y * w + Mod(x + 1, w)];
                    float d = hgt[Mod(y - 1, h) * w + x], u = hgt[Mod(y + 1, h) * w + x];
                    Vector3 n = new Vector3((l - r) * strength, (d - u) * strength, 1f).normalized;
                    px[y * w + x] = C(n.x * 0.5f + 0.5f, n.y * 0.5f + 0.5f, n.z * 0.5f + 0.5f, 1f);
                }
            return Make(w, h, px, true, true, name);
        }

        // ---------- 地毯：灰色方块拼接、条纹四分之一转拼法（参照视频） ----------
        public static void Carpet(out Texture2D albedo, out Texture2D normal)
        {
            const int N = 1024, TILES = 4, TP = N / TILES;   // 贴图覆盖 2m×2m，每块 0.5m
            var px = new Color32[N * N];
            var hgt = new float[N * N];
            for (int y = 0; y < N; y++)
                for (int x = 0; x < N; x++)
                {
                    int tx = x / TP, ty = y / TP;
                    int lx = x % TP, ly = y % TP;
                    bool turn = ((tx + ty) & 1) == 1;
                    int across = turn ? lx : ly, along = turn ? ly : lx;
                    int stripe = across / 5;
                    int tileId = tx + ty * 7;
                    // 每条绒线的基础明度 + 沿线分段的明暗变化（形成视频里的不规则条块）
                    float sBase = Hash(stripe, tileId, 11);
                    int segLen = 24 + (int)(Hash(stripe, tileId, 5) * 70f);
                    float seg = Hash(stripe + (along / segLen) * 31, tileId, 23);
                    float block = Hash(across / 40, along / 64 + tileId * 3, 41);
                    float v = 0.30f + 0.10f * sBase + 0.16f * (seg > 0.55f ? 1f : 0f) * (0.5f + 0.5f * block) + 0.07f * block;
                    float fiber = Hash(x, y, 3);
                    v += (fiber - 0.5f) * 0.07f;
                    // 绒线之间的细缝与地毯块之间的拼缝
                    float groove = (across % 5 == 0) ? 0.82f : 1f;
                    float seam = (lx < 1 || ly < 1) ? 0.72f : 1f;
                    v *= groove * seam;
                    px[y * N + x] = C(v * 0.96f, v * 0.97f, v * 1.02f, 1f);
                    hgt[y * N + x] = fiber * 0.6f + (across % 5 == 0 ? 0f : 0.4f) - (seam < 1f ? 0.6f : 0f);
                }
            albedo = Make(N, N, px, false, true, "CarpetAlbedo");
            normal = NormalFromHeight(hgt, N, N, 2.2f, "CarpetNormal");
        }

        // ---------- 木纹（浅色办公桌面 / 深胡桃木休闲桌 / 柜门） ----------
        public static void Wood(Color baseCol, Color darkCol, int seed, float plankCount, out Texture2D albedo, out Texture2D normal)
        {
            const int N = 512;
            var px = new Color32[N * N];
            var hgt = new float[N * N];
            for (int y = 0; y < N; y++)
                for (int x = 0; x < N; x++)
                {
                    float u = (float)x / N, v = (float)y / N;
                    int plank = Mathf.FloorToInt(v * plankCount);
                    float pOff = Hash(plank, seed, 7);
                    float grain = Fbm(u + pOff, v, 4, 96, 4, seed);          // 沿 U 方向拉长的纹理
                    float fine = Fbm(u + pOff * 3f, v, 16, 256, 2, seed + 9);
                    float t = Mathf.Clamp01(0.55f * grain + 0.45f * fine);
                    float tone = 0.92f + 0.16f * (pOff - 0.5f);
                    Color c = Color.Lerp(darkCol, baseCol, Mathf.SmoothStep(0.15f, 0.85f, t)) * tone;
                    float pv = v * plankCount - plank;
                    float seam = (pv < 0.012f * plankCount / 4f) ? 0.86f : 1f;
                    c *= seam;
                    px[y * N + x] = C(c.r, c.g, c.b, 1f);
                    hgt[y * N + x] = t * 0.5f - (seam < 1f ? 0.5f : 0f);
                }
            albedo = Make(N, N, px, false, true, "WoodAlbedo" + seed);
            normal = NormalFromHeight(hgt, N, N, 1.2f, "WoodNormal" + seed);
        }

        // ---------- 织物（隔断屏风、椅面网布） ----------
        public static void Fabric(Color col, float contrast, int cell, int seed, out Texture2D albedo, out Texture2D normal)
        {
            const int N = 256;
            var px = new Color32[N * N];
            var hgt = new float[N * N];
            for (int y = 0; y < N; y++)
                for (int x = 0; x < N; x++)
                {
                    bool warp = ((x / cell) + (y / cell)) % 2 == 0;
                    float thread = warp ? Mathf.Abs(((x % cell) + 0.5f) / cell - 0.5f) : Mathf.Abs(((y % cell) + 0.5f) / cell - 0.5f);
                    float hgtV = 1f - thread * 2f;
                    float n = Hash(x, y, seed);
                    float v = 1f - contrast + contrast * (0.6f * hgtV + 0.4f * n);
                    px[y * N + x] = C(col.r * v, col.g * v, col.b * v, 1f);
                    hgt[y * N + x] = hgtV * 0.7f + n * 0.3f;
                }
            albedo = Make(N, N, px, false, true, "FabricAlbedo" + seed);
            normal = NormalFromHeight(hgt, N, N, 1.6f, "FabricNormal" + seed);
        }

        // ---------- 矿棉板吊顶：600mm 网格 + 细小孔洞 ----------
        public static Texture2D CeilingTile()
        {
            const int N = 256;
            var px = new Color32[N * N];
            for (int y = 0; y < N; y++)
                for (int x = 0; x < N; x++)
                {
                    float v = 0.93f + (Hash(x, y, 2) - 0.5f) * 0.03f;
                    if (Hash(x / 2, y / 2, 8) > 0.965f) v -= 0.10f;
                    if (x < 2 || y < 2) v *= 0.86f;
                    px[y * N + x] = C(v, v, v * 0.995f, 1f);
                }
            return Make(N, N, px, false, true, "CeilingTile");
        }

        /// <summary>带轻微涂料颗粒的墙面。</summary>
        public static Texture2D Plaster()
        {
            const int N = 256;
            var px = new Color32[N * N];
            for (int y = 0; y < N; y++)
                for (int x = 0; x < N; x++)
                {
                    float v = 0.95f + (Fbm((float)x / N, (float)y / N, 8, 8, 3, 5) - 0.5f) * 0.05f + (Hash(x, y, 4) - 0.5f) * 0.015f;
                    px[y * N + x] = C(v, v, v, 1f);
                }
            return Make(N, N, px, false, true, "Plaster");
        }

        // ---------- 画布工具 ----------
        public class Canvas
        {
            public readonly int w, h;
            public readonly Color32[] px;
            public Canvas(int w, int h, Color fill)
            {
                this.w = w; this.h = h; px = new Color32[w * h];
                Color32 f = fill;
                for (int i = 0; i < px.Length; i++) px[i] = f;
            }
            /// <summary>以左上角为原点的归一化矩形（便于按"屏幕坐标"排版）。</summary>
            public void Rect(float x, float y, float rw, float rh, Color c)
            {
                int x0 = Mathf.Clamp(Mathf.RoundToInt(x * w), 0, w), x1 = Mathf.Clamp(Mathf.RoundToInt((x + rw) * w), 0, w);
                int y1 = h - Mathf.Clamp(Mathf.RoundToInt(y * h), 0, h), y0 = h - Mathf.Clamp(Mathf.RoundToInt((y + rh) * h), 0, h);
                Color32 cc = c;
                for (int yy = y0; yy < y1; yy++)
                    for (int xx = x0; xx < x1; xx++)
                        px[yy * w + xx] = c.a >= 0.999f ? cc : (Color32)Color.Lerp(px[yy * w + xx], new Color(c.r, c.g, c.b, 1f), c.a);
            }
            public void Dot(float cx, float cy, float radiusPx, Color c)
            {
                int px0 = Mathf.RoundToInt(cx * w), py0 = h - Mathf.RoundToInt(cy * h);
                int r = Mathf.CeilToInt(radiusPx);
                for (int yy = -r; yy <= r; yy++)
                    for (int xx = -r; xx <= r; xx++)
                    {
                        int X = px0 + xx, Y = py0 + yy;
                        if (X < 0 || Y < 0 || X >= w || Y >= h) continue;
                        float d = Mathf.Sqrt(xx * xx + yy * yy);
                        float a = Mathf.Clamp01(radiusPx - d) * c.a;
                        if (a <= 0) continue;
                        px[Y * w + X] = Color.Lerp(px[Y * w + X], new Color(c.r, c.g, c.b, 1f), a);
                    }
            }
            public Texture2D ToTexture(string name, bool repeat = false) { return Make(w, h, px, false, repeat, name); }
        }

        // ---------- 显示器画面（4 种：代码、表格、图表仪表盘、文档） ----------
        public static Texture2D Screen(int kind)
        {
            var c = new Canvas(512, 320, new Color(0.10f, 0.11f, 0.13f));
            var rnd = new System.Random(1000 + kind);
            System.Func<float> R = () => (float)rnd.NextDouble();
            switch (kind % 4)
            {
                case 0: // 代码编辑器
                    c.Rect(0, 0, 1, 0.06f, new Color(0.16f, 0.17f, 0.20f));
                    c.Rect(0, 0.06f, 0.18f, 0.94f, new Color(0.13f, 0.14f, 0.16f));
                    for (int i = 0; i < 14; i++) c.Rect(0.02f + (i % 3) * 0.015f, 0.10f + i * 0.055f, 0.06f + R() * 0.07f, 0.018f, new Color(0.55f, 0.58f, 0.64f));
                    Color[] syn = { new Color(0.45f, 0.70f, 0.95f), new Color(0.85f, 0.60f, 0.40f), new Color(0.60f, 0.85f, 0.55f), new Color(0.80f, 0.80f, 0.82f), new Color(0.78f, 0.55f, 0.85f) };
                    for (int i = 0; i < 17; i++)
                    {
                        float x = 0.21f + ((i * 7) % 4) * 0.025f, y = 0.10f + i * 0.05f;
                        int words = 1 + rnd.Next(5);
                        for (int k = 0; k < words; k++)
                        {
                            float wl = 0.03f + R() * 0.10f;
                            if (x + wl > 0.96f) break;
                            c.Rect(x, y, wl, 0.020f, syn[rnd.Next(syn.Length)]);
                            x += wl + 0.012f;
                        }
                    }
                    break;
                case 1: // 电子表格
                    c.Rect(0, 0, 1, 1, new Color(0.96f, 0.97f, 0.97f));
                    c.Rect(0, 0, 1, 0.09f, new Color(0.13f, 0.45f, 0.27f));
                    c.Rect(0, 0.09f, 1, 0.06f, new Color(0.88f, 0.90f, 0.90f));
                    for (int i = 0; i <= 14; i++) c.Rect(0, 0.15f + i * 0.06f, 1, 0.004f, new Color(0.78f, 0.80f, 0.80f));
                    for (int i = 0; i <= 8; i++) c.Rect(0.06f + i * 0.118f, 0.15f, 0.003f, 0.85f, new Color(0.78f, 0.80f, 0.80f));
                    for (int r = 0; r < 14; r++)
                        for (int k = 0; k < 8; k++)
                            if (R() > 0.3f) c.Rect(0.07f + k * 0.118f, 0.17f + r * 0.06f, 0.03f + R() * 0.06f, 0.02f, k == 0 ? new Color(0.2f, 0.2f, 0.25f) : new Color(0.40f, 0.42f, 0.48f));
                    c.Rect(0.42f, 0.33f, 0.118f, 0.06f, new Color(0.13f, 0.55f, 0.30f, 0.35f));
                    break;
                case 2: // 数据仪表盘
                    c.Rect(0, 0, 1, 1, new Color(0.07f, 0.09f, 0.14f));
                    c.Rect(0, 0, 1, 0.08f, new Color(0.10f, 0.13f, 0.21f));
                    c.Rect(0.02f, 0.025f, 0.16f, 0.03f, new Color(0.75f, 0.80f, 0.92f));
                    for (int k = 0; k < 4; k++)
                    {
                        c.Rect(0.03f + k * 0.243f, 0.12f, 0.215f, 0.20f, new Color(0.12f, 0.15f, 0.24f));
                        c.Rect(0.045f + k * 0.243f, 0.15f, 0.08f, 0.02f, new Color(0.55f, 0.62f, 0.78f));
                        c.Rect(0.045f + k * 0.243f, 0.20f, 0.12f, 0.06f, new Color[] { new Color(0.35f, 0.75f, 1f), new Color(0.45f, 0.90f, 0.60f), new Color(1f, 0.75f, 0.35f), new Color(0.95f, 0.45f, 0.55f) }[k]);
                    }
                    c.Rect(0.03f, 0.36f, 0.60f, 0.58f, new Color(0.12f, 0.15f, 0.24f));
                    for (int i = 0; i < 16; i++)
                    {
                        float bh = 0.08f + R() * 0.38f;
                        c.Rect(0.05f + i * 0.035f, 0.90f - bh, 0.022f, bh, new Color(0.30f, 0.62f, 0.98f));
                    }
                    c.Rect(0.66f, 0.36f, 0.31f, 0.58f, new Color(0.12f, 0.15f, 0.24f));
                    for (int i = 0; i < 7; i++)
                    {
                        c.Rect(0.68f, 0.41f + i * 0.072f, 0.27f * (0.3f + R() * 0.7f), 0.03f, new Color(0.45f, 0.88f, 0.62f));
                    }
                    break;
                default: // 文档
                    c.Rect(0, 0, 1, 1, new Color(0.82f, 0.84f, 0.87f));
                    c.Rect(0, 0, 1, 0.10f, new Color(0.17f, 0.34f, 0.60f));
                    c.Rect(0.20f, 0.14f, 0.60f, 0.86f, Color.white);
                    c.Rect(0.26f, 0.20f, 0.30f, 0.035f, new Color(0.15f, 0.16f, 0.20f));
                    for (int i = 0; i < 12; i++)
                        c.Rect(0.26f, 0.29f + i * 0.052f, (i % 4 == 3) ? 0.25f + R() * 0.1f : 0.48f, 0.016f, new Color(0.38f, 0.40f, 0.45f));
                    break;
            }
            // 任务栏
            c.Rect(0, 0.955f, 1, 0.045f, new Color(0.08f, 0.09f, 0.11f, 0.92f));
            for (int i = 0; i < 6; i++) c.Rect(0.012f + i * 0.035f, 0.965f, 0.022f, 0.026f, new Color(0.35f + 0.1f * i, 0.55f, 0.85f - 0.1f * i));
            return c.ToTexture("Screen" + kind);
        }

        /// <summary>墙面电视：播放中的信息看板。</summary>
        public static Texture2D TvPanel()
        {
            var c = new Canvas(512, 288, new Color(0.03f, 0.05f, 0.09f));
            for (int i = 0; i < 40; i++)
            {
                float x = Hash(i, 1, 77), y = Hash(i, 2, 77);
                c.Rect(x, 0.15f + y * 0.7f, 0.02f + Hash(i, 3, 77) * 0.2f, 0.012f, new Color(0.15f, 0.35f + 0.3f * Hash(i, 4, 77), 0.65f, 0.8f));
            }
            c.Rect(0.05f, 0.06f, 0.35f, 0.05f, new Color(0.8f, 0.86f, 0.95f));
            return c.ToTexture("TvPanel");
        }

        /// <summary>键盘顶面：按键网格。</summary>
        public static Texture2D Keyboard()
        {
            var c = new Canvas(256, 96, new Color(0.035f, 0.035f, 0.04f));
            for (int r = 0; r < 5; r++)
                for (int k = 0; k < 17; k++)
                {
                    if (r == 4 && k >= 4 && k <= 9) { if (k == 4) c.Rect(0.03f + k * 0.056f, 0.08f + r * 0.18f, 0.056f * 6 - 0.012f, 0.13f, new Color(0.10f, 0.10f, 0.11f)); continue; }
                    c.Rect(0.03f + k * 0.056f, 0.08f + r * 0.18f, 0.044f, 0.13f, new Color(0.10f, 0.10f, 0.11f));
                }
            return c.ToTexture("Keyboard");
        }

        /// <summary>墙上的书法横幅（抽象墨迹，不还原任何真实文字）。</summary>
        public static Texture2D InkArt()
        {
            var c = new Canvas(768, 256, new Color(0.93f, 0.91f, 0.86f));
            var rnd = new System.Random(88);
            for (int g = 0; g < 4; g++)
            {
                float cx = 0.24f + g * 0.17f, cy = 0.5f;
                for (int s = 0; s < 5; s++)
                {
                    float x = cx + ((float)rnd.NextDouble() - 0.5f) * 0.10f, y = cy + ((float)rnd.NextDouble() - 0.5f) * 0.40f;
                    float dx = ((float)rnd.NextDouble() - 0.5f) * 0.012f, dy = ((float)rnd.NextDouble() - 0.5f) * 0.03f;
                    float curl = ((float)rnd.NextDouble() - 0.5f) * 0.0016f;
                    float rad = 5f + (float)rnd.NextDouble() * 5f;
                    for (int i = 0; i < 26; i++)
                    {
                        c.Dot(x, y, rad * (1f - i / 34f), new Color(0.07f, 0.07f, 0.08f, 0.92f));
                        x += dx * 0.5f; y += dy * 0.5f; dx += curl * 2f; dy -= curl;
                    }
                }
            }
            c.Rect(0.90f, 0.62f, 0.035f, 0.11f, new Color(0.72f, 0.12f, 0.10f));
            return c.ToTexture("InkArt");
        }

        /// <summary>公告栏海报。</summary>
        public static Texture2D Poster(int kind)
        {
            Color bg = kind == 0 ? new Color(0.86f, 0.92f, 0.97f) : new Color(0.99f, 0.95f, 0.80f);
            Color ink = kind == 0 ? new Color(0.20f, 0.42f, 0.70f) : new Color(0.80f, 0.52f, 0.12f);
            var c = new Canvas(256, 384, bg);
            c.Rect(0.08f, 0.05f, 0.84f, 0.10f, ink);
            var rnd = new System.Random(5 + kind);
            for (int i = 0; i < 16; i++) c.Rect(0.10f, 0.20f + i * 0.045f, 0.45f + (float)rnd.NextDouble() * 0.35f, 0.016f, new Color(ink.r * 0.6f, ink.g * 0.6f, ink.b * 0.6f, 0.8f));
            return c.ToTexture("Poster" + kind);
        }

        /// <summary>红底白块的提示牌。</summary>
        public static Texture2D Notice()
        {
            var c = new Canvas(128, 96, Color.white);
            c.Rect(0.05f, 0.07f, 0.9f, 0.86f, new Color(0.80f, 0.12f, 0.12f));
            c.Rect(0.14f, 0.22f, 0.72f, 0.18f, Color.white);
            c.Rect(0.14f, 0.52f, 0.72f, 0.07f, new Color(1f, 1f, 1f, 0.9f));
            c.Rect(0.14f, 0.68f, 0.52f, 0.07f, new Color(1f, 1f, 1f, 0.9f));
            return c.ToTexture("Notice");
        }

        /// <summary>安全出口灯箱：绿底 + 箭头 + 门形图案。</summary>
        public static Texture2D ExitSign()
        {
            var c = new Canvas(256, 96, new Color(0.05f, 0.62f, 0.30f));
            c.Rect(0.08f, 0.18f, 0.20f, 0.64f, Color.white);
            c.Rect(0.12f, 0.26f, 0.12f, 0.56f, new Color(0.05f, 0.62f, 0.30f));
            c.Rect(0.36f, 0.42f, 0.34f, 0.16f, Color.white);
            for (int i = 0; i < 20; i++) { float t = i / 20f; c.Rect(0.70f + t * 0.16f, 0.22f + t * 0.28f, 0.012f, 0.56f * (1f - t), Color.white); }
            return c.ToTexture("ExitSign");
        }

        /// <summary>窗外远景：天空渐变 + 楼群剪影。night=true 时为亮着窗灯的夜景。</summary>
        public static Texture2D Skyline(bool night)
        {
            const int W = 1024, H = 256;
            var px = new Color32[W * H];
            var heights = new float[64];
            for (int i = 0; i < 64; i++) heights[i] = 0.18f + Hash(i, 3, 91) * 0.42f;
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++)
                {
                    float v = (float)y / H;
                    Color sky = night ? Color.Lerp(new Color(0.05f, 0.06f, 0.11f), new Color(0.01f, 0.012f, 0.03f), v)
                                      : Color.Lerp(new Color(0.86f, 0.90f, 0.95f), new Color(0.42f, 0.62f, 0.90f), v);
                    int b = x / 16;
                    Color col = sky;
                    if (v < heights[b])
                    {
                        float shade = 0.75f + Hash(b, 7, 12) * 0.25f;
                        col = night ? new Color(0.025f, 0.03f, 0.045f) * shade : Color.Lerp(new Color(0.55f, 0.60f, 0.67f) * shade, sky, 0.35f);
                        int wx = (x % 16) / 4, wy = y / 6;
                        bool isWin = (x % 4) != 0 && (y % 6) > 1 && wx > 0;
                        if (isWin)
                        {
                            float lit = Hash(b * 5 + wx, wy, 33);
                            if (night && lit > 0.62f) col = Color.Lerp(new Color(1.0f, 0.82f, 0.50f), new Color(0.75f, 0.88f, 1f), Hash(b, wy, 2)) * (0.6f + 0.4f * lit);
                            else if (!night) col *= 0.86f;
                        }
                    }
                    px[y * W + x] = C(col.r, col.g, col.b, 1f);
                }
            var t = Make(W, H, px, false, true, night ? "SkylineNight" : "SkylineDay");
            t.wrapModeV = TextureWrapMode.Clamp;
            return t;
        }

        // ---------- 特效 / UI 图元 ----------
        /// <summary>柔和的径向渐变（白色，alpha 由中心向外衰减）。用于接触阴影贴片、摇杆等。</summary>
        public static Texture2D SoftBlob(int n, float power)
        {
            var px = new Color32[n * n];
            for (int y = 0; y < n; y++)
                for (int x = 0; x < n; x++)
                {
                    float dx = (x + 0.5f) / n * 2f - 1f, dy = (y + 0.5f) / n * 2f - 1f;
                    float a = Mathf.Pow(Mathf.Clamp01(1f - Mathf.Sqrt(dx * dx + dy * dy)), power);
                    px[y * n + x] = new Color32(255, 255, 255, (byte)(a * 255f));
                }
            return Make(n, n, px, false, false, "SoftBlob", false);
        }

        /// <summary>柔边矩形（桌下接触阴影）。</summary>
        public static Texture2D SoftRect(int n)
        {
            var px = new Color32[n * n];
            for (int y = 0; y < n; y++)
                for (int x = 0; x < n; x++)
                {
                    float dx = Mathf.Abs((x + 0.5f) / n * 2f - 1f), dy = Mathf.Abs((y + 0.5f) / n * 2f - 1f);
                    float a = Mathf.SmoothStep(0f, 1f, Mathf.Clamp01((1f - dx) * 3.2f)) * Mathf.SmoothStep(0f, 1f, Mathf.Clamp01((1f - dy) * 6f));
                    px[y * n + x] = new Color32(255, 255, 255, (byte)(a * 255f));
                }
            return Make(n, n, px, false, false, "SoftRect", false);
        }

        /// <summary>窗边光束：自上而下、两侧柔化的渐变。</summary>
        public static Texture2D LightShaft()
        {
            const int W = 64, H = 128;
            var px = new Color32[W * H];
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++)
                {
                    float u = (x + 0.5f) / W, v = (y + 0.5f) / H;
                    float side = Mathf.SmoothStep(0f, 1f, Mathf.Clamp01(Mathf.Min(u, 1f - u) * 4f));
                    float a = side * Mathf.Pow(v, 1.4f) * (0.75f + 0.25f * Hash(x / 3, 0, 6));
                    px[y * W + x] = new Color32(255, 255, 255, (byte)(a * 255f));
                }
            return Make(W, H, px, false, false, "LightShaft", false);
        }

        /// <summary>九宫格圆角矩形精灵（UI 面板与按钮）。</summary>
        public static Sprite RoundedSprite(int size, int radius)
        {
            var px = new Color32[size * size];
            for (int y = 0; y < size; y++)
                for (int x = 0; x < size; x++)
                {
                    float cx = Mathf.Clamp(x + 0.5f, radius, size - radius), cy = Mathf.Clamp(y + 0.5f, radius, size - radius);
                    float d = Mathf.Sqrt((x + 0.5f - cx) * (x + 0.5f - cx) + (y + 0.5f - cy) * (y + 0.5f - cy));
                    px[y * size + x] = new Color32(255, 255, 255, (byte)(Mathf.Clamp01(radius - d + 0.5f) * 255f));
                }
            var t = Make(size, size, px, false, false, "UIRounded", false);
            t.filterMode = FilterMode.Bilinear;
            return Sprite.Create(t, new Rect(0, 0, size, size), new Vector2(0.5f, 0.5f), 100f, 0, SpriteMeshType.FullRect, new Vector4(radius, radius, radius, radius));
        }

        /// <summary>圆形 / 圆环精灵（摇杆、圆形按钮）。ring=0 为实心。</summary>
        public static Sprite CircleSprite(int size, float ring)
        {
            var px = new Color32[size * size];
            float r = size * 0.5f - 1f;
            for (int y = 0; y < size; y++)
                for (int x = 0; x < size; x++)
                {
                    float d = Mathf.Sqrt((x + 0.5f - size * 0.5f) * (x + 0.5f - size * 0.5f) + (y + 0.5f - size * 0.5f) * (y + 0.5f - size * 0.5f));
                    float a = Mathf.Clamp01(r - d + 0.5f);
                    if (ring > 0f) a *= Mathf.Clamp01(d - (r - ring) + 0.5f);
                    px[y * size + x] = new Color32(255, 255, 255, (byte)(a * 255f));
                }
            var t = Make(size, size, px, false, false, "UICircle", false);
            t.filterMode = FilterMode.Bilinear;
            return Sprite.Create(t, new Rect(0, 0, size, size), new Vector2(0.5f, 0.5f), 100f);
        }
    }
}
