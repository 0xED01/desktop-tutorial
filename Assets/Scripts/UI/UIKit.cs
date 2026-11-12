using System;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.UI;

namespace OfficeExplorer
{
    /// <summary>按下即触发的按钮（跳跃 / 奔跑用，比 onClick 抬手触发更跟手）。</summary>
    public class PressButton : MonoBehaviour, IPointerDownHandler
    {
        public Action onDown;
        public void OnPointerDown(PointerEventData e) { onDown?.Invoke(); }
    }

    /// <summary>运行时构建 uGUI 的小工具集：中文字体、圆角面板、按钮、滑条、选项行。</summary>
    public static class UIKit
    {
        public static readonly Color Panel = new Color(0.07f, 0.08f, 0.10f, 0.86f);
        public static readonly Color Card = new Color(0.13f, 0.14f, 0.17f, 0.96f);
        public static readonly Color Accent = new Color(0.22f, 0.53f, 0.96f, 1f);
        public static readonly Color Soft = new Color(1f, 1f, 1f, 0.10f);
        public static readonly Color HudBtn = new Color(0.06f, 0.07f, 0.09f, 0.50f);
        public static readonly Color TextDim = new Color(0.78f, 0.80f, 0.84f, 1f);

        static Font font;
        static Sprite rounded, circle, ring;

        public static Font Font
        {
            get
            {
                if (font != null) return font;
                font = Resources.Load<Font>("Fonts/NotoSansSC-Regular");
                if (font == null)
                    font = Font.CreateDynamicFontFromOSFont(new[] { "Noto Sans CJK SC", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", "Droid Sans Fallback", "sans-serif" }, 32);
                if (font == null) font = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
                return font;
            }
        }

        public static Sprite Rounded => rounded != null ? rounded : (rounded = ProcTextures.RoundedSprite(64, 22));
        public static Sprite Circle => circle != null ? circle : (circle = ProcTextures.CircleSprite(128, 0f));
        public static Sprite Ring => ring != null ? ring : (ring = ProcTextures.CircleSprite(256, 5f));

        public static RectTransform Node(string name, Transform parent)
        {
            var go = new GameObject(name, typeof(RectTransform)) { layer = 5 };
            var rt = (RectTransform)go.transform;
            rt.SetParent(parent, false);
            return rt;
        }

        public static RectTransform Stretch(RectTransform rt, float left = 0, float bottom = 0, float right = 0, float top = 0)
        {
            rt.anchorMin = Vector2.zero; rt.anchorMax = Vector2.one;
            rt.offsetMin = new Vector2(left, bottom); rt.offsetMax = new Vector2(-right, -top);
            return rt;
        }

        public static RectTransform Place(RectTransform rt, Vector2 anchor, Vector2 pos, Vector2 size)
        {
            rt.anchorMin = rt.anchorMax = anchor; rt.pivot = new Vector2(0.5f, 0.5f);
            rt.anchoredPosition = pos; rt.sizeDelta = size;
            return rt;
        }

        public static Image Img(string name, Transform parent, Color c, Sprite sprite = null, bool raycast = false)
        {
            var rt = Node(name, parent);
            var img = rt.gameObject.AddComponent<Image>();
            img.color = c; img.sprite = sprite; img.raycastTarget = raycast;
            if (sprite != null && sprite.border.sqrMagnitude > 0) { img.type = Image.Type.Sliced; img.pixelsPerUnitMultiplier = 1f; }
            return img;
        }

        public static Text Txt(string name, Transform parent, string s, int size, Color c, TextAnchor align = TextAnchor.MiddleCenter)
        {
            var rt = Node(name, parent);
            var t = rt.gameObject.AddComponent<Text>();
            t.font = Font; t.text = s; t.fontSize = size; t.color = c; t.alignment = align;
            t.horizontalOverflow = HorizontalWrapMode.Wrap; t.verticalOverflow = VerticalWrapMode.Overflow;
            t.raycastTarget = false; t.supportRichText = false;
            Stretch(rt);
            return t;
        }

        /// <summary>圆角 / 圆形按钮，返回按钮本体；label 通过 out 参数给出便于后续改字。</summary>
        public static Button Btn(string name, Transform parent, string label, int fontSize, Color bg, Action onClick, out Text text, bool round = false)
        {
            var img = Img(name, parent, bg, round ? Circle : Rounded, true);
            var b = img.gameObject.AddComponent<Button>();
            b.targetGraphic = img;
            var cb = b.colors;
            cb.normalColor = Color.white; cb.highlightedColor = Color.white; cb.selectedColor = Color.white;
            cb.pressedColor = new Color(0.72f, 0.80f, 0.95f, 1f); cb.fadeDuration = 0.05f;
            b.colors = cb;
            b.navigation = new Navigation { mode = Navigation.Mode.None };
            if (onClick != null) b.onClick.AddListener(() => { AudioManager.Play(AudioManager.Sfx.Click, 0.6f); onClick(); });
            text = Txt("Label", img.transform, label, fontSize, Color.white);
            return b;
        }

        public static Button Btn(string name, Transform parent, string label, int fontSize, Color bg, Action onClick, bool round = false)
        {
            return Btn(name, parent, label, fontSize, bg, onClick, out _, round);
        }

        public static Slider MakeSlider(Transform parent, float min, float max, float value, Action<float> onChanged)
        {
            var root = Node("Slider", parent);
            var slider = root.gameObject.AddComponent<Slider>();
            var bg = Img("Background", root, new Color(1, 1, 1, 0.16f), Rounded, true);
            Stretch(bg.rectTransform); bg.rectTransform.anchorMin = new Vector2(0, 0.38f); bg.rectTransform.anchorMax = new Vector2(1, 0.62f);
            bg.rectTransform.offsetMin = bg.rectTransform.offsetMax = Vector2.zero;
            var fillArea = Node("Fill Area", root);
            fillArea.anchorMin = new Vector2(0, 0.38f); fillArea.anchorMax = new Vector2(1, 0.62f);
            fillArea.offsetMin = new Vector2(0, 0); fillArea.offsetMax = new Vector2(-20, 0);
            var fill = Img("Fill", fillArea, Accent, Rounded);
            fill.rectTransform.anchorMin = Vector2.zero; fill.rectTransform.anchorMax = Vector2.one;
            fill.rectTransform.offsetMin = Vector2.zero; fill.rectTransform.offsetMax = new Vector2(20, 0);
            var handleArea = Node("Handle Slide Area", root);
            Stretch(handleArea, 24, 0, 24, 0);
            var handle = Img("Handle", handleArea, Color.white, Circle, true);
            handle.rectTransform.sizeDelta = new Vector2(52, 0);
            handle.rectTransform.anchorMin = new Vector2(0, 0.08f); handle.rectTransform.anchorMax = new Vector2(0, 0.92f);
            slider.fillRect = fill.rectTransform; slider.handleRect = handle.rectTransform; slider.targetGraphic = handle;
            slider.direction = Slider.Direction.LeftToRight;
            slider.minValue = min; slider.maxValue = max; slider.value = value;
            slider.navigation = new Navigation { mode = Navigation.Mode.None };
            if (onChanged != null) slider.onValueChanged.AddListener(v => onChanged(v));
            return slider;
        }
    }
}
