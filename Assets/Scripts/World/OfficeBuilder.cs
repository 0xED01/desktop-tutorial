using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Rendering;

namespace OfficeExplorer
{
    public struct SeatInfo { public Vector3 seatPos; public float yaw; public Vector3 standPos; }
    public struct ScreenInfo { public Vector3 center; public Vector3 normal; }
    public struct SwitchInfo { public Vector3 pos; public Vector3 normal; }

    /// <summary>场景构建结果：供灯光、交互、角色系统使用。</summary>
    public class OfficeScene
    {
        public Transform root;
        public readonly List<SeatInfo> seats = new List<SeatInfo>();
        public readonly List<ScreenInfo> screens = new List<ScreenInfo>();
        public readonly List<SwitchInfo> switches = new List<SwitchInfo>();
        public readonly List<Light> fillLights = new List<Light>();
        public readonly List<Light> windowLights = new List<Light>();
        public readonly List<ReflectionProbe> probes = new List<ReflectionProbe>();
        public readonly List<LODGroup> detailLods = new List<LODGroup>();
        public Light mainLight;
        public GameObject roofBlocker;
        public Vector3 spawnPos;
        public float spawnYaw;
        public Bounds bounds;
    }

    /// <summary>
    /// 依据视频关键帧重建的开放式办公室。
    /// 坐标系：X 为房间长向（27.5m），Z 为进深（18.6m），窗墙位于 Z=0，层高 2.8m。
    /// 尺寸由画面透视与常见办公家具规格推断（工位 1.4m×0.7m、桌高 0.75m、地毯块 0.5m）。
    /// </summary>
    public static class OfficeBuilder
    {
        public const float L = 27.5f, D = 18.6f, H = 2.8f;
        const float SeatW = 1.4f, BenchHalf = 0.7f, DeskTop = 0.75f;
        static readonly float[] RowX = { 3.6f, 7.6f, 11.6f, 15.6f, 19.6f, 23.6f };
        static readonly float[] ColX = { 5.6f, 13.6f, 21.6f };
        static readonly float[] ColZ = { 1.5f, 11.3f };
        const float BlockAZ = 4.6f, BlockBZ = 12.4f;
        const int BlockASeats = 4, BlockBSeats = 3;

        static System.Random rnd;
        static OfficeScene scene;
        static Transform colliderRoot;

        static float R() { return (float)rnd.NextDouble(); }
        static float R(float a, float b) { return a + (b - a) * (float)rnd.NextDouble(); }
        static Quaternion Yaw(float deg) { return Quaternion.Euler(0, deg, 0); }

        public static OfficeScene Build(Transform parent)
        {
            Mats.Build();
            rnd = new System.Random(20240547);
            scene = new OfficeScene();
            var root = new GameObject("Office").transform;
            root.SetParent(parent, false);
            scene.root = root;
            scene.bounds = new Bounds(new Vector3(L / 2, H / 2, D / 2), new Vector3(L, H, D));
            colliderRoot = new GameObject("Colliders").transform;
            colliderRoot.SetParent(root, false);

            BuildShell(root);
            BuildWindowWall(root);
            BuildNorthWallDecor(root);
            BuildSideWallDecor(root);
            BuildCeilingFixtures(root);
            foreach (float x in RowX)
            {
                BuildBench(root, x, BlockAZ, BlockASeats);
                BuildBench(root, x, BlockBZ, BlockBSeats);
            }
            BuildLounge(root);
            BuildLights(root);
            BuildProbes(root);

            // 出生点：视频拍摄者所在位置——靠窗立柱旁、休闲圆桌边
            scene.spawnPos = new Vector3(4.2f, 0.05f, 3.0f);
            scene.spawnYaw = 55f;
            var result = scene;
            scene = null;
            return result;
        }

        // ------------------------------------------------------------------
        static void AddBoxCollider(Vector3 center, Vector3 size, float yaw = 0f)
        {
            var go = new GameObject("Col");
            go.transform.SetParent(colliderRoot, false);
            go.transform.localPosition = center;
            go.transform.localRotation = Yaw(yaw);
            go.AddComponent<BoxCollider>().size = size;
        }

        static void ColMinMax(Vector3 min, Vector3 max) { AddBoxCollider((min + max) * 0.5f, max - min); }

