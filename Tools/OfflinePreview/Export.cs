// Dumps the generated office, character rig, poses and textures into a neutral format
// (Godot conventions: right-handed, -Z forward, UV origin top-left).
using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Text;
using UnityEngine;
using UnityEngine.Rendering;
using OfficeExplorer;

static class Export
{
    static string F(float f) => f.ToString("0.#####", CultureInfo.InvariantCulture);
    static string V3(Vector3 v) => "[" + F(v.x) + "," + F(v.y) + "," + F(-v.z) + "]";
    static string Size(Vector3 v) => "[" + F(v.x) + "," + F(v.y) + "," + F(v.z) + "]";
    static string Q(Quaternion q) => "[" + F(-q.x) + "," + F(-q.y) + "," + F(q.z) + "," + F(q.w) + "]";
    static string Col(Color c) => "[" + F(c.r) + "," + F(c.g) + "," + F(c.b) + "," + F(c.a) + "]";
    static string Str(string s) => "\"" + s.Replace("\\", "\\\\").Replace("\"", "\\\"") + "\"";

    static Dictionary<Material, int> matIds = new Dictionary<Material, int>();
    static List<Material> mats = new List<Material>();
    static Dictionary<Texture2D, string> texNames = new Dictionary<Texture2D, string>();
    static string outDir;

    static int MatId(Material m) { if (!matIds.TryGetValue(m, out int id)) { id = mats.Count; matIds[m] = id; mats.Add(m); } return id; }

    static string Tex(Texture t)
    {
        var tex = t as Texture2D; if (tex == null) return "";
        if (texNames.TryGetValue(tex, out var n)) return n;
        n = tex.name; texNames[tex] = n;
        var bytes = new byte[tex.width * tex.height * 4];
        for (int y = 0; y < tex.height; y++)
            for (int x = 0; x < tex.width; x++)
            {
                var c = tex.px[(tex.height - 1 - y) * tex.width + x]; int o = (y * tex.width + x) * 4;
                bytes[o] = c.r; bytes[o + 1] = c.g; bytes[o + 2] = c.b; bytes[o + 3] = c.a;
            }
        File.WriteAllBytes(outDir + "/textures/" + n + "." + tex.width + "x" + tex.height + ".rgba", bytes);
        return n;
    }

    static int meshCount;
    static string WriteMesh(Mesh mesh)
    {
        string id = "m" + (meshCount++);
        using (var bw = new BinaryWriter(File.Create(outDir + "/meshes/" + id + ".bin")))
        {
            bw.Write(mesh.verts.Count); bw.Write(mesh.tris.Count);
            foreach (var v in mesh.verts) { bw.Write(v.x); bw.Write(v.y); bw.Write(-v.z); }
            foreach (var v in mesh.normals) { bw.Write(v.x); bw.Write(v.y); bw.Write(-v.z); }
            foreach (var v in mesh.uvs) { bw.Write(v.x); bw.Write(1f - v.y); }
            foreach (var i in mesh.tris) bw.Write(i);
        }
        return id;
    }

    static void MeshesUnder(Transform t, string group, bool detail, StringBuilder sb, ref bool first)
    {
        var go = t.gameObject;
        var mr = go.GetComponent<MeshRenderer>(); var mf = go.GetComponent<MeshFilter>();
        if (mr != null && mf != null)
        {
            if (!first) sb.Append(",\n"); first = false;
            sb.Append("{\"file\":" + Str(WriteMesh(mf.sharedMesh)) + ",\"name\":" + Str(go.name) + ",\"group\":" + Str(group) + ",\"detail\":" + (detail ? "true" : "false") +
                ",\"mat\":" + MatId(mr.sharedMaterial) + ",\"cast\":" + (mr.shadowCastingMode == ShadowCastingMode.On ? "1" : mr.shadowCastingMode == ShadowCastingMode.ShadowsOnly ? "2" : "0") + "}");
        }
        foreach (var c in t.children) MeshesUnder(c, group, detail || c.gameObject.name == "Detail", sb, ref first);
    }

