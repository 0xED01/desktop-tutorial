using System;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.UI;

namespace OfficeExplorer
{
    /// <summary>
    /// 中文游戏界面：主菜单、暂停菜单、画质设置、操作设置、
    /// 触控 HUD（虚拟摇杆、跳跃 / 奔跑 / 视角 / 交互按钮、灯光与时间快捷键）。
    /// </summary>
    public class GameUI : MonoBehaviour
    {
        Game game;
        RectTransform safe;
        CanvasGroup hud;
        GameObject mainMenu, pauseMenu, graphicsPanel, controlsPanel, showHudBtn, backBtn, interactBtn;
        RectTransform joyBase, joyKnob;
        Image runImg;
        Text interactText, lightText, timeText, viewText, fpsText, toastText, pauseLightText, pauseTimeText;
        CanvasGroup toastGroup;
        float toastTimer;
        readonly List<Action> refreshers = new List<Action>();
        float fpsAccum; int fpsFrames; float fpsTimer;
        Rect lastSafe;

        public bool SubPanelOpen => graphicsPanel.activeSelf || controlsPanel.activeSelf;

        public static GameUI Create(Game g)
        {
            if (EventSystem.current == null)
            {
                var es = new GameObject("EventSystem");
                es.AddComponent<EventSystem>();
                es.AddComponent<StandaloneInputModule>();
            }
            var go = new GameObject("UI") { layer = 5 };
            var canvas = go.AddComponent<Canvas>();
            canvas.renderMode = RenderMode.ScreenSpaceOverlay;
            canvas.sortingOrder = 10;
            var scaler = go.AddComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1920, 1080);
            scaler.screenMatchMode = CanvasScaler.ScreenMatchMode.MatchWidthOrHeight;
            scaler.matchWidthOrHeight = 1f;
            go.AddComponent<GraphicRaycaster>();
            var ui = go.AddComponent<GameUI>();
            ui.game = g;
            ui.Build();
            return ui;
        }

        // ------------------------------------------------------------------
        void Build()
        {
            safe = UIKit.Stretch(UIKit.Node("SafeArea", transform));
            BuildHud();
            BuildOverlays();
            mainMenu = BuildMainMenu();
            pauseMenu = BuildPauseMenu();
            graphicsPanel = BuildGraphicsPanel();
            controlsPanel = BuildControlsPanel();
            graphicsPanel.SetActive(false); controlsPanel.SetActive(false);
            ApplySafeArea();
        }

        void ApplySafeArea()
        {
            Rect r = Screen.safeArea;
            lastSafe = r;
            if (Screen.width <= 0 || Screen.height <= 0) return;
            safe.anchorMin = new Vector2(r.xMin / Screen.width, r.yMin / Screen.height);
            safe.anchorMax = new Vector2(r.xMax / Screen.width, r.yMax / Screen.height);
            safe.offsetMin = safe.offsetMax = Vector2.zero;
        }