        // ------------------------------------------------------------------
        // 建筑外壳：地毯、吊顶、三面实墙、立柱、踢脚线
        static void BuildShell(Transform root)
        {
            var b = new GeoBatch();
            b.BoxMinMax(Mats.Carpet, new Vector3(-0.2f, -0.2f, -0.3f), new Vector3(L + 0.2f, 0f, D + 0.2f));
            b.BoxMinMax(Mats.Wall, new Vector3(-0.2f, 0, D), new Vector3(L + 0.2f, H, D + 0.2f));          // 北墙
            b.BoxMinMax(Mats.Wall, new Vector3(-0.2f, 0, -0.3f), new Vector3(0, H, D));                     // 西墙
            b.BoxMinMax(Mats.Wall, new Vector3(L, 0, -0.3f), new Vector3(L + 0.2f, H, D));                  // 东墙
            // 踢脚线
            b.BoxMinMax(Mats.Baseboard, new Vector3(0, 0, D - 0.012f), new Vector3(L, 0.08f, D));
            b.BoxMinMax(Mats.Baseboard, new Vector3(0, 0, 0.45f), new Vector3(0.012f, 0.08f, D));
            b.BoxMinMax(Mats.Baseboard, new Vector3(L - 0.012f, 0, 0.45f), new Vector3(L, 0.08f, D));
            b.BoxMinMax(Mats.Baseboard, new Vector3(0, 0, 0.45f), new Vector3(L, 0.08f, 0.462f));

            foreach (float cx in ColX)
                foreach (float cz in ColZ)
                {
                    b.BoxMinMax(Mats.Wall, new Vector3(cx - 0.4f, 0, cz - 0.4f), new Vector3(cx + 0.4f, H, cz + 0.4f));
                    b.BoxMinMax(Mats.Baseboard, new Vector3(cx - 0.412f, 0, cz - 0.412f), new Vector3(cx + 0.412f, 0.08f, cz + 0.412f));
                    ColMinMax(new Vector3(cx - 0.4f, 0, cz - 0.4f), new Vector3(cx + 0.4f, H, cz + 0.4f));
                }
            b.Build("Shell", root, true);

            // 吊顶单独成批：顶灯主光模式下不投射阴影
            var c = new GeoBatch();
            c.BoxMinMax(Mats.Ceiling, new Vector3(-0.2f, H, -0.3f), new Vector3(L + 0.2f, H + 0.1f, D + 0.2f));
            // 回风口、喷淋头、监控半球
            for (int i = 0; i < 6; i++)
                for (int j = 0; j < 3; j++)
                {
                    Vector3 p = new Vector3(2.95f + i * 4.8f, H - 0.006f, 3.1f + j * 4.8f);
                    c.Box(Mats.VentDark, p, new Vector3(0.58f, 0.012f, 0.58f));
                    c.Box(Mats.WhiteMetal, p + new Vector3(0, -0.004f, 0), new Vector3(0.62f, 0.008f, 0.04f));
                    c[Mats.Chrome].Cylinder(new Vector3(p.x + 1.2f, H - 0.05f, p.z), 0.02f, 0.012f, 0.05f, 8, Quaternion.identity);
                }
            c[Mats.BlackPlastic].Ellipsoid(new Vector3(9.2f, H, 6.0f), new Vector3(0.07f, 0.07f, 0.07f), 12, 6, Quaternion.identity);
            c[Mats.BlackPlastic].Ellipsoid(new Vector3(18.4f, H, 13.0f), new Vector3(0.07f, 0.07f, 0.07f), 12, 6, Quaternion.identity);
            c.Build("Ceiling", root, false);

            // 只投影不可见的屋面：阳光 / 月光作为主光时阻挡来自上方的光线，只让光从窗户进入
            var roof = new GeoBatch();
            roof.BoxMinMax(Mats.Wall, new Vector3(-1f, H + 0.02f, -0.3f), new Vector3(L + 1f, H + 0.3f, D + 1f));
            roof.BoxMinMax(Mats.Wall, new Vector3(-1f, -0.2f, D + 0.2f), new Vector3(L + 1f, H + 0.3f, D + 0.5f));
            var roofGo = roof.Build("RoofShadowBlocker", root, true);
            foreach (var mr in roofGo.GetComponentsInChildren<MeshRenderer>()) mr.shadowCastingMode = ShadowCastingMode.ShadowsOnly;
            scene.roofBlocker = roofGo;

            // 碰撞：加厚的墙体与地面，防止高速穿透或掉出地图
            ColMinMax(new Vector3(-2, -1, -2), new Vector3(L + 2, 0, D + 2));
            ColMinMax(new Vector3(-2, H, -2), new Vector3(L + 2, H + 1, D + 2));
            ColMinMax(new Vector3(-2, 0, D), new Vector3(L + 2, H, D + 2));
            ColMinMax(new Vector3(-2, 0, -2), new Vector3(0, H, D + 2));
            ColMinMax(new Vector3(L, 0, -2), new Vector3(L + 2, H, D + 2));
            ColMinMax(new Vector3(-2, 0, -2), new Vector3(L + 2, H, 0.45f));
        }

        // ------------------------------------------------------------------
        // 窗墙（Z=0）：白色窗台矮墙 + 古铜色金属窗框 + 玻璃 + 半放下的卷帘 + 窗外远景
        static void BuildWindowWall(Transform root)
        {
            const float sill = 0.9f, head = 2.45f;
            var b = new GeoBatch();
            b.BoxMinMax(Mats.Wall, new Vector3(-0.2f, 0, -0.3f), new Vector3(L + 0.2f, sill, 0.45f));
            b.BoxMinMax(Mats.Bronze, new Vector3(0, sill, 0.0f), new Vector3(L, sill + 0.015f, 0.47f));
            b.BoxMinMax(Mats.Wall, new Vector3(-0.2f, head, -0.3f), new Vector3(L + 0.2f, H, 0.12f));

            var piers = new List<float> { 0.2f };
            foreach (float cx in ColX) piers.Add(cx);
            piers.Add(L - 0.2f);
            for (int i = 0; i < piers.Count; i++)
            {
                float half = (i == 0 || i == piers.Count - 1) ? 0.2f : 0.3f;
                b.BoxMinMax(Mats.Wall, new Vector3(piers[i] - half, sill, -0.3f), new Vector3(piers[i] + half, head, 0.12f));
                if (i == piers.Count - 1) break;
                float x0 = piers[i] + half, x1 = piers[i + 1] - ((i + 1 == piers.Count - 1) ? 0.2f : 0.3f);
                int n = Mathf.Max(1, Mathf.RoundToInt((x1 - x0) / 1.3f));
                float w = (x1 - x0) / n;
                b.BoxMinMax(Mats.Bronze, new Vector3(x0, head - 0.07f, -0.06f), new Vector3(x1, head, 0.10f));
                b.BoxMinMax(Mats.Bronze, new Vector3(x0, sill + 0.015f, -0.06f), new Vector3(x1, sill + 0.07f, 0.10f));
                for (int k = 0; k <= n; k++)
                {
                    float mx = x0 + k * w;
                    b.BoxMinMax(Mats.Bronze, new Vector3(mx - 0.055f, sill, -0.06f), new Vector3(mx + 0.055f, head, 0.12f));
                    if (k == n) break;
                    float drop = R(0.30f, 0.85f);
                    b.BoxMinMax(Mats.Blind, new Vector3(mx + 0.07f, head - 0.07f - drop, 0.045f), new Vector3(mx + w - 0.07f, head - 0.07f, 0.06f));
                    b.BoxMinMax(Mats.WhiteMetal, new Vector3(mx + 0.07f, head - 0.09f - drop, 0.035f), new Vector3(mx + w - 0.07f, head - 0.07f - drop, 0.07f));
                }
                // 低成本"体积光"：从窗口斜射到地面的加色渐变面片
                Vector3 top = new Vector3((x0 + x1) * 0.5f, head - 0.1f, 0.15f), bottom = new Vector3((x0 + x1) * 0.5f, 0.02f, 3.3f);
                Vector3 up = (top - bottom).normalized;
                b[Mats.Shaft].Face((top + bottom) * 0.5f, Vector3.Cross(up, Vector3.right), up, x1 - x0 - 0.2f, (top - bottom).magnitude);
            }
            b[Mats.Glass].Face(new Vector3(L / 2, (sill + head) / 2, -0.02f), Vector3.forward, Vector3.up, L, head - sill);
            b.Build("WindowWall", root, true);
            Mats.Shaft.SetFloat("_Cull", 0f);

            var bd = new GeoBatch();
            bd[Mats.Backdrop].Face(new Vector3(L / 2, 5.5f, -16f), Vector3.forward, Vector3.up, 96f, 24f, new Rect(0, 0, 3, 1));
            bd.Build("Backdrop", root, false, false);

            // 靠窗的红色消防器材箱（见视频末尾）
            var p = new GeoBatch();
            foreach (float x in new[] { 9.6f, 17.6f, 25.4f })
            {
                p.BoxMinMax(Mats.RedPaint, new Vector3(x - 0.21f, 0, 0.47f), new Vector3(x + 0.21f, 0.62f, 0.69f));
                p.BoxMinMax(Mats.YellowBox, new Vector3(x - 0.05f, 0.40f, 0.69f), new Vector3(x + 0.05f, 0.44f, 0.70f));
                ColMinMax(new Vector3(x - 0.21f, 0, 0.45f), new Vector3(x + 0.21f, 0.62f, 0.69f));
            }
            // 角落的大型绿植
            AddPlant(p, new Vector3(0.55f, 0, 0.95f), 1.0f);
            AddPlant(p, new Vector3(L - 0.6f, 0, D - 0.6f), 1.15f);
            AddPlant(p, new Vector3(0.55f, 0, D - 0.6f), 0.9f);
            p.Build("WindowProps", root, true);
        }