    static void Main(string[] args)
    {
        outDir = args[0];
        Directory.CreateDirectory(outDir + "/meshes"); Directory.CreateDirectory(outDir + "/textures");
        var scene = OfficeBuilder.Build(null);
        var sb = new StringBuilder("{\n");

        // ---- office meshes, grouped by top-level batch ----
        sb.Append("\"meshes\":[\n"); bool first = true;
        foreach (var batch in scene.root.children)
            if (batch.gameObject.name != "Colliders" && batch.gameObject.name != "Lights" && !batch.gameObject.name.StartsWith("ReflectionProbe"))
                MeshesUnder(batch, batch.gameObject.name, false, sb, ref first);
        sb.Append("\n],\n");

        sb.Append("\"colliders\":[");
        Transform colRoot = null; foreach (var c in scene.root.children) if (c.gameObject.name == "Colliders") colRoot = c;
        for (int i = 0; i < colRoot.children.Count; i++)
        {
            var c = colRoot.children[i];
            sb.Append((i > 0 ? "," : "") + "{\"p\":" + V3(c.localPosition) + ",\"s\":" + Size(c.gameObject.GetComponent<BoxCollider>().size) + ",\"q\":" + Q(c.localRotation) + "}");
        }
        sb.Append("],\n");

        Func<Light, string> L = l => "{\"p\":" + V3(l.transform.localPosition) + ",\"q\":" + Q(l.transform.localRotation) + ",\"range\":" + F(l.range) + ",\"angle\":" + F(l.spotAngle) + ",\"inner\":" + F(l.innerSpotAngle) + "}";
        sb.Append("\"fills\":["); for (int i = 0; i < scene.fillLights.Count; i++) sb.Append((i > 0 ? "," : "") + L(scene.fillLights[i])); sb.Append("],\n");
        sb.Append("\"windows\":["); for (int i = 0; i < scene.windowLights.Count; i++) sb.Append((i > 0 ? "," : "") + L(scene.windowLights[i])); sb.Append("],\n");
        sb.Append("\"probes\":["); for (int i = 0; i < scene.probes.Count; i++) sb.Append((i > 0 ? "," : "") + "{\"p\":" + V3(scene.probes[i].transform.localPosition) + ",\"s\":" + Size(scene.probes[i].size) + "}"); sb.Append("],\n");
        sb.Append("\"seats\":["); for (int i = 0; i < scene.seats.Count; i++) sb.Append((i > 0 ? "," : "") + "{\"p\":" + V3(scene.seats[i].seatPos) + ",\"yaw\":" + F(-scene.seats[i].yaw) + "}"); sb.Append("],\n");
        sb.Append("\"screens\":["); for (int i = 0; i < scene.screens.Count; i++) sb.Append((i > 0 ? "," : "") + "{\"p\":" + V3(scene.screens[i].center) + ",\"n\":" + V3(scene.screens[i].normal) + "}"); sb.Append("],\n");
        sb.Append("\"switches\":["); for (int i = 0; i < scene.switches.Count; i++) sb.Append((i > 0 ? "," : "") + "{\"p\":" + V3(scene.switches[i].pos) + ",\"n\":" + V3(scene.switches[i].normal) + "}"); sb.Append("],\n");
        sb.Append("\"spawn\":{\"p\":" + V3(scene.spawnPos) + ",\"yaw\":" + F(-scene.spawnYaw) + "},\n");
        sb.Append("\"room\":{\"L\":" + F(OfficeBuilder.L) + ",\"D\":" + F(OfficeBuilder.D) + ",\"H\":" + F(OfficeBuilder.H) + "},\n");

        // ---- character rig ----
        var player = new GameObject("Player");
        var rig = CharacterRig.Create(player.transform);
        int[] parentOf = { -1, 0, 1, 1, 3, 1, 5, 0, 7, 8, 0, 10, 11 };
        sb.Append("\"rig\":[\n");
        for (int j = 0; j < CharacterRig.JointCount; j++)
        {
            var jt = rig.joints[j];
            sb.Append((j > 0 ? ",\n" : "") + "{\"name\":" + Str(jt.gameObject.name) + ",\"parent\":" + parentOf[j] + ",\"p\":" + V3(jt.localPosition) + ",\"meshes\":[");
            bool f2 = true;
            foreach (var ch in jt.children) if (ch.gameObject.name == "Geo") MeshesUnder(ch, jt.gameObject.name, false, sb, ref f2);
            sb.Append("]}");
        }
        sb.Append("\n],\n");

        // ---- animation clips sampled from the pose library ----
        sb.Append("\"anims\":{\n");
        var kinds = (AnimClip[])Enum.GetValues(typeof(AnimClip)); var pose = new Pose();
        for (int k = 0; k < kinds.Length; k++)
        {
            float len = PoseLibrary.Length(kinds[k]); bool loop = PoseLibrary.Loops(kinds[k]);
            int frames = Math.Max(4, (int)Math.Ceiling(len * 30f));
            sb.Append((k > 0 ? ",\n" : "") + Str(kinds[k].ToString()) + ":{\"length\":" + F(len) + ",\"loop\":" + (loop ? "true" : "false") + ",\"frames\":[");
            for (int f = 0; f <= frames; f++)
            {
                PoseLibrary.Evaluate(kinds[k], loop && f == frames ? 0f : (float)f / frames, pose);
                sb.Append((f > 0 ? "," : "") + "{\"h\":" + V3(pose.hipsPos) + ",\"r\":[");
                for (int j = 0; j < CharacterRig.JointCount; j++) sb.Append((j > 0 ? "," : "") + Q(pose.rot[j]));
                sb.Append("]}");
            }
            sb.Append("]}");
        }
        sb.Append("\n},\n");

        // ---- materials (after all meshes so ids are complete) ----
        Tex(Mats.SkylineNight); Tex(Mats.SkylineDay);
        sb.Append("\"materials\":[\n");
        for (int i = 0; i < mats.Count; i++)
        {
            var m = mats[i];
            Color bc = m.colors.TryGetValue("_BaseColor", out var c0) ? c0 : Color.white;
            Color ec = m.colors.TryGetValue("_EmissionColor", out var c1) ? c1 : Color.black;
            var bt = m.GetTexture("_BaseMap") as Texture2D;
            sb.Append((i > 0 ? ",\n" : "") + "{\"name\":" + Str(m.name) + ",\"unlit\":" + (m.shader.name.Contains("Unlit") ? "true" : "false") + ",\"color\":" + Col(bc) +
                ",\"metallic\":" + F(m.floats.TryGetValue("_Metallic", out var me) ? me : 0f) + ",\"smooth\":" + F(m.floats.TryGetValue("_Smoothness", out var sm) ? sm : 0.5f) +
                ",\"tex\":" + Str(Tex(bt)) + ",\"repeat\":" + (bt != null && bt.wrapMode == TextureWrapMode.Repeat ? "true" : "false") +
                ",\"normal\":" + Str(Tex(m.GetTexture("_BumpMap"))) + ",\"bump\":" + F(m.floats.TryGetValue("_BumpScale", out var bs) ? bs : 1f) +
                ",\"emissive\":" + (m.keywords.Contains("_EMISSION") ? "true" : "false") + ",\"emission\":" + Col(ec) + ",\"emissionTex\":" + Str(Tex(m.GetTexture("_EmissionMap"))) +
                ",\"transparent\":" + (m.keywords.Contains("_SURFACE_TYPE_TRANSPARENT") ? "true" : "false") + ",\"additive\":" + (m.floats.TryGetValue("_Blend", out var bl) && bl == 2f ? "true" : "false") +
                ",\"cullOff\":" + (m.floats.TryGetValue("_Cull", out var cu) && cu == 0f ? "true" : "false") + "}");
        }
        sb.Append("\n]\n}\n");
        File.WriteAllText(outDir + "/scene.json", sb.ToString());
        Console.WriteLine("meshes=" + meshCount + " materials=" + mats.Count + " textures=" + texNames.Count);
    }
}
