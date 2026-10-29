package com.acct.subjects;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class DetailActivity extends Activity {
    static final String EXTRA_NAME = "name";

    private Subject subject;
    private ImageView star;
    private float density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        density = getResources().getDisplayMetrics().density;

        subject = Repo.byName(this, getIntent().getStringExtra(EXTRA_NAME));
        if (subject == null) { finish(); return; }
        Subject s = subject;
        int color = Repo.color(s.cat);

        getWindow().setStatusBarColor(color);
        getWindow().getDecorView().setSystemUiVisibility(0); // 深色状态栏背景配白色图标

        findViewById(R.id.header).setBackgroundColor(color);
        findViewById(R.id.back).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.d_name)).setText(s.name);

        LinearLayout tags = findViewById(R.id.tags);
        addTag(tags, s.hasCode() ? "编号 " + s.code : "新准则科目");
        addTag(tags, s.cat);
        addTag(tags, "余额方向：" + s.dir);

        star = findViewById(R.id.star);
        updateStar();
        star.setOnClickListener(v -> {
            boolean fav = !Repo.isFavorite(this, s.name);
            Repo.setFavorite(this, s.name, fav);
            updateStar();
            Toast.makeText(this, fav ? "已收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
        });

        LinearLayout sections = findViewById(R.id.sections);
        addSection(sections, "核算内容", s.desc, color, false);
        addDebitCredit(sections, s);
        addSection(sections, "期末余额", s.bal, color, false);
        addSection(sections, "学习要点", s.tip, color, false);
        addSection(sections, "典型分录", s.entry, color, true);
    }

    private void updateStar() {
        star.setImageResource(Repo.isFavorite(this, subject.name) ? R.drawable.ic_star : R.drawable.ic_star_border);
    }

    private void addTag(LinearLayout parent, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(13);
        t.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x33FFFFFF);
        bg.setCornerRadius(dp(12));
        t.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(8));
        parent.addView(t, lp);
    }

    private LinearLayout card(LinearLayout parent, String title, int color) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_section);
        card.setPadding(dp(16), dp(14), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        parent.addView(card, lp);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        View bar = new View(this);
        GradientDrawable bd = new GradientDrawable();
        bd.setColor(color);
        bd.setCornerRadius(dp(2));
        bar.setBackground(bd);
        head.addView(bar, new LinearLayout.LayoutParams(dp(4), dp(16)));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(0xFF1C2430);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(8), 0, 0, 0);
        head.addView(t);
        card.addView(head);
        return card;
    }

    private TextView body(String text) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(0xFF3A4555);
        b.setTextSize(15);
        b.setLineSpacing(dp(4), 1f);
        b.setTextIsSelectable(true);
        return b;
    }

    private void addSection(LinearLayout parent, String title, String text, int color, boolean mono) {
        if (text == null || text.isEmpty()) return;
        LinearLayout card = card(parent, title, color);
        TextView b = body(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        if (mono) {
            b.setTypeface(Typeface.MONOSPACE);
            b.setTextSize(14);
            b.setBackgroundResource(R.drawable.bg_code);
            b.setPadding(dp(12), dp(10), dp(12), dp(10));
        }
        card.addView(b, lp);
    }

    /** 借方 / 贷方 并排对照展示。 */
    private void addDebitCredit(LinearLayout parent, Subject s) {
        LinearLayout card = card(parent, "借贷方向", Repo.color(s.cat));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(10);
        card.addView(row, rlp);
        addSide(row, "借方", s.debit, Repo.dirColor("借"), true);
        addSide(row, "贷方", s.credit, Repo.dirColor("贷"), false);
    }

    private void addSide(LinearLayout row, String label, String text, int color, boolean first) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Repo.tint(color));
        bg.setCornerRadius(dp(10));
        box.setBackground(bg);
        box.setPadding(dp(12), dp(10), dp(12), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        if (first) lp.setMarginEnd(dp(5)); else lp.setMarginStart(dp(5));
        row.addView(box, lp);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(color);
        l.setTextSize(14);
        l.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(l);
        TextView b = body(text);
        b.setTextSize(14);
        b.setPadding(0, dp(4), 0, 0);
        box.addView(b);
    }

    private int dp(int v) {
        return Math.round(v * density);
    }
}