        void BuildHud()
        {
            var root = UIKit.Stretch(UIKit.Node("HUD", safe));
            hud = root.gameObject.AddComponent<CanvasGroup>();

            // 虚拟摇杆（仅作显示，输入由 GameInput 直接读取触点）
            var b = UIKit.Img("JoystickBase", root, new Color(1, 1, 1, 0.30f), UIKit.Ring);
            joyBase = b.rectTransform; joyBase.sizeDelta = new Vector2(290, 290);
            var inner = UIKit.Img("Inner", joyBase, new Color(0, 0, 0, 0.16f), UIKit.Circle);
            UIKit.Stretch(inner.rectTransform, 6, 6, 6, 6);
            var k = UIKit.Img("JoystickKnob", root, new Color(1, 1, 1, 0.55f), UIKit.Circle);
            joyKnob = k.rectTransform; joyKnob.sizeDelta = new Vector2(120, 120);

            // 右下角动作键
            Vector2 br = new Vector2(1, 0);
            var jump = UIKit.Btn("Jump", root, "跳跃", 38, UIKit.HudBtn, null, true);
            UIKit.Place((RectTransform)jump.transform, br, new Vector2(-200, 200), new Vector2(180, 180));
            jump.gameObject.AddComponent<PressButton>().onDown = GameInput.QueueJump;
            AddRing(jump.transform);

            var run = UIKit.Btn("Run", root, "奔跑", 32, UIKit.HudBtn, null, true);
            UIKit.Place((RectTransform)run.transform, br, new Vector2(-410, 150), new Vector2(140, 140));
            run.gameObject.AddComponent<PressButton>().onDown = () => game.Input.ToggleRun();
            runImg = run.GetComponent<Image>();
            AddRing(run.transform);

            var view = UIKit.Btn("View", root, "第一\n人称", 26, UIKit.HudBtn, () => game.ToggleView(), out viewText, true);
            UIKit.Place((RectTransform)view.transform, br, new Vector2(-150, 420), new Vector2(120, 120));
            AddRing(view.transform);

            var inter = UIKit.Btn("Interact", root, "坐下", 36, UIKit.Accent, () => game.Interact(), out interactText);
            UIKit.Place((RectTransform)inter.transform, br, new Vector2(-440, 350), new Vector2(250, 104));
            interactBtn = inter.gameObject;
            interactBtn.SetActive(false);

            // 右上角快捷键
            Vector2 tr = new Vector2(1, 1);
            var menu = UIKit.Btn("Menu", root, "菜单", 30, UIKit.HudBtn, () => game.Pause());
            UIKit.Place((RectTransform)menu.transform, tr, new Vector2(-100, -64), new Vector2(150, 76));
            var hide = UIKit.Btn("Hide", root, "隐藏界面", 28, UIKit.HudBtn, () => game.SetHudHidden(true));
            UIKit.Place((RectTransform)hide.transform, tr, new Vector2(-282, -64), new Vector2(190, 76));
            var time = UIKit.Btn("Time", root, "白天", 28, UIKit.HudBtn, () => game.CycleTime(), out timeText);
            UIKit.Place((RectTransform)time.transform, tr, new Vector2(-474, -64), new Vector2(170, 76));
            var light = UIKit.Btn("Light", root, "关灯", 28, UIKit.HudBtn, () => game.ToggleLights(), out lightText);
            UIKit.Place((RectTransform)light.transform, tr, new Vector2(-646, -64), new Vector2(150, 76));
        }

        static void AddRing(Transform t)
        {
            var ring = UIKit.Img("Ring", t, new Color(1, 1, 1, 0.38f), UIKit.Ring);
            UIKit.Stretch(ring.rectTransform);
            ring.transform.SetAsFirstSibling();
        }

        void BuildOverlays()
        {
            var show = UIKit.Btn("ShowHud", safe, "显示界面", 26, new Color(0, 0, 0, 0.22f), () => game.SetHudHidden(false));
            UIKit.Place((RectTransform)show.transform, new Vector2(1, 1), new Vector2(-120, -58), new Vector2(190, 68));
            show.GetComponentInChildren<Text>().color = new Color(1, 1, 1, 0.55f);
            showHudBtn = show.gameObject; showHudBtn.SetActive(false);

            var back = UIKit.Btn("Back", safe, "返回", 36, UIKit.Accent, () => game.Interact());
            UIKit.Place((RectTransform)back.transform, new Vector2(1, 0), new Vector2(-220, 130), new Vector2(260, 104));
            backBtn = back.gameObject; backBtn.SetActive(false);

            fpsText = UIKit.Txt("Fps", safe, "", 26, new Color(0.6f, 1f, 0.7f, 0.9f), TextAnchor.UpperLeft);
            UIKit.Place(fpsText.rectTransform, new Vector2(0, 1), new Vector2(190, -48), new Vector2(320, 60));

            var toastBg = UIKit.Img("Toast", safe, new Color(0, 0, 0, 0.55f), UIKit.Rounded);
            UIKit.Place(toastBg.rectTransform, new Vector2(0.5f, 1), new Vector2(0, -150), new Vector2(520, 76));
            toastText = UIKit.Txt("Text", toastBg.transform, "", 30, Color.white);
            toastGroup = toastBg.gameObject.AddComponent<CanvasGroup>();
            toastGroup.alpha = 0f; toastGroup.blocksRaycasts = false;
        }

        // ------------------------------------------------------------------
        GameObject Dim(string name, float alpha)
        {
            var dim = UIKit.Img(name, transform, new Color(0, 0, 0, alpha), null, true);
            UIKit.Stretch(dim.rectTransform);
            return dim.gameObject;
        }

        RectTransform CardIn(GameObject dim, Vector2 size, string title)
        {
            var card = UIKit.Img("Card", dim.transform, UIKit.Card, UIKit.Rounded, true);
            UIKit.Place(card.rectTransform, new Vector2(0.5f, 0.5f), Vector2.zero, size);
            var t = UIKit.Txt("Title", card.transform, title, 46, Color.white);
            t.rectTransform.anchorMin = new Vector2(0, 1); t.rectTransform.anchorMax = new Vector2(1, 1);
            t.rectTransform.pivot = new Vector2(0.5f, 1); t.rectTransform.anchoredPosition = new Vector2(0, -28); t.rectTransform.sizeDelta = new Vector2(0, 70);
            return card.rectTransform;
        }

