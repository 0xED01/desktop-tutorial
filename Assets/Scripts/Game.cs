using UnityEngine;

namespace OfficeExplorer
{
    public enum GameState { MainMenu, Playing, Paused, Inspecting }

    /// <summary>
    /// 游戏入口与总控。挂在 Main 场景唯一的 GameObject 上：
    /// 启动时生成办公室、人物、相机、灯光、音频和界面，并管理
    /// 主菜单 / 探索 / 暂停 / 查看屏幕 四个状态以及所有交互。
    /// </summary>
    public class Game : MonoBehaviour
    {
        public static Game I { get; private set; }

        public GameState State { get; private set; } = GameState.MainMenu;
        public bool HudHidden { get; private set; }
        public OfficeScene Scene { get; private set; }
        public PlayerController Player { get; private set; }
        public CameraRig Cam { get; private set; }
        public LightingDirector Lighting { get; private set; }
        public GameInput Input { get; private set; }
        public GameUI UI { get; private set; }

        enum ActionKind { None, Sit, Stand, ViewScreen, Switch }
        ActionKind action;
        SeatInfo actionSeat; ScreenInfo actionScreen;
        float scanTimer;
        bool wasFirstPerson;

        void Awake()
        {
            if (I != null && I != this) { Destroy(gameObject); return; }
            I = this;
            Screen.sleepTimeout = SleepTimeout.NeverSleep;
            Screen.orientation = ScreenOrientation.AutoRotation;
            Screen.autorotateToPortrait = false; Screen.autorotateToPortraitUpsideDown = false;
            Screen.autorotateToLandscapeLeft = true; Screen.autorotateToLandscapeRight = true;
            Physics.autoSyncTransforms = true;

            GameSettings.Load();
            AudioManager.Create();
            Scene = OfficeBuilder.Build(null);
            Lighting = LightingDirector.Create(Scene);
            Player = PlayerController.Create(Scene.spawnPos, Scene.spawnYaw, Scene.bounds);
            Cam = CameraRig.Create(Player, Scene.bounds);
            Input = gameObject.AddComponent<GameInput>();
            UI = GameUI.Create(this);

            GameInput.OnInteractKey += Interact;
            GameInput.OnViewKey += ToggleView;
            GameInput.OnPauseKey += OnPauseKey;

            ApplySettings();
            EnterMenu();
        }

        void OnDestroy()
        {
            if (I != this) return;
            GameInput.OnInteractKey -= Interact;
            GameInput.OnViewKey -= ToggleView;
            GameInput.OnPauseKey -= OnPauseKey;
            Time.timeScale = 1f;
            I = null;
        }

        // ---------- 状态切换 ----------
        void EnterMenu()
        {
            State = GameState.MainMenu;
            Time.timeScale = 1f;
            GameInput.Enabled = false;
            Player.Frozen = true;
            Cam.SetMode(CamMode.Menu);
            Player.Rig.SetFirstPerson(false);
            Lighting.SetDepthOfField(true, 7f, 20f);
            AudioManager.SetAmbient(true, 0.14f);
            UI.SetInteract(null);
            UI.Refresh();
        }

        public void StartExploring()
        {
            State = GameState.Playing;
            Time.timeScale = 1f;
            GameInput.Enabled = true;
            Player.Frozen = false;
            Cam.SetMode(CamMode.Follow);
            Cam.SetFirstPerson(Cam.FirstPerson);
            Cam.SnapBehindPlayer();
            Lighting.SetDepthOfField(false);
            AudioManager.SetAmbient(true, 0.22f);
            UI.Refresh();
        }

        public void Pause()
        {
            if (State != GameState.Playing) return;
            State = GameState.Paused;
            Time.timeScale = 0f;
            GameInput.Enabled = false;
            UI.Refresh();
        }

        public void Resume()
        {
            if (State != GameState.Paused) return;
            State = GameState.Playing;
            Time.timeScale = 1f;
            GameInput.Enabled = true;
            UI.Refresh();
        }

        void OnPauseKey()
        {
            if (UI.SubPanelOpen) { UI.ClosePanels(); return; }
            if (State == GameState.Playing) Pause();
            else if (State == GameState.Paused) Resume();
            else if (State == GameState.Inspecting) Interact();
        }

        public void ReturnToMenu()
        {
            if (Player.IsSitting) Player.StandUp();
            Player.Respawn();
            EnterMenu();
        }

        public void Quit()
        {
            GameSettings.Save();
#if UNITY_EDITOR
            UnityEditor.EditorApplication.isPlaying = false;
#else
            Application.Quit();
#endif
        }

