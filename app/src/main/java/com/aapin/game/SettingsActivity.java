package com.aapin.game;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.aapin.game.core.Level;

public final class SettingsActivity extends BaseActivity {

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackground(Ui.background(th));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        col.setPadding(pad, Ui.dp(this, 20), pad, Ui.dp(this, 32));
        sv.addView(col);
        int full = ViewGroup.LayoutParams.MATCH_PARENT, wrap = ViewGroup.LayoutParams.WRAP_CONTENT;
        int m = Ui.dp(this, 6);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.text(this, "‹ 返回", 18, th.text, true);
        back.setPadding(0, Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        back.setOnClickListener(v -> finish());
        header.addView(back);
        header.addView(Ui.text(this, "皮肤与设置", 24, th.text, true));
        col.addView(header);

        col.addView(section("设置"));
        boolean snd = Prefs.sound(this), vib = Prefs.vibrate(this);
        LinearLayout row = new LinearLayout(this);
        row.addView(Ui.button(this, "音效：" + (snd ? "开" : "关"), snd ? th.p1 : 0xFF999999, 0xFFFFFFFF, v -> {
            Prefs.setSound(this, !snd);
            build();
        }), weight(m));
        row.addView(Ui.button(this, "振动：" + (vib ? "开" : "关"), vib ? th.p1 : 0xFF999999, 0xFFFFFFFF, v -> {
            Prefs.setVibrate(this, !vib);
            if (!vib) Sfx.buzz(60);
            build();
        }), weight(m));
        col.addView(row, Ui.lp(full, wrap, 0));

        col.addView(Ui.text(this, "联机昵称", 15, Ui.sub(th), false), Ui.lp(wrap, wrap, m));
        EditText nick = new EditText(this);
        nick.setText(Prefs.nick(this));
        nick.setSingleLine(true);
        nick.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});
        nick.setTextColor(th.dark() ? 0xFFFFFFFF : 0xFF222222);
        nick.setBackground(Ui.round(th.panel, Ui.dp(this, 12)));
        nick.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        nick.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                String v = s.toString().trim().replace("|", "");
                if (!v.isEmpty()) Prefs.setNick(SettingsActivity.this, v);
            }
        });
        col.addView(nick, Ui.lp(full, wrap, 0));

        int total = Prefs.totalStars(this);
        col.addView(section("皮肤（已获得 ★" + total + "）"));
        int cur = Prefs.theme(this);
        for (int i = 0; i < Theme.ALL.length; i++) {
            final int idx = i;
            Theme t = Theme.ALL[i];
            boolean open = total >= t.unlockStars;
            LinearLayout item = new LinearLayout(this);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
            item.setBackground(Ui.round(Painter.blend(t.bgTop, t.bgBottom, 0.5f), Ui.dp(this, 16)));
            View disc = new View(this);
            disc.setBackground(Ui.round(t.disc, Ui.dp(this, 14)));
            item.addView(disc, new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28)));
            for (int col2 : new int[]{t.p1, t.p2, t.accent}) {
                View dot = new View(this);
                dot.setBackground(Ui.round(col2, Ui.dp(this, 6)));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Ui.dp(this, 12), Ui.dp(this, 12));
                p.leftMargin = Ui.dp(this, 6);
                item.addView(dot, p);
            }
            TextView name = Ui.text(this, "  " + t.name, 18, t.text, true);
            item.addView(name, new LinearLayout.LayoutParams(0, wrap, 1f));
            String state = !open ? "🔒 需要 ★" + t.unlockStars : i == cur ? "使用中 ✓" : "点击使用";
            item.addView(Ui.text(this, state, 14, t.text, i == cur));
            item.setOnClickListener(v -> {
                Sfx.play(Sfx.CLICK);
                if (!open) {
                    Toast.makeText(this, "闯关获得 " + t.unlockStars + " 颗星即可解锁", Toast.LENGTH_SHORT).show();
                    return;
                }
                Prefs.setTheme(this, idx);
                th = Theme.current(this);
                build();
            });
            col.addView(item, Ui.lp(full, wrap, m));
        }

        col.addView(section("统计"));
        String stats = "累计插针：" + Prefs.stat(this, "pins")
                + "\n闯关进度：" + Math.min(Prefs.unlocked(this), Level.COUNT) + " / " + Level.COUNT
                + "\n星星：" + total + " / " + Level.COUNT * 3
                + "\n无尽模式最佳：" + Prefs.best(this, "endless")
                + "\n限时模式最佳：" + Prefs.best(this, "time")
                + "\n联机对战：" + Prefs.stat(this, "lanGames") + " 场，胜 " + Prefs.stat(this, "lanWins") + " 场";
        TextView st = Ui.text(this, stats, 15, th.text, false);
        st.setLineSpacing(Ui.dp(this, 4), 1f);
        col.addView(st);
        setContentView(sv);
    }

    private TextView section(String s) {
        TextView t = Ui.text(this, s, 19, th.text, true);
        t.setPadding(0, Ui.dp(this, 22), 0, Ui.dp(this, 6));
        return t;
    }

    private LinearLayout.LayoutParams weight(int m) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(m, m, m, m);
        return p;
    }
}