        Button MenuButton(Transform parent, string label, float y, Action onClick, bool primary = false, float width = 520)
        {
            var b = UIKit.Btn(label, parent, label, 36, primary ? UIKit.Accent : UIKit.Soft, onClick);
            var rt = (RectTransform)b.transform;
            rt.anchorMin = rt.anchorMax = new Vector2(0.5f, 1); rt.pivot = new Vector2(0.5f, 1);
            rt.anchoredPosition = new Vector2(0, y); rt.sizeDelta = new Vector2(width, 92);
            return b;
        }

        GameObject BuildMainMenu()
        {
            var dim = Dim("MainMenu", 0.38f);
            var title = UIKit.Txt("Title", dim.transform, "办公室自由探索", 104, Color.white);
            UIKit.Place(title.rectTransform, new Vector2(0.5f, 1), new Vector2(0, -190), new Vector2(1400, 140));
            var shadow = title.gameObject.AddComponent<Shadow>();
            shadow.effectColor = new Color(0, 0, 0, 0.6f); shadow.effectDistance = new Vector2(3, -3);
            var sub = UIKit.Txt("Sub", dim.transform, "一间可以自由走动的 3D 办公室 · 无任务 · 无倒计时 · 完全离线", 32, UIKit.TextDim);
            UIKit.Place(sub.rectTransform, new Vector2(0.5f, 1), new Vector2(0, -290), new Vector2(1500, 50));

            var col = UIKit.Node("Buttons", dim.transform);
            UIKit.Place(col, new Vector2(0.5f, 0.5f), new Vector2(0, -150), new Vector2(560, 480));
            MenuButton(col, "开始探索", 0, () => game.StartExploring(), true);
            MenuButton(col, "画质设置", -116, () => OpenPanel(graphicsPanel));
            MenuButton(col, "操作设置", -232, () => OpenPanel(controlsPanel));
            MenuButton(col, "退出游戏", -348, () => game.Quit());
            return dim;
        }

        GameObject BuildPauseMenu()
        {
            var dim = Dim("PauseMenu", 0.55f);
            var card = CardIn(dim, new Vector2(680, 900), "已暂停");
            MenuButton(card, "继续探索", -120, () => game.Resume(), true);
            MenuButton(card, "重置人物位置", -226, () => game.ResetPlayer());
            var lb = MenuButton(card, "关闭灯光", -332, () => game.ToggleLights());
            pauseLightText = lb.GetComponentInChildren<Text>();
            var tb = MenuButton(card, "时间：白天", -438, () => game.CycleTime());
            pauseTimeText = tb.GetComponentInChildren<Text>();
            MenuButton(card, "画质设置", -544, () => OpenPanel(graphicsPanel));
            MenuButton(card, "操作设置", -650, () => OpenPanel(controlsPanel));
            MenuButton(card, "返回主菜单", -756, () => game.ReturnToMenu());
            dim.SetActive(false);
            return dim;
        }

        // ---------- 设置行 ----------
        RectTransform Row(Transform card, string label, int index)
        {
            var row = UIKit.Node("Row_" + label, card);
            row.anchorMin = new Vector2(0, 1); row.anchorMax = new Vector2(1, 1); row.pivot = new Vector2(0.5f, 1);
            row.anchoredPosition = new Vector2(0, -116 - index * 78); row.sizeDelta = new Vector2(-100, 70);
            var t = UIKit.Txt("Label", row, label, 32, UIKit.TextDim, TextAnchor.MiddleLeft);
            t.rectTransform.anchorMax = new Vector2(0.45f, 1);
            return row;
        }

        void CycleRow(Transform card, string label, int index, string[] options, Func<int> get, Action<int> set)
        {
            var row = Row(card, label, index);
            Text value = null;
            Action<int> step = d =>
            {
                int n = options.Length, v = ((get() + d) % n + n) % n;
                set(v);
                OnSettingChanged();
            };
            var prev = UIKit.Btn("Prev", row, "<", 44, UIKit.Soft, () => step(-1));
            var prt = (RectTransform)prev.transform;
            prt.anchorMin = prt.anchorMax = new Vector2(0.5f, 0.5f); prt.anchoredPosition = new Vector2(0, 0); prt.sizeDelta = new Vector2(84, 66);
            var next = UIKit.Btn("Next", row, ">", 44, UIKit.Soft, () => step(1));
            var nrt = (RectTransform)next.transform;
            nrt.anchorMin = nrt.anchorMax = new Vector2(1, 0.5f); nrt.anchoredPosition = new Vector2(-42, 0); nrt.sizeDelta = new Vector2(84, 66);
            value = UIKit.Txt("Value", row, "", 32, Color.white);
            value.rectTransform.anchorMin = new Vector2(0.5f, 0); value.rectTransform.anchorMax = new Vector2(1, 1);
            value.rectTransform.offsetMin = new Vector2(48, 0); value.rectTransform.offsetMax = new Vector2(-88, 0);
            refreshers.Add(() => value.text = options[Mathf.Clamp(get(), 0, options.Length - 1)]);
        }

