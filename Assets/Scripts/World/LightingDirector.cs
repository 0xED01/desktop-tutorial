using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.Universal;

namespace OfficeExplorer
{
    public enum TimeMode { Day, Evening, Night }

    /// <summary>
    /// 光照总控：白天 / 傍晚 / 夜间 × 顶灯开 / 关 共六种光照状态，带平滑过渡。
    /// · 顶灯开：主光为模拟顶灯阵列的竖直柔光（给人物与家具投射实时软阴影），
    ///   点光补光形成墙地面的明暗起伏，灯盘自发光 + Bloom。
    /// · 顶灯关：主光变为从窗户射入的阳光 / 夕阳 / 月光，屋面只投影，
    ///   地面出现窗格光斑，室内靠环境光与亮着的显示器照明。
    /// 同时管理后期 Volume（Bloom、色调映射、颜色校正、暗角、景深）与反射探针刷新。
    /// </summary>
    public class LightingDirector : MonoBehaviour
    {
        class State
        {
            public Quaternion mainRot; public Color mainColor; public float mainIntensity; public float shadowStrength;
            public Color ambSky, ambEquator, ambGround;
            public float fill; public Color fillColor;
            public float window; public Color windowColor;
            public Color panel; public Color backdrop; public float shaft; public float exposure; public float ceiling;
            public bool sun, night;

            public static void Lerp(State a, State b, float t, State r)
            {
                r.mainRot = Quaternion.Slerp(a.mainRot, b.mainRot, t);
                r.mainColor = Color.Lerp(a.mainColor, b.mainColor, t);
                // 主光在"顶灯 ↔ 日光"之间切换时先暗后亮，避免阴影方向扫过整个房间
                float dip = a.sun != b.sun ? Mathf.Abs(t * 2f - 1f) : 1f;
                r.mainIntensity = Mathf.Lerp(a.mainIntensity, b.mainIntensity, t) * dip;
                r.shadowStrength = Mathf.Lerp(a.shadowStrength, b.shadowStrength, t);
                r.ambSky = Color.Lerp(a.ambSky, b.ambSky, t); r.ambEquator = Color.Lerp(a.ambEquator, b.ambEquator, t); r.ambGround = Color.Lerp(a.ambGround, b.ambGround, t);
                r.fill = Mathf.Lerp(a.fill, b.fill, t); r.fillColor = Color.Lerp(a.fillColor, b.fillColor, t);
                r.window = Mathf.Lerp(a.window, b.window, t); r.windowColor = Color.Lerp(a.windowColor, b.windowColor, t);
                r.panel = Color.Lerp(a.panel, b.panel, t); r.backdrop = Color.Lerp(a.backdrop, b.backdrop, t);
                r.shaft = Mathf.Lerp(a.shaft, b.shaft, t); r.exposure = Mathf.Lerp(a.exposure, b.exposure, t); r.ceiling = Mathf.Lerp(a.ceiling, b.ceiling, t);
                r.sun = t < 0.5f ? a.sun : b.sun; r.night = t < 0.5f ? a.night : b.night;
                if (a.sun != b.sun) r.mainRot = t < 0.5f ? a.mainRot : b.mainRot;
            }
        }

        public TimeMode TimeOfDay { get; private set; } = TimeMode.Day;
        public bool LightsOn { get; private set; } = true;
        public static readonly string[] TimeNames = { "白天", "傍晚", "夜间" };

        OfficeScene scene;
        readonly State from = new State(), to = new State(), cur = new State();
        float blend = 1f;
        int fillLevel = 2;
        bool postOn = true;
        int probeCountdown;

        Volume volume;
        Bloom bloom; Tonemapping tonemap; ColorAdjustments color; Vignette vignette; DepthOfField dof;
        float dofWeight, dofTarget, dofStart = 9f, dofEnd = 24f;

        public static LightingDirector Create(OfficeScene s)
        {
            var go = new GameObject("LightingDirector");
            var d = go.AddComponent<LightingDirector>();
            d.scene = s;
            d.BuildVolume();
            RenderSettings.ambientMode = AmbientMode.Trilight;
            RenderSettings.fog = false;
            RenderSettings.skybox = null;
            RenderSettings.reflectionIntensity = 1f;
            d.Fill(d.to, d.TimeOfDay, d.LightsOn);
            State.Lerp(d.to, d.to, 1f, d.cur);
            d.Push(d.cur);
            d.probeCountdown = 2;
            return d;
        }

