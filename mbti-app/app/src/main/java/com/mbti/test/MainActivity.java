package com.mbti.test;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int PRIMARY = 0xFF4F46E5;
    private static final int PRIMARY_LIGHT = 0xFFEEF0FF;
    private static final int TEXT = 0xFF1F2937;
    private static final int TEXT_SUB = 0xFF6B7280;
    private static final int BG = 0xFFF5F6FA;
    private static final int[] DIM_COLORS = {0xFF0EA5E9, 0xFFF59E0B, 0xFF10B981, 0xFFEC4899};
    private static final String[][] DIM_NAMES = {
            {"外向 E", "内向 I"}, {"实感 S", "直觉 N"}, {"思考 T", "情感 F"}, {"判断 J", "感知 P"}};
    private static final String[] DIM_TITLES = {"精力来源", "获取信息", "做出决定", "生活方式"};

    private static final String PREFS = "mbti";
    private static final String KEY_PROGRESS = "progress";
    private static final String KEY_POS = "pos";
    private static final String KEY_HISTORY = "history";

    private enum Screen { HOME, QUIZ, RESULT, HISTORY, ABOUT }

    private Screen screen = Screen.HOME;
    private List<Questions.Item> items;
    private int[] answers;
    private int pos;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(0xFF3730A3);
        }
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        items = Questions.ordered();
        answers = new int[items.size()];
        showHome();
    }

    // ------------------------------------------------------------------ 首页

    private void showHome() {
        screen = Screen.HOME;
        LinearLayout root = column();
        root.setPadding(dp(20), dp(40), dp(20), dp(24));

        TextView logo = text("MBTI", 44, PRIMARY, true);
        logo.setGravity(Gravity.CENTER);
        root.addView(logo);
        TextView title = text("16 型人格测试", 22, TEXT, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView sub = text("基于荣格心理类型理论 · 四维度 · 60 题", 14, TEXT_SUB, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(20));
        root.addView(sub);

        LinearLayout card = card();
        card.addView(sectionTitle("作答须知"));
        card.addView(body("1. 共 60 道二选一题目，约需 8~12 分钟。\n"
                + "2. 每题有两种说法，请选择更符合你的一侧，并选择程度（非常 / 比较）。\n"
                + "3. 请按照平时自然、放松状态下真实的你作答，而不是你\"希望成为\"或\"工作要求\"的样子。\n"
                + "4. 题目没有对错好坏之分，凭第一反应作答即可，不要过度思考。\n"
                + "5. 可随时退出，进度会自动保存。"));
        root.addView(card);

        int savedPos = prefs.getInt(KEY_POS, -1);
        String saved = prefs.getString(KEY_PROGRESS, null);
        if (saved != null && savedPos > 0) {
            root.addView(primaryButton("继续上次测试（已答 " + savedPos + " / " + items.size() + "）",
                    v -> resumeQuiz()));
            root.addView(secondaryButton("重新开始测试", v -> startQuiz()));
        } else {
            root.addView(primaryButton("开始测试", v -> startQuiz()));
        }
        root.addView(secondaryButton("历史结果", v -> showHistory()));
        root.addView(secondaryButton("关于本测试", v -> showAbout()));

        setScroll(root);
    }

    // ------------------------------------------------------------------ 答题

    private void startQuiz() {
        answers = new int[items.size()];
        pos = 0;
        saveProgress();
        showQuestion();
    }

    private void resumeQuiz() {
        String saved = prefs.getString(KEY_PROGRESS, "");
        String[] parts = saved.split(",");
        answers = new int[items.size()];
        for (int i = 0; i < parts.length && i < answers.length; i++) {
            try {
                answers[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        pos = Math.min(prefs.getInt(KEY_POS, 0), items.size() - 1);
        showQuestion();
    }

    private void saveProgress() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < answers.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(answers[i]);
        }
        prefs.edit().putString(KEY_PROGRESS, sb.toString()).putInt(KEY_POS, pos).apply();
    }

    private void clearProgress() {
        prefs.edit().remove(KEY_PROGRESS).remove(KEY_POS).apply();
    }

    private void showQuestion() {
        screen = Screen.QUIZ;
        Questions.Item item = items.get(pos);

        LinearLayout root = column();
        root.setPadding(dp(20), dp(16), dp(20), dp(24));

        // 顶部：上一题 + 进度
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text(pos == 0 ? "‹ 首页" : "‹ 上一题", 15, PRIMARY, false);
        back.setPadding(0, dp(8), dp(16), dp(8));
        back.setOnClickListener(v -> onBackPressed());
        top.addView(back);
        View spacer = new View(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        top.addView(text((pos + 1) + " / " + items.size(), 15, TEXT_SUB, true));
        root.addView(top);

        // 进度条
        FrameLayout bar = new FrameLayout(this);
        bar.setBackground(round(0xFFE5E7EB, 4));
        View fill = new View(this);
        fill.setBackground(round(PRIMARY, 4));
        bar.addView(fill);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)));
        final float frac = (pos + 1f) / items.size();
        bar.post(() -> {
            ViewGroup.LayoutParams lp = fill.getLayoutParams();
            lp.width = (int) (bar.getWidth() * frac);
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            fill.setLayoutParams(lp);
        });

        TextView prompt = text(item.prompt, 20, TEXT, true);
        prompt.setPadding(0, dp(28), 0, dp(20));
        root.addView(prompt);

        int current = answers[pos];
        root.addView(optionCard("A", item.optionA, current > 0));
        TextView or = text("或", 13, TEXT_SUB, false);
        or.setGravity(Gravity.CENTER);
        or.setPadding(0, dp(6), 0, dp(6));
        root.addView(or);
        root.addView(optionCard("B", item.optionB, current < 0));

        TextView hint = text("哪一种更像你？", 14, TEXT_SUB, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(24), 0, dp(10));
        root.addView(hint);

        LinearLayout scale = new LinearLayout(this);
        scale.setOrientation(LinearLayout.HORIZONTAL);
        int[] values = {2, 1, -1, -2};
        String[] labels = {"非常像A", "比较像A", "比较像B", "非常像B"};
        for (int i = 0; i < 4; i++) {
            final int value = values[i];
            scale.addView(scaleButton(labels[i], value, current == value, v -> answer(value)),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        root.addView(scale);

        if (current != 0 && pos < items.size() - 1) {
            root.addView(secondaryButton("下一题 ›", v -> {
                pos++;
                saveProgress();
                showQuestion();
            }));
        }

        setScroll(root);
    }

    private View optionCard(String tag, String content, boolean selected) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable bg = round(selected ? PRIMARY_LIGHT : Color.WHITE, 14);
        bg.setStroke(dp(selected ? 2 : 1), selected ? PRIMARY : 0xFFE5E7EB);
        row.setBackground(bg);

        TextView badge = text(tag, 16, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(round(PRIMARY, 16));
        row.addView(badge, new LinearLayout.LayoutParams(dp(32), dp(32)));

        TextView t = text(content, 17, TEXT, false);
        t.setPadding(dp(14), 0, 0, 0);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private View scaleButton(String label, int value, boolean selected, View.OnClickListener l) {
        LinearLayout col = column();
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(2), dp(4), dp(2), dp(4));
        int size = Math.abs(value) == 2 ? 46 : 36;
        View circle = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        int color = value > 0 ? PRIMARY : 0xFF7C3AED;
        d.setColor(selected ? color : Color.WHITE);
        d.setStroke(dp(2), color);
        circle.setBackground(d);
        FrameLayout holder = new FrameLayout(this);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(size), dp(size));
        clp.gravity = Gravity.CENTER;
        holder.addView(circle, clp);
        col.addView(holder, new LinearLayout.LayoutParams(dp(50), dp(50)));
        TextView t = text(label, 12, selected ? color : TEXT_SUB, selected);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(4), 0, 0);
        col.addView(t);
        col.setOnClickListener(l);
        return col;
    }

    private void answer(int value) {
        answers[pos] = value;
        if (pos < items.size() - 1) {
            pos++;
            saveProgress();
            showQuestion();
        } else {
            saveProgress();
            int firstMissing = -1;
            for (int i = 0; i < answers.length; i++) {
                if (answers[i] == 0) {
                    firstMissing = i;
                    break;
                }
            }
            if (firstMissing >= 0) {
                pos = firstMissing;
                new AlertDialog.Builder(this)
                        .setMessage("第 " + (firstMissing + 1) + " 题还没有作答，请先完成所有题目。")
                        .setPositiveButton("去作答", (dlg, w) -> showQuestion())
                        .show();
                return;
            }
            Scoring.Result r = Scoring.score(items, answers);
            saveHistory(r);
            clearProgress();
            showResult(r, true);
        }
    }

    // ------------------------------------------------------------------ 结果

    private void showResult(Scoring.Result r, boolean fresh) {
        screen = Screen.RESULT;
        Types.Info info = Types.get(r.type);

        LinearLayout root = column();
        root.setPadding(dp(20), dp(24), dp(20), dp(24));

        LinearLayout hero = column();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(dp(16), dp(24), dp(16), dp(24));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF4F46E5, 0xFF7C3AED});
        g.setCornerRadius(dp(18));
        hero.setBackground(g);
        hero.addView(centered(text("你的类型倾向", 14, 0xFFE0E7FF, false)));
        TextView code = centered(text(r.type, 52, Color.WHITE, true));
        code.setLetterSpacing(0.1f);
        hero.addView(code);
        hero.addView(centered(text(info.name, 20, Color.WHITE, true)));
        TextView pop = centered(text("人群中约占比例：" + info.population.replace("约 ", ""), 13, 0xFFE0E7FF, false));
        pop.setPadding(0, dp(8), 0, 0);
        hero.addView(pop);
        root.addView(hero);

        // 四维度
        LinearLayout dims = card();
        dims.addView(sectionTitle("四个维度的偏好"));
        boolean anySlight = false;
        for (int d = 0; d < 4; d++) {
            dims.addView(dimensionRow(r, d));
            if (r.clarity(d) <= 25) anySlight = true;
        }
        TextView note = body("百分比表示你在该维度两端选择的相对比例；清晰度表示偏好的明确程度"
                + "（轻微 / 中等 / 明显 / 非常明显）。");
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        note.setTextColor(TEXT_SUB);
        dims.addView(note);
        root.addView(dims);

        if (anySlight) {
            LinearLayout warn = card();
            warn.setBackground(round(0xFFFFF7ED, 14));
            warn.addView(sectionTitle("提示"));
            StringBuilder sb = new StringBuilder("你在");
            boolean first = true;
            for (int d = 0; d < 4; d++) {
                if (r.clarity(d) <= 25) {
                    if (!first) sb.append("、");
                    sb.append(DIM_TITLES[d]).append("（")
                            .append(Scoring.POLES[d][0]).append("/").append(Scoring.POLES[d][1]).append("）");
                    first = false;
                }
            }
            sb.append(" 维度上的偏好较轻微，两端比较接近。这在实际测评中很常见，"
                    + "说明这一字母的判定不够稳定，重测时可能改变。建议同时阅读相邻类型的描述，看哪一个更像你。");
            warn.addView(body(sb.toString()));
            root.addView(warn);
        }

        root.addView(infoCard("类型概述", info.summary));
        root.addView(infoCard("可能的优势", info.strengths));
        root.addView(infoCard("可能的盲点", info.blindSpots));
        root.addView(infoCard("认知功能顺序（主导 → 劣势）", info.functions));
        root.addView(infoCard("常见于这些领域", info.careers
                + "\n\n（职业偏好仅反映统计上的倾向，任何类型的人都可以在任何领域取得成功。）"));

        LinearLayout disc = card();
        disc.setBackground(round(0xFFF3F4F6, 14));
        TextView d = body("说明：本测试为依据 MBTI 四维度理论自行编制的自评问卷，并非 Myers-Briggs 公司的官方 MBTI® 测评。"
                + "结果描述的是你自我报告的偏好倾向，而不是能力、智力或心理健康状况，"
                + "也不应作为招聘、诊断等重要决策的依据。如需正式测评，请通过持证施测师进行。");
        d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        d.setTextColor(TEXT_SUB);
        disc.addView(d);
        root.addView(disc);

        root.addView(primaryButton("重新测试", v -> startQuiz()));
        root.addView(secondaryButton(fresh ? "返回首页" : "返回", v -> {
            if (fresh) showHome(); else showHistory();
        }));

        setScroll(root);
    }

    private View dimensionRow(Scoring.Result r, int d) {
        LinearLayout box = column();
        box.setPadding(0, dp(10), 0, dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        boolean leftWins = r.letter(d) == Scoring.POLES[d][0];
        int p = r.percent(d);
        int leftPct = leftWins ? p : 100 - p;
        int color = DIM_COLORS[d];

        TextView left = text(DIM_NAMES[d][0] + "  " + leftPct + "%", 14, leftWins ? color : TEXT_SUB, leftWins);
        header.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView right = text((100 - leftPct) + "%  " + DIM_NAMES[d][1], 14, leftWins ? TEXT_SUB : color, !leftWins);
        right.setGravity(Gravity.END);
        header.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        box.addView(header);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(0, dp(6), 0, dp(4));
        View l = new View(this);
        l.setBackground(round(leftWins ? color : 0xFFE5E7EB, 5));
        View rr = new View(this);
        rr.setBackground(round(leftWins ? 0xFFE5E7EB : color, 5));
        LinearLayout.LayoutParams lpL = new LinearLayout.LayoutParams(0, dp(10), Math.max(leftPct, 1));
        lpL.setMarginEnd(dp(2));
        bar.addView(l, lpL);
        bar.addView(rr, new LinearLayout.LayoutParams(0, dp(10), Math.max(100 - leftPct, 1)));
        box.addView(bar);

        TextView c = text(DIM_TITLES[d] + " · 偏好清晰度：" + r.clarityLabel(d), 12, TEXT_SUB, false);
        box.addView(c);
        return box;
    }

    // ------------------------------------------------------------------ 历史

    private void saveHistory(Scoring.Result r) {
        StringBuilder sb = new StringBuilder();
        sb.append(System.currentTimeMillis()).append('|').append(r.type).append('|');
        for (int d = 0; d < 4; d++) {
            sb.append(r.scores[d][0]).append(',').append(r.scores[d][1]);
            if (d < 3) sb.append(',');
        }
        String old = prefs.getString(KEY_HISTORY, "");
        String all = old.isEmpty() ? sb.toString() : sb + "\n" + old;
        // 最多保留 30 条
        String[] lines = all.split("\n");
        if (lines.length > 30) {
            String[] keep = new String[30];
            System.arraycopy(lines, 0, keep, 0, 30);
            all = TextUtils.join("\n", keep);
        }
        prefs.edit().putString(KEY_HISTORY, all).apply();
    }

    private Scoring.Result parseHistory(String line) {
        String[] parts = line.split("\\|");
        if (parts.length != 3) return null;
        String[] nums = parts[2].split(",");
        if (nums.length != 8) return null;
        Scoring.Result r = new Scoring.Result();
        r.type = parts[1];
        r.maxPerDim = Questions.countPerDimension() * 2;
        try {
            for (int d = 0; d < 4; d++) {
                r.scores[d][0] = Integer.parseInt(nums[d * 2]);
                r.scores[d][1] = Integer.parseInt(nums[d * 2 + 1]);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return Types.get(r.type) == null ? null : r;
    }

    private void showHistory() {
        screen = Screen.HISTORY;
        LinearLayout root = column();
        root.setPadding(dp(20), dp(16), dp(20), dp(24));
        TextView back = text("‹ 首页", 15, PRIMARY, false);
        back.setPadding(0, dp(8), 0, dp(8));
        back.setOnClickListener(v -> showHome());
        root.addView(back);
        TextView title = text("历史结果", 22, TEXT, true);
        title.setPadding(0, dp(8), 0, dp(12));
        root.addView(title);

        String all = prefs.getString(KEY_HISTORY, "");
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        List<String> lines = new ArrayList<>();
        for (String s : all.split("\n")) if (!s.trim().isEmpty()) lines.add(s);
        if (lines.isEmpty()) {
            root.addView(body("还没有测试记录。"));
        } else {
            for (String line : lines) {
                final Scoring.Result r = parseHistory(line);
                if (r == null) continue;
                long ts = Long.parseLong(line.split("\\|")[0]);
                LinearLayout c = card();
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(text(r.type, 24, PRIMARY, true));
                LinearLayout info = column();
                info.setPadding(dp(16), 0, 0, 0);
                info.addView(text(Types.get(r.type).name, 16, TEXT, true));
                info.addView(text(fmt.format(new Date(ts)), 13, TEXT_SUB, false));
                row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                row.addView(text("›", 22, TEXT_SUB, false));
                c.addView(row);
                c.setOnClickListener(v -> showResult(r, false));
                root.addView(c);
            }
            root.addView(secondaryButton("清空历史记录", v -> new AlertDialog.Builder(this)
                    .setMessage("确定清空所有历史结果吗？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("清空", (dlg, w) -> {
                        prefs.edit().remove(KEY_HISTORY).apply();
                        showHistory();
                    }).show()));
        }
        setScroll(root);
    }

    // ------------------------------------------------------------------ 关于

    private void showAbout() {
        screen = Screen.ABOUT;
        LinearLayout root = column();
        root.setPadding(dp(20), dp(16), dp(20), dp(24));
        TextView back = text("‹ 首页", 15, PRIMARY, false);
        back.setPadding(0, dp(8), 0, dp(8));
        back.setOnClickListener(v -> showHome());
        root.addView(back);
        TextView title = text("关于本测试", 22, TEXT, true);
        title.setPadding(0, dp(8), 0, dp(12));
        root.addView(title);

        root.addView(infoCard("理论来源",
                "MBTI（迈尔斯-布里格斯类型指标）由 Katharine Cook Briggs 和 Isabel Briggs Myers 母女"
                        + "在荣格（C. G. Jung）《心理类型》（1921）的基础上发展而来。"
                        + "它用四个维度描述人们在感知世界和做决定时的偏好，组合成 16 种类型。"));
        root.addView(infoCard("四个维度",
                "E 外向 / I 内向：你的精力主要来自外部的人和活动，还是内心的想法和独处。\n\n"
                        + "S 实感 / N 直觉：你更关注具体的事实与细节，还是整体的模式与可能性。\n\n"
                        + "T 思考 / F 情感：你做决定时更依据客观逻辑，还是个人与他人的价值和感受。\n\n"
                        + "J 判断 / P 感知：你更喜欢有计划、有结论的生活，还是灵活、开放的生活。"));
        root.addView(infoCard("计分方式",
                "每个维度 15 题，每题按程度计 1 或 2 分，单侧最高 30 分。"
                        + "得分较高的一侧决定该维度的字母；两侧得分相同时，按 MBTI 惯例判为 I、N、F、P。"
                        + "偏好清晰度按两侧得分差占满分的比例分为四档：轻微（0~25%）、中等（26~50%）、"
                        + "明显（51~75%）、非常明显（76~100%）。"));
        root.addView(infoCard("如何看待结果",
                "• 类型描述的是\"偏好\"，就像惯用手一样，你也会使用另一侧，只是不那么自然。\n\n"
                        + "• 类型没有好坏之分，也不代表能力高低。\n\n"
                        + "• 心理学研究指出，MBTI 类测试的重测稳定性有限，偏好轻微的维度在重测时较容易改变；"
                        + "在学术研究中，大五人格（Big Five）模型的信效度证据更充分。\n\n"
                        + "• 因此，建议把结果当作认识自己的参考和起点，而不是给自己贴上固定标签。"));
        root.addView(infoCard("声明",
                "MBTI、Myers-Briggs 为 The Myers-Briggs Company 的注册商标。"
                        + "本应用题目为依据公开理论原创编写，与该公司无关，并非官方测评，所有数据仅保存在本机。"));
        setScroll(root);
    }

    // ------------------------------------------------------------------ 返回键

    @Override
    public void onBackPressed() {
        switch (screen) {
            case QUIZ:
                if (pos > 0) {
                    pos--;
                    saveProgress();
                    showQuestion();
                } else {
                    showHome();
                }
                break;
            case HOME:
                super.onBackPressed();
                break;
            default:
                showHome();
        }
    }

    // ------------------------------------------------------------------ UI 工具

    private void setScroll(View content) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(BG);
        sv.addView(content);
        setContentView(sv);
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout card() {
        LinearLayout c = column();
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(round(Color.WHITE, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    private View infoCard(String title, String content) {
        LinearLayout c = card();
        c.addView(sectionTitle(title));
        c.addView(body(content));
        return c;
    }

    private TextView sectionTitle(String s) {
        TextView t = text(s, 16, TEXT, true);
        t.setPadding(0, 0, 0, dp(8));
        return t;
    }

    private TextView body(String s) {
        TextView t = text(s, 15, 0xFF374151, false);
        t.setLineSpacing(dp(4), 1f);
        return t;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView centered(TextView t) {
        t.setGravity(Gravity.CENTER);
        t.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    private View primaryButton(String s, View.OnClickListener l) {
        TextView b = text(s, 16, Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        b.setBackground(round(PRIMARY, 12));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(20);
        b.setLayoutParams(lp);
        return b;
    }

    private View secondaryButton(String s, View.OnClickListener l) {
        TextView b = text(s, 16, PRIMARY, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = round(Color.WHITE, 12);
        bg.setStroke(dp(1), 0xFFC7D2FE);
        b.setBackground(bg);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        b.setLayoutParams(lp);
        return b;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}
