package com.sleepwell.app;

import org.json.JSONException;
import org.json.JSONObject;

/** 一晚的睡眠日记。时间都以“当天第几分钟”(0-1439)存储。 */
public class SleepEntry {
    public String date;   // 醒来那天的日期 yyyy-MM-dd
    public int bed;       // 上床时间
    public int sol;       // 入睡用时(分钟)
    public int waso;      // 夜间醒着的总时长(分钟)
    public int wake;      // 最终醒来时间
    public int up;        // 起床(离开床)时间
    public int quality;   // 主观质量 1-5

    public static int diff(int from, int to) {
        return ((to - from) % 1440 + 1440) % 1440;
    }

    /** 在床总时长 */
    public int tib() {
        return diff(bed, up);
    }

    /** 实际睡着时长 */
    public int tst() {
        return Math.max(0, diff(bed, wake) - sol - waso);
    }

    /** 睡眠效率 = 睡着 / 在床 */
    public float se() {
        int t = tib();
        return t > 0 ? Math.min(1f, tst() / (float) t) : 0f;
    }

    public SleepEntry copy() {
        SleepEntry e = new SleepEntry();
        e.date = date; e.bed = bed; e.sol = sol; e.waso = waso;
        e.wake = wake; e.up = up; e.quality = quality;
        return e;
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("date", date); o.put("bed", bed); o.put("sol", sol); o.put("waso", waso);
        o.put("wake", wake); o.put("up", up); o.put("q", quality);
        return o;
    }

    static SleepEntry fromJson(JSONObject o) {
        SleepEntry e = new SleepEntry();
        e.date = o.optString("date");
        e.bed = o.optInt("bed"); e.sol = o.optInt("sol"); e.waso = o.optInt("waso");
        e.wake = o.optInt("wake"); e.up = o.optInt("up"); e.quality = o.optInt("q", 3);
        return e;
    }
}
