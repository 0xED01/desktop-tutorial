package com.piggybank.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends Activity {

    private static final int PRIMARY = 0xFFFF7A59;
    private static final int PRIMARY_DARK = 0xFFE0664A;
    private static final int TEXT = 0xFF2B2B2B;
    private static final int TEXT_2 = 0xFF8A8A8A;
    private static final int BG = 0xFFF6F6F6;
    private static final int GREEN = 0xFF2E9D5B;

    private Store store;
    private TextView balanceView, initialView, totalView, monthView, emptyView;
    private RecordAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        setContentView(buildUi());
        refresh();
        if (!store.isConfigured()) showSettings(true);
    }

    // ---------- UI ----------

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(PRIMARY);
        header.setPadding(dp(20), dp(14), dp(20), dp(22));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("我的存钱罐", 18, Color.WHITE, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView settings = text("设置", 15, Color.WHITE, false);
        settings.setPadding(dp(12), dp(6), dp(12), dp(6));
        settings.setBackground(roundRect(0x33FFFFFF, 16));
        settings.setOnClickListener(v -> showSettings(false));
        titleRow.addView(settings);
        header.addView(titleRow);

        TextView label = text("当前余额（元）", 14, 0xDDFFFFFF, false);
        label.setPadding(0, dp(18), 0, 0);
        header.addView(label);
        balanceView = text("0.00", 40, Color.WHITE, true);
        header.addView(balanceView);

        LinearLayout stats = new LinearLayout(this);
        stats.setPadding(0, dp(14), 0, 0);
        initialView = addStat(stats, "原有存款");
        totalView = addStat(stats, "累计存入");
        monthView = addStat(stats, "本月存入");
        header.addView(stats);
        root.addView(header);

        // Records list
        TextView listTitle = text("存入记录", 14, TEXT_2, false);
        listTitle.setPadding(dp(20), dp(14), dp(20), dp(6));
        root.addView(listTitle);

        LinearLayout listWrap = new LinearLayout(this);
        listWrap.setOrientation(LinearLayout.VERTICAL);
        ListView list = new ListView(this);
        list.setDivider(null);
        list.setPadding(dp(12), 0, dp(12), dp(8));
        list.setClipToPadding(false);
        adapter = new RecordAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> showRecordActions(adapter.getItem(pos)));
        emptyView = text("还没有记录\n点击下方按钮，记下这个月的收入吧", 14, TEXT_2, false);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setLineSpacing(dp(4), 1f);
        listWrap.addView(emptyView, new LinearLayout.LayoutParams(-1, -1));
        listWrap.addView(list, new LinearLayout.LayoutParams(-1, -1));
        root.addView(listWrap, new LinearLayout.LayoutParams(-1, 0, 1));

        // Bottom actions
        LinearLayout bottom = new LinearLayout(this);
        bottom.setPadding(dp(16), dp(10), dp(16), dp(16));
        bottom.setBackgroundColor(Color.WHITE);
        Button withdraw = button("取出", Color.WHITE, PRIMARY);
        withdraw.setOnClickListener(v -> showWithdraw(null));
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(dp(96), dp(52));
        wl.rightMargin = dp(12);
        bottom.addView(withdraw, wl);
        Button add = button("＋ 记录收入", PRIMARY, Color.WHITE);
        add.setOnClickListener(v -> showIncome(null));
        bottom.addView(add, new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(bottom);

        return root;
    }

    private TextView addStat(LinearLayout parent, String name) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(text(name, 12, 0xCCFFFFFF, false));
        TextView value = text("0.00", 15, Color.WHITE, true);
        value.setPadding(0, dp(2), 0, 0);
        box.addView(value);
        parent.addView(box, new LinearLayout.LayoutParams(0, -2, 1));
        return value;
    }

    private void refresh() {
        int ym = currentYm();
        balanceView.setText(money(store.balance()));
        initialView.setText(money(store.getInitial()));
        totalView.setText(money(store.totalSaved()));
        monthView.setText(money(store.savedInMonth(ym)));
        emptyView.setVisibility(store.getRecords().isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    // ---------- Dialogs ----------

    private void showSettings(boolean firstRun) {
        LinearLayout form = form();
        if (firstRun) {
            TextView hint = text("先告诉我你现在已有的存款，以及主业、副业收入各存多少比例。之后每月只需填收入即可。", 14, TEXT_2, false);
            hint.setPadding(0, 0, 0, dp(8));
            hint.setLineSpacing(dp(2), 1f);
            form.addView(hint);
        }
        EditText initial = field(form, "现在已有存款（元）", store.isConfigured() ? plain(store.getInitial()) : "");
        EditText mainPct = field(form, "主业收入存入比例（%）", pct(store.getMainPct()));
        EditText sidePct = field(form, "副业收入存入比例（%）", pct(store.getSidePct()));
        TextView note = text("修改比例只影响之后新记录，不会改变历史记录。", 12, TEXT_2, false);
        note.setPadding(0, dp(4), 0, 0);
        form.addView(note);

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(firstRun ? "欢迎使用存钱罐" : "设置")
                .setView(form)
                .setPositiveButton("保存", null)
                .setNegativeButton(firstRun ? null : "取消", null)
                .setCancelable(!firstRun)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            double mp = parseDouble(mainPct), sp = parseDouble(sidePct);
            if (mp < 0 || mp > 100 || sp < 0 || sp > 100) {
                toast("比例需在 0 ~ 100 之间");
                return;
            }
            store.saveSettings(parseCents(initial), mp, sp);
            refresh();
            d.dismiss();
        }));
        showWithKeyboard(d);
    }

    private void showIncome(Store.Record existing) {
        final Store.Record r = existing != null ? existing : new Store.Record();
        if (existing == null) {
            r.type = Store.TYPE_INCOME;
            r.yearMonth = currentYm();
            r.mainPct = store.getMainPct();
            r.sidePct = store.getSidePct();
        }
        final int[] ym = {r.yearMonth};

        LinearLayout form = form();
        TextView monthLabel = monthPicker(form, ym);
        EditText main = field(form, "主业收入（元）", existing != null && r.main != 0 ? plain(r.main) : "");
        EditText side = field(form, "副业收入（元）", existing != null && r.side != 0 ? plain(r.side) : "");
        TextView ratio = text("主业存 " + pct(r.mainPct) + "%　·　副业存 " + pct(r.sidePct) + "%", 13, TEXT_2, false);
        ratio.setPadding(0, dp(6), 0, 0);
        form.addView(ratio);
        TextView preview = text("", 18, PRIMARY_DARK, true);
        preview.setPadding(0, dp(10), 0, 0);
        form.addView(preview);

        Runnable update = () -> {
            Store.Record tmp = new Store.Record();
            tmp.main = parseCents(main);
            tmp.side = parseCents(side);
            tmp.mainPct = r.mainPct;
            tmp.sidePct = r.sidePct;
            preview.setText("本次存入 ¥" + money(tmp.saved()) + "\n存入后余额 ¥"
                    + money(store.balance() - (existing != null ? existing.saved() : 0) + tmp.saved()));
        };
        watch(main, update);
        watch(side, update);
        update.run();

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "记录收入" : "修改记录")
                .setView(form)
                .setPositiveButton("存入", null)
                .setNegativeButton("取消", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            long m = parseCents(main), s = parseCents(side);
            if (m <= 0 && s <= 0) {
                toast("请至少填写一项收入");
                return;
            }
            r.main = m;
            r.side = s;
            r.yearMonth = ym[0];
            store.put(r);
            refresh();
            d.dismiss();
            toast("已存入 ¥" + money(r.saved()));
        }));
        showWithKeyboard(d);
    }

    private void showWithdraw(Store.Record existing) {
        final Store.Record r = existing != null ? existing : new Store.Record();
        if (existing == null) {
            r.type = Store.TYPE_WITHDRAW;
            r.yearMonth = currentYm();
        }
        final int[] ym = {r.yearMonth};

        LinearLayout form = form();
        monthPicker(form, ym);
        EditText amount = field(form, "取出金额（元）", existing != null ? plain(r.main) : "");
        EditText note = field(form, "用途（选填）", r.note);
        note.setInputType(InputType.TYPE_CLASS_TEXT);

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "从存钱罐取出" : "修改取出记录")
                .setView(form)
                .setPositiveButton("确定", null)
                .setNegativeButton("取消", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            long a = parseCents(amount);
            if (a <= 0) {
                toast("请输入取出金额");
                return;
            }
            r.main = a;
            r.note = note.getText().toString().trim();
            r.yearMonth = ym[0];
            store.put(r);
            refresh();
            d.dismiss();
        }));
        showWithKeyboard(d);
    }

    private void showRecordActions(Store.Record r) {
        new AlertDialog.Builder(this)
                .setTitle(ymText(r.yearMonth) + (r.type == Store.TYPE_WITHDRAW ? " 取出" : " 收入"))
                .setItems(new String[]{"修改", "删除"}, (dlg, which) -> {
                    if (which == 0) {
                        if (r.type == Store.TYPE_WITHDRAW) showWithdraw(r); else showIncome(r);
                    } else {
                        new AlertDialog.Builder(this)
                                .setMessage("确定删除这条记录吗？")
                                .setPositiveButton("删除", (a, b) -> { store.delete(r); refresh(); })
                                .setNegativeButton("取消", null)
                                .show();
                    }
                })
                .show();
    }

    /** "‹ 2026年10月 ›" row; arrows step the month held in ym[0]. */
    private TextView monthPicker(LinearLayout form, int[] ym) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, 0, 0, dp(6));
        TextView prev = text("‹", 26, PRIMARY, true);
        TextView next = text("›", 26, PRIMARY, true);
        TextView label = text(ymText(ym[0]), 17, TEXT, true);
        label.setGravity(Gravity.CENTER);
        prev.setPadding(dp(18), 0, dp(18), dp(4));
        next.setPadding(dp(18), 0, dp(18), dp(4));
        prev.setOnClickListener(v -> { ym[0] = shift(ym[0], -1); label.setText(ymText(ym[0])); });
        next.setOnClickListener(v -> { ym[0] = shift(ym[0], 1); label.setText(ymText(ym[0])); });
        row.addView(prev);
        row.addView(label, new LinearLayout.LayoutParams(dp(140), -2));
        row.addView(next);
        form.addView(row);
        return label;
    }

    // ---------- List ----------

    private class RecordAdapter extends BaseAdapter {
        @Override public int getCount() { return store.getRecords().size(); }
        @Override public Store.Record getItem(int i) { return store.getRecords().get(i); }
        @Override public long getItemId(int i) { return getItem(i).id; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            LinearLayout card = new LinearLayout(MainActivity.this);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), roundRect(Color.WHITE, 12), null));
            LinearLayout outer = new LinearLayout(MainActivity.this);
            outer.setPadding(0, dp(4), 0, dp(4));

            Store.Record r = getItem(i);
            LinearLayout left = new LinearLayout(MainActivity.this);
            left.setOrientation(LinearLayout.VERTICAL);
            boolean out = r.type == Store.TYPE_WITHDRAW;
            left.addView(text(ymText(r.yearMonth) + (out ? "　取出" : ""), 15, TEXT, true));
            String sub;
            if (out) {
                sub = r.note.isEmpty() ? "从存钱罐取出" : r.note;
            } else {
                StringBuilder sb = new StringBuilder();
                if (r.main != 0) sb.append("主业 ").append(money(r.main)).append(" × ").append(pct(r.mainPct)).append("%");
                if (r.side != 0) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append("副业 ").append(money(r.side)).append(" × ").append(pct(r.sidePct)).append("%");
                }
                sub = sb.toString();
            }
            TextView subView = text(sub, 12, TEXT_2, false);
            subView.setPadding(0, dp(3), 0, 0);
            left.addView(subView);
            card.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

            long v = r.saved();
            card.addView(text((v >= 0 ? "+" : "-") + money(Math.abs(v)), 17, out ? TEXT_2 : GREEN, true));
            outer.addView(card, new LinearLayout.LayoutParams(-1, -2));
            return outer;
        }
    }

    // ---------- Helpers ----------

    private LinearLayout form() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(dp(24), dp(12), dp(24), 0);
        return f;
    }

    private EditText field(LinearLayout form, String hint, String value) {
        TextView label = text(hint, 13, TEXT_2, false);
        label.setPadding(dp(4), dp(8), 0, 0);
        form.addView(label);
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setTextSize(18);
        e.setSingleLine(true);
        e.setText(value);
        e.setSelection(e.getText().length());
        form.addView(e);
        return e;
    }

    private void showWithKeyboard(AlertDialog d) {
        d.show();
        if (d.getWindow() != null) {
            d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }

    private void watch(EditText e, Runnable r) {
        e.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { r.run(); }
        });
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String s, int bg, int fg) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(16);
        b.setTextColor(fg);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        GradientDrawable shape = roundRect(bg, 26);
        if (bg == Color.WHITE) shape.setStroke(dp(1.5f), fg);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33000000), shape, null));
        return b;
    }

    private GradientDrawable roundRect(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");
    private static final DecimalFormat PLAIN = new DecimalFormat("0.##");

    private static String money(long cents) { return MONEY.format(cents / 100.0); }
    private static String plain(long cents) { return PLAIN.format(cents / 100.0); }
    private static String pct(double p) { return PLAIN.format(p); }

    private static double parseDouble(EditText e) {
        try {
            return Double.parseDouble(e.getText().toString().trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static long parseCents(EditText e) { return Math.round(parseDouble(e) * 100); }

    private static int currentYm() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH) + 1;
    }

    private static int shift(int ym, int delta) {
        int idx = (ym / 100) * 12 + (ym % 100 - 1) + delta;
        return (idx / 12) * 100 + idx % 12 + 1;
    }

    private static String ymText(int ym) { return (ym / 100) + "年" + (ym % 100) + "月"; }
}