        void SliderRow(Transform card, string label, int index, float min, float max, Func<float> get, Action<float> set, Func<float, string> format)
        {
            var row = Row(card, label, index);
            Text value = UIKit.Txt("Value", row, "", 30, Color.white, TextAnchor.MiddleRight);
            value.rectTransform.anchorMin = new Vector2(0.86f, 0);
            bool muted = false;
            var slider = UIKit.MakeSlider(row, min, max, get(), v =>
            {
                if (muted) return;
                set(v); value.text = format(v);
                OnSettingChanged(false);
            });
            var srt = (RectTransform)slider.transform;
            srt.anchorMin = new Vector2(0.42f, 0.1f); srt.anchorMax = new Vector2(0.84f, 0.9f); srt.offsetMin = srt.offsetMax = Vector2.zero;
            refreshers.Add(() => { muted = true; slider.value = get(); muted = false; value.text = format(get()); });
        }

        void OnSettingChanged(bool refresh = true)
        {
            game.ApplySettings();
            GameSettings.Save();
            if (refresh) RefreshRows();
        }

        void RefreshRows() { foreach (var r in refreshers) r(); }

        void Custom() { GameSettings.Preset = 4; }

        GameObject BuildGraphicsPanel()
        {
            var dim = Dim("GraphicsPanel", 0.6f);
            var card = CardIn(dim, new Vector2(1180, 960), "画质设置");
            int i = 0;
            CycleRow(card, "画质预设", i++, GameSettings.PresetNames, () => GameSettings.Preset, v => { if (v < 4) GameSettings.ApplyPreset(v); else GameSettings.Preset = 4; });
            SliderRow(card, "渲染分辨率", i++, 0.5f, 1f, () => GameSettings.RenderScale, v => { GameSettings.RenderScale = Mathf.Round(v * 20f) / 20f; Custom(); }, v => Mathf.RoundToInt(Mathf.Round(v * 20f) * 5f) + "%");
            CycleRow(card, "阴影质量", i++, GameSettings.ShadowNames, () => GameSettings.ShadowQuality, v => { GameSettings.ShadowQuality = v; Custom(); });
            CycleRow(card, "抗锯齿", i++, GameSettings.AANames, () => GameSettings.AA, v => { GameSettings.AA = v; Custom(); });
            CycleRow(card, "环境光遮蔽 (SSAO)", i++, GameSettings.OnOff, () => GameSettings.AO ? 1 : 0, v => { GameSettings.AO = v == 1; Custom(); });
            CycleRow(card, "后期特效 (Bloom / 景深)", i++, GameSettings.OnOff, () => GameSettings.Post ? 1 : 0, v => { GameSettings.Post = v == 1; Custom(); });
            CycleRow(card, "灯光与细节", i++, GameSettings.DetailNames, () => GameSettings.LightDetail, v => { GameSettings.LightDetail = v; Custom(); });
            CycleRow(card, "最大帧率", i++, GameSettings.FpsNames, () => GameSettings.FpsIndex, v => GameSettings.FpsIndex = v);
            CycleRow(card, "显示帧率", i++, GameSettings.OnOff, () => GameSettings.ShowFps ? 1 : 0, v => GameSettings.ShowFps = v == 1);
            MenuButton(card, "返回", -116 - i * 78 - 16, () => ClosePanels(), true, 360);
            return dim;
        }

