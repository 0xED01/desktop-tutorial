package com.sleepwell.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 所有本地数据（只保存在手机上）。 */
public class Store {
    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("anshui", Context.MODE_PRIVATE);
    }

    public static int wakeMin(Context c) { return sp(c).getInt("wake", 7 * 60); }
    public static void setWakeMin(Context c, int m) { sp(c).edit().putInt("wake", m).apply(); }

    public static boolean remindersOn(Context c) { return sp(c).getBoolean("reminders", true); }
    public static void setRemindersOn(Context c, boolean on) { sp(c).edit().putBoolean("reminders", on).apply(); }

    /** 睡眠窗口（分钟），-1 表示还没开始计划 */
    public static int windowMin(Context c) { return sp(c).getInt("window", -1); }
    public static String windowDate(Context c) { return sp(c).getString("windowDate", ""); }
    public static void setWindow(Context c, int min) {
        sp(c).edit().putInt("window", min).putString("windowDate", LocalDate.now().toString()).apply();
    }

    /** 用户自定义的上床时间，-1 表示自动计算 */
    public static int customBed(Context c) { return sp(c).getInt("customBed", -1); }
    public static void setCustomBed(Context c, int m) { sp(c).edit().putInt("customBed", m).apply(); }

    /** 按睡眠窗口自动算出的上床时间 = 起床时间 - 睡眠窗口（未开始计划时按 8 小时） */
    public static int autoBedMin(Context c) {
        int w = windowMin(c);
        return SleepEntry.diff(w > 0 ? w : 480, wakeMin(c));
    }

    /** 目标上床时间：优先用自定义时间 */
    public static int bedMin(Context c) {
        int b = customBed(c);
        return b >= 0 ? b : autoBedMin(c);
    }

    public static String worries(Context c) { return sp(c).getString("worries", ""); }
    public static void setWorries(Context c, String s) { sp(c).edit().putString("worries", s).apply(); }

    public static int soundType(Context c) { return sp(c).getInt("soundType", 1); }
    public static int soundVol(Context c) { return sp(c).getInt("soundVol", 50); }
    public static int soundTimer(Context c) { return sp(c).getInt("soundTimer", 60); }
    public static void setSound(Context c, int type, int vol, int timer) {
        sp(c).edit().putInt("soundType", type).putInt("soundVol", vol).putInt("soundTimer", timer).apply();
    }

    /** 按日期从新到旧 */
    public static List<SleepEntry> entries(Context c) {
        List<SleepEntry> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("entries", "[]"));
            for (int i = 0; i < a.length(); i++) list.add(SleepEntry.fromJson(a.getJSONObject(i)));
        } catch (JSONException ignored) {
        }
        list.sort((x, y) -> y.date.compareTo(x.date));
        return list;
    }

    private static void saveAll(Context c, List<SleepEntry> list) {
        JSONArray a = new JSONArray();
        try {
            for (SleepEntry e : list) a.put(e.toJson());
        } catch (JSONException ignored) {
        }
        sp(c).edit().putString("entries", a.toString()).apply();
    }

    /** 同一天的记录会被替换 */
    public static void saveEntry(Context c, SleepEntry e) {
        List<SleepEntry> list = entries(c);
        list.removeIf(x -> x.date.equals(e.date));
        list.add(e);
        saveAll(c, list);
    }

    public static void deleteEntry(Context c, String date) {
        List<SleepEntry> list = entries(c);
        list.removeIf(x -> x.date.equals(date));
        saveAll(c, list);
    }
}
