package com.acct.subjects;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 科目数据、分类配色与收藏。 */
public final class Repo {
    public static final String[] CATS = {"资产类", "负债类", "共同类", "所有者权益类", "成本类", "损益类"};
    private static final int[] CAT_COLORS = {
            0xFF2F6FEB, 0xFFE5484D, 0xFF8E4EC6, 0xFFE38F00, 0xFF12A594, 0xFFD6409F};

    private static List<Subject> all;
    private static SharedPreferences prefs;

    static synchronized List<Subject> all(Context ctx) {
        if (all == null) {
            List<Subject> list = new ArrayList<>();
            try (InputStream in = ctx.getAssets().open("subjects.json")) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                JSONArray arr = new JSONArray(bos.toString(StandardCharsets.UTF_8.name()));
                for (int i = 0; i < arr.length(); i++) list.add(new Subject(arr.getJSONObject(i)));
            } catch (Exception e) {
                throw new RuntimeException("无法读取科目数据", e);
            }
            all = Collections.unmodifiableList(list);
        }
        return all;
    }

    static Subject byName(Context ctx, String name) {
        for (Subject s : all(ctx)) if (s.name.equals(name)) return s;
        return null;
    }

    static int color(String cat) {
        for (int i = 0; i < CATS.length; i++) if (CATS[i].equals(cat)) return CAT_COLORS[i];
        return 0xFF5E6A7D;
    }

    /** 分类色的浅色底（约 12% 不透明度）。 */
    static int tint(int color) {
        return Color.argb(30, Color.red(color), Color.green(color), Color.blue(color));
    }

    static int dirColor(String dir) {
        switch (dir) {
            case "借": return 0xFF2F6FEB;
            case "贷": return 0xFFE5484D;
            default: return 0xFF8E4EC6;
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        if (prefs == null) prefs = ctx.getApplicationContext().getSharedPreferences("fav", Context.MODE_PRIVATE);
        return prefs;
    }

    static Set<String> favorites(Context ctx) {
        return new HashSet<>(prefs(ctx).getStringSet("names", Collections.emptySet()));
    }

    static boolean isFavorite(Context ctx, String name) {
        return favorites(ctx).contains(name);
    }

    static void setFavorite(Context ctx, String name, boolean fav) {
        Set<String> set = favorites(ctx);
        if (fav) set.add(name); else set.remove(name);
        prefs(ctx).edit().putStringSet("names", set).apply();
    }
}
