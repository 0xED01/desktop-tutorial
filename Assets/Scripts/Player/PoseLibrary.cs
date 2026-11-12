using UnityEngine;

namespace OfficeExplorer
{
    public enum AnimClip { Idle, Walk, Run, Jump, Air, Land, Sit }

    /// <summary>
    /// 动画数据的唯一来源。编辑器脚本把这里的姿态函数烘焙成 AnimationClip + AnimatorController；
    /// 运行时若找不到烘焙好的控制器，则直接采样同一组函数做程序动画。
    /// 约定：角色面向 +Z；绕 X 轴负角度 = 肢体向前摆。
    /// </summary>
    public static class PoseLibrary
    {
        public const float WalkSpeed = 1.7f, RunSpeed = 4.8f;

        public static float Length(AnimClip c)
        {
            switch (c)
            {
                case AnimClip.Idle: return 3.2f;
                case AnimClip.Walk: return 1.0f;
                case AnimClip.Run: return 0.64f;
                case AnimClip.Jump: return 0.22f;
                case AnimClip.Air: return 1.0f;
                case AnimClip.Land: return 0.32f;
                default: return 4.0f;
            }
        }

        public static bool Loops(AnimClip c) { return c != AnimClip.Jump && c != AnimClip.Land; }

        public static void Evaluate(AnimClip c, float t, Pose p)
        {
            p.Reset();
            t = Loops(c) ? Mathf.Repeat(t, 1f) : Mathf.Clamp01(t);
            float tau = Mathf.PI * 2f;
            switch (c)
            {
                case AnimClip.Idle:
                {
                    float br = Mathf.Sin(t * tau), sway = Mathf.Sin(t * tau * 0.5f);
                    p.hipsPos = new Vector3(sway * 0.006f, CharacterRig.HipsHeight - 0.004f + br * 0.003f, 0);
                    p.Set(Joint.Spine, 1.5f + br * 1.2f, sway * 1.5f, 0);
                    p.Set(Joint.Head, -1.5f - br * 0.8f, -sway * 3f, 0);
                    p.Set(Joint.ArmL, 3f + br * 1.0f, 0, -7f - br * 0.6f);
                    p.Set(Joint.ArmR, 3f + br * 1.0f, 0, 7f + br * 0.6f);
                    p.Set(Joint.ForeArmL, -12f - br, 0, 0);
                    p.Set(Joint.ForeArmR, -12f - br, 0, 0);
                    p.Set(Joint.LegL, -1f, 0, -2.5f); p.Set(Joint.LegR, -1f, 0, 2.5f);
                    p.Set(Joint.ShinL, 2f); p.Set(Joint.ShinR, 2f);
                    p.Set(Joint.FootL, -1f, -6f, 2.5f); p.Set(Joint.FootR, -1f, 6f, -2.5f);
                    break;
                }
                case AnimClip.Walk:
                    Locomotion(p, t, 27f, 46f, 24f, 14f, 12f, 0.022f, 0.012f, 3f, 5f, 7f);
                    break;
                case AnimClip.Run:
                    Locomotion(p, t, 42f, 92f, 46f, 78f, 10f, 0.05f, 0.035f, 11f, 8f, 12f);
                    break;
                case AnimClip.Jump:
                {
                    // 起跳：由微蹲迅速蹬伸，双臂上摆
                    float k = 1f - t;
                    p.hipsPos = new Vector3(0, CharacterRig.HipsHeight - 0.10f * k * k, 0);
                    Symmetric(p, -30f * k - 6f, 58f * k + 6f, -26f * k);
                    p.Set(Joint.Spine, 12f * k - 3f);
                    p.Set(Joint.ArmL, 28f * k - 55f * t, 0, -10f); p.Set(Joint.ArmR, 28f * k - 55f * t, 0, 10f);
                    p.Set(Joint.ForeArmL, -30f); p.Set(Joint.ForeArmR, -30f);
                    break;
                }
                case AnimClip.Air:
                {
                    float f = Mathf.Sin(t * tau) * 3f;
                    p.Set(Joint.Spine, 4f);
                    p.Set(Joint.Head, -4f);
                    p.Set(Joint.LegL, -24f + f); p.Set(Joint.ShinL, 38f);
                    p.Set(Joint.LegR, 6f - f); p.Set(Joint.ShinR, 30f);
                    p.Set(Joint.FootL, 14f); p.Set(Joint.FootR, 18f);
                    p.Set(Joint.ArmL, -18f - f, 0, -32f); p.Set(Joint.ArmR, -10f + f, 0, 32f);
                    p.Set(Joint.ForeArmL, -38f); p.Set(Joint.ForeArmR, -38f);
                    break;
                }
                case AnimClip.Land:
                {
                    // 落地缓冲：瞬间下蹲后回弹
                    float k = (1f - t) * (1f - t);
                    float impact = Mathf.Sin(Mathf.Clamp01(t * 3.5f) * Mathf.PI * 0.5f);
                    float a = k * impact;
                    p.hipsPos = new Vector3(0, CharacterRig.HipsHeight - 0.17f * a, 0);
                    Symmetric(p, -40f * a, 75f * a, -35f * a);
                    p.Set(Joint.Spine, 14f * a);
                    p.Set(Joint.ArmL, 14f * a, 0, -8f - 10f * a); p.Set(Joint.ArmR, 14f * a, 0, 8f + 10f * a);
                    p.Set(Joint.ForeArmL, -14f - 30f * a); p.Set(Joint.ForeArmR, -14f - 30f * a);
                    break;
                }
                case AnimClip.Sit:
                {
                    float br = Mathf.Sin(t * tau);
                    p.hipsPos = new Vector3(0, 0.57f + br * 0.002f, -0.04f);
                    p.Set(Joint.Spine, -5f + br * 1.0f);
                    p.Set(Joint.Head, 4f - br * 0.6f, Mathf.Sin(t * tau * 0.5f) * 5f, 0);
                    p.Set(Joint.LegL, -83f, 0, -4f); p.Set(Joint.LegR, -83f, 0, 4f);
                    p.Set(Joint.ShinL, 80f); p.Set(Joint.ShinR, 80f);
                    p.Set(Joint.FootL, 3f); p.Set(Joint.FootR, 3f);
                    p.Set(Joint.ArmL, -24f, 0, -6f); p.Set(Joint.ArmR, -24f, 0, 6f);
                    p.Set(Joint.ForeArmL, -52f, 0, 10f); p.Set(Joint.ForeArmR, -52f, 0, -10f);
                    break;
                }
            }
        }

