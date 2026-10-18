package com.stardrive.gauge;

import android.content.Context;
import android.content.SharedPreferences;

/** Shared constants and preference access. */
final class Cfg {
    static final String PREFS = "stardrive";

    static final int[] ACCENTS = {
            0xFF2FA8FF, 0xFFFF2D3A, 0xFFA95CFF, 0xFFFFB02E,
            0xFF00E5D4, 0xFF39FF88, 0xFFFF4FA3, 0xFFE8F1FF
    };
    static final String[] ACCENT_NAMES = {
            "冰川蓝", "AMG 赤红", "极光紫", "琥珀金",
            "湖光青", "极速绿", "玫瑰粉", "冰晶白"
    };

    static final String[] STYLE_NAMES = {"经典指针", "运动光环", "HUD 数字"};
    static final int[] LIMITS = {0, 40, 60, 80, 100, 120, 130, 140};
    /** Dial range per unit (km/h, mph). */
    static final int[][] SCALES = {{180, 260, 320}, {120, 160, 200}};

    static final float MS_TO_KMH = 3.6f;
    static final float MS_TO_MPH = 2.236936f;

    private Cfg() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int accent(Context c) {
        int i = prefs(c).getInt("accent", 0);
        return ACCENTS[Math.max(0, Math.min(ACCENTS.length - 1, i))];
    }
}