        void OnApplicationPause(bool paused)
        {
            if (paused && State == GameState.Playing) Pause();
        }

        // ---------- 功能 ----------
        public void ApplySettings()
        {
            GameSettings.Apply(Cam, Lighting, Scene);
        }

        public void ResetPlayer()
        {
            if (Player.IsSitting) Player.StandUp();
            Player.Respawn();
            Cam.SnapBehindPlayer();
            UI.Toast("已回到出生点");
            if (State == GameState.Paused) Resume();
        }

        public void ToggleLights()
        {
            Lighting.ToggleLights();
            AudioManager.Play(AudioManager.Sfx.Switch, 0.8f);
            UI.Toast(Lighting.LightsOn ? "灯光已打开" : "灯光已关闭");
            UI.Refresh();
        }

        public void CycleTime()
        {
            Lighting.CycleTime();
            UI.Toast("时间：" + LightingDirector.TimeNames[(int)Lighting.TimeOfDay]);
            UI.Refresh();
        }

        public void ToggleView()
        {
            if (State != GameState.Playing) return;
            Cam.SetFirstPerson(!Cam.FirstPerson);
            UI.Refresh();
        }

        public void SetHudHidden(bool hidden)
        {
            HudHidden = hidden;
            UI.Refresh();
        }

        /// <summary>交互键：坐下 / 站起 / 查看屏幕 / 返回 / 开关灯。</summary>
        public void Interact()
        {
            if (State == GameState.Inspecting)
            {
                State = GameState.Playing;
                Player.Frozen = false;
                Cam.SetMode(CamMode.Follow);
                Cam.SetFirstPerson(wasFirstPerson);
                Lighting.SetDepthOfField(false);
                UI.Refresh();
                return;
            }
            if (State != GameState.Playing) return;
            switch (action)
            {
                case ActionKind.Sit:
                    Player.Sit(actionSeat);
                    break;
                case ActionKind.Stand:
                    Player.StandUp();
                    break;
                case ActionKind.Switch:
                    ToggleLights();
                    break;
                case ActionKind.ViewScreen:
                    State = GameState.Inspecting;
                    Player.Frozen = true;
                    wasFirstPerson = Cam.FirstPerson;
                    Vector3 eye = actionScreen.center + actionScreen.normal * 0.62f + Vector3.up * 0.03f;
                    Cam.Inspect(eye, Quaternion.LookRotation(actionScreen.center - eye, Vector3.up));
                    Lighting.SetDepthOfField(true, 1.1f, 3.5f);
                    UI.SetInteract(null);
                    UI.Refresh();
                    break;
            }
            scanTimer = 0f;
        }

        void Update()
        {
            if (State != GameState.Playing) return;
            scanTimer -= Time.deltaTime;
            if (scanTimer > 0f) return;
            scanTimer = 0.1f;
            ScanInteractables();
        }

        void ScanInteractables()
        {
            action = ActionKind.None;
            string label = null;
            if (Player.IsSitting) { action = ActionKind.Stand; label = "站起"; }
            else if (Player.Grounded)
            {
                Vector3 p = Player.transform.position;
                Vector3 look = Quaternion.Euler(0, Cam.Yaw, 0) * Vector3.forward;
                float best = float.MaxValue;

                foreach (var s in Scene.switches)
                {
                    Vector3 d = s.pos - p; d.y = 0;
                    float dist = d.magnitude;
                    if (dist < 1.5f && dist * 0.7f < best) { best = dist * 0.7f; action = ActionKind.Switch; label = Lighting.LightsOn ? "关灯" : "开灯"; }
                }
                foreach (var s in Scene.screens)
                {
                    Vector3 d = p - s.center; d.y = 0;
                    float dist = d.magnitude;
                    // 必须站在屏幕正面且大致面向它
                    if (dist < 1.9f && Vector3.Dot(d.normalized, s.normal) > 0.45f && Vector3.Dot(look, -d.normalized) > 0.2f && dist * 0.85f < best)
                    {
                        best = dist * 0.85f; action = ActionKind.ViewScreen; actionScreen = s; label = "查看屏幕";
                    }
                }
                foreach (var s in Scene.seats)
                {
                    Vector3 d = s.seatPos - p; d.y = 0;
                    float dist = d.magnitude;
                    if (dist < 1.25f && dist < best) { best = dist; action = ActionKind.Sit; actionSeat = s; label = "坐下"; }
                }
            }
            UI.SetInteract(label);
        }
    }
}
