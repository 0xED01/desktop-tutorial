// Offline preview: runs the real scene-generation code and software-rasterizes it.
using System;
using System.Collections.Generic;
using System.IO;
using UnityEngine;
using UnityEngine.Rendering;
using OfficeExplorer;

static class Program
{
    const int W = 1280, H = 720;
    static float[] depth = new float[W * H];
    static float[] fb = new float[W * H * 3];
    static Vector3 eye, camR, camU, camF; static float focal;
    static int culled, drawn;

    class Item { public Mesh mesh; public Material mat; public Matrix4x4 m; public string path; }

    static void Collect(Transform t, List<Item> list, string path)
    {
        var go = t.gameObject;
        if (!go.activeSelf) return;
        string p = path + "/" + go.name;
        var mr = go.GetComponent<MeshRenderer>(); var mf = go.GetComponent<MeshFilter>();
        if (mr != null && mf != null && mr.shadowCastingMode != ShadowCastingMode.ShadowsOnly)
            list.Add(new Item { mesh = mf.sharedMesh, mat = mr.sharedMaterial, m = t.World, path = p });
        foreach (var c in t.children) Collect(c, list, p);
    }

    static void SetCamera(Vector3 e, Vector3 fwd, Vector3 up, float fovDeg)
    {
        eye = e; camF = fwd.normalized; camR = Vector3.Cross(up, camF).normalized; camU = Vector3.Cross(camF, camR);
        focal = (H * 0.5f) / (float)Math.Tan(fovDeg * Mathf.Deg2Rad * 0.5f);
    }

    struct V { public Vector3 view, n; public Vector2 uv; }

    static V LerpV(V a, V b, float t) => new V { view = a.view + (b.view - a.view) * t, n = a.n + (b.n - a.n) * t, uv = a.uv + (b.uv - a.uv) * t };

    static void Draw(Item it, bool transparentPass)
    {
        var mat = it.mat;
        bool transparent = mat.keywords.Contains("_SURFACE_TYPE_TRANSPARENT");
        if (transparent != transparentPass) return;
        bool unlit = mat.shader.name.Contains("Unlit");
        bool additive = mat.floats.TryGetValue("_Blend", out float bl) && bl == 2f;
        bool cullOff = mat.floats.TryGetValue("_Cull", out float cu) && cu == 0f;
        Color baseCol = mat.colors.TryGetValue("_BaseColor", out var bc) ? bc : Color.white;
        var baseTex = mat.GetTexture("_BaseMap") as Texture2D;
        bool emissive = mat.keywords.Contains("_EMISSION");
        Color emCol = mat.colors.TryGetValue("_EmissionColor", out var ec) ? ec : Color.black;
        var emTex = mat.GetTexture("_EmissionMap") as Texture2D;
        var mesh = it.mesh;
        Vector3 L = new Vector3(0.07f, 0.98f, 0.16f).normalized;
        var poly = new List<V>(8); var tmp = new List<V>(8);
        const float near = 0.08f;
        for (int i = 0; i < mesh.tris.Count; i += 3)
        {
            poly.Clear();
            for (int k = 0; k < 3; k++)
            {
                int idx = mesh.tris[i + k];
                Vector3 wp = it.m.MultiplyPoint3x4(mesh.verts[idx]) - eye;
                poly.Add(new V { view = new Vector3(Vector3.Dot(wp, camR), Vector3.Dot(wp, camU), Vector3.Dot(wp, camF)), n = it.m.MultiplyVector(mesh.normals[idx]), uv = mesh.uvs[idx] });
            }
            // near-plane clip
            tmp.Clear();
            for (int k = 0; k < poly.Count; k++)
            {
                V a = poly[k], b = poly[(k + 1) % poly.Count];
                bool ia = a.view.z >= near, ib = b.view.z >= near;
                if (ia) tmp.Add(a);
                if (ia != ib) tmp.Add(LerpV(a, b, (near - a.view.z) / (b.view.z - a.view.z)));
            }
            if (tmp.Count < 3) continue;
            for (int k = 1; k + 1 < tmp.Count; k++) Raster(tmp[0], tmp[k], tmp[k + 1], unlit, transparent, additive, cullOff, baseCol, baseTex, emissive, emCol, emTex, L);
        }
    }

