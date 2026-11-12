using System.IO;
using UnityEditor;
using UnityEditor.Animations;
using UnityEditor.Build;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.Universal;

namespace OfficeExplorer.EditorTools
{
    /// <summary>
    /// 工程自动配置。首次打开工程时自动执行一次，也可通过菜单「办公室探索」手动执行：
    ///  1. 生成 URP 模板材质（保证打包包含法线 / 发光 / 透明等着色器变体）
    ///  2. 把 PoseLibrary 的姿态函数烘焙为 AnimationClip，并生成 Animator 状态机
    ///  3. 写入安卓构建设置（ARM64 / IL2CPP / 横屏 / Vulkan+GLES3 / 线性空间）
    /// </summary>
    [InitializeOnLoad]
    public static class ProjectSetup
    {
        const string TemplateDir = "Assets/Resources/MatTemplates";
        const string AnimDir = "Assets/Animations";
        const string ControllerPath = "Assets/Resources/" + RigAnimator.ControllerResource + ".controller";
        const string PipelinePath = "Assets/Settings/Mobile_RPAsset.asset";
        public const string ScenePath = "Assets/Scenes/Main.unity";

        static ProjectSetup()
        {
            EditorApplication.delayCall += AutoRun;
        }

        static void AutoRun()
        {
            if (EditorApplication.isPlayingOrWillChangePlaymode || Application.isBatchMode) return;
            bool missing = !File.Exists(ControllerPath);
            foreach (MatKind k in System.Enum.GetValues(typeof(MatKind)))
                if (!File.Exists(TemplateDir + "/" + k + ".mat")) missing = true;
            if (missing) RunAll();
        }

        [MenuItem("办公室探索/一键配置工程（材质模板 + 动画 + 构建设置）", priority = 0)]
        public static void RunAllMenu()
        {
            if (RunAll()) EditorUtility.DisplayDialog("办公室自由探索", "配置完成。\n打开 Assets/Scenes/Main.unity 并点击 Play 即可运行；\n或使用菜单「办公室探索/构建安卓 APK」打包。", "好的");
        }

        public static bool RunAll()
        {
            EnsurePipeline();
            bool ok = CreateMaterialTemplates();
            BakeAnimations();
            ConfigurePlayer();
            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh();
            Debug.Log(ok ? "[办公室] 工程配置完成。" : "[办公室] 着色器尚未导入完成，稍后请重新执行「一键配置工程」。");
            return ok;
        }

        static void EnsureFolder(string path)
        {
            if (AssetDatabase.IsValidFolder(path)) return;
            string parent = Path.GetDirectoryName(path).Replace('\\', '/');
            EnsureFolder(parent);
            AssetDatabase.CreateFolder(parent, Path.GetFileName(path));
        }

        static void EnsurePipeline()
        {
            var asset = AssetDatabase.LoadAssetAtPath<UniversalRenderPipelineAsset>(PipelinePath);
            if (asset == null) { Debug.LogError("[办公室] 找不到 " + PipelinePath); return; }
            if (GraphicsSettings.defaultRenderPipeline != asset) GraphicsSettings.defaultRenderPipeline = asset;
            if (QualitySettings.renderPipeline != null && QualitySettings.renderPipeline != asset) QualitySettings.renderPipeline = asset;
        }

        // ---------- 1. 模板材质 ----------
        static bool CreateMaterialTemplates()
        {
            var lit = Shader.Find(Mats.LitShader);
            var unlit = Shader.Find(Mats.UnlitShader);
            if (lit == null || unlit == null) return false;
            EnsureFolder(TemplateDir);
            foreach (MatKind k in System.Enum.GetValues(typeof(MatKind)))
            {
                string path = TemplateDir + "/" + k + ".mat";
                var existing = AssetDatabase.LoadAssetAtPath<Material>(path);
                if (existing != null) AssetDatabase.DeleteAsset(path);
                var m = new Material(k >= MatKind.Unlit ? unlit : lit) { name = k.ToString() };
                Mats.Configure(m, k);
                m.enableInstancing = true;
                AssetDatabase.CreateAsset(m, path);
            }
            return true;
        }

