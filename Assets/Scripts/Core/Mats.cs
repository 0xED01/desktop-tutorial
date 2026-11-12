using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Rendering;

namespace OfficeExplorer
{
    public enum MatKind { Lit, LitNormal, LitEmissive, LitTransparent, Unlit, UnlitTransparent }

    /// <summary>
    /// PBR 材质库（URP/Lit）。模板材质由编辑器脚本生成到 Resources/MatTemplates，
    /// 保证打包时所需的着色器变体（法线、发光、透明）都被包含；
    /// 在编辑器里若模板尚未生成，则回退到 Shader.Find。
    /// </summary>
    public static class Mats
    {
        public const string LitShader = "Universal Render Pipeline/Lit";
        public const string UnlitShader = "Universal Render Pipeline/Unlit";

        static readonly Dictionary<Material, float> tiling = new Dictionary<Material, float>();
        static readonly HashSet<Material> transparent = new HashSet<Material>();
        static bool built;

        // 建筑
        public static Material Carpet, Wall, Ceiling, Baseboard, DoorDark, DoorWood, CabinetWood, WhiteMetal, Bronze, Blind, Glass, GlassDoor;
        // 家具
        public static Material DeskWood, Walnut, Partition, BlackMetal, BlackPlastic, ChairFabric, ChairMesh, Chrome, Cushion, Bin;
        // 电子设备
        public static Material MonitorBody, ScreenOff, TvScreen, KeyboardTop;
        public static Material[] ScreenOn;
        // 发光体
        public static Material LightPanel, ExitSign, Backdrop, Shaft, ContactShadow;
        // 杂物
        public static Material Paper, RedPaint, Plant, Pot, InkArt, Notice, Ceramic, BlueBox, YellowBox, PurpleBox, VentDark;
        public static Material[] Posters;
        // 角色
        public static Material Skin, Hair, Shirt, Pants, Shoes;

        public static Texture2D SkylineDay, SkylineNight;

        public static float Tiling(Material m) { return tiling.TryGetValue(m, out var t) ? t : 1f; }
        public static bool IsTransparent(Material m) { return transparent.Contains(m); }

        // ---------- 模板与关键字 ----------
        public static void Configure(Material m, MatKind k)
        {
            if (k == MatKind.LitNormal) m.EnableKeyword("_NORMALMAP");
            if (k == MatKind.LitEmissive)
            {
                m.EnableKeyword("_EMISSION");
                m.globalIlluminationFlags = MaterialGlobalIlluminationFlags.RealtimeEmissive;
            }
            if (k == MatKind.LitTransparent || k == MatKind.UnlitTransparent)
            {
                m.SetFloat("_Surface", 1f);
                m.SetFloat("_Blend", 0f);
                m.SetFloat("_SrcBlend", (float)BlendMode.SrcAlpha);
                m.SetFloat("_DstBlend", (float)BlendMode.OneMinusSrcAlpha);
                m.SetFloat("_SrcBlendAlpha", (float)BlendMode.One);
                m.SetFloat("_DstBlendAlpha", (float)BlendMode.OneMinusSrcAlpha);
                m.SetFloat("_ZWrite", 0f);
                m.EnableKeyword("_SURFACE_TYPE_TRANSPARENT");
                m.SetOverrideTag("RenderType", "Transparent");
                m.SetShaderPassEnabled("ShadowCaster", false);
                m.SetShaderPassEnabled("DepthOnly", false);
                m.renderQueue = (int)RenderQueue.Transparent;
            }
        }

