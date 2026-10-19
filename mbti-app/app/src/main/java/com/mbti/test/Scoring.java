package com.mbti.test;

import java.util.List;

/**
 * 计分规则：
 * 每题有 4 个选项：非常同意A(2分给A极) / 比较同意A(1分) / 比较同意B(1分) / 非常同意B(2分)。
 * 每个维度 15 题，单极最高 30 分。
 * 偏好清晰度 = |两极得分差| / 30，分为 轻微 / 中等 / 明显 / 非常明显 四档
 * （参照 MBTI 偏好清晰度指数 PCI 的分档方式）。
 * 两极得分相同时，按 MBTI 惯例判为 I、N、F、P。
 */
public final class Scoring {

    public static final char[][] POLES = {{'E', 'I'}, {'S', 'N'}, {'T', 'F'}, {'J', 'P'}};
    /** 平分时的判定方向（MBTI 惯例） */
    private static final char[] TIE_BREAK = {'I', 'N', 'F', 'P'};

    public static final class Result {
        /** scores[dim][0] 为第一极（E/S/T/J）得分，scores[dim][1] 为第二极（I/N/F/P）得分 */
        public final int[][] scores = new int[4][2];
        public String type;

        public int maxPerDim;

        public char letter(int dim) {
            return type.charAt(dim);
        }

        /** 所选一极的百分比（50%~100%） */
        public int percent(int dim) {
            int total = scores[dim][0] + scores[dim][1];
            if (total == 0) return 50;
            int chosen = letter(dim) == POLES[dim][0] ? scores[dim][0] : scores[dim][1];
            return Math.round(chosen * 100f / total);
        }

        /** 偏好清晰度 0~100 */
        public int clarity(int dim) {
            return Math.round(Math.abs(scores[dim][0] - scores[dim][1]) * 100f / maxPerDim);
        }

        public String clarityLabel(int dim) {
            int c = clarity(dim);
            if (c <= 25) return "轻微";
            if (c <= 50) return "中等";
            if (c <= 75) return "明显";
            return "非常明显";
        }
    }

    /**
     * @param answers 与 items 一一对应，取值 2/1 表示倾向 A，-1/-2 表示倾向 B
     */
    public static Result score(List<Questions.Item> items, int[] answers) {
        Result r = new Result();
        r.maxPerDim = Questions.countPerDimension() * 2;
        for (int i = 0; i < items.size(); i++) {
            Questions.Item it = items.get(i);
            int a = answers[i];
            if (a == 0) continue;
            char pole = a > 0 ? it.poleA : opposite(it.dim, it.poleA);
            int idx = POLES[it.dim][0] == pole ? 0 : 1;
            r.scores[it.dim][idx] += Math.abs(a);
        }
        StringBuilder sb = new StringBuilder();
        for (int d = 0; d < 4; d++) {
            int first = r.scores[d][0], second = r.scores[d][1];
            if (first > second) sb.append(POLES[d][0]);
            else if (second > first) sb.append(POLES[d][1]);
            else sb.append(TIE_BREAK[d]);
        }
        r.type = sb.toString();
        return r;
    }

    static char opposite(int dim, char pole) {
        return POLES[dim][0] == pole ? POLES[dim][1] : POLES[dim][0];
    }

    private Scoring() {}
}
