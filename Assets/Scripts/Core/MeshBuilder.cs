using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Rendering;

namespace OfficeExplorer
{
    /// <summary>
    /// 程序化网格构建器：把大量盒体 / 圆柱 / 椭球累积进同一个 Mesh，
    /// 从源头上完成"静态合批"（同材质 = 一次 Draw Call）。
    /// </summary>
    public class MeshBuilder
    {
        readonly List<Vector3> verts = new List<Vector3>(4096);
        readonly List<Vector3> normals = new List<Vector3>(4096);
        readonly List<Vector2> uvs = new List<Vector2>(4096);
        readonly List<int> tris = new List<int>(8192);

        /// <summary>当前局部到批次空间的变换，后续加入的几何都会乘上它。</summary>
        public Matrix4x4 M = Matrix4x4.identity;
        /// <summary>每米对应多少个 UV 单位（用于按真实尺寸平铺贴图）。</summary>
        public float uvScale = 1f;

        public int VertexCount => verts.Count;

        int V(Vector3 p, Vector3 n, Vector2 uv)
        {
            verts.Add(M.MultiplyPoint3x4(p));
            normals.Add(M.MultiplyVector(n).normalized);
            uvs.Add(uv);
            return verts.Count - 1;
        }

        void Tri(int a, int b, int c) { tris.Add(a); tris.Add(b); tris.Add(c); }

        static readonly Vector3[] FaceN = { Vector3.up, Vector3.down, Vector3.right, Vector3.left, Vector3.forward, Vector3.back };
        static readonly Vector3[] FaceU = { Vector3.right, Vector3.forward, Vector3.forward, Vector3.up, Vector3.up, Vector3.right };
        static readonly Vector3[] FaceV = { Vector3.forward, Vector3.right, Vector3.up, Vector3.forward, Vector3.right, Vector3.up };

        /// <summary>轴对齐（可旋转）盒体，UV 按世界尺寸平铺。</summary>
        public void Box(Vector3 center, Vector3 size, Quaternion rot)
        {
            Vector3 h = size * 0.5f;
            for (int f = 0; f < 6; f++)
            {
                Vector3 n = FaceN[f], u = FaceU[f], v = FaceV[f];
                float hn = Mathf.Abs(Vector3.Dot(n, h)), hu = Mathf.Abs(Vector3.Dot(u, h)), hv = Mathf.Abs(Vector3.Dot(v, h));
                Vector3 c = n * hn;
                Vector3 p0 = c - u * hu - v * hv, p1 = c - u * hu + v * hv, p2 = c + u * hu + v * hv, p3 = c + u * hu - v * hv;
                Vector3 wn = rot * n;
                int i0 = V(center + rot * p0, wn, BoxUV(center + p0, f));
                int i1 = V(center + rot * p1, wn, BoxUV(center + p1, f));
                int i2 = V(center + rot * p2, wn, BoxUV(center + p2, f));
                int i3 = V(center + rot * p3, wn, BoxUV(center + p3, f));
                Tri(i0, i1, i2); Tri(i0, i2, i3);
            }
        }

        public void Box(Vector3 center, Vector3 size) { Box(center, size, Quaternion.identity); }

        /// <summary>用最小 / 最大角描述的盒体。</summary>
        public void BoxMinMax(Vector3 min, Vector3 max) { Box((min + max) * 0.5f, max - min, Quaternion.identity); }

        Vector2 BoxUV(Vector3 p, int face)
        {
            if (face < 2) return new Vector2(p.x, p.z) * uvScale;      // 顶 / 底
            if (face < 4) return new Vector2(p.z, p.y) * uvScale;      // ±X
            return new Vector2(p.x, p.y) * uvScale;                    // ±Z
        }

