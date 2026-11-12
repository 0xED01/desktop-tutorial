using UnityEngine;

namespace OfficeExplorer
{
    /// <summary>
    /// 角色动画驱动。优先使用编辑器烘焙出的 Animator 状态机
    /// （Resources/PlayerAnimator：Locomotion 混合树 → Jump → Air → Land，以及 Sit）；
    /// 若控制器资源不存在，则退回到等价的程序化状态机，保证任何情况下人物都有动画。
    /// </summary>
    public class RigAnimator : MonoBehaviour
    {
        public const string ControllerResource = "PlayerAnimator";
        static readonly int SpeedId = Animator.StringToHash("Speed");
        static readonly int GroundedId = Animator.StringToHash("Grounded");
        static readonly int SittingId = Animator.StringToHash("Sitting");
        static readonly int JumpId = Animator.StringToHash("Jump");

        CharacterRig rig;
        Animator animator;
        public bool UsingAnimator { get; private set; }

        float speed, smoothedSpeed;
        bool grounded = true, sitting;

        // 程序化回退用的状态
        readonly Pose pIdle = new Pose(), pWalk = new Pose(), pRun = new Pose(), pTmp = new Pose(), pTarget = new Pose(), pCurrent = new Pose();
        float phase, idleTime, airBlend, sitBlend, landTimer = 1f, jumpTimer = 1f, airTime;
        bool wasGrounded = true;

        public void Init(CharacterRig r)
        {
            rig = r;
            var ctrl = Resources.Load<RuntimeAnimatorController>(ControllerResource);
            if (ctrl != null)
            {
                animator = rig.gameObject.AddComponent<Animator>();
                animator.runtimeAnimatorController = ctrl;
                animator.applyRootMotion = false;
                animator.cullingMode = AnimatorCullingMode.AlwaysAnimate;
                UsingAnimator = true;
            }
            else
            {
                Debug.Log("[办公室] 未找到烘焙的 Animator 控制器，使用程序动画回退。可执行菜单「办公室探索/一键配置工程」生成。");
            }
            PoseLibrary.Evaluate(AnimClip.Idle, 0, pCurrent);
        }

        public void SetState(float horizontalSpeed, bool isGrounded, bool isSitting)
        {
            speed = horizontalSpeed; grounded = isGrounded; sitting = isSitting;
        }

        public void TriggerJump()
        {
            if (UsingAnimator) animator.SetTrigger(JumpId);
            jumpTimer = 0f;
        }

        void Update()
        {
            float dt = Time.deltaTime;
            if (dt <= 0f) return;
            smoothedSpeed = Mathf.Lerp(smoothedSpeed, speed, 1f - Mathf.Exp(-12f * dt));
            if (UsingAnimator)
            {
                animator.SetFloat(SpeedId, smoothedSpeed);
                animator.SetBool(GroundedId, grounded);
                animator.SetBool(SittingId, sitting);
                // 落地瞬间清掉残留的起跳触发器，避免落地后误播一次起跳
                if (grounded && !wasGrounded) animator.ResetTrigger(JumpId);
                wasGrounded = grounded;
                return;
            }
            Procedural(dt);
        }

        void Procedural(float dt)
        {
            // 步频随速度变化，使步幅与位移匹配，避免滑步
            float sp = smoothedSpeed;
            float cycleLen = sp < PoseLibrary.WalkSpeed
                ? PoseLibrary.Length(AnimClip.Walk)
                : Mathf.Lerp(PoseLibrary.Length(AnimClip.Walk), PoseLibrary.Length(AnimClip.Run), Mathf.InverseLerp(PoseLibrary.WalkSpeed, PoseLibrary.RunSpeed, sp));
            float refSpeed = Mathf.Max(sp < PoseLibrary.WalkSpeed ? PoseLibrary.WalkSpeed : sp, 0.01f);
            phase = Mathf.Repeat(phase + dt / cycleLen * Mathf.Clamp(sp / refSpeed, 0.35f, 1.3f), 1f);
            idleTime += dt / PoseLibrary.Length(AnimClip.Idle);

            PoseLibrary.Evaluate(AnimClip.Idle, idleTime, pIdle);
            PoseLibrary.Evaluate(AnimClip.Walk, phase, pWalk);
            PoseLibrary.Evaluate(AnimClip.Run, phase, pRun);
            if (sp <= PoseLibrary.WalkSpeed) Pose.Lerp(pIdle, pWalk, Mathf.Clamp01(sp / PoseLibrary.WalkSpeed), pTarget);
            else Pose.Lerp(pWalk, pRun, Mathf.InverseLerp(PoseLibrary.WalkSpeed, PoseLibrary.RunSpeed, sp), pTarget);

            // 起跳 → 滞空 → 落地
            if (!grounded) airTime += dt; else airTime = 0f;
            if (grounded && !wasGrounded) landTimer = 0f;
            wasGrounded = grounded;
            jumpTimer += dt; landTimer += dt;
            airBlend = Mathf.MoveTowards(airBlend, (!grounded && airTime > 0.05f) ? 1f : 0f, dt * 7f);
            if (airBlend > 0f)
            {
                float jt = jumpTimer / PoseLibrary.Length(AnimClip.Jump);
                if (jt < 1f) PoseLibrary.Evaluate(AnimClip.Jump, jt, pTmp);
                else PoseLibrary.Evaluate(AnimClip.Air, airTime, pTmp);
                Pose.Lerp(pTarget, pTmp, airBlend, pTarget);
            }
            float lt = landTimer / PoseLibrary.Length(AnimClip.Land);
            if (grounded && lt < 1f)
            {
                PoseLibrary.Evaluate(AnimClip.Land, lt, pTmp);
                // Land 片段的偏移量叠加在当前运动姿态上
                pTarget.hipsPos.y += pTmp.hipsPos.y - CharacterRig.HipsHeight;
                for (int i = 0; i < CharacterRig.JointCount; i++) pTarget.rot[i] = pTarget.rot[i] * pTmp.rot[i];
            }

            sitBlend = Mathf.MoveTowards(sitBlend, sitting ? 1f : 0f, dt * 3.5f);
            if (sitBlend > 0f)
            {
                PoseLibrary.Evaluate(AnimClip.Sit, idleTime * 0.8f, pTmp);
                Pose.Lerp(pTarget, pTmp, Mathf.SmoothStep(0, 1, sitBlend), pTarget);
            }

            // 轻微的整体平滑，消除状态切换时的突变
            Pose.Lerp(pCurrent, pTarget, 1f - Mathf.Exp(-22f * dt), pCurrent);
            rig.ApplyPose(pCurrent);
        }
    }
}
