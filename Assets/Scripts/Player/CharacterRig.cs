using UnityEngine;
using UnityEngine.Rendering;

namespace OfficeExplorer
{
    public enum Joint { Hips, Spine, Head, ArmL, ForeArmL, ArmR, ForeArmR, LegL, ShinL, FootL, LegR, ShinR, FootR }

    /// <summary>
    /// 程序化人物：1.75m 身高、按真实人体比例分段的关节骨架（13 个关节），
    /// 每段为刚性网格（躯干、四肢、头、手、鞋），由 Animator 或程序动画驱动。
    /// </summary>
    public class CharacterRig : MonoBehaviour
    {
        public const int JointCount = 13;
        public const float HipsHeight = 0.93f;

        public static readonly string[] Paths =
        {
            "Hips", "Hips/Spine", "Hips/Spine/Head",
            "Hips/Spine/ArmL", "Hips/Spine/ArmL/ForeArmL",
            "Hips/Spine/ArmR", "Hips/Spine/ArmR/ForeArmR",
            "Hips/LegL", "Hips/LegL/ShinL", "Hips/LegL/ShinL/FootL",
            "Hips/LegR", "Hips/LegR/ShinR", "Hips/LegR/ShinR/FootR"
        };

        static readonly int[] ParentOf = { -1, 0, 1, 1, 3, 1, 5, 0, 7, 8, 0, 10, 11 };
        static readonly Vector3[] RestPos =
        {
            new Vector3(0, HipsHeight, 0), new Vector3(0, 0.06f, 0), new Vector3(0, 0.50f, 0),
            new Vector3(-0.195f, 0.44f, 0), new Vector3(0, -0.28f, 0),
            new Vector3(0.195f, 0.44f, 0), new Vector3(0, -0.28f, 0),
            new Vector3(-0.095f, -0.02f, 0), new Vector3(0, -0.43f, 0), new Vector3(0, -0.42f, 0),
            new Vector3(0.095f, -0.02f, 0), new Vector3(0, -0.43f, 0), new Vector3(0, -0.42f, 0)
        };

        public readonly Transform[] joints = new Transform[JointCount];
        Renderer[] renderers;

        public static Vector3 RestPosition(Joint j) { return RestPos[(int)j]; }

        public static CharacterRig Create(Transform parent)
        {
            Mats.Build();
            var go = new GameObject("Visual");
            go.transform.SetParent(parent, false);
            var rig = go.AddComponent<CharacterRig>();
            rig.BuildRig();
            return rig;
        }