        static void AddPlant(GeoBatch b, Vector3 pos, float scale)
        {
            b[Mats.Pot].Cylinder(pos, 0.17f * scale, 0.22f * scale, 0.40f * scale, 14, Quaternion.identity);
            for (int i = 0; i < 9; i++)
            {
                float a = i * 0.7f + R() * 0.5f, tilt = R(8f, 38f), len = R(0.5f, 0.95f) * scale;
                Quaternion q = Quaternion.Euler(0, a * Mathf.Rad2Deg, tilt);
                Vector3 baseP = pos + new Vector3(0, 0.38f * scale, 0);
                b[Mats.Plant].Limb(baseP, baseP + q * Vector3.up * len * 0.6f, 0.008f, 0.006f, 5, false);
                b[Mats.Plant].Ellipsoid(baseP + q * Vector3.up * len * 0.8f, new Vector3(0.07f, 0.24f, 0.015f) * scale, 8, 5, q);
            }
            AddBoxCollider(pos + new Vector3(0, 0.5f * scale, 0), new Vector3(0.45f, 1.0f, 0.45f) * scale);
        }

        // ------------------------------------------------------------------
        // 北墙：双联电视、两扇带出口指示灯的门、提示牌、书法横幅、玻璃门、木色吊柜与台面
        static void BuildNorthWallDecor(Transform root)
        {
            var b = new GeoBatch();
            Vector3 n = Vector3.back, up = Vector3.up;
            float z = D;

            foreach (float x in new[] { 8.25f, 9.78f })
            {
                b.Box(Mats.MonitorBody, new Vector3(x, 1.72f, z - 0.03f), new Vector3(1.50f, 0.87f, 0.05f));
                b[Mats.TvScreen].Face(new Vector3(x, 1.72f, z - 0.057f), n, up, 1.45f, 0.82f);
            }

            foreach (float x in new[] { 13.2f, 15.0f })
            {
                b.Box(Mats.DoorDark, new Vector3(x, 1.25f, z - 0.02f), new Vector3(1.06f, 2.5f, 0.04f));
                b.Box(Mats.WhiteMetal, new Vector3(x, 1.04f, z - 0.05f), new Vector3(0.88f, 2.06f, 0.03f));
                b[Mats.Chrome].Limb(new Vector3(x + 0.33f, 1.0f, z - 0.10f), new Vector3(x + 0.22f, 1.0f, z - 0.10f), 0.012f, 0.012f, 8);
                b.Box(Mats.Paper, new Vector3(x - 0.05f, 1.55f, z - 0.067f), new Vector3(0.20f, 0.12f, 0.004f));
                b.Box(Mats.MonitorBody, new Vector3(x, 2.60f, z - 0.05f), new Vector3(0.37f, 0.15f, 0.06f));
                b[Mats.ExitSign].Face(new Vector3(x, 2.60f, z - 0.082f), n, up, 0.35f, 0.13f);
            }
            AddSwitch(b, new Vector3(15.85f, 1.30f, z), n);

            b[Mats.Notice].Face(new Vector3(17.1f, 1.55f, z - 0.004f), n, up, 0.42f, 0.32f);

            b.Box(Mats.Walnut, new Vector3(19.3f, 1.75f, z - 0.015f), new Vector3(1.72f, 0.58f, 0.03f));
            b[Mats.InkArt].Face(new Vector3(19.3f, 1.75f, z - 0.031f), n, up, 1.64f, 0.50f);

            // 玻璃门（门后做深色处理以模拟走廊的暗部与反射）
            b.Box(Mats.DoorDark, new Vector3(21.9f, 1.15f, z - 0.01f), new Vector3(1.10f, 2.3f, 0.02f));
            b[Mats.GlassDoor].Face(new Vector3(21.9f, 1.10f, z - 0.06f), n, up, 0.96f, 2.14f);
            b.Box(Mats.Chrome, new Vector3(21.9f, 2.22f, z - 0.06f), new Vector3(1.04f, 0.05f, 0.05f));
            b.Box(Mats.Chrome, new Vector3(21.40f, 1.12f, z - 0.06f), new Vector3(0.05f, 2.24f, 0.05f));
            b.Box(Mats.Chrome, new Vector3(22.40f, 1.12f, z - 0.06f), new Vector3(0.05f, 2.24f, 0.05f));
            b[Mats.Chrome].Limb(new Vector3(22.22f, 0.85f, z - 0.10f), new Vector3(22.22f, 1.35f, z - 0.10f), 0.014f, 0.014f, 8);

            // 茶水 / 打印区：吊柜 + 地柜
            float x0 = 23.4f, x1 = 26.3f;
            b.BoxMinMax(Mats.CabinetWood, new Vector3(x0, 1.50f, z - 0.36f), new Vector3(x1, 2.15f, z));
            b.BoxMinMax(Mats.CabinetWood, new Vector3(x0, 0.10f, z - 0.58f), new Vector3(x1, 0.84f, z));
            b.BoxMinMax(Mats.Baseboard, new Vector3(x0 + 0.03f, 0, z - 0.54f), new Vector3(x1 - 0.03f, 0.10f, z));
            b.BoxMinMax(Mats.WhiteMetal, new Vector3(x0 - 0.01f, 0.84f, z - 0.60f), new Vector3(x1 + 0.01f, 0.87f, z));
            for (int i = 1; i < 4; i++)
            {
                float gx = x0 + (x1 - x0) * i / 4f;
                b.BoxMinMax(Mats.Baseboard, new Vector3(gx - 0.003f, 1.51f, z - 0.362f), new Vector3(gx + 0.003f, 2.14f, z - 0.36f));
                b.BoxMinMax(Mats.Baseboard, new Vector3(gx - 0.003f, 0.11f, z - 0.582f), new Vector3(gx + 0.003f, 0.83f, z - 0.58f));
            }
            // 打印机与饮水设备
            b.BoxMinMax(Mats.Paper, new Vector3(23.7f, 0.87f, z - 0.52f), new Vector3(24.25f, 1.17f, z - 0.08f));
            b.BoxMinMax(Mats.MonitorBody, new Vector3(23.72f, 1.17f, z - 0.50f), new Vector3(24.23f, 1.20f, z - 0.10f));
            b.BoxMinMax(Mats.MonitorBody, new Vector3(25.3f, 0.87f, z - 0.45f), new Vector3(25.62f, 1.25f, z - 0.12f));
            b[Mats.Ceramic].Cylinder(new Vector3(24.8f, 0.87f, z - 0.3f), 0.045f, 0.10f, 12);
            ColMinMax(new Vector3(x0, 0, z - 0.6f), new Vector3(x1, 2.15f, z));

            b.Build("NorthWall", root, true);
        }