        /// <summary>朝向 normal 的矩形面片，UV 完整铺满 uvRect（用于屏幕、挂画、标识）。</summary>
        public void Face(Vector3 center, Vector3 normal, Vector3 up, float width, float height, Rect uvRect)
        {
            normal.Normalize(); up.Normalize();
            Vector3 r = Vector3.Cross(normal, up) * (width * 0.5f);
            Vector3 u = up * (height * 0.5f);
            int bl = V(center - r - u, normal, new Vector2(uvRect.xMin, uvRect.yMin));
            int tl = V(center - r + u, normal, new Vector2(uvRect.xMin, uvRect.yMax));
            int tr = V(center + r + u, normal, new Vector2(uvRect.xMax, uvRect.yMax));
            int br = V(center + r - u, normal, new Vector2(uvRect.xMax, uvRect.yMin));
            Tri(bl, tl, tr); Tri(bl, tr, br);
        }

        public void Face(Vector3 center, Vector3 normal, Vector3 up, float width, float height)
        {
            Face(center, normal, up, width, height, new Rect(0, 0, 1, 1));
        }

        /// <summary>沿 rot*Y 方向的圆柱 / 圆台，baseCenter 为底面圆心。</summary>
        public void Cylinder(Vector3 baseCenter, float r0, float r1, float height, int seg, Quaternion rot, bool caps = true)
        {
            int start = verts.Count;
            float slope = (r0 - r1) / Mathf.Max(height, 1e-4f);
            for (int i = 0; i <= seg; i++)
            {
                float a = i * Mathf.PI * 2f / seg;
                float cx = Mathf.Cos(a), sz = Mathf.Sin(a);
                Vector3 n = rot * new Vector3(cx, slope, sz).normalized;
                float u = (float)i / seg;
                V(baseCenter + rot * new Vector3(cx * r0, 0, sz * r0), n, new Vector2(u * 2f, 0));
                V(baseCenter + rot * new Vector3(cx * r1, height, sz * r1), n, new Vector2(u * 2f, height * uvScale));
            }
            for (int i = 0; i < seg; i++)
            {
                int b0 = start + i * 2, t0 = b0 + 1, b1 = b0 + 2, t1 = b0 + 3;
                Tri(b0, t0, t1); Tri(b0, t1, b1);
            }
            if (!caps) return;
            if (r1 > 1e-4f) Disc(baseCenter + rot * new Vector3(0, height, 0), r1, seg, rot, true);
            if (r0 > 1e-4f) Disc(baseCenter, r0, seg, rot, false);
        }

        public void Cylinder(Vector3 baseCenter, float radius, float height, int seg = 12)
        {
            Cylinder(baseCenter, radius, radius, height, seg, Quaternion.identity, true);
        }

        /// <summary>连接两点的圆台（肢体、椅腿等）。</summary>
        public void Limb(Vector3 from, Vector3 to, float r0, float r1, int seg = 10, bool caps = true)
        {
            Vector3 d = to - from;
            float len = d.magnitude;
            if (len < 1e-5f) return;
            Cylinder(from, r0, r1, len, seg, Quaternion.FromToRotation(Vector3.up, d / len), caps);
        }

        public void Disc(Vector3 center, float radius, int seg, Quaternion rot, bool faceUp)
        {
            Vector3 n = rot * (faceUp ? Vector3.up : Vector3.down);
            int c = V(center, n, new Vector2(0.5f, 0.5f));
            int first = verts.Count;
            for (int i = 0; i <= seg; i++)
            {
                float a = i * Mathf.PI * 2f / seg;
                float cx = Mathf.Cos(a), sz = Mathf.Sin(a);
                V(center + rot * new Vector3(cx * radius, 0, sz * radius), n, new Vector2(0.5f + cx * 0.5f, 0.5f + sz * 0.5f));
            }
            for (int i = 0; i < seg; i++)
            {
                if (faceUp) Tri(c, first + i + 1, first + i);
                else Tri(c, first + i, first + i + 1);
            }
        }

