package com.acct.subjects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 检索：支持科目编号、名称、拼音全拼、拼音首字母及核算内容关键词。 */
final class Search {
    private Search() {}

    /** 返回匹配得分，越小越靠前；不匹配返回 -1。 */
    static int score(Subject s, String q) {
        if (q.isEmpty()) return 0;
        if (s.code.equals(q)) return 0;
        if (s.name.equals(q)) return 0;
        if (s.name.startsWith(q)) return 1;
        if (s.hasCode() && s.code.startsWith(q)) return 2;
        if (s.name.contains(q)) return 3;
        if (s.pyi.startsWith(q)) return 4;
        if (s.py.startsWith(q)) return 5;
        if (s.pyi.contains(q) || s.py.contains(q)) return 6;
        if (s.fullText.contains(q)) return 7;
        return -1;
    }

    static List<Subject> run(List<Subject> source, String rawQuery) {
        String q = rawQuery.trim().toLowerCase().replace(" ", "");
        List<int[]> hits = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            int sc = score(source.get(i), q);
            if (sc >= 0) hits.add(new int[]{sc, i});
        }
        // 稳定排序：同分时保持科目表原顺序（即按编号）
        Collections.sort(hits, (a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        List<Subject> out = new ArrayList<>(hits.size());
        for (int[] h : hits) out.add(source.get(h[1]));
        return out;
    }
}