        static void AddSwitch(GeoBatch b, Vector3 wallPoint, Vector3 normal)
        {
            Vector3 right = Vector3.Cross(normal, Vector3.up);
            Quaternion q = Quaternion.LookRotation(normal, Vector3.up);
            b.Box(Mats.Paper, wallPoint + normal * 0.006f, new Vector3(0.18f, 0.09f, 0.012f), q);
            b.Box(Mats.WhiteMetal, wallPoint + normal * 0.014f - right * 0.04f, new Vector3(0.05f, 0.05f, 0.006f), q);
            b.Box(Mats.WhiteMetal, wallPoint + normal * 0.014f + right * 0.04f, new Vector3(0.05f, 0.05f, 0.006f), q);
            scene.switches.Add(new SwitchInfo { pos = wallPoint, normal = normal });
        }

        // ------------------------------------------------------------------
        // 东西两侧墙：公告海报、木门、白板
        static void BuildSideWallDecor(Transform root)
        {
            var b = new GeoBatch();
            // 西墙（X=0，法线 +X）
            for (int i = 0; i < 2; i++)
            {
                float z = 13.0f + i * 0.85f;
                b.Box(Mats.WhiteMetal, new Vector3(0.008f, 1.65f, z), new Vector3(0.016f, 0.96f, 0.66f));
                b[Mats.Posters[i]].Face(new Vector3(0.018f, 1.65f, z), Vector3.right, Vector3.up, 0.60f, 0.90f);
            }
            b.Box(Mats.DoorDark, new Vector3(0.015f, 1.10f, 7.6f), new Vector3(0.03f, 2.2f, 1.02f));
            b.Box(Mats.DoorWood, new Vector3(0.04f, 1.05f, 7.6f), new Vector3(0.03f, 2.06f, 0.90f));
            b[Mats.Chrome].Limb(new Vector3(0.09f, 1.0f, 7.92f), new Vector3(0.09f, 1.0f, 7.80f), 0.012f, 0.012f, 8);
            AddSwitch(b, new Vector3(0f, 1.30f, 8.45f), Vector3.right);

            // 东墙（X=L，法线 -X）
            b.Box(Mats.Chrome, new Vector3(L - 0.012f, 1.55f, 7.0f), new Vector3(0.024f, 1.26f, 2.46f));
            b.Box(Mats.Ceramic, new Vector3(L - 0.026f, 1.55f, 7.0f), new Vector3(0.006f, 1.20f, 2.40f));
            b.Box(Mats.Chrome, new Vector3(L - 0.05f, 0.94f, 7.0f), new Vector3(0.07f, 0.02f, 2.40f));
            b.Box(Mats.DoorDark, new Vector3(L - 0.015f, 1.10f, 14.6f), new Vector3(0.03f, 2.2f, 1.02f));
            b.Box(Mats.DoorWood, new Vector3(L - 0.04f, 1.05f, 14.6f), new Vector3(0.03f, 2.06f, 0.90f));
            b[Mats.Chrome].Limb(new Vector3(L - 0.09f, 1.0f, 14.28f), new Vector3(L - 0.09f, 1.0f, 14.40f), 0.012f, 0.012f, 8);
            AddSwitch(b, new Vector3(L, 1.30f, 13.75f), Vector3.left);
            b.Build("SideWalls", root, true);
        }

