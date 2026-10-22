package com.textshortcut.app;

import android.content.Context;
import android.content.SharedPreferences;

/** 图标样式设置，保存在 SharedPreferences 中。 */
public class IconStyle {
    public static final int SHAPE_CIRCLE = 0;
    public static final int SHAPE_ROUNDED = 1;
    public static final int SHAPE_SQUARE = 2;
    public static final int SHAPE_NONE = 3;

    public static final int FONT_CUSTOM = -1;

    public String text = "你好世界";
    public int textColor = 0xFFFFFFFF;
    public int fontIndex = 0;
    public boolean bold = true;
    /** 文字占图标的比例，百分比 20~100 */
    public int textScale = 60;
    /** 竖直方向偏移，百分比 -30~30 */
    public int textOffset = 0;
    public boolean shadow = false;

    public int shape = SHAPE_ROUNDED;
    public int bgColor = 0xFF5B6CFF;
    public boolean gradient = false;
    public int bgColor2 = 0xFFFF6CAB;
    public boolean useBgImage = false;
    public int borderColor = 0xFFFFFFFF;
    /** 边框宽度，百分比 0~15 */
    public int borderWidth = 0;

    public boolean showLabel = true;
    public boolean adaptive = false;
    public String url = "";

    private static final String PREFS = "style";

    public static IconStyle load(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        IconStyle s = new IconStyle();
        s.text = p.getString("text", s.text);
        s.textColor = p.getInt("textColor", s.textColor);
        s.fontIndex = p.getInt("fontIndex", s.fontIndex);
        s.bold = p.getBoolean("bold", s.bold);
        s.textScale = p.getInt("textScale", s.textScale);
        s.textOffset = p.getInt("textOffset", s.textOffset);
        s.shadow = p.getBoolean("shadow", s.shadow);
        s.shape = p.getInt("shape", s.shape);
        s.bgColor = p.getInt("bgColor", s.bgColor);
        s.gradient = p.getBoolean("gradient", s.gradient);
        s.bgColor2 = p.getInt("bgColor2", s.bgColor2);
        s.useBgImage = p.getBoolean("useBgImage", s.useBgImage);
        s.borderColor = p.getInt("borderColor", s.borderColor);
        s.borderWidth = p.getInt("borderWidth", s.borderWidth);
        s.showLabel = p.getBoolean("showLabel", s.showLabel);
        s.adaptive = p.getBoolean("adaptive", s.adaptive);
        s.url = p.getString("url", s.url);
        return s;
    }

    public void save(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("text", text)
                .putInt("textColor", textColor)
                .putInt("fontIndex", fontIndex)
                .putBoolean("bold", bold)
                .putInt("textScale", textScale)
                .putInt("textOffset", textOffset)
                .putBoolean("shadow", shadow)
                .putInt("shape", shape)
                .putInt("bgColor", bgColor)
                .putBoolean("gradient", gradient)
                .putInt("bgColor2", bgColor2)
                .putBoolean("useBgImage", useBgImage)
                .putInt("borderColor", borderColor)
                .putInt("borderWidth", borderWidth)
                .putBoolean("showLabel", showLabel)
                .putBoolean("adaptive", adaptive)
                .putString("url", url)
                .apply();
    }
}
