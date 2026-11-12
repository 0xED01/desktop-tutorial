using System.Collections.Generic;
using UnityEngine;
using UnityEngine.EventSystems;

namespace OfficeExplorer
{
    /// <summary>
    /// 统一输入层：触屏（左半屏浮动摇杆 + 右半屏滑动转镜头）以及编辑器 / 外接键鼠。
    /// 直接读取 Input.touches，当帧生效，没有 UI 事件转发带来的延迟。
    /// </summary>
    [DefaultExecutionOrder(-200)]
    public class GameInput : MonoBehaviour
    {
        public static Vector2 Move;                 // 摇杆向量，长度 0~1
        public static Vector2 LookDelta;            // 本帧镜头转动量（度）
        public static bool Run;                     // 奔跑状态（按钮切换 / Shift 按住）
        public static bool Enabled = true;          // 菜单打开时关闭
        static bool jumpQueued;

        public static bool JoystickActive { get; private set; }
        public static Vector2 JoystickOrigin { get; private set; }
        public static Vector2 JoystickKnob { get; private set; }

        public static event System.Action OnInteractKey, OnViewKey, OnPauseKey;

        int moveFinger = -1, lookFinger = -1;
        Vector2 lookLast;
        bool runToggle, movedWhileRunning;
        bool mouseLooking;
        Vector2 mouseLast;
        static readonly List<RaycastResult> hits = new List<RaycastResult>();

        public static void QueueJump() { jumpQueued = true; }
        public static bool ConsumeJump() { bool j = jumpQueued; jumpQueued = false; return j; }

        public void ToggleRun() { runToggle = !runToggle; movedWhileRunning = false; }
        public bool RunToggled => runToggle;

        float JoystickRadius => Screen.height * 0.13f;

        static bool OverUI(Vector2 pos)
        {
            var es = EventSystem.current;
            if (es == null) return false;
            var ped = new PointerEventData(es) { position = pos };
            hits.Clear();
            es.RaycastAll(ped, hits);
            return hits.Count > 0;
        }

        void Update()
        {
            LookDelta = Vector2.zero;
            if (!Enabled)
            {
                Move = Vector2.zero; JoystickActive = false; moveFinger = lookFinger = -1; mouseLooking = false; Run = false;
                if (Input.GetKeyDown(KeyCode.Escape)) OnPauseKey?.Invoke();
                return;
            }

            Vector2 move = Vector2.zero;
            float degPerPixel = 150f / Mathf.Max(Screen.height, 1);   // 滑过整个屏幕高度 ≈ 150°（再乘灵敏度）

            // ---------- 触屏 ----------
            bool moveSeen = false, lookSeen = false;
            for (int i = 0; i < Input.touchCount; i++)
            {
                Touch t = Input.GetTouch(i);
                if (t.phase == TouchPhase.Began)
                {
                    if (OverUI(t.position)) continue;
                    if (t.position.x < Screen.width * 0.42f && moveFinger < 0)
                    {
                        moveFinger = t.fingerId; JoystickOrigin = t.position; JoystickKnob = t.position;
                    }
                    else if (lookFinger < 0)
                    {
                        lookFinger = t.fingerId; lookLast = t.position;
                    }
                }
                if (t.fingerId == moveFinger)
                {
                    bool ended = t.phase == TouchPhase.Ended || t.phase == TouchPhase.Canceled;
                    if (ended) moveFinger = -1;
                    else
                    {
                        moveSeen = true;
                        Vector2 d = t.position - JoystickOrigin;
                        float r = JoystickRadius;
                        // 手指拖出范围时让摇杆底座跟随，保持方向灵敏
                        if (d.magnitude > r * 1.25f) JoystickOrigin = t.position - d.normalized * r * 1.25f;
                        d = t.position - JoystickOrigin;
                        Vector2 v = Vector2.ClampMagnitude(d / r, 1f);
                        float m = v.magnitude;
                        move = m < 0.12f ? Vector2.zero : v.normalized * Mathf.InverseLerp(0.12f, 1f, m);
                        JoystickKnob = JoystickOrigin + Vector2.ClampMagnitude(d, r);
                    }
                }
                else if (t.fingerId == lookFinger)
                {
                    bool ended = t.phase == TouchPhase.Ended || t.phase == TouchPhase.Canceled;
                    if (ended) lookFinger = -1;
                    else
                    {
                        lookSeen = true;
                        LookDelta += (t.position - lookLast) * degPerPixel;
                        lookLast = t.position;
                    }
                }
            }
            if (!moveSeen) moveFinger = -1;
            if (!lookSeen) lookFinger = -1;
            JoystickActive = moveFinger >= 0;

            // ---------- 键鼠（编辑器调试 / 外接设备） ----------
            Vector2 kb = new Vector2(
                (Input.GetKey(KeyCode.D) ? 1 : 0) - (Input.GetKey(KeyCode.A) ? 1 : 0),
                (Input.GetKey(KeyCode.W) ? 1 : 0) - (Input.GetKey(KeyCode.S) ? 1 : 0));
            if (kb.sqrMagnitude > 0) move = kb.normalized;
            if (Input.touchCount == 0 && Input.mousePresent)
            {
                if (Input.GetMouseButtonDown(1) || (Input.GetMouseButtonDown(0) && !OverUI(Input.mousePosition)))
                {
                    mouseLooking = true; mouseLast = Input.mousePosition;
                }
                if (!Input.GetMouseButton(0) && !Input.GetMouseButton(1)) mouseLooking = false;
                if (mouseLooking)
                {
                    Vector2 mp = Input.mousePosition;
                    LookDelta += (mp - mouseLast) * degPerPixel;
                    mouseLast = mp;
                }
            }
            if (Input.GetKeyDown(KeyCode.Space)) QueueJump();
            if (Input.GetKeyDown(KeyCode.E)) OnInteractKey?.Invoke();
            if (Input.GetKeyDown(KeyCode.V)) OnViewKey?.Invoke();
            if (Input.GetKeyDown(KeyCode.Escape)) OnPauseKey?.Invoke();

            Move = move;
            // 奔跑键可在静止时预先按下；跑起来之后一旦停下则自动取消
            if (runToggle && move.sqrMagnitude > 0.01f) movedWhileRunning = true;
            if (runToggle && movedWhileRunning && move.sqrMagnitude < 0.01f && !JoystickActive) { runToggle = false; movedWhileRunning = false; }
            Run = runToggle || Input.GetKey(KeyCode.LeftShift);
        }
    }
}