    static void Raster(V a, V b, V c, bool unlit, bool transparent, bool additive, bool cullOff, Color baseCol, Texture2D baseTex, bool emissive, Color emCol, Texture2D emTex, Vector3 L)
    {
        float ax = W * 0.5f + focal * a.view.x / a.view.z, ay = H * 0.5f - focal * a.view.y / a.view.z;
        float bx = W * 0.5f + focal * b.view.x / b.view.z, by = H * 0.5f - focal * b.view.y / b.view.z;
        float cx = W * 0.5f + focal * c.view.x / c.view.z, cy = H * 0.5f - focal * c.view.y / c.view.z;
        float area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay);   // y-down image space: Unity front faces (clockwise, y-up) give area > 0
        if (Math.Abs(area) < 1e-6f) return;
        if (area < 0) { if (!cullOff) { culled++; return; } }
        drawn++;
        int x0 = Math.Max(0, (int)Math.Floor(Math.Min(ax, Math.Min(bx, cx)))), x1 = Math.Min(W - 1, (int)Math.Ceiling(Math.Max(ax, Math.Max(bx, cx))));
        int y0 = Math.Max(0, (int)Math.Floor(Math.Min(ay, Math.Min(by, cy)))), y1 = Math.Min(H - 1, (int)Math.Ceiling(Math.Max(ay, Math.Max(by, cy))));
        float ia = 1f / a.view.z, ib = 1f / b.view.z, ic = 1f / c.view.z;
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++)
            {
                float px = x + 0.5f, py = y + 0.5f;
                float w0 = ((bx - px) * (cy - py) - (cx - px) * (by - py)) / area;
                float w1 = ((cx - px) * (ay - py) - (ax - px) * (cy - py)) / area;
                float w2 = 1f - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                float iz = w0 * ia + w1 * ib + w2 * ic;
                int o = y * W + x;
                if (iz <= depth[o]) continue;
                float p0 = w0 * ia / iz, p1 = w1 * ib / iz, p2 = w2 * ic / iz;
                Vector2 uv = a.uv * p0 + b.uv * p1 + c.uv * p2;
                Vector3 n = (a.n * p0 + b.n * p1 + c.n * p2).normalized;
                if (area < 0) n = -n;
                Color t = baseTex != null ? baseTex.Sample(uv.x, uv.y) : Color.white;
                float r = baseCol.r * t.r, g = baseCol.g * t.g, bl = baseCol.b * t.b, al = baseCol.a * t.a;
                if (!unlit)
                {
                    float amb = n.y >= 0 ? Mathf.Lerp(0.44f, 0.42f, n.y) : Mathf.Lerp(0.44f, 0.26f, -n.y);
                    float lit = amb + 0.55f * Math.Max(0f, Vector3.Dot(n, L)) + 0.22f * Math.Max(0f, 0.3f + 0.7f * n.y) * 0.6f;
                    r *= lit; g *= lit; bl *= lit;
                    if (emissive)
                    {
                        Color e = emTex != null ? emTex.Sample(uv.x, uv.y) : Color.white;
                        r += emCol.r * e.r; g += emCol.g * e.g; bl += emCol.b * e.b;
                    }
                }
                if (transparent)
                {
                    if (additive) { fb[o * 3] += r * al; fb[o * 3 + 1] += g * al; fb[o * 3 + 2] += bl * al; }
                    else { fb[o * 3] += (r - fb[o * 3]) * al; fb[o * 3 + 1] += (g - fb[o * 3 + 1]) * al; fb[o * 3 + 2] += (bl - fb[o * 3 + 2]) * al; }
                }
                else { depth[o] = iz; fb[o * 3] = r; fb[o * 3 + 1] = g; fb[o * 3 + 2] = bl; }
            }
    }

    static void Render(string file, List<Item> items, Func<Item, bool> filter)
    {
        Array.Clear(depth, 0, depth.Length);
        for (int i = 0; i < fb.Length; i++) fb[i] = 0.02f;
        culled = drawn = 0;
        foreach (var it in items) if (filter == null || filter(it)) Draw(it, false);
        foreach (var it in items) if (filter == null || filter(it)) Draw(it, true);
        var bytes = new byte[W * H * 3];
        for (int i = 0; i < fb.Length; i++)
        {
            float v = fb[i]; v = v / (1f + Math.Max(0f, v - 0.8f) * 0.6f);   // soft shoulder
            bytes[i] = (byte)(Math.Pow(Mathf.Clamp01(v), 1 / 2.2) * 255.0 + 0.5);
        }
        using (var f = File.Create(file))
        {
            var hdr = System.Text.Encoding.ASCII.GetBytes("P6\n" + W + " " + H + "\n255\n");
            f.Write(hdr, 0, hdr.Length); f.Write(bytes, 0, bytes.Length);
        }
        Console.WriteLine(file + "  drawn=" + drawn + " backface-culled=" + culled);
    }

    static Vector3 Dir(float pitch, float yaw) => Quaternion.Euler(pitch, yaw, 0) * Vector3.forward;

    static CharacterRig MakeChar(Vector3 pos, float yaw, AnimClip clip, float t)
    {
        var p = new GameObject("Player");
        p.transform.SetPositionAndRotation(pos, Quaternion.Euler(0, yaw, 0));
        var rig = CharacterRig.Create(p.transform);
        var pose = new Pose(); PoseLibrary.Evaluate(clip, t, pose); rig.ApplyPose(pose);
        return rig;
    }

    static void Main(string[] args)
    {
        string outDir = args.Length > 0 ? args[0] : ".";
        var sw = System.Diagnostics.Stopwatch.StartNew();
        var scene = OfficeBuilder.Build(null);
        Console.WriteLine("build ms=" + sw.ElapsedMilliseconds + " seats=" + scene.seats.Count + " screens=" + scene.screens.Count + " switches=" + scene.switches.Count);
        MakeChar(scene.spawnPos + new Vector3(0, -0.05f, 0), scene.spawnYaw, AnimClip.Walk, 0.25f);
        var seat = scene.seats[9];
        MakeChar(seat.seatPos, seat.yaw, AnimClip.Sit, 0.1f);
        MakeChar(new Vector3(9.6f, 0, 2.9f), 200f, AnimClip.Idle, 0.2f);
        MakeChar(new Vector3(11.6f, 0, 2.9f), 250f, AnimClip.Run, 0.25f);
        MakeChar(new Vector3(13.0f, 0.45f, 2.9f), 250f, AnimClip.Air, 0.25f);

        var items = new List<Item>();
        foreach (var r in new List<GameObject>(GameObject.roots)) Collect(r.transform, items, "");
        long tris = 0, verts = 0; var perMat = new Dictionary<string, long>();
        foreach (var it in items) { tris += it.mesh.tris.Count / 3; verts += it.mesh.verts.Count; perMat[it.mat.name] = (perMat.TryGetValue(it.mat.name, out var v) ? v : 0) + it.mesh.tris.Count / 3; }
        Console.WriteLine("renderers=" + items.Count + " triangles=" + tris + " vertices=" + verts);
        int colliders = 0; foreach (var r in GameObject.roots) colliders += r.GetComponentsInChildren<BoxCollider>().Length;
        Console.WriteLine("box colliders=" + colliders);

        Vector3 cam = new Vector3(4.2f, 1.55f, 3.0f);
        float[] yaws = { -35f, 10f, 50f, 88f };
        for (int i = 0; i < yaws.Length; i++) { SetCamera(cam + new Vector3(-0.6f, 0, -0.5f), Dir(6f, yaws[i]), Vector3.up, 62f); Render(outDir + "/view_video_" + i + ".ppm", items, null); }

        SetCamera(new Vector3(OfficeBuilder.L / 2, 30f, OfficeBuilder.D / 2), Vector3.down, Vector3.forward, 38f);
        Render(outDir + "/view_top.ppm", items, it => !it.path.Contains("Ceiling"));

        Vector3 target = new Vector3(4.6f, 0.85f, 6.6f); Vector3 e2 = new Vector3(6.3f, 1.55f, 5.0f);
        SetCamera(e2, target - e2, Vector3.up, 55f); Render(outDir + "/view_desk.ppm", items, null);

        target = new Vector3(7.05f, 0.6f, 1.5f); e2 = new Vector3(9.3f, 1.6f, 3.9f);
        SetCamera(e2, target - e2, Vector3.up, 55f); Render(outDir + "/view_lounge.ppm", items, null);

        e2 = new Vector3(15.5f, 1.6f, 11.6f); SetCamera(e2, Dir(2f, -20f), Vector3.up, 62f); Render(outDir + "/view_north.ppm", items, null);
        e2 = new Vector3(23f, 1.6f, 11.4f); SetCamera(e2, Dir(2f, 15f), Vector3.up, 62f); Render(outDir + "/view_north2.ppm", items, null);

        target = new Vector3(11.3f, 1.0f, 2.9f); e2 = new Vector3(11.2f, 1.35f, 6.4f);
        SetCamera(e2, target - e2, Vector3.up, 48f); Render(outDir + "/view_chars.ppm", items, null);

        // third-person framing behind the player
        Vector3 pf = Quaternion.Euler(0, scene.spawnYaw, 0) * Vector3.forward;
        e2 = scene.spawnPos + Vector3.up * 1.52f - Quaternion.Euler(12f, scene.spawnYaw, 0) * Vector3.forward * 3.2f;
        SetCamera(e2, Quaternion.Euler(12f, scene.spawnYaw, 0) * Vector3.forward, Vector3.up, 60f); Render(outDir + "/view_third.ppm", items, null);

        var st = scene.seats[9]; target = st.seatPos + Vector3.up * 0.9f; e2 = target + new Vector3(st.seatPos.x > 3.6f ? 2.0f : -2.0f, 0.7f, 1.6f);
        SetCamera(e2, target - e2, Vector3.up, 50f); Render(outDir + "/view_sit.ppm", items, null);
        Console.WriteLine("total ms=" + sw.ElapsedMilliseconds);
    }
}