        // ------------------------------------------------------------------
        // 吊顶灯具：成排的双管格栅灯盘（发光材质 + Bloom）
        static void BuildCeilingFixtures(Transform root)
        {
            var b = new GeoBatch();
            for (float x = 1.75f; x < L - 1f; x += 2.4f)
                for (float z = 1.9f; z < D - 0.8f; z += 2.4f)
                {
                    b.Box(Mats.WhiteMetal, new Vector3(x, H - 0.008f, z), new Vector3(1.26f, 0.016f, 0.36f));
                    b.Box(Mats.LightPanel, new Vector3(x, H - 0.020f, z - 0.085f), new Vector3(1.18f, 0.012f, 0.125f));
                    b.Box(Mats.LightPanel, new Vector3(x, H - 0.020f, z + 0.085f), new Vector3(1.18f, 0.012f, 0.125f));
                }
            b.Build("CeilingFixtures", root, false, false);
        }

        // ------------------------------------------------------------------
        // 一组背靠背工位（沿 Z 方向排列 n 个座位，两侧各一排）
        static void BuildBench(Transform root, float cx, float z0, int n)
        {
            var b = new GeoBatch();        // 主体家具
            var d = new GeoBatch();        // 桌面小物件（带 LOD 剔除）
            float len = n * SeatW, z1 = z0 + len, zc = z0 + len * 0.5f;

            b.BoxMinMax(Mats.DeskWood, new Vector3(cx - BenchHalf, DeskTop - 0.03f, z0), new Vector3(cx + BenchHalf, DeskTop, z1));
            // 桌面中央的灰色织物屏风 + 座位之间的侧挡板
            b.Box(Mats.Partition, new Vector3(cx, DeskTop + 0.21f, zc), new Vector3(0.03f, 0.42f, len - 0.08f));
            for (int i = 1; i < n; i++)
                for (int s = -1; s <= 1; s += 2)
                    b.Box(Mats.Partition, new Vector3(cx + s * 0.40f, DeskTop + 0.18f, z0 + i * SeatW), new Vector3(0.56f, 0.36f, 0.026f));
            // 黑色钢制桌架
            for (int i = 0; i <= n; i++)
            {
                float z = Mathf.Clamp(z0 + i * SeatW, z0 + 0.06f, z1 - 0.06f);
                b.Box(Mats.BlackMetal, new Vector3(cx - 0.60f, 0.36f, z), new Vector3(0.05f, 0.72f, 0.07f));
                b.Box(Mats.BlackMetal, new Vector3(cx + 0.60f, 0.36f, z), new Vector3(0.05f, 0.72f, 0.07f));
                b.Box(Mats.BlackMetal, new Vector3(cx, 0.02f, z), new Vector3(1.36f, 0.04f, 0.07f));
                b.Box(Mats.BlackMetal, new Vector3(cx, 0.70f, z), new Vector3(1.25f, 0.04f, 0.05f));
            }
            b.Box(Mats.BlackMetal, new Vector3(cx, 0.62f, zc), new Vector3(0.22f, 0.05f, len - 0.2f));

            for (int i = 0; i < n; i++)
                for (int s = -1; s <= 1; s += 2)
                {
                    float sz = z0 + (i + 0.5f) * SeatW;
                    // 桌下黑色活动柜
                    b.Box(Mats.BlackPlastic, new Vector3(cx + s * 0.40f, 0.30f, sz + 0.44f), new Vector3(0.42f, 0.56f, 0.44f));
                    b.Box(Mats.BlackMetal, new Vector3(cx + s * 0.612f, 0.43f, sz + 0.44f), new Vector3(0.006f, 0.012f, 0.40f));
                    b.Box(Mats.BlackMetal, new Vector3(cx + s * 0.612f, 0.22f, sz + 0.44f), new Vector3(0.006f, 0.012f, 0.40f));

                    bool occupied = R() < 0.82f;
                    if (occupied)
                    {
                        bool on = R() < 0.42f;
                        int kind = rnd.Next(4);
                        Vector3 mp = new Vector3(cx + s * 0.20f, DeskTop, sz + R(-0.08f, 0.08f));
                        float myaw = s * 90f + R(-8f, 8f);
                        AddMonitor(b, mp, myaw, on, kind);
                        Vector3 kp = new Vector3(cx + s * 0.47f, DeskTop, mp.z + R(-0.04f, 0.04f));
                        d.Box(Mats.BlackPlastic, kp + new Vector3(0, 0.008f, 0), new Vector3(0.14f, 0.016f, 0.44f));
                        d[Mats.KeyboardTop].Face(kp + new Vector3(0, 0.0165f, 0), Vector3.up, s > 0 ? Vector3.left : Vector3.right, 0.43f, 0.13f);
                        Vector3 mouse = kp + new Vector3(0, 0, s * 0.33f);
                        if (R() < 0.4f) d.Box(Mats.BlueBox, mouse + new Vector3(0, 0.002f, 0), new Vector3(0.22f, 0.004f, 0.26f));
                        d[Mats.BlackPlastic].Ellipsoid(mouse + new Vector3(0, 0.018f, 0), new Vector3(0.032f, 0.018f, 0.052f), 8, 5, Yaw(s * 90f));
                        AddClutter(d, cx, s, sz);
                    }
                    // 办公椅：朝向桌面并带随机转角；个别椅子被拉出
                    bool pulled = R() < 0.18f;
                    Vector3 cp = new Vector3(cx + s * (BenchHalf + (pulled ? R(0.62f, 0.78f) : R(0.36f, 0.52f))), 0, sz + R(-0.16f, 0.16f));
                    float cyaw = -s * 90f + (pulled ? R(-75f, 75f) : R(-22f, 22f));
                    AddChair(b, cp, cyaw);
                    AddBoxCollider(cp + new Vector3(0, 0.46f, 0), new Vector3(0.52f, 0.92f, 0.52f), cyaw);
                    scene.seats.Add(new SeatInfo { seatPos = cp, yaw = cyaw, standPos = new Vector3(cx + s * 1.62f, 0.02f, cp.z) });
                    // 黑色网格垃圾桶
                    if ((i + (s > 0 ? 0 : 1)) % 2 == 0)
                        b[Mats.Bin].Cylinder(new Vector3(cx + s * 0.50f, 0, sz - 0.50f), 0.11f, 0.14f, 0.28f, 12, Quaternion.identity);
                }

            // 桌下柔和的接触阴影（模拟烘焙 AO）
            b[Mats.ContactShadow].Face(new Vector3(cx, 0.006f, zc), Vector3.up, Vector3.forward, 2.3f, len + 0.7f);

            var main = b.Build("Bench_" + cx + "_" + z0, root, true);
            var det = d.Build("Detail", main.transform, false);
            var lod = det.AddComponent<LODGroup>();
            lod.SetLODs(new[] { new LOD(0.045f, det.GetComponentsInChildren<Renderer>()) });
            lod.RecalculateBounds();
            scene.detailLods.Add(lod);

            // 碰撞：桌体（含桌下空间，防止钻桌底）+ 屏风与显示器区域
            ColMinMax(new Vector3(cx - BenchHalf, 0, z0), new Vector3(cx + BenchHalf, DeskTop, z1));
            ColMinMax(new Vector3(cx - 0.33f, DeskTop, z0 + 0.02f), new Vector3(cx + 0.33f, DeskTop + 0.46f, z1 - 0.02f));
        }