        GameObject BuildControlsPanel()
        {
            var dim = Dim("ControlsPanel", 0.6f);
            var card = CardIn(dim, new Vector2(1180, 900), "操作设置");
            int i = 0;
            SliderRow(card, "镜头灵敏度", i++, 0.3f, 2.5f, () => GameSettings.Sensitivity, v => GameSettings.Sensitivity = v, v => v.ToString("0.00"));
            CycleRow(card, "反转垂直视角", i++, GameSettings.OnOff, () => GameSettings.InvertY ? 1 : 0, v => GameSettings.InvertY = v == 1);
            SliderRow(card, "音量", i++, 0f, 1f, () => GameSettings.Volume, v => GameSettings.Volume = v, v => Mathf.RoundToInt(v * 100f) + "%");
            var help = UIKit.Txt("Help", card,
                "· 屏幕左侧：按住并拖动 = 虚拟摇杆，控制人物移动\n" +
                "· 屏幕右侧：滑动 = 转动镜头（360° 环视、抬头 / 低头）\n" +
                "· 右下角：跳跃、奔跑（点按切换，停下自动取消）、第一 / 第三人称切换\n" +
                "· 靠近办公椅出现「坐下」，靠近亮着的显示器出现「查看屏幕」，靠近墙上开关可开关灯\n" +
                "· 右上角：开关灯、切换白天 / 傍晚 / 夜间、隐藏界面、菜单", 28, UIKit.TextDim, TextAnchor.UpperLeft);
            help.lineSpacing = 1.25f;
            help.rectTransform.anchorMin = new Vector2(0, 0); help.rectTransform.anchorMax = new Vector2(1, 1);
            help.rectTransform.offsetMin = new Vector2(60, 150); help.rectTransform.offsetMax = new Vector2(-60, -116 - i * 78 - 20);
            MenuButton(card, "返回", -770, () => ClosePanels(), true, 360);
            return dim;
        }

        void OpenPanel(GameObject panel)
        {
            RefreshRows();
            mainMenu.SetActive(false); pauseMenu.SetActive(false);
            panel.SetActive(true);
        }

        public void ClosePanels()
        {
            graphicsPanel.SetActive(false); controlsPanel.SetActive(false);
            Refresh();
        }

        // ------------------------------------------------------------------
        public void Toast(string msg)
        {
            toastText.text = msg; toastTimer = 1.8f;
        }

        public void SetInteract(string label)
        {
            bool show = !string.IsNullOrEmpty(label);
            if (interactBtn.activeSelf != show) interactBtn.SetActive(show);
            if (show && interactText.text != label) interactText.text = label;
        }

        /// <summary>根据游戏状态刷新各层界面的显隐与文字。</summary>
        public void Refresh()
        {
            bool sub = SubPanelOpen;
            var st = game.State;
            mainMenu.SetActive(st == GameState.MainMenu && !sub);
            pauseMenu.SetActive(st == GameState.Paused && !sub);
            bool playing = st == GameState.Playing;
            bool hudVisible = playing && !game.HudHidden;
            hud.alpha = hudVisible ? 1f : 0f;
            hud.interactable = hudVisible; hud.blocksRaycasts = hudVisible;
            showHudBtn.SetActive(playing && game.HudHidden);
            backBtn.SetActive(st == GameState.Inspecting);

            string lt = game.Lighting.LightsOn ? "关灯" : "开灯";
            lightText.text = lt; pauseLightText.text = game.Lighting.LightsOn ? "关闭灯光" : "打开灯光";
            string tn = LightingDirector.TimeNames[(int)game.Lighting.TimeOfDay];
            timeText.text = tn; pauseTimeText.text = "时间：" + tn;
            viewText.text = game.Cam.FirstPerson ? "第三\n人称" : "第一\n人称";
        }

        void Update()
        {
            if (Screen.safeArea != lastSafe) ApplySafeArea();

            // 摇杆显示
            if (hud.alpha > 0f)
            {
                float unit = Screen.height / 1080f;
                Vector2 idle = new Vector2(Screen.safeArea.xMin + 270f * unit, Screen.safeArea.yMin + 260f * unit);
                Vector2 bp = GameInput.JoystickActive ? GameInput.JoystickOrigin : idle;
                Vector2 kp = GameInput.JoystickActive ? GameInput.JoystickKnob : idle;
                joyBase.position = bp; joyKnob.position = kp;
                runImg.color = game.Input.RunToggled ? UIKit.Accent : UIKit.HudBtn;
            }

            // 提示条淡出
            if (toastTimer > 0f) toastTimer -= Time.unscaledDeltaTime;
            toastGroup.alpha = Mathf.MoveTowards(toastGroup.alpha, toastTimer > 0f ? 1f : 0f, Time.unscaledDeltaTime * 5f);

            // 帧率
            if (GameSettings.ShowFps)
            {
                fpsAccum += Time.unscaledDeltaTime; fpsFrames++; fpsTimer += Time.unscaledDeltaTime;
                if (fpsTimer >= 0.5f)
                {
                    fpsText.text = "FPS " + Mathf.RoundToInt(fpsFrames / Mathf.Max(fpsAccum, 1e-4f));
                    fpsAccum = 0; fpsFrames = 0; fpsTimer = 0;
                }
            }
            else if (fpsText.text.Length > 0) fpsText.text = "";
        }
    }
}
