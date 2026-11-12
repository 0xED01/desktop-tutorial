using UnityEngine;
using UnityEngine.Rendering.Universal;

namespace OfficeExplorer
{
    public enum CamMode { Menu, Follow, Inspect }

    /// <summary>
    /// 相机：第三人称环绕跟随（球形扫掠防穿墙、遇障自动拉近）、第一人称、
    /// 主菜单的环游镜头以及查看显示器时的特写镜头。
    /// 在所有角色移动之后（LateUpdate）更新，避免抖动。
    /// </summary>
    [DefaultExecutionOrder(100)]
    public class CameraRig : MonoBehaviour
    {
        public Camera Cam { get; private set; }
        public UniversalAdditionalCameraData CamData { get; private set; }
        public CamMode Mode { get; private set; } = CamMode.Menu;
        public bool FirstPerson { get; private set; }
        public float Yaw => yaw;

        PlayerController player;
        Bounds room;
        float yaw, pitch = 12f;
        float distance = 3.2f;
        const float MaxDistance = 3.4f, MinDistance = 0.35f, ProbeRadius = 0.22f;
        Vector3 pivotSmoothed, pivotVel;
        Vector3 inspectPos; Quaternion inspectRot;
        float menuAngle = 200f;
        float fov = 60f;
        int mask;

        public static CameraRig Create(PlayerController p, Bounds roomBounds)
        {
            var go = new GameObject("Main Camera") { tag = "MainCamera" };
            var rig = go.AddComponent<CameraRig>();
            rig.Cam = go.AddComponent<Camera>();
            rig.Cam.nearClipPlane = 0.08f;
            rig.Cam.farClipPlane = 120f;
            rig.Cam.fieldOfView = 60f;
            rig.Cam.clearFlags = CameraClearFlags.SolidColor;
            rig.Cam.backgroundColor = new Color(0.02f, 0.02f, 0.03f);
            rig.Cam.allowHDR = true;
            rig.Cam.allowMSAA = true;
            go.AddComponent<AudioListener>();
            rig.CamData = go.AddComponent<UniversalAdditionalCameraData>();
            rig.CamData.renderPostProcessing = true;
            rig.CamData.renderShadows = true;
            rig.player = p;
            rig.room = roomBounds;
            rig.yaw = p.transform.eulerAngles.y;
            rig.mask = ~(1 << 2) & ~(1 << 5);        // 忽略角色（Ignore Raycast）与 UI 层
            rig.pivotSmoothed = rig.PivotTarget();
            return rig;
        }

        Vector3 PivotTarget()
        {
            float h = player.IsSitting ? 1.12f : 1.52f;
            return player.transform.position + Vector3.up * h;
        }

        public void SetMode(CamMode m)
        {
            Mode = m;
            if (m == CamMode.Follow) pivotSmoothed = PivotTarget();
        }

        public void SetFirstPerson(bool fp)
        {
            FirstPerson = fp;
            player.FirstPerson = fp;
            player.Rig.SetFirstPerson(fp && Mode != CamMode.Menu);
            pitch = Mathf.Clamp(pitch, fp ? -80f : -30f, fp ? 80f : 70f);
        }

        public void Inspect(Vector3 pos, Quaternion rot)
        {
            inspectPos = pos; inspectRot = rot;
            Mode = CamMode.Inspect;
            player.Rig.SetFirstPerson(false);
        }

        public void SnapBehindPlayer()
        {
            yaw = player.transform.eulerAngles.y;
            pitch = FirstPerson ? 0f : 12f;
            pivotSmoothed = PivotTarget();
        }

        void LateUpdate()
        {
            float dt = Time.unscaledDeltaTime;
            Vector3 pos; Quaternion rot; float targetFov;

            if (Mode == CamMode.Menu)
            {
                // 主菜单：沿椭圆轨道缓慢环视办公室
                menuAngle += dt * 4.5f;
                float a = menuAngle * Mathf.Deg2Rad;
                Vector3 c = room.center;
                pos = new Vector3(c.x + Mathf.Cos(a) * room.extents.x * 0.50f, 1.95f, c.z + Mathf.Sin(a) * room.extents.z * 0.55f);
                rot = Quaternion.LookRotation(new Vector3(c.x, 0.9f, c.z) - pos, Vector3.up);
                targetFov = 58f;
                transform.SetPositionAndRotation(pos, rot);
                Cam.fieldOfView = targetFov; fov = targetFov;
                return;
            }

            if (Mode == CamMode.Inspect)
            {
                float k = 1f - Mathf.Exp(-7f * dt);
                transform.SetPositionAndRotation(Vector3.Lerp(transform.position, inspectPos, k), Quaternion.Slerp(transform.rotation, inspectRot, k));
                fov = Mathf.Lerp(fov, 38f, k);
                Cam.fieldOfView = fov;
                return;
            }

            // ---------- 跟随模式 ----------
            if (Time.timeScale > 0f && !player.Frozen)
            {
                Vector2 look = GameInput.LookDelta * GameSettings.Sensitivity;
                yaw += look.x;
                pitch -= look.y * (GameSettings.InvertY ? -1f : 1f);
            }
            pitch = Mathf.Clamp(pitch, FirstPerson ? -80f : -30f, FirstPerson ? 80f : 70f);
            yaw = Mathf.Repeat(yaw, 360f);
            player.CameraYaw = yaw;
            rot = Quaternion.Euler(pitch, yaw, 0);

            Vector3 target = PivotTarget();
            pivotSmoothed = Vector3.SmoothDamp(pivotSmoothed, target, ref pivotVel, 0.06f, Mathf.Infinity, Mathf.Max(dt, 1e-4f));
            // 垂直方向稍慢，水平方向紧跟，防止高速奔跑时角色跑出画面
            Vector3 pivot = new Vector3(Mathf.Lerp(pivotSmoothed.x, target.x, 0.6f), pivotSmoothed.y, Mathf.Lerp(pivotSmoothed.z, target.z, 0.6f));

            if (FirstPerson && !player.IsSitting)
            {
                pos = player.transform.position + Vector3.up * 1.64f + Quaternion.Euler(0, yaw, 0) * Vector3.forward * 0.10f;
                targetFov = 68f;
            }
            else if (FirstPerson)
            {
                pos = player.transform.position + Vector3.up * 1.20f + player.transform.forward * 0.12f;
                targetFov = 68f;
            }
            else
            {
                // 从轴心向后做球形扫掠：碰到墙 / 家具立即拉近，离开后平滑恢复
                Vector3 back = rot * Vector3.back;
                Vector3 shoulder = rot * Vector3.right * 0.22f;
                Vector3 origin = pivot;
                if (!Physics.SphereCast(pivot, ProbeRadius * 0.8f, shoulder.normalized, out _, 0.22f, mask, QueryTriggerInteraction.Ignore)) origin += shoulder;
                float want = MaxDistance;
                if (Physics.SphereCast(origin, ProbeRadius, back, out RaycastHit hit, MaxDistance, mask, QueryTriggerInteraction.Ignore))
                    want = Mathf.Max(hit.distance - 0.03f, MinDistance);
                else if (Physics.CheckSphere(origin, ProbeRadius, mask, QueryTriggerInteraction.Ignore))
                    want = MinDistance;
                distance = want < distance ? want : Mathf.Lerp(distance, want, 1f - Mathf.Exp(-4.5f * dt));
                pos = origin + back * distance;
                targetFov = 60f;
            }

            fov = Mathf.Lerp(fov, targetFov, 1f - Mathf.Exp(-8f * dt));
            Cam.fieldOfView = fov;
            transform.SetPositionAndRotation(pos, rot);
        }
    }
}
