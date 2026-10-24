package com.aapin.game;

import android.content.Context;

/** 配色皮肤，累计星星解锁。 */
public final class Theme {
    public final String name;
    public final int unlockStars;
    public final int bgTop, bgBottom, disc, pin, text, accent, p1, p2, discText, panel;

    private Theme(String name, int unlockStars, int bgTop, int bgBottom, int disc, int pin, int text,
                  int accent, int p1, int p2, int discText, int panel) {
        this.name = name;
        this.unlockStars = unlockStars;
        this.bgTop = bgTop;
        this.bgBottom = bgBottom;
        this.disc = disc;
        this.pin = pin;
        this.text = text;
        this.accent = accent;
        this.p1 = p1;
        this.p2 = p2;
        this.discText = discText;
        this.panel = panel;
    }

    public static final Theme[] ALL = {
            new Theme("经典", 0, 0xFFF4F1EA, 0xFFE6E1D6, 0xFF1E1E1E, 0xFF1E1E1E, 0xFF1E1E1E,
                    0xFFE85D3F, 0xFF2F7DE1, 0xFFE5484D, 0xFFFFFFFF, 0xFFFFFFFF),
            new Theme("夜空", 10, 0xFF0F1630, 0xFF1E2A5A, 0xFFF4F4FF, 0xFFE8ECFF, 0xFFF4F4FF,
                    0xFFFFC857, 0xFF4CC9F0, 0xFFF72585, 0xFF1E2A5A, 0xFF26346B),
            new Theme("樱花", 25, 0xFFFFE4EC, 0xFFFFC6D6, 0xFF8E3B5B, 0xFF8E3B5B, 0xFF6B2142,
                    0xFFFF6F91, 0xFF5A8DEE, 0xFFD63F6B, 0xFFFFE4EC, 0xFFFFF3F7),
            new Theme("薄荷", 45, 0xFFDFF7EE, 0xFFB8EBD7, 0xFF1F6F5C, 0xFF1F6F5C, 0xFF134D40,
                    0xFFFF8C42, 0xFF2A6FDB, 0xFFE4572E, 0xFFDFF7EE, 0xFFF3FFFA),
            new Theme("霓虹", 70, 0xFF07070C, 0xFF15102A, 0xFFFF00E5, 0xFF00F0FF, 0xFFE0FFFF,
                    0xFFFFE600, 0xFF00F0FF, 0xFFFF2E88, 0xFF07070C, 0xFF1C1636),
            new Theme("落日", 100, 0xFFFF9A5A, 0xFF6A2C70, 0xFF2D1B3D, 0xFF2D1B3D, 0xFFFFF5E6,
                    0xFFFFD166, 0xFF3A86FF, 0xFFFF006E, 0xFFFFD166, 0xFF4A2552),
            new Theme("黄金", 150, 0xFF1A1408, 0xFF3A2C10, 0xFFE8C468, 0xFFE8C468, 0xFFF7E7B4,
                    0xFFFFFFFF, 0xFF6EC1FF, 0xFFFF6B6B, 0xFF1A1408, 0xFF3D3015),
    };

    public static Theme current(Context c) {
        int i = Prefs.theme(c);
        if (i < 0 || i >= ALL.length) i = 0;
        return ALL[i];
    }

    public boolean dark() {
        int c = panel;
        int lum = ((c >> 16) & 0xFF) * 3 + ((c >> 8) & 0xFF) * 6 + (c & 0xFF);
        return lum < 1280;
    }
}