        // 24 寸显示器。pos 为桌面上的底座中心，yaw 为屏幕朝向。
        static void AddMonitor(GeoBatch b, Vector3 pos, float yaw, bool on, int kind)
        {
            Matrix4x4 keep = b.M;
            b.M = keep * Matrix4x4.TRS(pos, Yaw(yaw), Vector3.one);
            b.Box(Mats.MonitorBody, new Vector3(0, 0.008f, 0), new Vector3(0.24f, 0.016f, 0.18f));
            b.Box(Mats.MonitorBody, new Vector3(0, 0.12f, -0.03f), new Vector3(0.05f, 0.23f, 0.03f));
            b.Box(Mats.MonitorBody, new Vector3(0, 0.30f, 0), new Vector3(0.55f, 0.34f, 0.028f));
            b[on ? Mats.ScreenOn[kind] : Mats.ScreenOff].Face(new Vector3(0, 0.305f, 0.0145f), Vector3.forward, Vector3.up, 0.525f, 0.30f);
            b.M = keep;
            if (on)
            {
                Quaternion q = Yaw(yaw);
                scene.screens.Add(new ScreenInfo { center = pos + q * new Vector3(0, 0.305f, 0.0145f), normal = q * Vector3.forward });
            }
        }

        // 桌面杂物：文件、水杯、书、纸巾盒、小盆栽、电话、收纳盒……
        static void AddClutter(GeoBatch d, float cx, int s, float sz)
        {
            int count = rnd.Next(1, 5);
            for (int k = 0; k < count; k++)
            {
                Vector3 p = new Vector3(cx + s * R(0.10f, 0.60f), DeskTop, sz + (R() < 0.5f ? R(-0.62f, -0.36f) : R(0.36f, 0.62f)));
                float yaw = R(0, 360f);
                switch (rnd.Next(8))
                {
                    case 0:
                        for (int j = 0; j < 3; j++) d.Box(Mats.Paper, p + new Vector3(0, 0.004f + j * 0.008f, 0), new Vector3(0.21f, 0.008f, 0.297f), Yaw(yaw + j * 7f));
                        break;
                    case 1:
                        d[Mats.Ceramic].Cylinder(p, 0.038f, 0.042f, 0.095f, 12, Quaternion.identity);
                        break;
                    case 2:
                        d.Box(Mats.BlueBox, p + new Vector3(0, 0.02f, 0), new Vector3(0.16f, 0.04f, 0.23f), Yaw(yaw));
                        d.Box(Mats.PurpleBox, p + new Vector3(0, 0.055f, 0), new Vector3(0.15f, 0.03f, 0.22f), Yaw(yaw + 12f));
                        break;
                    case 3:
                        d.Box(Mats.PurpleBox, p + new Vector3(0, 0.04f, 0), new Vector3(0.22f, 0.08f, 0.12f), Yaw(yaw));
                        d.Box(Mats.Paper, p + new Vector3(0, 0.085f, 0), new Vector3(0.10f, 0.02f, 0.03f), Yaw(yaw));
                        break;
                    case 4:
                        d[Mats.Pot].Cylinder(p, 0.05f, 0.065f, 0.10f, 10, Quaternion.identity);
                        for (int j = 0; j < 5; j++)
                        {
                            Quaternion q = Quaternion.Euler(0, j * 72f + yaw, R(10f, 35f));
                            d[Mats.Plant].Ellipsoid(p + new Vector3(0, 0.10f, 0) + q * Vector3.up * 0.13f, new Vector3(0.03f, 0.13f, 0.008f), 6, 4, q);
                        }
                        break;
                    case 5:
                        d.Box(Mats.BlackPlastic, p + new Vector3(0, 0.03f, 0), new Vector3(0.17f, 0.06f, 0.21f), Yaw(s * 90f) * Quaternion.Euler(-12f, 0, 0));
                        d.Box(Mats.BlackPlastic, p + new Vector3(0, 0.07f, -s * 0.0f) + Yaw(s * 90f) * new Vector3(-0.06f, 0, 0), new Vector3(0.05f, 0.04f, 0.20f), Yaw(s * 90f));
                        break;
                    case 6:
                        d.Box(Mats.CabinetWood, p + new Vector3(0, 0.10f, 0), new Vector3(0.22f, 0.20f, 0.16f), Yaw(yaw));
                        break;
                    default:
                        d[Mats.YellowBox].Sphere(p + new Vector3(0, 0.05f, 0), 0.05f, 10, 6);
                        d[Mats.Ceramic].Cylinder(p + Yaw(yaw) * new Vector3(0.12f, 0, 0), 0.03f, 0.028f, 0.20f, 10, Quaternion.identity);
                        break;
                }
            }
        }