        public static Material Create(MatKind k, string name)
        {
            Material m;
            var tpl = Resources.Load<Material>("MatTemplates/" + k);
            if (tpl != null) m = new Material(tpl);
            else
            {
                var sh = Shader.Find(k >= MatKind.Unlit ? UnlitShader : LitShader);
                if (sh == null)
                {
                    Debug.LogError("[办公室] 找不到 URP 着色器，请先执行菜单：办公室探索/一键配置工程");
                    sh = Shader.Find("Hidden/InternalErrorShader");
                }
                m = new Material(sh);
                Configure(m, k);
            }
            m.name = name;
            m.enableInstancing = true;
            if (k == MatKind.LitTransparent || k == MatKind.UnlitTransparent) transparent.Add(m);
            return m;
        }

        static Material Lit(string name, Color col, float metallic, float smooth, float uvTiling = 1f)
        {
            var m = Create(MatKind.Lit, name);
            m.SetColor("_BaseColor", col);
            m.SetFloat("_Metallic", metallic);
            m.SetFloat("_Smoothness", smooth);
            tiling[m] = uvTiling;
            return m;
        }

        static Material LitTex(string name, Texture2D albedo, Texture2D normal, Color tint, float metallic, float smooth, float uvTiling, float bump = 1f)
        {
            var m = Create(normal != null ? MatKind.LitNormal : MatKind.Lit, name);
            m.SetColor("_BaseColor", tint);
            m.SetTexture("_BaseMap", albedo);
            if (normal != null) { m.SetTexture("_BumpMap", normal); m.SetFloat("_BumpScale", bump); }
            m.SetFloat("_Metallic", metallic);
            m.SetFloat("_Smoothness", smooth);
            tiling[m] = uvTiling;
            return m;
        }

        static Material Emissive(string name, Color baseCol, Texture2D emissionTex, Color emission, float smooth)
        {
            var m = Create(MatKind.LitEmissive, name);
            m.SetColor("_BaseColor", baseCol);
            if (emissionTex != null) { m.SetTexture("_EmissionMap", emissionTex); m.SetTexture("_BaseMap", emissionTex); }
            m.SetColor("_EmissionColor", emission);
            m.SetFloat("_Metallic", 0f);
            m.SetFloat("_Smoothness", smooth);
            return m;
        }

        static Material UnlitTr(string name, Texture2D tex, Color col, bool additive)
        {
            var m = Create(MatKind.UnlitTransparent, name);
            m.SetColor("_BaseColor", col);
            if (tex != null) m.SetTexture("_BaseMap", tex);
            if (additive)
            {
                m.SetFloat("_Blend", 2f);
                m.SetFloat("_DstBlend", (float)BlendMode.One);
                m.SetFloat("_DstBlendAlpha", (float)BlendMode.One);
            }
            return m;
        }

        public static void SetEmission(Material m, Color c) { m.SetColor("_EmissionColor", c); }

