package com.balancesheet.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int NAVY = 0xFF1E3A5F;
    private static final int TEAL = 0xFF2A9D8F;
    private static final int CORAL = 0xFFE76F51;
    private static final int TEXT = 0xFF263238;
    private static final int TEXT_2 = 0xFF90A4AE;
    private static final int BG = 0xFFF3F5F8;
    private static final int TEAL_LIGHT = 0xFF8CCFC6;

    private static final int TAB_ASSETS = 0, TAB_LIABILITIES = 1, TAB_TREND = 2;

    private Store store;
    private int tab = TAB_ASSETS;
    private TextView netView, assetsView, liabView, ratioView;
    private View ratioAssets, ratioLiab;
    private final TextView[] tabViews = new TextView[3];
    private final View[] tabLines = new View[3];
    private LinearLayout content;
    private Button addButton;
    private View bottomBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        setContentView(buildUi());
        refresh();
    }

    // ---------- Layout ----------

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(NAVY);
        header.setPadding(dp(20), dp(14), dp(20), dp(18));
        header.addView(text("资产负债表", 18, Color.WHITE, true));

        TextView netLabel = text("净资产（元）", 13, 0xB3FFFFFF, false);
        netLabel.setPadding(0, dp(16), 0, 0);
        header.addView(netLabel);
        netView = text("0.00", 36, Color.WHITE, true);
        header.addView(netView);

        // Assets vs liabilities proportion bar
        LinearLayout ratioBar = new LinearLayout(this);
        ratioBar.setPadding(0, 0, 0, 0);
        ratioAssets = new View(this);
        ratioAssets.setBackgroundColor(TEAL);
        ratioLiab = new View(this);
        ratioLiab.setBackgroundColor(CORAL);
        ratioBar.addView(ratioAssets, new LinearLayout.LayoutParams(0, -1, 1));
        ratioBar.addView(ratioLiab, new LinearLayout.LayoutParams(0, -1, 0));
        ratioBar.setBackground(round(0x33FFFFFF, 3));
        ratioBar.setClipToOutline(true);
        LinearLayout.LayoutParams rbl = new LinearLayout.LayoutParams(-1, dp(6));
        rbl.topMargin = dp(14);
        header.addView(ratioBar, rbl);

        LinearLayout stats = new LinearLayout(this);
        stats.setPadding(0, dp(12), 0, 0);
        assetsView = stat(stats, "总资产", TEAL_LIGHT);
        liabView = stat(stats, "总负债", 0xFFF4A28C);
        ratioView = stat(stats, "负债率", Color.WHITE);
        header.addView(stats);
        root.addView(header);

        // Tabs
        LinearLayout tabs = new LinearLayout(this);
        tabs.setBackgroundColor(Color.WHITE);
        String[] names = {"资产", "负债", "趋势"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView t = text(names[i], 15, TEXT_2, true);
            t.setPadding(0, dp(12), 0, dp(9));
            cell.addView(t);
            View line = new View(this);
            cell.addView(line, new LinearLayout.LayoutParams(dp(28), dp(3)));
            cell.setOnClickListener(v -> { tab = idx; refresh(); });
            tabViews[i] = t;
            tabLines[i] = line;
            tabs.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
        }
        root.addView(tabs);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(10), dp(14), dp(16));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setPadding(dp(16), dp(10), dp(16), dp(16));
        bottom.setBackgroundColor(Color.WHITE);
        addButton = new Button(this);
        addButton.setTextSize(16);
        addButton.setTextColor(Color.WHITE);
        addButton.setAllCaps(false);
        addButton.setStateListAnimator(null);
        addButton.setOnClickListener(v -> showItemDialog(null, tab == TAB_LIABILITIES ? Store.LIABILITY : Store.ASSET));
        bottom.addView(addButton, new LinearLayout.LayoutParams(-1, dp(52)));
        bottomBar = bottom;
        root.addView(bottom);
        return root;
    }

    private TextView stat(LinearLayout parent, String name, int color) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(text(name, 12, 0xB3FFFFFF, false));
        TextView v = text("0.00", 15, color, true);
        v.setPadding(0, dp(2), 0, 0);
        box.addView(v);
        parent.addView(box, new LinearLayout.LayoutParams(0, -2, 1));
        return v;
    }

    private void refresh() {
        long a = store.total(Store.ASSET), l = store.total(Store.LIABILITY);
        netView.setText(money(a - l));
        assetsView.setText(money(a));
        liabView.setText(money(l));
        ratioView.setText(a > 0 ? new DecimalFormat("0.0").format(l * 100.0 / a) + "%" : (l > 0 ? "—" : "0%"));
        float sum = a + l;
        ((LinearLayout.LayoutParams) ratioAssets.getLayoutParams()).weight = sum == 0 ? 1 : a / sum;
        ((LinearLayout.LayoutParams) ratioLiab.getLayoutParams()).weight = sum == 0 ? 0 : l / sum;
        ratioAssets.setBackgroundColor(sum == 0 ? 0x00000000 : TEAL);
        ratioAssets.requestLayout();

        for (int i = 0; i < 3; i++) {
            tabViews[i].setTextColor(i == tab ? NAVY : TEXT_2);
            tabLines[i].setBackground(i == tab ? round(i == TAB_LIABILITIES ? CORAL : i == TAB_TREND ? NAVY : TEAL, 2) : null);
        }

        content.removeAllViews();
        if (tab == TAB_TREND) {
            bottomBar.setVisibility(View.GONE);
            buildTrend();
        } else {
            int side = tab == TAB_ASSETS ? Store.ASSET : Store.LIABILITY;
            bottomBar.setVisibility(View.VISIBLE);
            addButton.setText(side == Store.ASSET ? "＋ 添加资产" : "＋ 添加负债");
            addButton.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33000000),
                    round(side == Store.ASSET ? TEAL : CORAL, 26), null));
            buildList(side);
        }
    }

    private void buildList(int side) {
        String[] cats = side == Store.ASSET ? Store.ASSET_CATEGORIES : Store.LIABILITY_CATEGORIES;
        long total = store.total(side);
        int accent = side == Store.ASSET ? TEAL : CORAL;
        boolean any = false;
        for (String cat : cats) {
            List<Store.Item> list = store.items(side, cat);
            if (list.isEmpty()) continue;
            any = true;
            long sub = 0;
            for (Store.Item i : list) sub += i.amount;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(round(Color.WHITE, 12));
            card.setPadding(0, dp(4), 0, dp(4));

            LinearLayout head = new LinearLayout(this);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(dp(16), dp(10), dp(16), dp(6));
            View dot = new View(this);
            dot.setBackground(round(accent, 4));
            head.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));
            TextView catView = text(cat, 14, TEXT, true);
            catView.setPadding(dp(8), 0, 0, 0);
            head.addView(catView, new LinearLayout.LayoutParams(0, -2, 1));
            String pct = total > 0 ? new DecimalFormat("0.#").format(sub * 100.0 / total) + "%　" : "";
            head.addView(text(pct + money(sub), 13, TEXT_2, false));
            card.addView(head);

            for (Store.Item item : list) {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(32), dp(10), dp(16), dp(10));
                row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x14000000), null, round(Color.WHITE, 0)));
                row.addView(text(item.name.isEmpty() ? cat : item.name, 15, TEXT, false), new LinearLayout.LayoutParams(0, -2, 1));
                row.addView(text(money(item.amount), 16, accent, true));
                row.setOnClickListener(v -> showItemDialog(item, item.side));
                card.addView(row);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(10);
            content.addView(card, lp);
        }
        if (!any) {
            TextView empty = text(side == Store.ASSET
                    ? "还没有资产\n把银行卡、理财、房车等逐项加进来吧"
                    : "还没有负债\n有信用卡、房贷、借款的话记在这里", 14, TEXT_2, false);
            empty.setGravity(Gravity.CENTER);
            empty.setLineSpacing(dp(4), 1f);
            empty.setPadding(0, dp(60), 0, 0);
            content.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        } else {
            TextView tip = text("点击条目可修改金额或删除", 12, TEXT_2, false);
            tip.setGravity(Gravity.CENTER);
            tip.setPadding(0, dp(4), 0, 0);
            content.addView(tip, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void buildTrend() {
        List<Store.Snapshot> snaps = store.snapshots();
        if (snaps.isEmpty()) {
            TextView empty = text("添加资产或负债后，每个月的净资产会自动记录在这里", 14, TEXT_2, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(60), dp(20), 0);
            content.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        LinearLayout chartCard = new LinearLayout(this);
        chartCard.setOrientation(LinearLayout.VERTICAL);
        chartCard.setBackground(round(Color.WHITE, 12));
        chartCard.setPadding(dp(14), dp(12), dp(14), dp(10));
        chartCard.addView(text("净资产走势", 14, TEXT, true));
        TrendChart chart = new TrendChart(this, TEAL_LIGHT, CORAL, TEAL, TEXT_2);
        chart.setData(snaps);
        chartCard.addView(chart, new LinearLayout.LayoutParams(-1, dp(170)));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.bottomMargin = dp(10);
        content.addView(chartCard, clp);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackground(round(Color.WHITE, 12));
        list.setPadding(0, dp(4), 0, dp(4));
        List<Store.Snapshot> rev = new ArrayList<>(snaps);
        java.util.Collections.reverse(rev);
        for (int i = 0; i < rev.size(); i++) {
            Store.Snapshot s = rev.get(i);
            Store.Snapshot prev = i + 1 < rev.size() ? rev.get(i + 1) : null;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(10), dp(16), dp(10));

            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            boolean current = s.yearMonth == Store.currentYm();
            left.addView(text((s.yearMonth / 100) + "年" + (s.yearMonth % 100) + "月" + (current ? "（本月）" : ""), 15, TEXT, true));
            left.addView(text("资产 " + compact(s.assets) + "　负债 " + compact(s.liabilities), 12, TEXT_2, false));
            row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            right.addView(text(money(s.net()), 15, TEXT, true));
            if (prev != null) {
                long d = s.net() - prev.net();
                right.addView(text((d >= 0 ? "▲ " : "▼ ") + money(Math.abs(d)), 12, d >= 0 ? TEAL : CORAL, false));
            }
            row.addView(right);
            if (!current) {
                row.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(this)
                            .setMessage("删除 " + (s.yearMonth / 100) + "年" + (s.yearMonth % 100) + "月 的记录？")
                            .setPositiveButton("删除", (x, y) -> { store.deleteSnapshot(s.yearMonth); refresh(); })
                            .setNegativeButton("取消", null)
                            .show();
                    return true;
                });
            }
            list.addView(row);
        }
        content.addView(list, new LinearLayout.LayoutParams(-1, -2));

        TextView tip = text("每月净资产自动按当月最后一次修改记录，平时更新各项金额即可", 12, TEXT_2, false);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(dp(10), dp(10), dp(10), 0);
        content.addView(tip, new LinearLayout.LayoutParams(-1, -2));
    }

    // ---------- Add / edit ----------

    private void showItemDialog(Store.Item existing, int side) {
        final String[] cats = side == Store.ASSET ? Store.ASSET_CATEGORIES : Store.LIABILITY_CATEGORIES;
        final int accent = side == Store.ASSET ? TEAL : CORAL;
        final String[] chosen = {existing != null ? existing.category : cats[0]};

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(10), dp(20), 0);
        form.addView(label("类别"));

        // Category chips, 4 per row
        final List<TextView> chips = new ArrayList<>();
        Runnable paint = () -> {
            for (TextView c : chips) {
                boolean on = c.getText().toString().equals(chosen[0]);
                c.setTextColor(on ? Color.WHITE : TEXT);
                c.setBackground(on ? round(accent, 16) : stroke(0xFFCFD8DC, 16));
            }
        };
        LinearLayout row = null;
        for (int i = 0; i < cats.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setPadding(0, dp(4), 0, dp(4));
                form.addView(row);
            }
            TextView chip = text(cats[i], 13, TEXT, false);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(0, dp(7), 0, dp(7));
            chip.setOnClickListener(v -> { chosen[0] = ((TextView) v).getText().toString(); paint.run(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.setMargins(dp(3), 0, dp(3), 0);
            row.addView(chip, lp);
            chips.add(chip);
        }
        for (int i = cats.length % 4; i > 0 && i < 4; i++) {
            row.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1));
        }
        paint.run();

        form.addView(label("名称（选填）"));
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint(side == Store.ASSET ? "如：招商银行储蓄卡" : "如：招行信用卡");
        if (existing != null) name.setText(existing.name);
        form.addView(name);

        form.addView(label(side == Store.ASSET ? "当前价值（元）" : "当前欠款（元）"));
        EditText amount = new EditText(this);
        amount.setSingleLine(true);
        amount.setTextSize(20);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (existing != null) amount.setText(new DecimalFormat("0.##").format(existing.amount / 100.0));
        form.addView(amount);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(existing == null ? (side == Store.ASSET ? "添加资产" : "添加负债") : "修改")
                .setView(form)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null);
        if (existing != null) b.setNeutralButton("删除", null);
        AlertDialog d = b.create();
        d.setOnShowListener(x -> {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                long cents;
                try {
                    cents = Math.round(Double.parseDouble(amount.getText().toString().trim()) * 100);
                } catch (NumberFormatException e) {
                    toast("请输入金额");
                    return;
                }
                if (cents < 0) {
                    toast("金额不能为负数");
                    return;
                }
                Store.Item item = existing != null ? existing : new Store.Item();
                item.side = side;
                item.category = chosen[0];
                item.name = name.getText().toString().trim();
                item.amount = cents;
                store.put(item);
                refresh();
                d.dismiss();
            });
            if (existing != null) {
                Button del = d.getButton(AlertDialog.BUTTON_NEUTRAL);
                del.setTextColor(CORAL);
                del.setOnClickListener(v -> new AlertDialog.Builder(this)
                        .setMessage("确定删除「" + (existing.name.isEmpty() ? existing.category : existing.name) + "」？")
                        .setPositiveButton("删除", (p, q) -> { store.delete(existing); refresh(); d.dismiss(); })
                        .setNegativeButton("取消", null)
                        .show());
            }
        });
        d.show();
        if (d.getWindow() != null) {
            d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        // Editing usually means updating the amount, so jump straight there.
        if (existing != null) {
            amount.requestFocus();
            amount.selectAll();
        } else {
            name.requestFocus();
        }
    }

    // ---------- Helpers ----------

    private TextView label(String s) {
        TextView t = text(s, 13, TEXT_2, false);
        t.setPadding(dp(4), dp(10), 0, dp(2));
        return t;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private GradientDrawable stroke(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setStroke(dp(1), color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    static String money(long cents) { return MONEY.format(cents / 100.0); }

    /** Short form for tight spaces: 1.23万 / 4.5亿. */
    static String compact(long cents) {
        double yuan = cents / 100.0, abs = Math.abs(yuan);
        if (abs >= 1e8) return new DecimalFormat("0.##").format(yuan / 1e8) + "亿";
        if (abs >= 1e4) return new DecimalFormat("0.##").format(yuan / 1e4) + "万";
        return new DecimalFormat("0.##").format(yuan);
    }
}