        // 黑色人体工学网椅。局部 +Z 为坐姿朝向，原点在地面。
        static void AddChair(GeoBatch b, Vector3 pos, float yaw)
        {
            Matrix4x4 keep = b.M;
            b.M = keep * Matrix4x4.TRS(pos, Yaw(yaw), Vector3.one);
            // 五星脚 + 万向轮
            for (int i = 0; i < 5; i++)
            {
                Quaternion q = Yaw(i * 72f + 18f);
                Vector3 tip = q * new Vector3(0, 0.055f, 0.30f);
                b[Mats.BlackPlastic].Limb(new Vector3(0, 0.10f, 0), tip, 0.022f, 0.016f, 6);
                b[Mats.BlackPlastic].Ellipsoid(new Vector3(tip.x, 0.03f, tip.z), new Vector3(0.028f, 0.03f, 0.03f), 8, 5, q);
            }
            b[Mats.Chrome].Cylinder(new Vector3(0, 0.09f, 0), 0.024f, 0.024f, 0.30f, 10, Quaternion.identity, false);
            b[Mats.BlackPlastic].Cylinder(new Vector3(0, 0.08f, 0), 0.04f, 0.03f, 0.10f, 10, Quaternion.identity);
            b.Box(Mats.BlackPlastic, new Vector3(0, 0.405f, -0.02f), new Vector3(0.22f, 0.05f, 0.26f));
            // 坐垫
            b.Box(Mats.ChairFabric, new Vector3(0, 0.465f, 0.01f), new Vector3(0.48f, 0.075f, 0.46f));
            b.Box(Mats.BlackPlastic, new Vector3(0, 0.432f, 0.01f), new Vector3(0.46f, 0.02f, 0.44f));
            // 靠背：后倾的网布 + 塑料边框 + 腰托
            Quaternion tilt = Quaternion.Euler(-9f, 0, 0);
            Vector3 backC = new Vector3(0, 0.80f, -0.245f);
            b.Box(Mats.ChairMesh, backC, new Vector3(0.43f, 0.52f, 0.018f), tilt);
            b.Box(Mats.BlackPlastic, backC + tilt * new Vector3(-0.225f, 0, 0), new Vector3(0.03f, 0.56f, 0.03f), tilt);
            b.Box(Mats.BlackPlastic, backC + tilt * new Vector3(0.225f, 0, 0), new Vector3(0.03f, 0.56f, 0.03f), tilt);
            b.Box(Mats.BlackPlastic, backC + tilt * new Vector3(0, 0.27f, 0), new Vector3(0.48f, 0.03f, 0.03f), tilt);
            b.Box(Mats.BlackPlastic, backC + tilt * new Vector3(0, -0.27f, 0), new Vector3(0.48f, 0.03f, 0.03f), tilt);
            b.Box(Mats.BlackPlastic, backC + tilt * new Vector3(0, -0.10f, -0.03f), new Vector3(0.30f, 0.08f, 0.02f), tilt);
            b[Mats.BlackPlastic].Limb(new Vector3(0, 0.41f, -0.12f), new Vector3(0, 0.62f, -0.29f), 0.028f, 0.022f, 6);
            // 扶手
            for (int s = -1; s <= 1; s += 2)
            {
                b.Box(Mats.BlackPlastic, new Vector3(s * 0.265f, 0.555f, -0.04f), new Vector3(0.028f, 0.20f, 0.045f));
                b.Box(Mats.BlackPlastic, new Vector3(s * 0.265f, 0.665f, 0.0f), new Vector3(0.07f, 0.025f, 0.25f));
            }
            b.M = keep;
        }

        // ------------------------------------------------------------------
        // 靠窗立柱旁的休闲洽谈区：深色木圆桌 + 米白软垫木椅
        static void BuildLounge(Transform root)
        {
            var b = new GeoBatch();
            for (int ci = 0; ci < ColX.Length; ci++)
            {
                Vector3 c = new Vector3(ColX[ci] + 1.45f, 0, 1.38f);
                b[Mats.Walnut].Cylinder(c + new Vector3(0, 0.70f, 0), 0.46f, 0.46f, 0.04f, 28, Quaternion.identity);
                b[Mats.Walnut].Cylinder(c + new Vector3(0, 0.04f, 0), 0.30f, 0.30f, 0.03f, 20, Quaternion.identity);
                for (int i = 0; i < 4; i++)
                {
                    Quaternion q = Yaw(i * 90f + 45f);
                    b[Mats.Walnut].Limb(c + q * new Vector3(0, 0.06f, 0.28f), c + q * new Vector3(0, 0.70f, 0.22f), 0.02f, 0.02f, 6, false);
                }
                AddBoxCollider(c + new Vector3(0, 0.37f, 0), new Vector3(0.74f, 0.74f, 0.74f), 45f);
                AddBoxCollider(c + new Vector3(0, 0.37f, 0), new Vector3(0.74f, 0.74f, 0.74f), 0f);

                float[] angles = { 10f, 80f, 140f };
                foreach (float a in angles)
                {
                    Vector3 cp = c + new Vector3(Mathf.Cos(a * Mathf.Deg2Rad), 0, Mathf.Sin(a * Mathf.Deg2Rad)) * 0.72f;
                    float yaw = 270f - a + R(-10f, 10f);               // 面向桌心
                    AddLoungeChair(b, cp, yaw);
                    AddBoxCollider(cp + new Vector3(0, 0.40f, 0), new Vector3(0.50f, 0.80f, 0.50f), yaw);
                    Vector3 fwd = Yaw(yaw) * Vector3.forward;
                    scene.seats.Add(new SeatInfo { seatPos = cp + new Vector3(0, -0.04f, 0), yaw = yaw, standPos = cp - fwd * 0.75f + new Vector3(0, 0.02f, 0) });
                }
                if (ci == 0)
                {
                    // 视频中圆桌上的书本与挎包
                    b.Box(Mats.BlueBox, c + new Vector3(0.10f, 0.76f, 0.08f), new Vector3(0.30f, 0.04f, 0.22f), Yaw(15f));
                    b.Box(Mats.PurpleBox, c + new Vector3(0.10f, 0.80f, 0.08f), new Vector3(0.28f, 0.04f, 0.21f), Yaw(22f));
                    b[Mats.Cushion].Ellipsoid(c + new Vector3(-0.18f, 0.83f, -0.05f), new Vector3(0.17f, 0.10f, 0.11f), 12, 8, Yaw(30f));
                }
            }
            b.Build("Lounge", root, true);
        }