        void BuildVolume()
        {
            volume = gameObject.AddComponent<Volume>();
            volume.isGlobal = true;
            volume.priority = 10;
            var profile = ScriptableObject.CreateInstance<VolumeProfile>();
            profile.name = "OfficeRuntimeProfile";
            volume.sharedProfile = profile;

            bloom = profile.Add<Bloom>(true);
            bloom.threshold.value = 1.05f; bloom.intensity.value = 0.42f; bloom.scatter.value = 0.62f;
            bloom.highQualityFiltering.value = false; bloom.maxIterations.value = 5;

            tonemap = profile.Add<Tonemapping>(true);
            tonemap.mode.value = TonemappingMode.Neutral;

            color = profile.Add<ColorAdjustments>(true);
            color.postExposure.value = 0f; color.contrast.value = 8f; color.saturation.value = 4f;
            color.colorFilter.value = Color.white; color.hueShift.value = 0f;

            vignette = profile.Add<Vignette>(true);
            vignette.intensity.value = 0.20f; vignette.smoothness.value = 0.45f; vignette.color.value = Color.black;
            vignette.center.value = new Vector2(0.5f, 0.5f); vignette.rounded.value = false;

            dof = profile.Add<DepthOfField>(true);
            dof.mode.value = DepthOfFieldMode.Gaussian;
            dof.gaussianStart.value = dofStart; dof.gaussianEnd.value = dofEnd; dof.gaussianMaxRadius.value = 1f;
            dof.highQualitySampling.value = false;
            dof.active = false;
        }

        // ---------- 六种光照状态 ----------
        void Fill(State s, TimeMode time, bool on)
        {
            Quaternion overhead = Quaternion.Euler(80f, 25f, 0f);
            s.night = time == TimeMode.Night;
            s.sun = !on;
            s.fillColor = new Color(1f, 0.98f, 0.95f);
            if (on)
            {
                s.mainRot = overhead; s.mainColor = new Color(1f, 0.985f, 0.96f); s.mainIntensity = 0.55f; s.shadowStrength = 0.70f;
                s.fill = 1.6f; s.panel = Color.white * 3.2f; s.exposure = 0f; s.ceiling = 0.40f;
            }
            else { s.fill = 0f; s.panel = Color.white * 0.02f; }

            switch (time)
            {
                case TimeMode.Day:
                    s.windowColor = new Color(0.86f, 0.93f, 1f); s.backdrop = Color.white * 1.7f;
                    if (on)
                    {
                        s.ambSky = new Color(0.42f, 0.43f, 0.45f); s.ambEquator = new Color(0.44f, 0.45f, 0.47f); s.ambGround = new Color(0.26f, 0.26f, 0.27f);
                        s.window = 1.6f; s.shaft = 0.07f;
                    }
                    else
                    {
                        s.mainRot = Quaternion.Euler(34f, 18f, 0f); s.mainColor = new Color(1f, 0.96f, 0.88f); s.mainIntensity = 2.2f; s.shadowStrength = 0.85f;
                        s.ambSky = new Color(0.26f, 0.29f, 0.34f); s.ambEquator = new Color(0.24f, 0.26f, 0.30f); s.ambGround = new Color(0.15f, 0.15f, 0.16f);
                        s.window = 2.6f; s.shaft = 0.20f; s.exposure = 0.5f; s.ceiling = 0.10f;
                    }
                    break;
                case TimeMode.Evening:
                    s.windowColor = new Color(1f, 0.62f, 0.36f); s.backdrop = new Color(1.6f, 0.92f, 0.55f);
                    if (on)
                    {
                        s.ambSky = new Color(0.42f, 0.42f, 0.43f); s.ambEquator = new Color(0.44f, 0.43f, 0.43f); s.ambGround = new Color(0.26f, 0.25f, 0.25f);
                        s.window = 1.4f; s.shaft = 0.10f;
                    }
                    else
                    {
                        s.mainRot = Quaternion.Euler(15f, 30f, 0f); s.mainColor = new Color(1f, 0.56f, 0.28f); s.mainIntensity = 2.2f; s.shadowStrength = 0.88f;
                        s.ambSky = new Color(0.19f, 0.17f, 0.20f); s.ambEquator = new Color(0.19f, 0.15f, 0.14f); s.ambGround = new Color(0.10f, 0.08f, 0.08f);
                        s.window = 2.4f; s.shaft = 0.28f; s.exposure = 0.6f; s.ceiling = 0.06f;
                    }
                    break;
                default:
                    s.windowColor = new Color(0.50f, 0.62f, 1f); s.backdrop = Color.white * 1.15f;
                    if (on)
                    {
                        s.ambSky = new Color(0.41f, 0.42f, 0.44f); s.ambEquator = new Color(0.43f, 0.44f, 0.46f); s.ambGround = new Color(0.25f, 0.25f, 0.26f);
                        s.window = 0.10f; s.shaft = 0f;
                    }
                    else
                    {
                        s.mainRot = Quaternion.Euler(42f, -22f, 0f); s.mainColor = new Color(0.55f, 0.66f, 1f); s.mainIntensity = 0.40f; s.shadowStrength = 0.9f;
                        s.ambSky = new Color(0.040f, 0.046f, 0.066f); s.ambEquator = new Color(0.034f, 0.038f, 0.054f); s.ambGround = new Color(0.016f, 0.017f, 0.022f);
                        s.window = 0.40f; s.shaft = 0.03f; s.exposure = 1.1f; s.ceiling = 0.004f;
                    }
                    break;
            }
        }

