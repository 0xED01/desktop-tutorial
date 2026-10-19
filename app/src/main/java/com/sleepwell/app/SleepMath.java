package com.sleepwell.app;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 睡眠限制疗法（CBT-I）的计算规则。 */
public class SleepMath {
    /** 窗口下限 5.5 小时，避免过度剥夺；上限 9 小时 */
    public static final int FLOOR = 330, CEIL = 540;

    public static class Stats {
        public int n;
        public int avgTst, avgTib, avgSol, avgWaso;
        public float avgSe, avgQuality;
    }

    public static Stats stats(List<SleepEntry> list) {
        Stats s = new Stats();
        s.n = list.size();
        if (s.n == 0) return s;
        long tst = 0, tib = 0, sol = 0, waso = 0, q = 0;
        for (SleepEntry e : list) {
            tst += e.tst(); tib += e.tib(); sol += e.sol; waso += e.waso; q += e.quality;
        }
        s.avgTst = (int) (tst / s.n);
        s.avgTib = (int) (tib / s.n);
        s.avgSol = (int) (sol / s.n);
        s.avgWaso = (int) (waso / s.n);
        s.avgSe = tib > 0 ? tst / (float) tib : 0;
        s.avgQuality = q / (float) s.n;
        return s;
    }

    /** 最近 14 天内的记录，最多取 7 条 */
    public static List<SleepEntry> recent(List<SleepEntry> sortedDesc, String afterDate) {
        String cutoff = LocalDate.now().minusDays(14).toString();
        if (afterDate != null && afterDate.compareTo(cutoff) > 0) cutoff = afterDate;
        List<SleepEntry> out = new ArrayList<>();
        for (SleepEntry e : sortedDesc) {
            if (e.date.compareTo(cutoff) > 0) out.add(e);
            if (out.size() == 7) break;
        }
        return out;
    }

    /** 初始窗口 = 平均实际睡眠时间，取整到 15 分钟 */
    public static int initialWindow(int avgTst) {
        int w = Math.round(avgTst / 15f) * 15;
        return Math.max(FLOOR, Math.min(CEIL, w));
    }

    /** 每周调整：效率 ≥90% 加 15 分钟；<85% 减 15 分钟；否则不变 */
    public static int adjust(int window, float se) {
        if (se >= 0.90f) return Math.min(CEIL, window + 15);
        if (se < 0.85f) return Math.max(FLOOR, window - 15);
        return window;
    }
}
