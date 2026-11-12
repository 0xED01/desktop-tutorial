using UnityEngine;

namespace OfficeExplorer
{
    /// <summary>
    /// 第三人称 / 第一人称通用的角色控制器（CharacterController 物理碰撞）。
    /// 支持行走、奔跑、跳跃（含跳跃缓冲与土狼时间）、坐下 / 站起、越界自动复位。
    /// </summary>
    [RequireComponent(typeof(CharacterController))]
    public class PlayerController : MonoBehaviour
    {
        public const float Height = 1.75f, Radius = 0.27f;
        const float Gravity = -22f, JumpSpeed = 6.3f, GroundAccel = 26f, AirAccel = 7f, TurnSpeed = 13f;

        public CharacterRig Rig { get; private set; }
        public RigAnimator Anim { get; private set; }
        public bool IsSitting { get; private set; }
        public bool Frozen;                         // 查看屏幕 / 菜单时冻结输入
        public bool FirstPerson;
        public float CameraYaw;                     // 由相机每帧写入
        public Vector3 Velocity => horizontal + Vector3.up * vertical;
        public bool Grounded => grounded;

        CharacterController cc;
        Vector3 horizontal;
        float vertical;
        bool grounded = true;
        float lastGroundedTime, jumpBufferedTime = -10f, stepAccum;
        Vector3 preSitPos; Quaternion preSitRot;
        Vector3 spawnPos; float spawnYaw;
        Bounds playArea;

        public static PlayerController Create(Vector3 pos, float yaw, Bounds area)
        {
            var go = new GameObject("Player") { layer = 2 };
            go.transform.SetPositionAndRotation(pos, Quaternion.Euler(0, yaw, 0));
            var cc = go.AddComponent<CharacterController>();
            cc.height = Height; cc.radius = Radius; cc.center = new Vector3(0, Height * 0.5f, 0);
            cc.stepOffset = 0.28f; cc.slopeLimit = 45f; cc.skinWidth = 0.03f; cc.minMoveDistance = 0f;
            var p = go.AddComponent<PlayerController>();
            p.cc = cc;
            p.spawnPos = pos; p.spawnYaw = yaw; p.playArea = area;
            p.Rig = CharacterRig.Create(go.transform);
            p.Anim = go.AddComponent<RigAnimator>();
            p.Anim.Init(p.Rig);
            return p;
        }

        public void Teleport(Vector3 pos, float yaw)
        {
            cc.enabled = false;
            transform.SetPositionAndRotation(pos, Quaternion.Euler(0, yaw, 0));
            cc.enabled = true;
            horizontal = Vector3.zero; vertical = 0f;
        }

        public void Respawn()
        {
            if (IsSitting) { IsSitting = false; }
            Teleport(spawnPos, spawnYaw);
        }

        public void Sit(SeatInfo seat)
        {
            if (IsSitting) return;
            preSitPos = transform.position; preSitRot = transform.rotation;
            IsSitting = true;
            cc.enabled = false;
            transform.SetPositionAndRotation(seat.seatPos, Quaternion.Euler(0, seat.yaw, 0));
            horizontal = Vector3.zero; vertical = 0f;
        }

        public void StandUp()
        {
            if (!IsSitting) return;
            IsSitting = false;
            transform.SetPositionAndRotation(preSitPos, preSitRot);
            cc.enabled = true;
        }

        void Update()
        {
            float dt = Time.deltaTime;
            if (dt <= 0f) return;

            if (IsSitting)
            {
                GameInput.ConsumeJump();
                Anim.SetState(0f, true, true);
                return;
            }

            Vector2 mv = Frozen ? Vector2.zero : GameInput.Move;
            bool run = !Frozen && GameInput.Run;
            Quaternion camRot = Quaternion.Euler(0, CameraYaw, 0);
            Vector3 wish = camRot * new Vector3(mv.x, 0, mv.y);
            float mag = Mathf.Clamp01(mv.magnitude);
            float targetSpeed = run ? PoseLibrary.RunSpeed : PoseLibrary.WalkSpeed * Mathf.Lerp(0.45f, 1f, mag);
            Vector3 desired = mag > 0.01f ? wish.normalized * targetSpeed : Vector3.zero;
            horizontal = Vector3.MoveTowards(horizontal, desired, (grounded ? GroundAccel : AirAccel) * dt);

            // 跳跃
            if (GameInput.ConsumeJump() && !Frozen) jumpBufferedTime = Time.time;
            bool canJump = grounded || Time.time - lastGroundedTime < 0.12f;
            if (canJump && Time.time - jumpBufferedTime < 0.14f && vertical <= 0.01f)
            {
                vertical = JumpSpeed;
                jumpBufferedTime = -10f; lastGroundedTime = -10f; grounded = false;
                Anim.TriggerJump();
                AudioManager.Play(AudioManager.Sfx.Jump, 0.5f);
            }
            if (grounded && vertical < 0f) vertical = -2.5f;
            vertical += Gravity * dt;

            float fallSpeed = vertical;
            Vector3 before = transform.position;
            CollisionFlags flags = cc.Move((horizontal + Vector3.up * vertical) * dt);
            Vector3 moved = transform.position - before;
            bool nowGrounded = (flags & CollisionFlags.Below) != 0;
            if ((flags & CollisionFlags.Above) != 0 && vertical > 0f) vertical = 0f;
            if (nowGrounded)
            {
                if (!grounded && fallSpeed < -3.5f) AudioManager.Play(AudioManager.Sfx.Land, Mathf.InverseLerp(-3f, -9f, fallSpeed) * 0.7f + 0.3f);
                lastGroundedTime = Time.time;
            }
            grounded = nowGrounded;

            // 实际水平速度（被家具挡住时动画自动减速，不会原地滑步）
            Vector3 flat = new Vector3(moved.x, 0, moved.z);
            float actualSpeed = flat.magnitude / dt;
            if ((flags & CollisionFlags.Sides) != 0) horizontal = Vector3.ClampMagnitude(horizontal, Mathf.Max(actualSpeed, 0.6f));

            // 朝向
            if (FirstPerson) transform.rotation = camRot;
            else if (horizontal.sqrMagnitude > 0.04f)
                transform.rotation = Quaternion.Slerp(transform.rotation, Quaternion.LookRotation(horizontal.normalized, Vector3.up), 1f - Mathf.Exp(-TurnSpeed * dt));

            Anim.SetState(actualSpeed, grounded || Time.time - lastGroundedTime < 0.1f, false);

            // 脚步声：按位移累计触发，与步幅一致
            if (grounded && actualSpeed > 0.4f)
            {
                stepAccum += flat.magnitude;
                float stride = Mathf.Lerp(0.78f, 1.45f, Mathf.InverseLerp(PoseLibrary.WalkSpeed, PoseLibrary.RunSpeed, actualSpeed));
                if (stepAccum >= stride)
                {
                    stepAccum = 0f;
                    AudioManager.Play(AudioManager.Sfx.Step, Mathf.Lerp(0.30f, 0.55f, actualSpeed / PoseLibrary.RunSpeed));
                }
            }
            else if (actualSpeed < 0.2f) stepAccum = 0.4f;

            // 安全网：万一离开可活动范围则复位
            Vector3 pos = transform.position;
            if (pos.y < -3f || pos.y > 6f || pos.x < playArea.min.x - 1f || pos.x > playArea.max.x + 1f || pos.z < playArea.min.z - 1f || pos.z > playArea.max.z + 1f)
                Respawn();
        }
    }
}