        /// <summary>椭球（经纬网格）。</summary>
        public void Ellipsoid(Vector3 center, Vector3 radii, int seg, int rings, Quaternion rot)
        {
            int start = verts.Count;
            for (int j = 0; j <= rings; j++)
            {
                float th = j * Mathf.PI / rings;
                float y = Mathf.Cos(th), rr = Mathf.Sin(th);
                for (int i = 0; i <= seg; i++)
                {
                    float a = i * Mathf.PI * 2f / seg;
                    Vector3 unit = new Vector3(Mathf.Cos(a) * rr, y, Mathf.Sin(a) * rr);
                    Vector3 p = Vector3.Scale(unit, radii);
                    Vector3 n = new Vector3(unit.x / radii.x, unit.y / radii.y, unit.z / radii.z).normalized;
                    V(center + rot * p, rot * n, new Vector2((float)i / seg, 1f - (float)j / rings));
                }
            }
            int w = seg + 1;
            for (int j = 0; j < rings; j++)
                for (int i = 0; i < seg; i++)
                {
                    int a = start + j * w + i, b = a + w, c = b + 1, d = a + 1;
                    Tri(a, c, b); Tri(a, d, c);
                }
        }

        public void Sphere(Vector3 center, float radius, int seg = 12, int rings = 8)
        {
            Ellipsoid(center, new Vector3(radius, radius, radius), seg, rings, Quaternion.identity);
        }

        public Mesh ToMesh(string name)
        {
            var mesh = new Mesh { name = name };
            if (verts.Count > 65000) mesh.indexFormat = IndexFormat.UInt32;
            mesh.SetVertices(verts);
            mesh.SetNormals(normals);
            mesh.SetUVs(0, uvs);
            mesh.SetTriangles(tris, 0, true);
            mesh.RecalculateTangents();
            mesh.RecalculateBounds();
            mesh.UploadMeshData(true);   // 释放 CPU 侧副本，节省内存
            return mesh;
        }
    }

    /// <summary>按材质分桶的几何批次：一个区域（例如一排工位）最终每种材质只生成一个 MeshRenderer。</summary>
    public class GeoBatch
    {
        readonly Dictionary<Material, MeshBuilder> map = new Dictionary<Material, MeshBuilder>();
        public Matrix4x4 M = Matrix4x4.identity;

        public MeshBuilder this[Material mat]
        {
            get
            {
                if (!map.TryGetValue(mat, out var b))
                {
                    b = new MeshBuilder { uvScale = Mats.Tiling(mat) };
                    map[mat] = b;
                }
                b.M = M;
                return b;
            }
        }

        public void Box(Material m, Vector3 center, Vector3 size) { this[m].Box(center, size, Quaternion.identity); }
        public void Box(Material m, Vector3 center, Vector3 size, Quaternion rot) { this[m].Box(center, size, rot); }
        public void BoxMinMax(Material m, Vector3 min, Vector3 max) { this[m].BoxMinMax(min, max); }

        public GameObject Build(string name, Transform parent, bool castShadows, bool receiveShadows = true)
        {
            var root = new GameObject(name);
            root.transform.SetParent(parent, false);
            foreach (var kv in map)
            {
                if (kv.Value.VertexCount == 0) continue;
                var go = new GameObject(name + "_" + kv.Key.name);
                go.transform.SetParent(root.transform, false);
                go.AddComponent<MeshFilter>().sharedMesh = kv.Value.ToMesh(go.name);
                var mr = go.AddComponent<MeshRenderer>();
                mr.sharedMaterial = kv.Key;
                mr.shadowCastingMode = castShadows && !Mats.IsTransparent(kv.Key) ? ShadowCastingMode.On : ShadowCastingMode.Off;
                mr.receiveShadows = receiveShadows;
                mr.lightProbeUsage = LightProbeUsage.Off;
                mr.reflectionProbeUsage = ReflectionProbeUsage.BlendProbes;
            }
            return root;
        }
    }
}