        static void AddLoungeChair(GeoBatch b, Vector3 pos, float yaw)
        {
            Matrix4x4 keep = b.M;
            b.M = keep * Matrix4x4.TRS(pos, Yaw(yaw), Vector3.one);
            for (int sx = -1; sx <= 1; sx += 2)
            {
                b.Box(Mats.Walnut, new Vector3(sx * 0.22f, 0.19f, 0.20f), new Vector3(0.035f, 0.38f, 0.035f));
                b.Box(Mats.Walnut, new Vector3(sx * 0.22f, 0.38f, -0.21f), new Vector3(0.035f, 0.76f, 0.035f));
                b.Box(Mats.Walnut, new Vector3(sx * 0.22f, 0.36f, 0), new Vector3(0.035f, 0.035f, 0.44f));
                b.Box(Mats.Walnut, new Vector3(sx * 0.22f, 0.03f, 0), new Vector3(0.035f, 0.035f, 0.44f));
            }
            b.Box(Mats.Walnut, new Vector3(0, 0.36f, 0.20f), new Vector3(0.44f, 0.035f, 0.035f));
            b.Box(Mats.Walnut, new Vector3(0, 0.74f, -0.21f), new Vector3(0.47f, 0.04f, 0.04f));
            b.Box(Mats.Cushion, new Vector3(0, 0.425f, 0.0f), new Vector3(0.46f, 0.09f, 0.44f));
            b.Box(Mats.Cushion, new Vector3(0, 0.60f, -0.18f), new Vector3(0.46f, 0.24f, 0.07f), Quaternion.Euler(-6f, 0, 0));
            b.M = keep;
        }

        // ------------------------------------------------------------------
        static void BuildLights(Transform root)
        {
            var lights = new GameObject("Lights").transform;
            lights.SetParent(root, false);

            var main = new GameObject("MainLight").AddComponent<Light>();
            main.transform.SetParent(lights, false);
            main.type = LightType.Directional;
            main.shadows = LightShadows.Soft;
            main.shadowStrength = 0.72f;
            main.shadowBias = 0.03f;
            main.shadowNormalBias = 0.35f;
            scene.mainLight = main;

            // 顶灯补光：每 5 米左右一盏向下的无阴影广角聚光（不会把吊顶照出光斑），
            // 在墙面、桌面与地面上形成一池一池的明暗起伏
            for (int i = 0; i < 5; i++)
                for (int j = 0; j < 3; j++)
                {
                    var l = new GameObject("Fill_" + i + "_" + j).AddComponent<Light>();
                    l.transform.SetParent(lights, false);
                    l.transform.localPosition = new Vector3(2.95f + i * 5.4f, H - 0.06f, 3.1f + j * 6.2f);
                    l.transform.localRotation = Quaternion.Euler(90f, 0f, 0f);
                    l.type = LightType.Spot;
                    l.spotAngle = 150f;
                    l.innerSpotAngle = 95f;
                    l.range = 8f;
                    l.shadows = LightShadows.None;
                    scene.fillLights.Add(l);
                }

            // 窗边自然光
            foreach (float x in new[] { 3.0f, 9.6f, 17.6f, 24.6f })
            {
                var l = new GameObject("WindowLight").AddComponent<Light>();
                l.transform.SetParent(lights, false);
                // 位于窗口上沿、斜向下照入室内
                l.transform.localPosition = new Vector3(x, 2.25f, 0.35f);
                l.transform.localRotation = Quaternion.Euler(38f, 0f, 0f);
                l.type = LightType.Spot;
                l.spotAngle = 125f;
                l.innerSpotAngle = 70f;
                l.range = 9.5f;
                l.shadows = LightShadows.None;
                scene.windowLights.Add(l);
            }
        }

        static void BuildProbes(Transform root)
        {
            for (int i = 0; i < 2; i++)
            {
                var go = new GameObject("ReflectionProbe" + i);
                go.transform.SetParent(root, false);
                go.transform.localPosition = new Vector3(L * (0.25f + 0.5f * i), 1.45f, D * 0.5f);
                var p = go.AddComponent<ReflectionProbe>();
                p.mode = ReflectionProbeMode.Realtime;
                p.refreshMode = ReflectionProbeRefreshMode.ViaScripting;
                p.timeSlicingMode = ReflectionProbeTimeSlicingMode.IndividualFaces;
                p.size = new Vector3(L * 0.5f + 1.5f, H + 0.2f, D + 0.4f);
                p.boxProjection = true;
                p.blendDistance = 1.5f;
                p.resolution = 128;
                p.hdr = true;
                p.nearClipPlane = 0.2f;
                p.farClipPlane = 40f;
                p.shadowDistance = 0f;
                p.clearFlags = ReflectionProbeClearFlags.SolidColor;
                p.backgroundColor = Color.black;
                p.cullingMask = ~((1 << 2) | (1 << 5));   // 不把角色与 UI 拍进反射
                scene.probes.Add(p);
            }
        }
    }
}
