using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.Universal;

namespace OfficeExplorer
{
    /// <summary>
    /// 画质 / 操作设置：预设（极高、高、中、低）+ 逐项自定义，PlayerPrefs 持久化，
    /// 运行时直接作用于 URP 管线资源、相机、SSAO 渲染特性与后期 Volume。
    /// </summary>
    public static class GameSettings
    {
        public static readonly string[] PresetNames = { "极高", "高", "中", "低", "自定义" };
        public static readonly string[] ShadowNames = { "关闭", "低", "中", "高" };
        public static readonly string[] AANames = { "关闭", "FXAA", "SMAA", "MSAA 2x", "MSAA 4x" };
        public static readonly string[] DetailNames = { "低", "中", "高" };
        public static readonly string[] OnOff = { "关闭", "开启" };
        public static readonly int[] FpsOptions = { 30, 60, 90, 120 };
        public static readonly string[] FpsNames = { "30", "60", "90", "120" };

        public static int Preset = 1;
        public static float RenderScale = 0.9f;
        public static int ShadowQuality = 2;
        public static int AA = 3;
        public static bool AO = true;
        public static bool Post = true;
        public static int LightDetail = 2;
        public static int FpsIndex = 1;
        public static bool ShowFps;
        public static float Sensitivity = 1f;
        public static bool InvertY;
        public static float Volume = 0.8f;

        public static event System.Action Changed;

        public static void ApplyPreset(int p)
        {
            Preset = p;
            switch (p)
            {
                case 0: RenderScale = 1.0f; ShadowQuality = 3; AA = 4; AO = true; Post = true; LightDetail = 2; break;
                case 1: RenderScale = 0.9f; ShadowQuality = 2; AA = 3; AO = true; Post = true; LightDetail = 2; break;
                case 2: RenderScale = 0.8f; ShadowQuality = 2; AA = 1; AO = false; Post = true; LightDetail = 1; break;
                case 3: RenderScale = 0.65f; ShadowQuality = 1; AA = 0; AO = false; Post = false; LightDetail = 0; break;
            }
        }

        public static void Load()
        {
            // 首次启动：按设备性能挑选默认预设
            int def = SystemInfo.graphicsMemorySize >= 6000 && SystemInfo.processorCount >= 8 ? 1 : (SystemInfo.graphicsMemorySize >= 3000 ? 2 : 3);
            if (Application.isEditor) def = 1;
            ApplyPreset(def);
            if (!PlayerPrefs.HasKey("gs_preset")) return;
            Preset = PlayerPrefs.GetInt("gs_preset", Preset);
            RenderScale = PlayerPrefs.GetFloat("gs_scale", RenderScale);
            ShadowQuality = PlayerPrefs.GetInt("gs_shadow", ShadowQuality);
            AA = PlayerPrefs.GetInt("gs_aa", AA);
            AO = PlayerPrefs.GetInt("gs_ao", AO ? 1 : 0) == 1;
            Post = PlayerPrefs.GetInt("gs_post", Post ? 1 : 0) == 1;
            LightDetail = PlayerPrefs.GetInt("gs_detail", LightDetail);
            FpsIndex = Mathf.Clamp(PlayerPrefs.GetInt("gs_fps", FpsIndex), 0, FpsOptions.Length - 1);
            ShowFps = PlayerPrefs.GetInt("gs_showfps", 0) == 1;
            Sensitivity = PlayerPrefs.GetFloat("gs_sens", Sensitivity);
            InvertY = PlayerPrefs.GetInt("gs_inverty", 0) == 1;
            Volume = PlayerPrefs.GetFloat("gs_volume", Volume);
        }

        public static void Save()
        {
            PlayerPrefs.SetInt("gs_preset", Preset);
            PlayerPrefs.SetFloat("gs_scale", RenderScale);
            PlayerPrefs.SetInt("gs_shadow", ShadowQuality);
            PlayerPrefs.SetInt("gs_aa", AA);
            PlayerPrefs.SetInt("gs_ao", AO ? 1 : 0);
            PlayerPrefs.SetInt("gs_post", Post ? 1 : 0);
            PlayerPrefs.SetInt("gs_detail", LightDetail);
            PlayerPrefs.SetInt("gs_fps", FpsIndex);
            PlayerPrefs.SetInt("gs_showfps", ShowFps ? 1 : 0);
            PlayerPrefs.SetFloat("gs_sens", Sensitivity);
            PlayerPrefs.SetInt("gs_inverty", InvertY ? 1 : 0);
            PlayerPrefs.SetFloat("gs_volume", Volume);
            PlayerPrefs.Save();
        }

        /// <summary>把当前设置应用到渲染管线与场景。</summary>
        public static void Apply(CameraRig cam, LightingDirector lighting, OfficeScene scene)
        {
            QualitySettings.vSyncCount = 0;
            Application.targetFrameRate = FpsOptions[FpsIndex];
            AudioListener.volume = Volume;
            QualitySettings.lodBias = LightDetail == 0 ? 0.7f : (LightDetail == 1 ? 1.0f : 1.6f);

            var urp = GraphicsSettings.currentRenderPipeline as UniversalRenderPipelineAsset;
            if (urp != null)
            {
                urp.renderScale = Mathf.Clamp(RenderScale, 0.5f, 1f);
                urp.msaaSampleCount = AA == 3 ? 2 : (AA == 4 ? 4 : 1);
                urp.supportsHDR = true;
                switch (ShadowQuality)
                {
                    case 1: urp.mainLightShadowmapResolution = 1024; urp.shadowCascadeCount = 1; urp.shadowDistance = 14f; break;
                    case 2: urp.mainLightShadowmapResolution = 2048; urp.shadowCascadeCount = 2; urp.shadowDistance = 22f; break;
                    case 3: urp.mainLightShadowmapResolution = 4096; urp.shadowCascadeCount = 2; urp.shadowDistance = 30f; break;
                    default: urp.shadowDistance = 0.01f; break;
                }
                // SSAO 渲染特性
                var list = urp.rendererDataList;
                for (int i = 0; i < list.Length; i++)
                {
                    if (list[i] == null) continue;
                    foreach (var f in list[i].rendererFeatures)
                        if (f is ScreenSpaceAmbientOcclusion) f.SetActive(AO);
                }
            }
            else
            {
                Debug.LogWarning("[办公室] 当前未启用 URP 管线资源，画质设置不会生效。请执行菜单「办公室探索/一键配置工程」。");
            }

            if (cam != null)
            {
                cam.CamData.antialiasing = AA == 1 ? AntialiasingMode.FastApproximateAntialiasing
                                         : AA == 2 ? AntialiasingMode.SubpixelMorphologicalAntiAliasing : AntialiasingMode.None;
                cam.CamData.antialiasingQuality = AntialiasingQuality.Medium;
                // 色调映射始终需要后期通道；"后期特效"开关只控制 Bloom / 暗角 / 景深
                cam.CamData.renderPostProcessing = true;
                cam.Cam.allowMSAA = AA >= 3;
            }

            if (lighting != null) lighting.ApplyQuality(ShadowQuality, LightDetail, Post);
            if (scene != null)
                foreach (var p in scene.probes) p.resolution = LightDetail == 2 ? 256 : 128;

            Changed?.Invoke();
        }
    }
}
