package com.aapin.game;

import android.content.Context;
import android.content.SharedPreferences;

import com.aapin.game.core.Level;

/** 本地存档：设置、关卡进度、最佳成绩、统计。 */
public final class Prefs {
    private static SharedPreferences sp;

    private static SharedPreferences sp(Context c) {
        if (sp == null) sp = c.getApplicationContext().getSharedPreferences("aapin", Context.MODE_PRIVATE);
        return sp;
    }

    public static boolean sound(Context c) { return sp(c).getBoolean("sound", true); }
    public static void setSound(Context c, boolean v) { sp(c).edit().putBoolean("sound", v).apply(); }

    public static boolean vibrate(Context c) { return sp(c).getBoolean("vibrate", true); }
    public static void setVibrate(Context c, boolean v) { sp(c).edit().putBoolean("vibrate", v).apply(); }

    public static int theme(Context c) { return sp(c).getInt("theme", 0); }
    public static void setTheme(Context c, int v) { sp(c).edit().putInt("theme", v).apply(); }

    public static String nick(Context c) {
        String n = sp(c).getString("nick", "");
        if (n.isEmpty()) {
            n = "玩家" + (1000 + (int) (Math.random() * 9000));
            setNick(c, n);
        }
        return n;
    }
    public static void setNick(Context c, String v) { sp(c).edit().putString("nick", v).apply(); }

    public static int stars(Context c, int level) { return sp(c).getInt("star_" + level, 0); }

    public static void setStars(Context c, int level, int stars) {
        if (stars > stars(c, level)) sp(c).edit().putInt("star_" + level, stars).apply();
    }

    public static int totalStars(Context c) {
        int s = 0;
        for (int i = 1; i <= Level.COUNT; i++) s += stars(c, i);
        return s;
    }

    /** 已解锁的最高关卡。 */
    public static int unlocked(Context c) { return Math.max(1, sp(c).getInt("unlocked", 1)); }

    public static void unlock(Context c, int level) {
        if (level > unlocked(c)) sp(c).edit().putInt("unlocked", Math.min(level, Level.COUNT)).apply();
    }

    public static int best(Context c, String key) { return sp(c).getInt("best_" + key, 0); }

    /** 返回是否刷新了纪录。 */
    public static boolean submitBest(Context c, String key, int v) {
        if (v > best(c, key)) {
            sp(c).edit().putInt("best_" + key, v).apply();
            return true;
        }
        return false;
    }

    public static int stat(Context c, String key) { return sp(c).getInt("stat_" + key, 0); }

    public static void addStat(Context c, String key, int v) {
        if (v != 0) sp(c).edit().putInt("stat_" + key, stat(c, key) + v).apply();
    }
}
