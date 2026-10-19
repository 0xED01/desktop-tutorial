package com.sleepwell.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RatingBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {
    static final int BG = 0xFF0E1220, CARD = 0xFF1A2033, TEXT = 0xFFE6E8F0, SUB = 0xFF9AA3B8,
            ACCENT = 0xFFFFB86B, GOOD = 0xFF7BD88F, MID = 0xFFFFD866, BAD = 0xFFFF7A7A;
    static final String[] TABS = {"今晚", "日记", "放松", "声音", "指南"};
    static final String SAFETY = "注意：如果你有双相情感障碍、癫痫、睡眠呼吸暂停（打鼾憋醒）、正在怀孕，或者工作需要驾驶/操作机械，请先咨询医生再做睡眠限制。计划期间白天犯困时不要开车。";

    private FrameLayout content;
    private LinearLayout tabBar;
    private int tab = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable sessionTick;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        tabBar = new LinearLayout(this);
        tabBar.setBackgroundColor(0xFF141A2B);
        root.addView(tabBar, new LinearLayout.LayoutParams(-1, dp(56)));
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(TABS[i]);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(15);
            t.setOnClickListener(v -> showTab(idx));
            tabBar.addView(t, new LinearLayout.LayoutParams(0, -1, 1));
        }
        setContentView(root);
        tab = getIntent().getIntExtra("tab", 0);

        Reminders.ensureChannel(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        Reminders.scheduleAll(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra("tab")) {
            stopSession();
            content.setTag(null);
            tab = intent.getIntExtra("tab", 0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content.getTag() == null) showTab(tab);
    }

    @Override
    protected void onDestroy() {
        stopSession();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (sessionTick != null || content.getTag() != null) {
            stopSession();
            showTab(tab);
        } else {
            super.onBackPressed();
        }
    }

    void showTab(int i) {
        stopSession();
        tab = i;
        content.setTag(null);
        for (int k = 0; k < tabBar.getChildCount(); k++) {
            TextView t = (TextView) tabBar.getChildAt(k);
            t.setTextColor(k == i ? ACCENT : SUB);
            t.setTypeface(null, k == i ? Typeface.BOLD : Typeface.NORMAL);
        }
        LinearLayout p = page();
        switch (i) {
            case 0: buildTonight(p); break;
            case 1: buildDiary(p); break;
            case 2: buildRelax(p); break;
            case 3: buildSound(p); break;
            default: buildGuide(p);
        }
        setPage(p);
    }

    private void setPage(LinearLayout p) {
        ScrollView sv = new ScrollView(this);
        sv.addView(p);
        content.removeAllViews();
        content.addView(sv);
    }

    // ---------------------------------------------------------------- 今晚

    private void buildTonight(LinearLayout p) {
        text(p, "安睡", 28, TEXT, true);
        text(p, "基于失眠认知行为疗法（CBT-I）", 13, SUB, false);
        space(p, 12);

        List<SleepEntry> all = Store.entries(this);
        SleepMath.Stats st = SleepMath.stats(SleepMath.recent(all, null));
        int win = Store.windowMin(this), wake = Store.wakeMin(this);

        LinearLayout c = card(p);
        text(c, "今晚计划", 13, SUB, false);
        text(c, "上床 " + hm(Store.bedMin(this)) + "   →   起床 " + hm(wake), 24, ACCENT, true);
        if (win > 0) text(c, "睡眠窗口 " + dur(win) + "：只在这段时间里躺在床上。", 14, TEXT, false);
        else text(c, "还没开始睡眠窗口计划，暂按 8 小时显示。", 14, SUB, false);
        space(c, 6);
        text(c, "★ 最重要的一条：无论昨晚睡得怎样，每天同一时间起床，周末也一样。", 14, TEXT, false);

        LinearLayout pc = card(p);
        text(pc, "睡眠窗口计划", 17, TEXT, true);
        text(pc, "睡眠限制疗法是 CBT-I 中效果最强的部分", 12, SUB, false);
        space(pc, 6);
        if (win <= 0) {
            if (st.n < 5) {
                text(pc, "第 1 步：先连续记录一周睡眠日记（已记录 " + st.n + " 晚，至少需要 5 晚）。这一周照常睡，不用刻意改变，App 会根据你真实的睡眠情况算出最适合你的窗口。", 14, TEXT, false);
                button(pc, "去记录昨晚的睡眠", v -> showTab(1));
            } else {
                final int w0 = SleepMath.initialWindow(st.avgTst);
                text(pc, "你最近平均每晚躺 " + dur(st.avgTib) + "，实际睡着 " + dur(st.avgTst) + "，睡眠效率 " + pct(st.avgSe) + "。", 14, TEXT, false);
                text(pc, "建议窗口 " + dur(w0) + "：" + hm(SleepEntry.diff(w0, wake)) + " 上床，" + hm(wake) + " 起床", 16, ACCENT, true);
                text(pc, "原理：把躺床时间缩短到接近你实际能睡着的时间 → 睡眠压力积累 → 入睡更快、夜醒更少，大脑重新把“床”和“睡着”联系起来。之后每周根据睡眠效率逐步延长。前 1-2 周白天可能更困，这是正常反应，坚持过去就会好转。", 13, SUB, false);
                text(pc, SAFETY, 12, BAD, false);
                button(pc, "开始计划", v -> {
                    Store.setWindow(this, w0);
                    Reminders.scheduleAll(this);
                    showTab(0);
                });
            }
        } else {
            String setDate = Store.windowDate(this);
            long days = daysSince(setDate);
            List<SleepEntry> since = SleepMath.recent(all, setDate);
            if (days >= 7 && since.size() >= 5) {
                SleepMath.Stats s2 = SleepMath.stats(since);
                final int nw = SleepMath.adjust(win, s2.avgSe);
                text(pc, "本周睡眠效率 " + pct(s2.avgSe) + "（实际睡着 ÷ 躺在床上）", 15, TEXT, true);
                String advice = nw > win ? "效率 ≥ 90%，说明你已经能睡满窗口，可以延长 15 分钟。"
                        : nw < win ? "效率 < 85%，躺床时间里还有不少清醒时间，建议再缩短 15 分钟。"
                        : "效率在 85%-90% 之间，窗口刚好，保持不变。";
                text(pc, advice, 14, TEXT, false);
                button(pc, nw == win ? "保持 " + dur(win) + "，开始新的一周"
                        : "调整为 " + dur(nw) + "（" + hm(SleepEntry.diff(nw, wake)) + " 上床）", v -> {
                    Store.setWindow(this, nw);
                    Reminders.scheduleAll(this);
                    showTab(0);
                });
            } else {
                text(pc, "当前窗口 " + dur(win) + "，每周根据睡眠效率调整一次。", 14, TEXT, false);
                text(pc, "距离下次调整还有 " + Math.max(0, 7 - days) + " 天（这期间需要至少 5 晚日记，目前 " + since.size() + " 晚）。", 14, SUB, false);
            }
            space(pc, 6);
            text(pc, "规则：\n• 困了才上床，不困就晚点睡，但起床时间不变\n• 窗口之外不躺床、不打盹\n• 躺下约 20 分钟睡不着就起来，困了再回去\n• 床只用来睡觉（和亲密），不在床上刷手机、追剧、工作", 13, SUB, false);
            ghost(pc, "结束计划", v -> new AlertDialog.Builder(this)
                    .setMessage("确定结束睡眠窗口计划吗？")
                    .setPositiveButton("结束", (d, w) -> {
                        Store.setWindow(this, -1);
                        Reminders.scheduleAll(this);
                        showTab(0);
                    })
                    .setNegativeButton("取消", null).show());
        }

        if (st.n > 0) {
            LinearLayout sc = card(p);
            text(sc, "最近 " + st.n + " 晚", 17, TEXT, true);
            row(sc, "平均睡着", dur(st.avgTst));
            row(sc, "睡眠效率", pct(st.avgSe) + (st.avgSe >= 0.85f ? "  良好" : "  偏低（目标 ≥ 85%）"));
            row(sc, "入睡用时", st.avgSol + " 分钟" + (st.avgSol <= 30 ? "" : "  偏长"));
            row(sc, "夜间醒着", st.avgWaso + " 分钟" + (st.avgWaso <= 30 ? "" : "  偏长"));
            row(sc, "睡眠质量", String.format(Locale.US, "%.1f / 5", st.avgQuality));
            int n = Math.min(14, all.size());
            float[] hs = new float[n];
            int[] cs = new int[n];
            String[] ls = new String[n];
            for (int i = 0; i < n; i++) {
                SleepEntry e = all.get(n - 1 - i);
                hs[i] = e.tst() / 60f;
                float se = e.se();
                cs[i] = se >= 0.85f ? GOOD : se >= 0.75f ? MID : BAD;
                ls[i] = e.date.substring(8);
            }
            space(sc, 8);
            text(sc, "每晚实际睡眠（小时），颜色 = 睡眠效率", 12, SUB, false);
            BarsView bv = new BarsView(this);
            bv.setData(hs, cs, ls, win > 0 ? win / 60f : 0);
            sc.addView(bv);
        }

        LinearLayout set = card(p);
        text(set, "设置", 17, TEXT, true);
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView wl = text(r, "固定起床时间  " + hm(wake), 15, TEXT, false);
        wl.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        Button chg = ghost(r, "修改", v -> new TimePickerDialog(this, (tp, h, m) -> {
            Store.setWakeMin(this, h * 60 + m);
            Reminders.scheduleAll(this);
            showTab(0);
        }, wake / 60, wake % 60, true).show());
        chg.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        set.addView(r);

        Switch sw = new Switch(this);
        sw.setText("每日提醒（咖啡因截止、睡前1小时放松、上床、起床）");
        sw.setTextColor(TEXT);
        sw.setTextSize(14);
        sw.setChecked(Store.remindersOn(this));
        sw.setPadding(0, dp(10), 0, dp(10));
        sw.setOnCheckedChangeListener((b, on) -> {
            Store.setRemindersOn(this, on);
            Reminders.scheduleAll(this);
        });
        set.addView(sw);

        button(set, "在系统闹钟里设置每天 " + hm(wake) + " 的起床闹钟", v -> setSystemAlarm(wake));
        if (Build.VERSION.SDK_INT >= 31 && !getSystemService(AlarmManager.class).canScheduleExactAlarms()) {
            ghost(set, "允许准时提醒（可选，否则可能延迟几分钟）", v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName())));
                } catch (ActivityNotFoundException ignored) {
                }
            });
        }
        text(set, "所有数据只保存在你的手机上。", 12, SUB, false);
    }

    private void setSystemAlarm(int wake) {
        ArrayList<Integer> days = new ArrayList<>();
        for (int d = Calendar.SUNDAY; d <= Calendar.SATURDAY; d++) days.add(d);
        Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, wake / 60)
                .putExtra(AlarmClock.EXTRA_MINUTES, wake % 60)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "安睡：固定时间起床")
                .putExtra(AlarmClock.EXTRA_DAYS, days);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "没有找到系统闹钟应用，请手动设置", Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------------- 日记

    private void buildDiary(LinearLayout p) {
        text(p, "睡眠日记", 26, TEXT, true);
        text(p, "每天早上起床后花 1 分钟填写。填估计值就行，不需要精确，夜里也不要看时间。", 13, SUB, false);
        space(p, 10);
        button(p, "＋ 记录昨晚的睡眠", v -> editEntry(null));
        space(p, 10);
        List<SleepEntry> all = Store.entries(this);
        if (all.isEmpty()) {
            text(p, "还没有记录。日记是整个方法的基础：它会告诉你真实的睡眠情况，并用来计算你的睡眠窗口。", 14, SUB, false);
        }
        for (SleepEntry e : all) {
            LinearLayout c = card(p);
            float se = e.se();
            text(c, dateLabel(e.date), 15, TEXT, true);
            TextView t = text(c, "睡着 " + dur(e.tst()) + "  ·  效率 " + pct(se) + "  ·  " + stars(e.quality), 15,
                    se >= 0.85f ? GOOD : se >= 0.75f ? MID : BAD, false);
            t.setPadding(0, dp(2), 0, dp(2));
            text(c, "上床 " + hm(e.bed) + " · 醒来 " + hm(e.wake) + " · 起床 " + hm(e.up)
                    + "\n入睡用时 " + e.sol + " 分 · 夜间醒着 " + e.waso + " 分", 13, SUB, false);
            c.setOnClickListener(v -> editEntry(e));
            c.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setMessage("删除 " + dateLabel(e.date) + " 的记录？")
                        .setPositiveButton("删除", (d, w) -> {
                            Store.deleteEntry(this, e.date);
                            showTab(1);
                        }).setNegativeButton("取消", null).show();
                return true;
            });
        }
        if (!all.isEmpty()) text(p, "点击可修改，长按可删除。", 12, SUB, false);
    }

    private void editEntry(SleepEntry src) {
        final SleepEntry e;
        if (src != null) {
            e = src.copy();
        } else {
            e = new SleepEntry();
            e.date = LocalDate.now().toString();
            List<SleepEntry> all = Store.entries(this);
            if (!all.isEmpty()) {
                SleepEntry last = all.get(0);
                e.bed = last.bed; e.wake = last.wake; e.up = last.up;
            } else {
                e.bed = Store.bedMin(this);
                e.wake = Store.wakeMin(this);
                e.up = (e.wake + 10) % 1440;
            }
            e.sol = 20; e.waso = 15; e.quality = 3;
        }

        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(dp(20), dp(8), dp(20), dp(8));

        final Button dateB = formButton(f);
        final Runnable[] refresh = new Runnable[1];
        final Button bedB = formButton(f), wakeB = formButton(f), upB = formButton(f);
        text(f, "躺下后大约多久睡着？（分钟）", 14, TEXT, false);
        final EditText solE = number(f, e.sol);
        text(f, "夜里醒来的时间加起来大约多久？（分钟）", 14, TEXT, false);
        final EditText wasoE = number(f, e.waso);
        text(f, "整体睡得怎么样？", 14, TEXT, false);
        final RatingBar rb = new RatingBar(this);
        rb.setNumStars(5);
        rb.setStepSize(1);
        rb.setRating(e.quality);
        rb.setProgressTintList(ColorStateList.valueOf(ACCENT));
        LinearLayout rbWrap = new LinearLayout(this);
        rbWrap.addView(rb, new LinearLayout.LayoutParams(-2, -2));
        f.addView(rbWrap);

        refresh[0] = () -> {
            dateB.setText("日期（醒来那天）：" + dateLabel(e.date));
            bedB.setText("上床时间：" + hm(e.bed));
            wakeB.setText("最后一次醒来：" + hm(e.wake));
            upB.setText("起床离开床：" + hm(e.up));
        };
        refresh[0].run();
        dateB.setOnClickListener(v -> {
            LocalDate d = LocalDate.parse(e.date);
            new DatePickerDialog(this, (dp, y, m, day) -> {
                e.date = LocalDate.of(y, m + 1, day).toString();
                refresh[0].run();
            }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()).show();
        });
        bedB.setOnClickListener(v -> pickTime(e.bed, m -> { e.bed = m; refresh[0].run(); }));
        wakeB.setOnClickListener(v -> pickTime(e.wake, m -> { e.wake = m; refresh[0].run(); }));
        upB.setOnClickListener(v -> pickTime(e.up, m -> { e.up = m; refresh[0].run(); }));

        ScrollView sv = new ScrollView(this);
        sv.addView(f);
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(src == null ? "记录睡眠" : "修改记录")
                .setView(sv)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            e.sol = parse(solE);
            e.waso = parse(wasoE);
            e.quality = Math.max(1, Math.round(rb.getRating()));
            int toWake = SleepEntry.diff(e.bed, e.wake);
            if (SleepEntry.diff(e.bed, e.up) < toWake) {
                toast("起床时间不能早于醒来时间");
                return;
            }
            if (e.sol + e.waso > toWake) {
                toast("入睡用时 + 夜间醒着 超过了上床到醒来的总时长，请检查");
                return;
            }
            if (e.date.compareTo(LocalDate.now().toString()) > 0) {
                toast("日期不能是未来");
                return;
            }
            Store.saveEntry(this, e);
            d.dismiss();
            toast("已保存：睡着 " + dur(e.tst()) + "，效率 " + pct(e.se()));
            showTab(1);
        }));
        d.show();
    }

    interface MinuteCallback { void on(int minute); }

    private void pickTime(int init, MinuteCallback cb) {
        new TimePickerDialog(this, (tp, h, m) -> cb.on(h * 60 + m), init / 60, init % 60, true).show();
    }

    private Button formButton(LinearLayout parent) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setTextColor(TEXT);
        b.setTextSize(15);
        b.setBackground(rounded(0xFF262D44, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        parent.addView(b, lp);
        return b;
    }

    private EditText number(LinearLayout parent, int value) {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(String.valueOf(value));
        et.setTextColor(TEXT);
        et.setSelectAllOnFocus(true);
        parent.addView(et);
        return et;
    }

    private int parse(EditText et) {
        try {
            return Math.max(0, Math.min(900, Integer.parseInt(et.getText().toString().trim())));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- 放松

    private void buildRelax(LinearLayout p) {
        text(p, "放松训练", 26, TEXT, true);
        text(p, "躺在床上或睡前做。练习时屏幕会自动调暗，每个阶段切换时手机会轻微振动，可以闭着眼跟着做。", 13, SUB, false);
        space(p, 10);

        relaxCard(p, "4-7-8 呼吸", "吸气 4 秒 → 屏住 7 秒 → 呼气 8 秒。长呼气能激活副交感神经，让心率慢下来。躺下后做 4-8 轮。",
                v -> startBreath("4-7-8 呼吸", new int[]{4, 7, 8}, new String[]{"吸气", "屏住", "呼气"}, new int[]{0, 1, 2}, 0));
        relaxCard(p, "慢呼吸（每分钟 6 次）", "吸气 4 秒 → 呼气 6 秒，持续 10 分钟。研究显示可以降低心率和焦虑，提高心率变异性。",
                v -> startBreath("慢呼吸", new int[]{4, 6}, new String[]{"吸气", "呼气"}, new int[]{0, 2}, 10));
        relaxCard(p, "渐进式肌肉放松", "约 5 分钟，从手到脚依次“收紧 5 秒 → 放松 15 秒”。是 CBT-I 中证据最充分的放松方法之一，特别适合身体紧绷、睡不着的时候。",
                v -> startPmr());
        relaxCard(p, "认知洗牌", "屏幕每隔 12 秒出现一个随机的词，你只需要在脑子里想象它的画面（比如“苹果”→ 一个红苹果）。它能打断反复的思考和担忧，模拟入睡前那种零散的思维。",
                v -> startShuffle());
        relaxCard(p, "写下烦恼", "睡前 1-2 小时，把担心的事和“下一步怎么做”写下来。研究表明这能减少躺下后的胡思乱想，让入睡更快。",
                v -> showWorries());
    }

    private void relaxCard(LinearLayout p, String title, String desc, View.OnClickListener l) {
        LinearLayout c = card(p);
        text(c, title, 17, TEXT, true);
        text(c, desc, 13, SUB, false);
        button(c, "开始", l);
    }

    private LinearLayout sessionPage(String title) {
        stopSession();
        content.setTag("session");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 0.05f;
        getWindow().setAttributes(lp);
        LinearLayout p = page();
        p.setGravity(Gravity.CENTER_HORIZONTAL);
        text(p, title, 20, SUB, true).setGravity(Gravity.CENTER);
        return p;
    }

    private void stopSession() {
        if (sessionTick != null) handler.removeCallbacks(sessionTick);
        sessionTick = null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        if (lp.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            getWindow().setAttributes(lp);
        }
    }

    private void endButton(LinearLayout p) {
        space(p, 16);
        ghost(p, "结束", v -> showTab(tab));
    }

    private void buzz() {
        Vibrator vib = getSystemService(Vibrator.class);
        if (vib != null && vib.hasVibrator()) vib.vibrate(VibrationEffect.createOneShot(35, 60));
    }

    private void startBreath(String title, int[] secs, String[] labels, int[] kinds, int maxMinutes) {
        LinearLayout p = sessionPage(title);
        final BreathView bv = new BreathView(this);
        p.addView(bv, new LinearLayout.LayoutParams(-1, -2));
        final TextView info = text(p, "", 14, SUB, false);
        info.setGravity(Gravity.CENTER);
        endButton(p);
        setPage(p);

        int cycle = 0;
        for (int s : secs) cycle += s;
        final int cycleMs = cycle * 1000;
        final long start = System.currentTimeMillis();
        final int[] lastPhase = {-1};
        sessionTick = new Runnable() {
            @Override
            public void run() {
                long el = System.currentTimeMillis() - start;
                if (maxMinutes > 0 && el >= maxMinutes * 60_000L) {
                    bv.set(0.3f, "完成", "");
                    info.setText("做得很好。保持这种放松的状态，安静地躺着就好。");
                    buzz();
                    sessionTick = null;
                    return;
                }
                float t = (el % cycleMs) / 1000f;
                int i = 0;
                float off = 0;
                while (i < secs.length - 1 && t >= off + secs[i]) off += secs[i++];
                float pt = t - off;
                float frac = Math.min(1f, pt / secs[i]);
                float sc = kinds[i] == 0 ? frac : kinds[i] == 2 ? 1f - frac : (i > 0 && kinds[i - 1] == 2 ? 0f : 1f);
                // 缓动，让变化更像呼吸
                sc = (float) (0.5 - 0.5 * Math.cos(Math.PI * sc));
                bv.set(sc, labels[i], String.valueOf((int) Math.ceil(secs[i] - pt)));
                int phaseId = (int) (el / cycleMs) * secs.length + i;
                if (phaseId != lastPhase[0]) {
                    lastPhase[0] = phaseId;
                    buzz();
                }
                String time = mmss(el / 1000) + (maxMinutes > 0 ? " / " + maxMinutes + ":00" : "");
                info.setText("第 " + (el / cycleMs + 1) + " 轮  ·  " + time);
                handler.postDelayed(this, 33);
            }
        };
        handler.post(sessionTick);
    }

    private void startPmr() {
        String[][] groups = {
                {"双手和前臂", "用力握拳"}, {"上臂", "弯曲手臂，像秀肌肉一样绷紧"},
                {"额头", "抬起眉毛，皱起额头"}, {"眼睛和脸颊", "紧紧闭眼，皱起鼻子"},
                {"嘴和下巴", "咬紧牙齿，嘴角向两边拉"}, {"脖子", "下巴轻轻往胸口压"},
                {"肩膀", "把肩膀耸向耳朵"}, {"后背", "肩胛骨向中间夹紧"},
                {"腹部", "收紧肚子，像准备被轻轻打一拳"}, {"臀部和大腿", "夹紧臀部，大腿绷直"},
                {"小腿", "脚尖往上勾向自己"}, {"双脚", "脚趾向下蜷起来"}};
        final List<String[]> steps = new ArrayList<>(); // {标题, 说明, 秒数}
        steps.add(new String[]{"准备", "找一个舒服的姿势躺好，慢慢呼吸。收紧时用 5-7 成力就好，不要用力过猛，有伤痛的部位可以跳过。", "10"});
        for (String[] g : groups) {
            steps.add(new String[]{"收紧：" + g[0], g[1] + "，保持住…", "5"});
            steps.add(new String[]{"放松：" + g[0], "一下子松开。感受紧张流走，注意放松后的温暖和沉重感。", "15"});
        }
        steps.add(new String[]{"全身放松", "从头到脚感受整个身体的沉重和放松。继续慢慢呼吸，什么都不用做。", "30"});

        LinearLayout p = sessionPage("渐进式肌肉放松");
        space(p, 40);
        final TextView head = text(p, "", 26, ACCENT, true);
        head.setGravity(Gravity.CENTER);
        final TextView count = text(p, "", 48, TEXT, false);
        count.setGravity(Gravity.CENTER);
        final TextView desc = text(p, "", 16, SUB, false);
        desc.setGravity(Gravity.CENTER);
        final TextView prog = text(p, "", 13, SUB, false);
        prog.setGravity(Gravity.CENTER);
        endButton(p);
        setPage(p);

        final long start = System.currentTimeMillis();
        final int[] last = {-1};
        sessionTick = new Runnable() {
            @Override
            public void run() {
                long el = System.currentTimeMillis() - start;
                long acc = 0;
                int i = 0;
                while (i < steps.size() && el >= acc + Long.parseLong(steps.get(i)[2]) * 1000) {
                    acc += Long.parseLong(steps.get(i)[2]) * 1000;
                    i++;
                }
                if (i >= steps.size()) {
                    head.setText("完成");
                    count.setText("");
                    desc.setText("保持这种放松的感觉，安静地躺着。不用去“努力”睡着，睡意会自己来。");
                    prog.setText("");
                    buzz();
                    sessionTick = null;
                    return;
                }
                String[] s = steps.get(i);
                if (i != last[0]) {
                    last[0] = i;
                    head.setText(s[0]);
                    desc.setText(s[1]);
                    buzz();
                }
                long rem = (acc + Long.parseLong(s[2]) * 1000 - el + 999) / 1000;
                count.setText(String.valueOf(rem));
                prog.setText((i + 1) + " / " + steps.size());
                handler.postDelayed(this, 200);
            }
        };
        handler.post(sessionTick);
    }

    private static final String[] WORDS = ("苹果 月亮 小船 毛毯 枕头 松树 湖面 蜡烛 雪花 羽毛 贝壳 草地 蝴蝶 茶杯 灯塔 风车 云朵 小猫 "
            + "气球 书架 雨伞 稻田 竹林 篝火 帐篷 铅笔 钢琴 吊床 橘子 海豚 鹅卵石 落叶 小溪 毛衣 风筝 花瓶 樱桃 熊猫 "
            + "向日葵 沙滩 雪人 木屋 星星 秋千 果园 小狗 面包 蜂蜜 山谷 南瓜 鸽子 彩虹 西瓜 青蛙 葡萄 灯笼 积木 "
            + "邮筒 麦穗 草莓 椰子树 小熊 拖鞋 地球仪 荷叶 金鱼 柠檬 瀑布 栅栏 蘑菇 刺猬 邮票 雨靴 陶罐 汽水 葫芦 "
            + "小鹿 时钟 摇椅 海鸥 烤红薯 围巾 玉米 帆船 窗台 篮子 天鹅 石桥 灯泡 纸飞机 棉花糖 骆驼 稻草人").split(" ");

    private void startShuffle() {
        LinearLayout p = sessionPage("认知洗牌");
        text(p, "想象这个东西的样子、颜色、质感。不用想得很清楚，下一个词出现前随意地想就好。", 14, SUB, false)
                .setGravity(Gravity.CENTER);
        space(p, 60);
        final TextView word = text(p, "", 44, ACCENT, true);
        word.setGravity(Gravity.CENTER);
        space(p, 60);
        final TextView info = text(p, "", 13, SUB, false);
        info.setGravity(Gravity.CENTER);
        endButton(p);
        setPage(p);

        final Random rnd = new Random();
        final long start = System.currentTimeMillis();
        final int[] n = {0};
        sessionTick = new Runnable() {
            @Override
            public void run() {
                String w;
                do {
                    w = WORDS[rnd.nextInt(WORDS.length)];
                } while (w.contentEquals(word.getText()));
                word.setText(w);
                n[0]++;
                info.setText("第 " + n[0] + " 个  ·  " + mmss((System.currentTimeMillis() - start) / 1000));
                handler.postDelayed(this, 12_000);
            }
        };
        handler.post(sessionTick);
    }

    private void showWorries() {
        content.setTag("worries");
        LinearLayout p = page();
        text(p, "写下烦恼", 24, TEXT, true);
        text(p, "1. 写下现在让你挂心的事（工作、人际、明天的安排…）\n2. 每件事后面写一个“下一步”，哪怕只是“明天上午10点再想”\n3. 写完就把它们留在这里——今晚不用再处理了。躺下后如果又想起来，告诉自己：已经记下了，明天再说。", 14, SUB, false);
        space(p, 10);
        final EditText et = new EditText(this);
        et.setText(Store.worries(this));
        et.setHint("例如：\n项目进度落后 → 明天先跟老王确认需求\n体检报告 → 周三打电话问医生");
        et.setHintTextColor(0xFF5E6680);
        et.setTextColor(TEXT);
        et.setGravity(Gravity.TOP);
        et.setMinLines(10);
        et.setBackground(rounded(CARD, 12));
        et.setPadding(dp(14), dp(14), dp(14), dp(14));
        p.addView(et, new LinearLayout.LayoutParams(-1, -2));
        space(p, 10);
        button(p, "保存，今天到此为止", v -> {
            Store.setWorries(this, et.getText().toString());
            toast("已记下。现在可以放下它们了。");
            showTab(2);
        });
        ghost(p, "清空", v -> et.setText(""));
        setPage(p);
    }

    // ---------------------------------------------------------------- 声音

    private void buildSound(LinearLayout p) {
        text(p, "助眠声音", 26, TEXT, true);
        text(p, "稳定的背景噪音能掩盖突然出现的环境声（车声、邻居、鼾声），减少被吵醒。音量调到刚好盖过环境声即可，不要太大。锁屏后也会继续播放，到时间自动淡出。", 13, SUB, false);
        space(p, 10);

        final int[] type = {Store.soundType(this)};
        final int[] vol = {Store.soundVol(this)};
        final int[] timer = {Store.soundTimer(this)};

        LinearLayout c = card(p);
        text(c, "声音", 15, TEXT, true);
        String[] desc = {"白噪音 — 像电视雪花声，最“亮”，遮盖力最强",
                "粉红噪音 — 像稳定的雨声，更柔和（推荐）",
                "棕色噪音 — 像瀑布或远处的雷声，低沉",
                "海浪 — 有节奏的起伏，可以跟着放慢呼吸"};
        RadioGroup rg = new RadioGroup(this);
        for (int i = 0; i < desc.length; i++) {
            RadioButton rb = radio(desc[i]);
            rb.setId(1000 + i);
            rg.addView(rb);
        }
        rg.check(1000 + type[0]);
        c.addView(rg);

        text(c, "音量", 15, TEXT, true).setPadding(0, dp(12), 0, 0);
        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(vol[0]);
        sb.setProgressTintList(ColorStateList.valueOf(ACCENT));
        sb.setThumbTintList(ColorStateList.valueOf(ACCENT));
        c.addView(sb);

        text(c, "定时关闭", 15, TEXT, true).setPadding(0, dp(12), 0, 0);
        int[] opts = {15, 30, 60, 90, 0};
        RadioGroup tg = new RadioGroup(this);
        tg.setOrientation(RadioGroup.HORIZONTAL);
        for (int o : opts) {
            RadioButton rb = radio(o == 0 ? "不限" : o + "分");
            rb.setId(2000 + o);
            tg.addView(rb);
        }
        tg.check(2000 + timer[0]);
        c.addView(tg);
        text(c, "建议设置定时：入睡后整夜播放并非必要，结束前会用 1 分钟慢慢淡出。", 12, SUB, false);

        space(p, 6);
        final Button play = button(p, NoiseService.running ? "■  停止" : "▶  播放", null);
        play.setOnClickListener(v -> {
            if (NoiseService.running) {
                NoiseService.stop(this);
                NoiseService.running = false;
                play.setText("▶  播放");
            } else {
                NoiseService.start(this, type[0], vol[0], timer[0]);
                NoiseService.running = true;
                play.setText("■  停止");
            }
        });

        rg.setOnCheckedChangeListener((g, id) -> {
            type[0] = id - 1000;
            Store.setSound(this, type[0], vol[0], timer[0]);
            NoiseService.update(type[0], vol[0]);
        });
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean user) {
                vol[0] = v;
                NoiseService.update(type[0], vol[0]);
            }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) {
                Store.setSound(MainActivity.this, type[0], vol[0], timer[0]);
            }
        });
        tg.setOnCheckedChangeListener((g, id) -> {
            timer[0] = id - 2000;
            Store.setSound(this, type[0], vol[0], timer[0]);
            if (NoiseService.running) NoiseService.start(this, type[0], vol[0], timer[0]);
        });
    }

    private RadioButton radio(String s) {
        RadioButton rb = new RadioButton(this);
        rb.setText(s);
        rb.setTextColor(TEXT);
        rb.setTextSize(14);
        rb.setButtonTintList(ColorStateList.valueOf(ACCENT));
        rb.setPadding(dp(4), dp(6), dp(8), dp(6));
        return rb;
    }

    // ---------------------------------------------------------------- 指南

    private void buildGuide(LinearLayout p) {
        text(p, "睡眠指南", 26, TEXT, true);
        space(p, 10);
        String[][] sections = {
                {"为什么这个方法有效",
                        "安睡的核心是失眠认知行为疗法（CBT-I）。它是美国医师协会、欧洲睡眠研究学会和中国失眠指南推荐的慢性失眠首选治疗，约 70%-80% 的人能明显改善，而且停止后效果依然持久，长期效果优于安眠药。\n\n它包括：睡眠日记、睡眠限制、刺激控制、放松训练、认知调整和睡眠卫生。通常坚持 4-8 周会看到明显变化，第 1-2 周可能会更困，这说明它在起作用。"},
                {"刺激控制：让床重新等于“睡觉”",
                        "1. 只有感到困（眼皮沉、打哈欠）才上床，而不是“到点了”\n2. 床只用来睡觉（和亲密），不在床上刷手机、追剧、工作、想事情\n3. 躺下大约 20 分钟还睡不着（凭感觉估计，不要看表），就起身去另一个房间，在昏暗灯光下做点无聊的事（看纸质书、叠衣服），困了再回床上。夜里醒来睡不着也一样\n4. 每天同一时间起床，不论睡了多少\n5. 白天不打盹（实在撑不住：下午 3 点前，不超过 20 分钟）"},
                {"白天做对的事",
                        "• 早上起床后 1 小时内晒 10-30 分钟太阳（阴天也有效），这是调整生物钟最强的信号\n• 规律运动，最好在白天；睡前 2 小时内避免剧烈运动\n• 下午 2 点后（或睡前 8 小时）不碰咖啡因：咖啡、浓茶、奶茶、可乐、能量饮料、黑巧克力\n• 酒精虽然让人更快犯困，但会让后半夜睡眠变浅、频繁醒来，睡前 3 小时内不要喝\n• 晚饭不要过饱，睡前 2-3 小时内少吃东西"},
                {"睡前 1 小时",
                        "• 调暗家里的灯，换成暖色光；减少手机电脑，或者打开手机的护眼/夜间模式\n• 睡前 1-2 小时洗个温水澡（40℃ 左右），之后体温下降会帮助入睡\n• 把担心的事写进“写下烦恼”\n• 做一个固定的睡前程序（洗漱 → 放松训练 → 上床），让大脑形成条件反射"},
                {"卧室环境",
                        "• 偏凉：18-22℃ 左右\n• 黑：遮光窗帘或眼罩\n• 静：耳塞，或用“声音”页的粉红噪音掩盖杂音\n• 把时钟转过去、手机放远一点——夜里看时间会增加焦虑"},
                {"调整对睡眠的想法",
                        "• 不要“努力”睡着，越用力越清醒。试试反过来：躺着放松，告诉自己“保持清醒也没关系”\n• 偶尔一晚没睡好并不危险，身体会在之后自动补偿深睡眠\n• “必须睡满 8 小时”不是真的，每个人需要的睡眠不同，以白天精神为准\n• 别把白天所有不顺都归咎于睡眠，也别因为睡不好而取消白天的活动"},
                {"什么时候应该看医生",
                        "• 打鼾很响、睡觉时憋醒或被人发现呼吸暂停、白天极度困倦 —— 可能是睡眠呼吸暂停\n• 晚上腿部有难受的感觉、必须动一动才舒服 —— 可能是不宁腿综合征\n• 失眠超过 3 个月，每周 3 晚以上，严重影响白天\n• 情绪持续低落、焦虑，或有伤害自己的想法 —— 请尽快就医或拨打心理援助热线（如 12356）\n• 正在服用安眠药想减停时，请在医生指导下逐渐减量\n\n本 App 是自助工具，不能替代医疗诊断和治疗。"},
        };
        for (String[] s : sections) {
            LinearLayout c = card(p);
            text(c, s[0], 17, ACCENT, true);
            space(c, 4);
            TextView t = text(c, s[1], 14, TEXT, false);
            t.setLineSpacing(dp(3), 1f);
        }
    }

    // ---------------------------------------------------------------- UI helpers

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private LinearLayout page() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(20), dp(16), dp(24));
        return l;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(rounded(CARD, 14));
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        parent.addView(c, lp);
        return c;
    }

    private TextView text(ViewGroup parent, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        t.setPadding(0, dp(2), 0, dp(2));
        parent.addView(t);
        return t;
    }

    private void row(LinearLayout parent, String k, String v) {
        LinearLayout r = new LinearLayout(this);
        TextView a = text(r, k, 14, SUB, false);
        a.setLayoutParams(new LinearLayout.LayoutParams(dp(90), -2));
        text(r, v, 14, TEXT, false);
        parent.addView(r);
    }

    private void space(LinearLayout parent, int dpH) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(dpH)));
    }

    private Button button(ViewGroup parent, String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(0xFF1A1206);
        b.setTextSize(15);
        b.setTypeface(null, Typeface.BOLD);
        b.setBackground(rounded(ACCENT, 12));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.topMargin = dp(10);
        parent.addView(b, lp);
        return b;
    }

    private Button ghost(ViewGroup parent, String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(ACCENT);
        b.setTextSize(14);
        GradientDrawable g = rounded(0x00000000, 12);
        g.setStroke(dp(1), 0x88FFB86B);
        b.setBackground(g);
        b.setOnClickListener(l);
        b.setPadding(dp(16), 0, dp(16), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(44));
        lp.topMargin = dp(8);
        parent.addView(b, lp);
        return b;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    // ---------------------------------------------------------------- formatting

    static String hm(int m) {
        return String.format(Locale.US, "%02d:%02d", m / 60, m % 60);
    }

    static String dur(int m) {
        return m / 60 + " 小时" + (m % 60 > 0 ? " " + m % 60 + " 分" : "");
    }

    static String pct(float f) {
        return Math.round(f * 100) + "%";
    }

    static String mmss(long s) {
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    static String stars(int q) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 5; i++) sb.append(i <= q ? '★' : '☆');
        return sb.toString();
    }

    static String dateLabel(String iso) {
        LocalDate d = LocalDate.parse(iso);
        String[] w = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        long ago = ChronoUnit.DAYS.between(d, LocalDate.now());
        String rel = ago == 0 ? "（今天）" : ago == 1 ? "（昨天）" : "";
        return d.getMonthValue() + "月" + d.getDayOfMonth() + "日 " + w[d.getDayOfWeek().getValue() - 1] + rel;
    }

    static long daysSince(String iso) {
        try {
            return ChronoUnit.DAYS.between(LocalDate.parse(iso), LocalDate.now());
        } catch (Exception e) {
            return 0;
        }
    }
}