        // ---------- 2. 动画烘焙 + Animator 状态机 ----------
        static AnimationClip BakeClip(AnimClip kind)
        {
            float len = PoseLibrary.Length(kind);
            bool loop = PoseLibrary.Loops(kind);
            int frames = Mathf.Max(4, Mathf.CeilToInt(len * 30f));
            var clip = new AnimationClip { name = kind.ToString(), frameRate = 30f };

            var pos = new AnimationCurve[3];
            for (int i = 0; i < 3; i++) pos[i] = new AnimationCurve();
            var rot = new AnimationCurve[CharacterRig.JointCount, 4];
            for (int j = 0; j < CharacterRig.JointCount; j++)
                for (int c = 0; c < 4; c++) rot[j, c] = new AnimationCurve();

            var pose = new Pose();
            var last = new Quaternion[CharacterRig.JointCount];
            for (int f = 0; f <= frames; f++)
            {
                float n = (float)f / frames, time = n * len;
                PoseLibrary.Evaluate(kind, loop && f == frames ? 0f : n, pose);
                pos[0].AddKey(time, pose.hipsPos.x); pos[1].AddKey(time, pose.hipsPos.y); pos[2].AddKey(time, pose.hipsPos.z);
                for (int j = 0; j < CharacterRig.JointCount; j++)
                {
                    Quaternion q = pose.rot[j];
                    if (f > 0 && Quaternion.Dot(q, last[j]) < 0f) q = new Quaternion(-q.x, -q.y, -q.z, -q.w);
                    last[j] = q;
                    rot[j, 0].AddKey(time, q.x); rot[j, 1].AddKey(time, q.y); rot[j, 2].AddKey(time, q.z); rot[j, 3].AddKey(time, q.w);
                }
            }

            string hips = CharacterRig.Paths[0];
            clip.SetCurve(hips, typeof(Transform), "localPosition.x", pos[0]);
            clip.SetCurve(hips, typeof(Transform), "localPosition.y", pos[1]);
            clip.SetCurve(hips, typeof(Transform), "localPosition.z", pos[2]);
            string[] comp = { "localRotation.x", "localRotation.y", "localRotation.z", "localRotation.w" };
            for (int j = 0; j < CharacterRig.JointCount; j++)
                for (int c = 0; c < 4; c++)
                    clip.SetCurve(CharacterRig.Paths[j], typeof(Transform), comp[c], rot[j, c]);
            clip.EnsureQuaternionContinuity();

            var settings = AnimationUtility.GetAnimationClipSettings(clip);
            settings.loopTime = loop;
            AnimationUtility.SetAnimationClipSettings(clip, settings);

            string path = AnimDir + "/" + kind + ".anim";
            if (AssetDatabase.LoadAssetAtPath<AnimationClip>(path) != null) AssetDatabase.DeleteAsset(path);
            AssetDatabase.CreateAsset(clip, path);
            return clip;
        }

        static AnimatorStateTransition Link(AnimatorState from, AnimatorState to, float duration, bool exitTime, float exitAt = 0.9f)
        {
            var t = from.AddTransition(to);
            t.hasExitTime = exitTime; t.exitTime = exitAt; t.duration = duration; t.hasFixedDuration = true;
            t.interruptionSource = TransitionInterruptionSource.None;
            return t;
        }

