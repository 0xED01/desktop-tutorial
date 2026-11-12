using System;
using System.IO;
using UnityEditor;
using UnityEditor.Build.Reporting;
using UnityEngine;

namespace OfficeExplorer.EditorTools
{
    /// <summary>
    /// 安卓 APK 构建。
    /// 编辑器内：菜单「办公室探索/构建安卓 APK」。
    /// 命令行： Unity -batchmode -quit -projectPath . -buildTarget Android
    ///          -executeMethod OfficeExplorer.EditorTools.BuildScript.BuildAndroid
    /// </summary>
    public static class BuildScript
    {
        public const string DefaultOutput = "Builds/Android/OfficeExplorer.apk";

        [MenuItem("办公室探索/构建安卓 APK (ARM64)", priority = 40)]
        public static void BuildAndroidMenu()
        {
            bool ok = Build();
            if (!Application.isBatchMode)
                EditorUtility.DisplayDialog("办公室自由探索", ok ? "构建成功：\n" + Path.GetFullPath(DefaultOutput) : "构建失败，请查看 Console。", "好的");
        }

        /// <summary>命令行 / CI 入口。</summary>
        public static void BuildAndroid()
        {
            bool ok = Build();
            if (Application.isBatchMode) EditorApplication.Exit(ok ? 0 : 1);
        }

        static string OutputPath()
        {
            // 兼容 GameCI 传入的 -customBuildPath
            string[] args = Environment.GetCommandLineArgs();
            for (int i = 0; i < args.Length - 1; i++)
                if (args[i] == "-customBuildPath" && args[i + 1].EndsWith(".apk", StringComparison.OrdinalIgnoreCase)) return args[i + 1];
            return DefaultOutput;
        }

        static bool Build()
        {
            if (!BuildPipeline.IsBuildTargetSupported(BuildTargetGroup.Android, BuildTarget.Android))
            {
                Debug.LogError("[办公室] 当前 Unity 未安装 Android Build Support（含 SDK / NDK / OpenJDK），请在 Unity Hub 中添加该模块。");
                return false;
            }
            if (EditorUserBuildSettings.activeBuildTarget != BuildTarget.Android)
                EditorUserBuildSettings.SwitchActiveBuildTarget(BuildTargetGroup.Android, BuildTarget.Android);

            if (!ProjectSetup.RunAll())
            {
                Debug.LogError("[办公室] 模板材质生成失败（URP 着色器未就绪），构建中止。");
                return false;
            }
            EditorUserBuildSettings.buildAppBundle = false;
            EditorUserBuildSettings.androidBuildSystem = AndroidBuildSystem.Gradle;

            string output = OutputPath();
            Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(output)));
            var options = new BuildPlayerOptions
            {
                scenes = new[] { ProjectSetup.ScenePath },
                locationPathName = output,
                target = BuildTarget.Android,
                targetGroup = BuildTargetGroup.Android,
                options = BuildOptions.None
            };
            BuildReport report = BuildPipeline.BuildPlayer(options);
            var s = report.summary;
            Debug.Log("[办公室] 构建结果：" + s.result + "，输出：" + s.outputPath + "，大小：" + (s.totalSize / (1024f * 1024f)).ToString("0.0") + " MB，耗时：" + s.totalTime);
            return s.result == BuildResult.Succeeded;
        }
    }
}