        // ---------- 构建全部材质 ----------
        public static void Build()
        {
            if (built && Carpet != null) return;
            built = true;
            tiling.Clear(); transparent.Clear();

            ProcTextures.Carpet(out var carpetA, out var carpetN);
            Carpet = LitTex("Carpet", carpetA, carpetN, Color.white, 0f, 0.06f, 0.5f, 1.1f);

            var plaster = ProcTextures.Plaster();
            Wall = LitTex("Wall", plaster, null, new Color(0.93f, 0.93f, 0.92f), 0f, 0.18f, 0.5f);
            // 吊顶朝下，直接光照不到；真实房间里它被地面与桌面的反弹光照亮，这里用可控的自发光模拟
            Ceiling = Emissive("Ceiling", new Color(0.95f, 0.95f, 0.94f), ProcTextures.CeilingTile(), Color.white * 0.4f, 0.08f);
            tiling[Ceiling] = 1f / 0.6f;
            Baseboard = Lit("Baseboard", new Color(0.16f, 0.16f, 0.17f), 0f, 0.35f);
            DoorDark = Lit("DoorDark", new Color(0.20f, 0.20f, 0.21f), 0.1f, 0.45f);
            WhiteMetal = Lit("WhiteMetal", new Color(0.90f, 0.90f, 0.90f), 0.2f, 0.55f);
            Bronze = Lit("Bronze", new Color(0.50f, 0.44f, 0.30f), 0.9f, 0.62f);
            Blind = Lit("Blind", new Color(0.90f, 0.90f, 0.88f), 0f, 0.15f);

            ProcTextures.Wood(new Color(0.82f, 0.68f, 0.47f), new Color(0.70f, 0.55f, 0.35f), 1, 6, out var deskA, out var deskN);
            DeskWood = LitTex("DeskWood", deskA, deskN, Color.white, 0f, 0.48f, 1.2f, 0.5f);
            ProcTextures.Wood(new Color(0.36f, 0.22f, 0.14f), new Color(0.20f, 0.11f, 0.07f), 2, 3, out var walA, out var walN);
            Walnut = LitTex("Walnut", walA, walN, Color.white, 0f, 0.55f, 1.5f, 0.6f);
            ProcTextures.Wood(new Color(0.80f, 0.69f, 0.52f), new Color(0.72f, 0.60f, 0.43f), 3, 2, out var cabA, out var cabN);
            CabinetWood = LitTex("CabinetWood", cabA, cabN, Color.white, 0f, 0.40f, 1.0f, 0.4f);
            DoorWood = LitTex("DoorWood", cabA, cabN, new Color(0.92f, 0.86f, 0.78f), 0f, 0.42f, 0.8f, 0.4f);

            ProcTextures.Fabric(new Color(0.74f, 0.75f, 0.76f), 0.16f, 4, 1, out var partA, out var partN);
            Partition = LitTex("Partition", partA, partN, Color.white, 0f, 0.10f, 6f, 0.8f);
            ProcTextures.Fabric(new Color(0.10f, 0.10f, 0.11f), 0.45f, 4, 2, out var chairA, out var chairN);
            ChairFabric = LitTex("ChairFabric", chairA, chairN, Color.white, 0f, 0.12f, 8f, 1f);
            ProcTextures.Fabric(new Color(0.07f, 0.07f, 0.08f), 0.75f, 8, 3, out var meshA, out var meshN);
            ChairMesh = LitTex("ChairMesh", meshA, meshN, Color.white, 0f, 0.22f, 10f, 1.4f);
            ProcTextures.Fabric(new Color(0.88f, 0.87f, 0.83f), 0.10f, 4, 4, out var cushA, out var cushN);
            Cushion = LitTex("Cushion", cushA, cushN, Color.white, 0f, 0.20f, 6f, 0.7f);

            BlackMetal = Lit("BlackMetal", new Color(0.035f, 0.035f, 0.04f), 0.85f, 0.50f);
            BlackPlastic = Lit("BlackPlastic", new Color(0.045f, 0.045f, 0.05f), 0f, 0.42f);
            Chrome = Lit("Chrome", new Color(0.78f, 0.79f, 0.80f), 1f, 0.86f);
            Bin = Lit("Bin", new Color(0.03f, 0.03f, 0.03f), 0.3f, 0.30f);
            MonitorBody = Lit("MonitorBody", new Color(0.03f, 0.03f, 0.035f), 0f, 0.38f);
            // 关闭的屏幕：深色玻璃，高光滑度 → 能反射顶灯与反射探针
            ScreenOff = Lit("ScreenOff", new Color(0.012f, 0.013f, 0.016f), 0.0f, 0.93f);
            TvScreen = Emissive("TvScreen", new Color(0.02f, 0.02f, 0.02f), ProcTextures.TvPanel(), Color.white * 0.9f, 0.9f);
            KeyboardTop = LitTex("KeyboardTop", ProcTextures.Keyboard(), null, Color.white, 0f, 0.35f, 1f);

            ScreenOn = new Material[4];
            for (int i = 0; i < 4; i++)
                ScreenOn[i] = Emissive("ScreenOn" + i, new Color(0.03f, 0.03f, 0.03f), ProcTextures.Screen(i), Color.white * 1.15f, 0.88f);

            LightPanel = Emissive("LightPanel", Color.white, null, Color.white * 3.2f, 0.3f);
            ExitSign = Emissive("ExitSign", new Color(0.1f, 0.1f, 0.1f), ProcTextures.ExitSign(), Color.white * 1.6f, 0.4f);

            SkylineDay = ProcTextures.Skyline(false);
            SkylineNight = ProcTextures.Skyline(true);
            Backdrop = Create(MatKind.Unlit, "Backdrop");
            Backdrop.SetTexture("_BaseMap", SkylineDay);
            Backdrop.SetColor("_BaseColor", Color.white);

            Glass = Create(MatKind.LitTransparent, "Glass");
            Glass.SetColor("_BaseColor", new Color(0.70f, 0.80f, 0.84f, 0.16f));
            Glass.SetFloat("_Metallic", 0f); Glass.SetFloat("_Smoothness", 0.96f);
            GlassDoor = Create(MatKind.LitTransparent, "GlassDoor");
            GlassDoor.SetColor("_BaseColor", new Color(0.62f, 0.72f, 0.74f, 0.30f));
            GlassDoor.SetFloat("_Metallic", 0f); GlassDoor.SetFloat("_Smoothness", 0.95f);

            Shaft = UnlitTr("Shaft", ProcTextures.LightShaft(), new Color(1f, 0.96f, 0.86f, 0f), true);
            ContactShadow = UnlitTr("ContactShadow", ProcTextures.SoftRect(64), new Color(0f, 0f, 0f, 0.34f), false);

            Paper = Lit("Paper", new Color(0.92f, 0.92f, 0.90f), 0f, 0.25f);
            RedPaint = Lit("RedPaint", new Color(0.72f, 0.07f, 0.06f), 0.3f, 0.62f);
            Plant = Lit("Plant", new Color(0.16f, 0.42f, 0.14f), 0f, 0.35f);
            Pot = Lit("Pot", new Color(0.86f, 0.86f, 0.84f), 0f, 0.55f);
            Ceramic = Lit("Ceramic", new Color(0.92f, 0.92f, 0.94f), 0f, 0.80f);
            BlueBox = Lit("BlueBox", new Color(0.20f, 0.25f, 0.70f), 0f, 0.40f);
            YellowBox = Lit("YellowBox", new Color(0.92f, 0.80f, 0.20f), 0f, 0.40f);
            PurpleBox = Lit("PurpleBox", new Color(0.62f, 0.50f, 0.80f), 0f, 0.30f);
            VentDark = Lit("VentDark", new Color(0.30f, 0.30f, 0.31f), 0.4f, 0.4f);
            InkArt = LitTex("InkArt", ProcTextures.InkArt(), null, Color.white, 0f, 0.30f, 1f);
            Notice = LitTex("Notice", ProcTextures.Notice(), null, Color.white, 0f, 0.35f, 1f);
            Posters = new[] { LitTex("Poster0", ProcTextures.Poster(0), null, Color.white, 0f, 0.35f, 1f), LitTex("Poster1", ProcTextures.Poster(1), null, Color.white, 0f, 0.35f, 1f) };

            Skin = Lit("Skin", new Color(0.86f, 0.66f, 0.54f), 0f, 0.38f);
            Hair = Lit("Hair", new Color(0.05f, 0.04f, 0.04f), 0f, 0.45f);
            Shirt = LitTex("Shirt", cushA, cushN, new Color(0.30f, 0.44f, 0.66f), 0f, 0.15f, 8f, 0.6f);
            Pants = LitTex("Pants", cushA, cushN, new Color(0.16f, 0.17f, 0.21f), 0f, 0.15f, 8f, 0.6f);
            Shoes = Lit("Shoes", new Color(0.90f, 0.90f, 0.90f), 0f, 0.45f);
        }
    }
}