        void BuildRig()
        {
            for (int i = 0; i < JointCount; i++)
            {
                string n = Paths[i].Substring(Paths[i].LastIndexOf('/') + 1);
                var t = new GameObject(n).transform;
                t.SetParent(ParentOf[i] < 0 ? transform : joints[ParentOf[i]], false);
                t.localPosition = RestPos[i];
                joints[i] = t;
            }

            Quaternion id = Quaternion.identity;
            // 骨盆
            var g = new GeoBatch();
            g[Mats.Pants].Ellipsoid(new Vector3(0, 0.02f, 0), new Vector3(0.165f, 0.115f, 0.115f), 16, 10, id);
            Attach(g, Joint.Hips);

            // 躯干：腹部、胸腔、肩线、脖子
            g = new GeoBatch();
            g[Mats.Shirt].Ellipsoid(new Vector3(0, 0.12f, 0), new Vector3(0.150f, 0.17f, 0.105f), 16, 10, id);
            g[Mats.Shirt].Ellipsoid(new Vector3(0, 0.33f, 0.005f), new Vector3(0.185f, 0.17f, 0.122f), 16, 10, id);
            g[Mats.Shirt].Ellipsoid(new Vector3(0, 0.44f, 0), new Vector3(0.20f, 0.065f, 0.085f), 14, 8, id);
            g[Mats.Skin].Cylinder(new Vector3(0, 0.46f, 0), 0.052f, 0.046f, 0.10f, 12, id, false);
            Attach(g, Joint.Spine);

            // 头部：脸、头发、鼻子、眼睛、耳朵
            g = new GeoBatch();
            g[Mats.Skin].Ellipsoid(new Vector3(0, 0.15f, 0.008f), new Vector3(0.092f, 0.118f, 0.102f), 18, 12, id);
            g[Mats.Hair].Ellipsoid(new Vector3(0, 0.178f, -0.014f), new Vector3(0.100f, 0.104f, 0.108f), 18, 12, id);
            g[Mats.Skin].Ellipsoid(new Vector3(0, 0.135f, 0.106f), new Vector3(0.013f, 0.02f, 0.016f), 8, 6, id);
            g[Mats.Hair].Sphere(new Vector3(-0.034f, 0.162f, 0.094f), 0.011f, 8, 6);
            g[Mats.Hair].Sphere(new Vector3(0.034f, 0.162f, 0.094f), 0.011f, 8, 6);
            g[Mats.Skin].Ellipsoid(new Vector3(-0.092f, 0.15f, 0.0f), new Vector3(0.012f, 0.028f, 0.018f), 8, 6, id);
            g[Mats.Skin].Ellipsoid(new Vector3(0.092f, 0.15f, 0.0f), new Vector3(0.012f, 0.028f, 0.018f), 8, 6, id);
            Attach(g, Joint.Head);

            foreach (var side in new[] { Joint.ArmL, Joint.ArmR })
            {
                g = new GeoBatch();
                g[Mats.Shirt].Sphere(Vector3.zero, 0.064f, 12, 8);
                g[Mats.Shirt].Limb(Vector3.zero, new Vector3(0, -0.28f, 0), 0.056f, 0.046f, 12, false);
                g[Mats.Shirt].Sphere(new Vector3(0, -0.28f, 0), 0.046f, 10, 8);
                Attach(g, side);

                g = new GeoBatch();
                g[Mats.Skin].Limb(new Vector3(0, -0.02f, 0), new Vector3(0, -0.25f, 0), 0.042f, 0.032f, 12, false);
                g[Mats.Skin].Ellipsoid(new Vector3(0, -0.305f, 0.004f), new Vector3(0.024f, 0.066f, 0.038f), 10, 8, id);
                Attach(g, side + 1);
            }

            foreach (var side in new[] { Joint.LegL, Joint.LegR })
            {
                g = new GeoBatch();
                g[Mats.Pants].Sphere(Vector3.zero, 0.088f, 12, 8);
                g[Mats.Pants].Limb(Vector3.zero, new Vector3(0, -0.43f, 0), 0.086f, 0.062f, 12, false);
                g[Mats.Pants].Sphere(new Vector3(0, -0.43f, 0), 0.062f, 10, 8);
                Attach(g, side);

                g = new GeoBatch();
                g[Mats.Pants].Limb(Vector3.zero, new Vector3(0, -0.42f, 0), 0.060f, 0.044f, 12, false);
                Attach(g, side + 1);

                g = new GeoBatch();
                g[Mats.Shoes].Ellipsoid(new Vector3(0, -0.022f, 0.055f), new Vector3(0.050f, 0.040f, 0.130f), 12, 8, id);
                g[Mats.Baseboard].Box(new Vector3(0, -0.052f, 0.055f), new Vector3(0.085f, 0.016f, 0.235f));
                g[Mats.Pants].Sphere(Vector3.zero, 0.046f, 10, 8);
                Attach(g, side + 2);
            }

            renderers = GetComponentsInChildren<Renderer>();
            foreach (var r in renderers)
            {
                r.gameObject.layer = 2;                      // Ignore Raycast：镜头碰撞检测忽略角色自身
                r.lightProbeUsage = LightProbeUsage.Off;
            }
        }

        void Attach(GeoBatch g, Joint j)
        {
            g.Build("Geo", joints[(int)j], true);
        }

        /// <summary>第一人称时隐藏身体但保留阴影。</summary>
        public void SetFirstPerson(bool fp)
        {
            foreach (var r in renderers)
                r.shadowCastingMode = fp ? ShadowCastingMode.ShadowsOnly : ShadowCastingMode.On;
        }

        public void ApplyPose(Pose p)
        {
            joints[0].localPosition = p.hipsPos;
            for (int i = 0; i < JointCount; i++) joints[i].localRotation = p.rot[i];
        }
    }

    /// <summary>一帧姿态：胯部位置 + 13 个关节的局部旋转。</summary>
    public class Pose
    {
        public Vector3 hipsPos = new Vector3(0, CharacterRig.HipsHeight, 0);
        public readonly Quaternion[] rot = new Quaternion[CharacterRig.JointCount];

        public Pose() { Reset(); }

        public void Reset()
        {
            hipsPos = new Vector3(0, CharacterRig.HipsHeight, 0);
            for (int i = 0; i < rot.Length; i++) rot[i] = Quaternion.identity;
        }

        public void Set(Joint j, float x, float y = 0f, float z = 0f) { rot[(int)j] = Quaternion.Euler(x, y, z); }

        public static void Lerp(Pose a, Pose b, float t, Pose result)
        {
            result.hipsPos = Vector3.LerpUnclamped(a.hipsPos, b.hipsPos, t);
            for (int i = 0; i < result.rot.Length; i++) result.rot[i] = Quaternion.SlerpUnclamped(a.rot[i], b.rot[i], t);
        }

        public void CopyFrom(Pose o)
        {
            hipsPos = o.hipsPos;
            for (int i = 0; i < rot.Length; i++) rot[i] = o.rot[i];
        }
    }
}