        static void BakeAnimations()
        {
            EnsureFolder(AnimDir);
            EnsureFolder("Assets/Resources");
            var idle = BakeClip(AnimClip.Idle); var walk = BakeClip(AnimClip.Walk); var run = BakeClip(AnimClip.Run);
            var jump = BakeClip(AnimClip.Jump); var air = BakeClip(AnimClip.Air); var land = BakeClip(AnimClip.Land); var sit = BakeClip(AnimClip.Sit);

            if (AssetDatabase.LoadAssetAtPath<AnimatorController>(ControllerPath) != null) AssetDatabase.DeleteAsset(ControllerPath);
            var ctrl = AnimatorController.CreateAnimatorControllerAtPath(ControllerPath);
            ctrl.AddParameter("Speed", AnimatorControllerParameterType.Float);
            ctrl.AddParameter("Grounded", AnimatorControllerParameterType.Bool);
            ctrl.AddParameter("Sitting", AnimatorControllerParameterType.Bool);
            ctrl.AddParameter("Jump", AnimatorControllerParameterType.Trigger);
            var ps = ctrl.parameters;
            for (int i = 0; i < ps.Length; i++) if (ps[i].name == "Grounded") ps[i].defaultBool = true;
            ctrl.parameters = ps;

            var sm = ctrl.layers[0].stateMachine;
            // 站立 / 行走 / 奔跑：一维混合树，由 Speed 参数驱动
            var loco = ctrl.CreateBlendTreeInController("Locomotion", out BlendTree tree, 0);
            tree.blendType = BlendTreeType.Simple1D;
            tree.blendParameter = "Speed";
            tree.useAutomaticThresholds = false;
            tree.AddChild(idle, 0f);
            tree.AddChild(walk, PoseLibrary.WalkSpeed);
            tree.AddChild(run, PoseLibrary.RunSpeed);
            sm.defaultState = loco;

            var sJump = sm.AddState("Jump"); sJump.motion = jump;
            var sAir = sm.AddState("Air"); sAir.motion = air;
            var sLand = sm.AddState("Land"); sLand.motion = land;
            var sSit = sm.AddState("Sit"); sSit.motion = sit;

            Link(loco, sJump, 0.05f, false).AddCondition(AnimatorConditionMode.If, 0, "Jump");
            Link(loco, sSit, 0.30f, false).AddCondition(AnimatorConditionMode.If, 0, "Sitting");
            Link(loco, sAir, 0.16f, false).AddCondition(AnimatorConditionMode.IfNot, 0, "Grounded");
            Link(sJump, sAir, 0.10f, true, 0.85f);
            Link(sAir, sLand, 0.05f, false).AddCondition(AnimatorConditionMode.If, 0, "Grounded");
            Link(sLand, sJump, 0.05f, false).AddCondition(AnimatorConditionMode.If, 0, "Jump");
            Link(sLand, loco, 0.14f, true, 0.80f);
            Link(sSit, loco, 0.30f, false).AddCondition(AnimatorConditionMode.IfNot, 0, "Sitting");
            EditorUtility.SetDirty(ctrl);
        }

        // ---------- 3. 构建设置 ----------
        public static void ConfigurePlayer()
        {
            PlayerSettings.companyName = "OfficeExplorer";
            PlayerSettings.productName = "办公室自由探索";
            PlayerSettings.bundleVersion = "1.0.0";
            PlayerSettings.colorSpace = ColorSpace.Linear;
            PlayerSettings.defaultInterfaceOrientation = UIOrientation.AutoRotation;
            PlayerSettings.allowedAutorotateToPortrait = false;
            PlayerSettings.allowedAutorotateToPortraitUpsideDown = false;
            PlayerSettings.allowedAutorotateToLandscapeLeft = true;
            PlayerSettings.allowedAutorotateToLandscapeRight = true;
            PlayerSettings.SetApplicationIdentifier(NamedBuildTarget.Android, "com.officeexplorer.freeroam");
            PlayerSettings.SetScriptingBackend(NamedBuildTarget.Android, ScriptingImplementation.IL2CPP);
            PlayerSettings.Android.targetArchitectures = AndroidArchitecture.ARM64;
            PlayerSettings.Android.minSdkVersion = AndroidSdkVersions.AndroidApiLevel26;
            PlayerSettings.SetUseDefaultGraphicsAPIs(BuildTarget.Android, false);
            PlayerSettings.SetGraphicsAPIs(BuildTarget.Android, new[] { GraphicsDeviceType.Vulkan, GraphicsDeviceType.OpenGLES3 });
            EditorBuildSettings.scenes = new[] { new EditorBuildSettingsScene(ScenePath, true) };
        }

        // ---------- 编辑器内预览 ----------
        const string PreviewName = "办公室预览（不随场景保存）";

        [MenuItem("办公室探索/在场景视图中预览办公室", priority = 20)]
        public static void Preview()
        {
            ClearPreview();
            var root = new GameObject(PreviewName);
            var scene = OfficeBuilder.Build(root.transform);
            scene.mainLight.transform.rotation = Quaternion.Euler(80f, 25f, 0f);
            scene.mainLight.intensity = 1f;
            scene.roofBlocker.SetActive(false);
            foreach (var l in scene.fillLights) l.intensity = 2.3f;
            foreach (var l in scene.windowLights) l.intensity = 2.6f;
            foreach (var t in root.GetComponentsInChildren<Transform>(true)) t.gameObject.hideFlags = HideFlags.DontSave;
            Selection.activeGameObject = root;
            if (SceneView.lastActiveSceneView != null) SceneView.lastActiveSceneView.FrameSelected();
        }

        [MenuItem("办公室探索/清除预览", priority = 21)]
        public static void ClearPreview()
        {
            var old = GameObject.Find(PreviewName);
            if (old != null) Object.DestroyImmediate(old);
        }
    }
}