        static void Symmetric(Pose p, float leg, float shin, float foot)
        {
            p.Set(Joint.LegL, leg); p.Set(Joint.LegR, leg);
            p.Set(Joint.ShinL, shin); p.Set(Joint.ShinR, shin);
            p.Set(Joint.FootL, foot); p.Set(Joint.FootR, foot);
        }

        /// <summary>走 / 跑共用的步态循环。t=0.25 时左腿摆到最前。</summary>
        static void Locomotion(Pose p, float t, float hipSwing, float kneeBend, float armSwing, float elbow, float elbowSwing,
                               float bob, float drop, float lean, float pelvisTwist, float chestTwist)
        {
            float tau = Mathf.PI * 2f;
            float s = Mathf.Sin(t * tau), c = Mathf.Cos(t * tau);
            p.hipsPos = new Vector3(0, CharacterRig.HipsHeight - drop - bob * (0.5f - 0.5f * Mathf.Cos(t * tau * 2f)), 0);
            p.Set(Joint.Hips, lean * 0.35f, pelvisTwist * s, 0);
            p.Set(Joint.Spine, lean * 0.65f, -(pelvisTwist + chestTwist) * s, 0);
            p.Set(Joint.Head, -lean * 0.6f, chestTwist * s * 0.8f, 0);

            // 左腿：c>0 为摆动相（屈膝向前迈），c<0 为支撑相
            float legL = -hipSwing * s, kneeL = 5f + kneeBend * Mathf.Pow(Mathf.Max(0f, c), 1.3f);
            float legR = hipSwing * s, kneeR = 5f + kneeBend * Mathf.Pow(Mathf.Max(0f, -c), 1.3f);
            p.Set(Joint.LegL, legL - kneeL * 0.35f); p.Set(Joint.ShinL, kneeL);
            p.Set(Joint.LegR, legR - kneeR * 0.35f); p.Set(Joint.ShinR, kneeR);
            p.Set(Joint.FootL, -(legL + kneeL * 0.65f) * 0.6f - lean * 0.35f);
            p.Set(Joint.FootR, -(legR + kneeR * 0.65f) * 0.6f - lean * 0.35f);

            // 手臂与同侧腿反向摆动
            p.Set(Joint.ArmL, armSwing * s, 0, -6f); p.Set(Joint.ArmR, -armSwing * s, 0, 6f);
            p.Set(Joint.ForeArmL, -(elbow + elbowSwing * Mathf.Max(0f, -s)));
            p.Set(Joint.ForeArmR, -(elbow + elbowSwing * Mathf.Max(0f, s)));
        }
    }
}
