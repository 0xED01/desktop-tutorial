package com.acct.subjects;

import org.json.JSONObject;

/** 一个会计科目。 */
public final class Subject {
    public final String code, name, cat, dir, desc, debit, credit, bal, tip, entry, py, pyi;
    /** 小写的检索文本，用于全文匹配。 */
    final String fullText;

    Subject(JSONObject o) {
        code = o.optString("code");
        name = o.optString("name");
        cat = o.optString("cat");
        dir = o.optString("dir");
        desc = o.optString("desc");
        debit = o.optString("debit");
        credit = o.optString("credit");
        bal = o.optString("bal");
        tip = o.optString("tip");
        entry = o.optString("entry");
        py = o.optString("py");
        pyi = o.optString("pyi");
        fullText = (desc + tip + entry).toLowerCase();
    }

    boolean hasCode() {
        return !"—".equals(code);
    }
}
