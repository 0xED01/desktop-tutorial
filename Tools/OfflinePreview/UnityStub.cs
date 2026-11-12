// Minimal stand-in for the UnityEngine API surface used by the scene-generation code.
// Used only by the offline preview harness; never shipped.
using System;
using System.Collections.Generic;
namespace UnityEngine
{
    public static class Mathf
    {
        public const float PI = 3.14159265f, Deg2Rad = PI / 180f, Rad2Deg = 180f / PI, Infinity = float.PositiveInfinity;
        public static float Sin(float a) => (float)Math.Sin(a); public static float Cos(float a) => (float)Math.Cos(a);
        public static float Sqrt(float a) => (float)Math.Sqrt(a); public static float Abs(float a) => Math.Abs(a);
        public static float Pow(float a, float b) => (float)Math.Pow(a, b); public static float Exp(float a) => (float)Math.Exp(a);
        public static float Min(float a, float b) => Math.Min(a, b); public static float Max(float a, float b) => Math.Max(a, b);
        public static int Min(int a, int b) => Math.Min(a, b); public static int Max(int a, int b) => Math.Max(a, b);
        public static float Clamp(float v, float a, float b) => v < a ? a : (v > b ? b : v);
        public static int Clamp(int v, int a, int b) => v < a ? a : (v > b ? b : v);
        public static float Clamp01(float v) => Clamp(v, 0, 1);
        public static float Lerp(float a, float b, float t) => a + (b - a) * Clamp01(t);
        public static float InverseLerp(float a, float b, float v) => a != b ? Clamp01((v - a) / (b - a)) : 0f;
        public static float SmoothStep(float a, float b, float t) { t = Clamp01(t); t = -2f * t * t * t + 3f * t * t; return b * t + a * (1f - t); }
        public static int FloorToInt(float f) => (int)Math.Floor(f); public static int CeilToInt(float f) => (int)Math.Ceiling(f);
        public static int RoundToInt(float f) => (int)Math.Round(f); public static float Round(float f) => (float)Math.Round(f);
        public static float Repeat(float t, float len) => Clamp(t - (float)Math.Floor(t / len) * len, 0f, len);
        public static float MoveTowards(float c, float t, float d) => Math.Abs(t - c) <= d ? t : c + Math.Sign(t - c) * d;
    }
    public struct Vector2
    {
        public float x, y; public Vector2(float x, float y) { this.x = x; this.y = y; }
        public static Vector2 zero => new Vector2(0, 0); public static Vector2 one => new Vector2(1, 1);
        public static Vector2 operator *(Vector2 a, float b) => new Vector2(a.x * b, a.y * b);
        public static Vector2 operator +(Vector2 a, Vector2 b) => new Vector2(a.x + b.x, a.y + b.y);
        public static Vector2 operator -(Vector2 a, Vector2 b) => new Vector2(a.x - b.x, a.y - b.y);
    }
    public struct Vector4 { public float x, y, z, w; public Vector4(float x, float y, float z, float w) { this.x = x; this.y = y; this.z = z; this.w = w; } public float sqrMagnitude => x * x + y * y + z * z + w * w; }
    public struct Vector3
    {
        public float x, y, z; public Vector3(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
        public Vector3(float x, float y) { this.x = x; this.y = y; this.z = 0; }
        public static Vector3 zero => new Vector3(0, 0, 0); public static Vector3 one => new Vector3(1, 1, 1);
        public static Vector3 up => new Vector3(0, 1, 0); public static Vector3 down => new Vector3(0, -1, 0);
        public static Vector3 right => new Vector3(1, 0, 0); public static Vector3 left => new Vector3(-1, 0, 0);
        public static Vector3 forward => new Vector3(0, 0, 1); public static Vector3 back => new Vector3(0, 0, -1);
        public float magnitude => (float)Math.Sqrt(x * x + y * y + z * z); public float sqrMagnitude => x * x + y * y + z * z;
        public Vector3 normalized { get { float m = magnitude; return m > 1e-5f ? this / m : zero; } }
        public void Normalize() { this = normalized; }
        public static Vector3 operator +(Vector3 a, Vector3 b) => new Vector3(a.x + b.x, a.y + b.y, a.z + b.z);
        public static Vector3 operator -(Vector3 a, Vector3 b) => new Vector3(a.x - b.x, a.y - b.y, a.z - b.z);
        public static Vector3 operator -(Vector3 a) => new Vector3(-a.x, -a.y, -a.z);
        public static Vector3 operator *(Vector3 a, float b) => new Vector3(a.x * b, a.y * b, a.z * b);
        public static Vector3 operator *(float b, Vector3 a) => new Vector3(a.x * b, a.y * b, a.z * b);
        public static Vector3 operator /(Vector3 a, float b) => new Vector3(a.x / b, a.y / b, a.z / b);
        public static float Dot(Vector3 a, Vector3 b) => a.x * b.x + a.y * b.y + a.z * b.z;
        public static Vector3 Cross(Vector3 a, Vector3 b) => new Vector3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
        public static Vector3 Scale(Vector3 a, Vector3 b) => new Vector3(a.x * b.x, a.y * b.y, a.z * b.z);
        public static Vector3 LerpUnclamped(Vector3 a, Vector3 b, float t) => a + (b - a) * t;
        public static Vector3 Lerp(Vector3 a, Vector3 b, float t) => a + (b - a) * Mathf.Clamp01(t);
    }
    public struct Quaternion
    {
        public float x, y, z, w; public Quaternion(float x, float y, float z, float w) { this.x = x; this.y = y; this.z = z; this.w = w; }
        public static Quaternion identity => new Quaternion(0, 0, 0, 1);
        public static Quaternion AngleAxis(float deg, Vector3 axis) { axis = axis.normalized; float h = deg * Mathf.Deg2Rad * 0.5f, s = Mathf.Sin(h); return new Quaternion(axis.x * s, axis.y * s, axis.z * s, Mathf.Cos(h)); }
        public static Quaternion Euler(float x, float y, float z) => AngleAxis(y, Vector3.up) * AngleAxis(x, Vector3.right) * AngleAxis(z, Vector3.forward);
        public static Quaternion operator *(Quaternion a, Quaternion b) => new Quaternion(
            a.w * b.x + a.x * b.w + a.y * b.z - a.z * b.y, a.w * b.y + a.y * b.w + a.z * b.x - a.x * b.z,
            a.w * b.z + a.z * b.w + a.x * b.y - a.y * b.x, a.w * b.w - a.x * b.x - a.y * b.y - a.z * b.z);
        public static Vector3 operator *(Quaternion q, Vector3 v)
        {
            Vector3 u = new Vector3(q.x, q.y, q.z); Vector3 t = 2f * Vector3.Cross(u, v);
            return v + q.w * t + Vector3.Cross(u, t);
        }
        public static float Dot(Quaternion a, Quaternion b) => a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
        public static Quaternion FromToRotation(Vector3 from, Vector3 to)
        {
            from = from.normalized; to = to.normalized; float d = Vector3.Dot(from, to);
            if (d > 0.99999f) return identity;
            if (d < -0.99999f) { Vector3 ax = Vector3.Cross(Vector3.right, from); if (ax.sqrMagnitude < 1e-4f) ax = Vector3.Cross(Vector3.up, from); return AngleAxis(180f, ax); }
            return AngleAxis((float)Math.Acos(d) * Mathf.Rad2Deg, Vector3.Cross(from, to));
        }
        public static Quaternion LookRotation(Vector3 fwd, Vector3 up)
        {
            Vector3 z = fwd.normalized, x = Vector3.Cross(up, z).normalized, y = Vector3.Cross(z, x);
            float m00 = x.x, m01 = y.x, m02 = z.x, m10 = x.y, m11 = y.y, m12 = z.y, m20 = x.z, m21 = y.z, m22 = z.z;
            float tr = m00 + m11 + m22; Quaternion q;
            if (tr > 0) { float s = (float)Math.Sqrt(tr + 1f) * 2f; q = new Quaternion((m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s, 0.25f * s); }
            else if (m00 > m11 && m00 > m22) { float s = (float)Math.Sqrt(1f + m00 - m11 - m22) * 2f; q = new Quaternion(0.25f * s, (m01 + m10) / s, (m02 + m20) / s, (m21 - m12) / s); }
            else if (m11 > m22) { float s = (float)Math.Sqrt(1f + m11 - m00 - m22) * 2f; q = new Quaternion((m01 + m10) / s, 0.25f * s, (m12 + m21) / s, (m02 - m20) / s); }
            else { float s = (float)Math.Sqrt(1f + m22 - m00 - m11) * 2f; q = new Quaternion((m02 + m20) / s, (m12 + m21) / s, 0.25f * s, (m10 - m01) / s); }
            return q;
        }
        public static Quaternion SlerpUnclamped(Quaternion a, Quaternion b, float t)
        {
            float d = Dot(a, b); if (d < 0) { b = new Quaternion(-b.x, -b.y, -b.z, -b.w); d = -d; }
            float wa = 1 - t, wb = t;
            if (d < 0.9995f) { float th = (float)Math.Acos(d), s = (float)Math.Sin(th); wa = (float)Math.Sin((1 - t) * th) / s; wb = (float)Math.Sin(t * th) / s; }
            var q = new Quaternion(a.x * wa + b.x * wb, a.y * wa + b.y * wb, a.z * wa + b.z * wb, a.w * wa + b.w * wb);
            float m = (float)Math.Sqrt(Dot(q, q)); return new Quaternion(q.x / m, q.y / m, q.z / m, q.w / m);
        }
        public static Quaternion Slerp(Quaternion a, Quaternion b, float t) => SlerpUnclamped(a, b, Mathf.Clamp01(t));
    }
    public struct Matrix4x4
    {
        public Vector3 c0, c1, c2, t;   // rotation*scale columns + translation
        public static Matrix4x4 identity => new Matrix4x4 { c0 = Vector3.right, c1 = Vector3.up, c2 = Vector3.forward, t = Vector3.zero };
        public static Matrix4x4 TRS(Vector3 p, Quaternion q, Vector3 s) => new Matrix4x4 { c0 = q * Vector3.right * s.x, c1 = q * Vector3.up * s.y, c2 = q * Vector3.forward * s.z, t = p };
        public Vector3 MultiplyVector(Vector3 v) => c0 * v.x + c1 * v.y + c2 * v.z;
        public Vector3 MultiplyPoint3x4(Vector3 v) => c0 * v.x + c1 * v.y + c2 * v.z + t;
        public static Matrix4x4 operator *(Matrix4x4 a, Matrix4x4 b) => new Matrix4x4 { c0 = a.MultiplyVector(b.c0), c1 = a.MultiplyVector(b.c1), c2 = a.MultiplyVector(b.c2), t = a.MultiplyPoint3x4(b.t) };
    }
    public struct Color
    {
        public float r, g, b, a; public Color(float r, float g, float b, float a = 1f) { this.r = r; this.g = g; this.b = b; this.a = a; }
        public static Color white => new Color(1, 1, 1, 1); public static Color black => new Color(0, 0, 0, 1);
        public static Color operator *(Color c, float f) => new Color(c.r * f, c.g * f, c.b * f, c.a * f);
        public static Color operator +(Color a, Color b) => new Color(a.r + b.r, a.g + b.g, a.b + b.b, a.a + b.a);
        public static Color Lerp(Color a, Color b, float t) { t = Mathf.Clamp01(t); return new Color(a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t, a.b + (b.b - a.b) * t, a.a + (b.a - a.a) * t); }
        static byte B(float f) => (byte)Math.Round(Mathf.Clamp01(f) * 255f);
        public static implicit operator Color32(Color c) => new Color32(B(c.r), B(c.g), B(c.b), B(c.a));
        public static implicit operator Color(Color32 c) => new Color(c.r / 255f, c.g / 255f, c.b / 255f, c.a / 255f);
    }
    public struct Color32 { public byte r, g, b, a; public Color32(byte r, byte g, byte b, byte a) { this.r = r; this.g = g; this.b = b; this.a = a; } }
    public struct Rect
    {
        public float x, y, width, height; public Rect(float x, float y, float w, float h) { this.x = x; this.y = y; width = w; height = h; }
        public float xMin => x; public float xMax => x + width; public float yMin => y; public float yMax => y + height;
    }
    public struct Bounds { public Vector3 center, size; public Bounds(Vector3 c, Vector3 s) { center = c; size = s; } public Vector3 extents => size * 0.5f; public Vector3 min => center - extents; public Vector3 max => center + extents; }
    public static class Debug { public static void Log(object o) => Console.WriteLine(o); public static void LogError(object o) => Console.WriteLine("ERROR " + o); public static void LogWarning(object o) => Console.WriteLine("WARN " + o); }
    public class Object { public string name = ""; public HideFlags hideFlags; public static implicit operator bool(Object o) => o != null; }
    public enum HideFlags { None, DontSave }
    public class Shader : Object { public static Shader Find(string n) => new Shader { name = n }; }
    public static class Resources { public static T Load<T>(string path) where T : class => null; }
    public enum TextureFormat { RGBA32 } public enum TextureWrapMode { Repeat, Clamp } public enum FilterMode { Point, Bilinear, Trilinear }
    public class Texture : Object { }
    public class Texture2D : Texture
    {
        public int width, height; public Color32[] px; public TextureWrapMode wrapMode, wrapModeV; public FilterMode filterMode; public int anisoLevel; bool vSet;
        public Texture2D(int w, int h, TextureFormat f, bool mips, bool linear) { width = w; height = h; }
        public void SetPixels32(Color32[] p) { px = p; } public void Apply(bool a, bool b) { }
        public Color Sample(float u, float v)
        {
            if (wrapMode == TextureWrapMode.Repeat) u -= (float)Math.Floor(u); else u = Mathf.Clamp01(u);
            if (wrapModeV == TextureWrapMode.Repeat && wrapMode == TextureWrapMode.Repeat) v -= (float)Math.Floor(v); else v = Mathf.Clamp01(v);
            int X = Mathf.Clamp((int)(u * width), 0, width - 1), Y = Mathf.Clamp((int)(v * height), 0, height - 1);
            return px[Y * width + X];
        }
    }
    public enum SpriteMeshType { FullRect }
    public class Sprite : Object { public Vector4 border; public static Sprite Create(Texture2D t, Rect r, Vector2 p, float ppu) => new Sprite(); public static Sprite Create(Texture2D t, Rect r, Vector2 p, float ppu, uint e, SpriteMeshType m, Vector4 b) => new Sprite { border = b }; }
    public enum MaterialGlobalIlluminationFlags { RealtimeEmissive }
    public class Material : Object
    {
        public Shader shader; public bool enableInstancing; public int renderQueue; public MaterialGlobalIlluminationFlags globalIlluminationFlags;
        public Dictionary<string, Color> colors = new Dictionary<string, Color>(); public Dictionary<string, float> floats = new Dictionary<string, float>();
        public Dictionary<string, Texture> textures = new Dictionary<string, Texture>(); public HashSet<string> keywords = new HashSet<string>();
        public Material(Shader s) { shader = s; } public Material(Material m) { shader = m.shader; }
        public void SetColor(string n, Color c) { colors[n] = c; } public void SetFloat(string n, float f) { floats[n] = f; }
        public void SetTexture(string n, Texture t) { textures[n] = t; } public Texture GetTexture(string n) => textures.TryGetValue(n, out var t) ? t : null;
        public void EnableKeyword(string k) { keywords.Add(k); } public void SetOverrideTag(string a, string b) { } public void SetShaderPassEnabled(string a, bool b) { }
    }
    public class Mesh : Object
    {
        public Rendering.IndexFormat indexFormat; public List<Vector3> verts, normals; public List<Vector2> uvs; public List<int> tris;
        public void SetVertices(List<Vector3> v) { verts = new List<Vector3>(v); } public void SetNormals(List<Vector3> v) { normals = new List<Vector3>(v); }
        public void SetUVs(int c, List<Vector2> v) { uvs = new List<Vector2>(v); } public void SetTriangles(List<int> t, int s, bool b) { tris = new List<int>(t); }
        public void RecalculateTangents() { } public void RecalculateBounds() { } public void UploadMeshData(bool b) { }
    }
    public class Component : Object
    {
        public GameObject gameObject; public Transform transform => gameObject.transform;
        public T[] GetComponentsInChildren<T>(bool inactive = false) where T : class => gameObject.GetComponentsInChildren<T>(inactive);
    }
    public class Behaviour : Component { public bool enabled = true; }
    public class MonoBehaviour : Behaviour { }
    public class Transform : Component
    {
        public Transform parent; public List<Transform> children = new List<Transform>();
        public Vector3 localPosition = Vector3.zero; public Quaternion localRotation = Quaternion.identity; public Vector3 localScale = Vector3.one;
        public void SetParent(Transform p, bool worldStays) { parent = p; if (p != null) { p.children.Add(this); GameObject.roots.Remove(gameObject); } }
        public Matrix4x4 World => parent == null ? Matrix4x4.TRS(localPosition, localRotation, localScale) : parent.World * Matrix4x4.TRS(localPosition, localRotation, localScale);
        public Quaternion rotation { get => parent == null ? localRotation : parent.rotation * localRotation; set => localRotation = value; }
        public Vector3 position { get => World.t; set => localPosition = value; }
        public void SetPositionAndRotation(Vector3 p, Quaternion q) { localPosition = p; localRotation = q; }
    }
    public class GameObject : Object
    {
        public static List<GameObject> roots = new List<GameObject>();
        public Transform transform; public int layer; public bool activeSelf = true; public List<Component> comps = new List<Component>();
        public GameObject(string n = "") { name = n; transform = new Transform { gameObject = this }; roots.Add(this); }
        public void SetActive(bool a) { activeSelf = a; }
        public T AddComponent<T>() where T : Component, new() { var c = new T { gameObject = this }; comps.Add(c); return c; }
        public T GetComponent<T>() where T : class { foreach (var c in comps) if (c is T t) return t; return null; }
        public T[] GetComponentsInChildren<T>(bool inactive = false) where T : class { var l = new List<T>(); Collect(transform, l); return l.ToArray(); }
        static void Collect<T>(Transform t, List<T> l) where T : class { foreach (var c in t.gameObject.comps) if (c is T x) l.Add(x); foreach (var ch in t.children) Collect(ch, l); }
        public bool ActiveInHierarchy { get { for (Transform t = transform; t != null; t = t.parent) if (!t.gameObject.activeSelf) return false; return true; } }
    }
    public class MeshFilter : Component { public Mesh sharedMesh; }
    public class Renderer : Component { public Material sharedMaterial; public Rendering.ShadowCastingMode shadowCastingMode; public bool receiveShadows; public Rendering.LightProbeUsage lightProbeUsage; public Rendering.ReflectionProbeUsage reflectionProbeUsage; }
    public class MeshRenderer : Renderer { }
    public class Collider : Component { }
    public class BoxCollider : Collider { public Vector3 size; }
    public enum LightType { Directional, Point, Spot } public enum LightShadows { None, Hard, Soft }
    public class Light : Behaviour { public LightType type; public LightShadows shadows; public float shadowStrength, shadowBias, shadowNormalBias, range, spotAngle, innerSpotAngle, intensity; public Color color; }
    public struct LOD { public float h; public Renderer[] r; public LOD(float h, Renderer[] r) { this.h = h; this.r = r; } }
    public class LODGroup : Component { public void SetLODs(LOD[] l) { } public void RecalculateBounds() { } }
    public enum ReflectionProbeClearFlags { SolidColor }
    public class ReflectionProbe : Behaviour
    {
        public Rendering.ReflectionProbeMode mode; public Rendering.ReflectionProbeRefreshMode refreshMode; public Rendering.ReflectionProbeTimeSlicingMode timeSlicingMode;
        public Vector3 size; public bool boxProjection, hdr; public float blendDistance, nearClipPlane, farClipPlane, shadowDistance; public int resolution, cullingMask;
        public ReflectionProbeClearFlags clearFlags; public Color backgroundColor;
    }
}
namespace UnityEngine.Rendering
{
    public enum IndexFormat { UInt16, UInt32 } public enum ShadowCastingMode { Off, On, TwoSided, ShadowsOnly }
    public enum LightProbeUsage { Off } public enum ReflectionProbeUsage { Off, BlendProbes }
    public enum BlendMode { Zero = 0, One = 1, SrcAlpha = 5, OneMinusSrcAlpha = 10 } public enum RenderQueue { Geometry = 2000, Transparent = 3000 }
    public enum ReflectionProbeMode { Baked, Realtime } public enum ReflectionProbeRefreshMode { OnAwake, EveryFrame, ViaScripting } public enum ReflectionProbeTimeSlicingMode { AllFacesAtOnce, IndividualFaces }
}