        void Push(State s)
        {
            var main = scene.mainLight;
            main.transform.rotation = s.mainRot;
            main.color = s.mainColor;
            main.intensity = s.mainIntensity;
            main.shadowStrength = s.shadowStrength;
            if (scene.roofBlocker.activeSelf != s.sun) scene.roofBlocker.SetActive(s.sun);

            // 低灯光细节时补光数量减少，用环境光补偿整体亮度
            float ambBoost = !s.sun ? (fillLevel == 0 ? 0.22f : fillLevel == 1 ? 0.10f : 0f) : 0f;
            Color boost = new Color(ambBoost, ambBoost, ambBoost, 0f);
            RenderSettings.ambientSkyColor = s.ambSky + boost;
            RenderSettings.ambientEquatorColor = s.ambEquator + boost;
            RenderSettings.ambientGroundColor = s.ambGround + boost * 0.6f;

            for (int i = 0; i < scene.fillLights.Count; i++)
            {
                var l = scene.fillLights[i];
                bool active = s.fill > 0.01f && (fillLevel == 2 || (fillLevel == 1 && i % 2 == 0));
                if (l.enabled != active) l.enabled = active;
                l.intensity = s.fill * (fillLevel == 1 ? 1.5f : 1f);
                l.color = s.fillColor;
            }
            for (int i = 0; i < scene.windowLights.Count; i++)
            {
                var l = scene.windowLights[i];
                bool active = s.window > 0.05f && fillLevel > 0;
                if (l.enabled != active) l.enabled = active;
                l.intensity = s.window; l.color = s.windowColor;
            }

            Mats.SetEmission(Mats.LightPanel, s.panel);
            Mats.SetEmission(Mats.Ceiling, Color.white * s.ceiling);
            Mats.LightPanel.SetColor("_BaseColor", Color.Lerp(new Color(0.75f, 0.76f, 0.78f), Color.white, Mathf.Clamp01(s.panel.r)));
            Mats.Backdrop.SetColor("_BaseColor", s.backdrop);
            var tex = s.night ? Mats.SkylineNight : Mats.SkylineDay;
            if (Mats.Backdrop.GetTexture("_BaseMap") != tex) Mats.Backdrop.SetTexture("_BaseMap", tex);
            Mats.Shaft.SetColor("_BaseColor", new Color(s.windowColor.r, s.windowColor.g, s.windowColor.b, s.shaft));
            // 桌下接触阴影：顶灯开时较明显，关灯后减弱
            Mats.ContactShadow.SetColor("_BaseColor", new Color(0, 0, 0, s.sun ? 0.16f : 0.30f));
            if (color != null) color.postExposure.value = s.exposure;
        }

        public void SetTime(TimeMode t) { if (t != TimeOfDay) { TimeOfDay = t; Begin(); } }
        public void CycleTime() { TimeOfDay = (TimeMode)(((int)TimeOfDay + 1) % 3); Begin(); }
        public void SetLights(bool on) { if (on != LightsOn) { LightsOn = on; Begin(); } }
        public void ToggleLights() { LightsOn = !LightsOn; Begin(); }

        void Begin()
        {
            State.Lerp(cur, cur, 0f, from);
            CopyFlags(cur, from);
            Fill(to, TimeOfDay, LightsOn);
            blend = 0f;
        }

        static void CopyFlags(State a, State b) { b.sun = a.sun; b.night = a.night; }

        public void ApplyQuality(int shadowQuality, int lightDetail, bool post)
        {
            fillLevel = lightDetail; postOn = post;
            scene.mainLight.shadows = shadowQuality == 0 ? LightShadows.None : (shadowQuality == 1 ? LightShadows.Hard : LightShadows.Soft);
            bloom.active = post; vignette.active = post;
            Push(cur);
            probeCountdown = 2;
        }

        /// <summary>景深只用于主菜单环游镜头与查看屏幕的特写。</summary>
        public void SetDepthOfField(bool on, float start = 9f, float end = 24f)
        {
            dofTarget = on ? 1f : 0f;
            if (on) { dofStart = start; dofEnd = end; }
        }

        public void RefreshProbes() { probeCountdown = 2; }

        void Update()
        {
            float dt = Time.unscaledDeltaTime;
            if (blend < 1f)
            {
                blend = Mathf.Min(1f, blend + dt / 0.9f);
                State.Lerp(from, to, Mathf.SmoothStep(0f, 1f, blend), cur);
                Push(cur);
                if (blend >= 1f) probeCountdown = 2;
            }

            // 反射探针：只在光照变化后重渲染一次（分帧完成），平时零开销
            if (probeCountdown > 0 && --probeCountdown == 0)
                foreach (var p in scene.probes) p.RenderProbe();

            dofWeight = Mathf.MoveTowards(dofWeight, postOn ? dofTarget : 0f, dt * 2.5f);
            bool dofActive = dofWeight > 0.01f;
            if (dof.active != dofActive) dof.active = dofActive;
            if (dofActive)
            {
                dof.gaussianStart.value = Mathf.Lerp(dofStart * 4f, dofStart, dofWeight);
                dof.gaussianEnd.value = Mathf.Lerp(dofEnd * 4f, dofEnd, dofWeight);
                dof.gaussianMaxRadius.value = Mathf.Lerp(0.5f, 1.1f, dofWeight);
            }
        }
    }
}
