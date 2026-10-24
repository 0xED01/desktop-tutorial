package com.aapin.game;

import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.aapin.game.core.Level;

public final class LevelSelectActivity extends BaseActivity {
    private ScrollView sv;
    private int scrollY = -1;

    @Override
    protected void onResume() {
        super.onResume();
        if (sv != null) scrollY = sv.getScrollY();
        build();
    }

    private void build() {
        sv = new ScrollView(this);
        sv.setBackground(Ui.background(th));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        col.setPadding(pad, Ui.dp(this, 20), pad, Ui.dp(this, 28));
        sv.addView(col);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.text(this, "‹ 返回", 18, th.text, true);
        back.setPadding(0, Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        back.setOnClickListener(v -> finish());
        header.addView(back);
        TextView title = Ui.text(this, "选择关卡", 24, th.text, true);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(Ui.text(this, "★ " + Prefs.totalStars(this) + "/" + Level.COUNT * 3, 18, th.accent, true));
        col.addView(header);

        int unlocked = Prefs.unlocked(this);
        int cellM = Ui.dp(this, 4);
        int cellH = Ui.dp(this, 64);
        int focusY = 0;
        for (int ch = 0; ch < Level.COUNT / 10; ch++) {
            TextView ct = Ui.text(this, "第 " + (ch + 1) + " 章 · " + Level.CHAPTERS[ch], 19, th.text, true);
            ct.setPadding(cellM, Ui.dp(this, 18), 0, 0);
            col.addView(ct);
            TextView tip = Ui.text(this, Level.CHAPTER_TIPS[ch], 13, Ui.sub(th), false);
            tip.setPadding(cellM, Ui.dp(this, 2), 0, Ui.dp(this, 6));
            col.addView(tip);
            for (int r = 0; r < 2; r++) {
                LinearLayout row = new LinearLayout(this);
                for (int k = 0; k < 5; k++) {
                    final int n = ch * 10 + r * 5 + k + 1;
                    boolean open = n <= unlocked;
                    int stars = Prefs.stars(this, n);
                    TextView cell = Ui.text(this, "", 18, 0xFFFFFFFF, true);
                    cell.setGravity(Gravity.CENTER);
                    if (open) {
                        String s = stars == 0 ? "☆☆☆" : "★★★".substring(0, stars) + "☆☆☆".substring(stars);
                        cell.setText(n + "\n" + s);
                        int bg = stars > 0 ? th.p1 : th.accent;
                        if (n % 10 == 0) bg = th.p2;
                        cell.setBackground(Ui.round(bg, Ui.dp(this, 14)));
                        cell.setOnClickListener(v -> {
                            Sfx.play(Sfx.CLICK);
                            GameActivity.start(this, "level", n);
                        });
                    } else {
                        cell.setText("🔒\n" + n);
                        cell.setTextColor(0xAAFFFFFF);
                        cell.setBackground(Ui.round(0x55888888, Ui.dp(this, 14)));
                    }
                    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, cellH, 1f);
                    p.setMargins(cellM, cellM, cellM, cellM);
                    row.addView(cell, p);
                }
                col.addView(row);
            }
            if (unlocked > ch * 10) focusY = ch;
        }
        setContentView(sv);
        final int target = scrollY >= 0 ? scrollY : -1;
        final int chapter = focusY;
        sv.post(() -> {
            if (target >= 0) sv.scrollTo(0, target);
            else sv.scrollTo(0, Math.max(0, chapter * (cellH * 2 + Ui.dp(this, 80)) - Ui.dp(this, 40)));
        });
    }
}
