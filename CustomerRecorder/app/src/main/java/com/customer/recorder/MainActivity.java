package com.customer.recorder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class MainActivity extends Activity {

    private static final int MENU_ACTIVITIES = 1;
    private static final int MENU_EXPORT = 2;
    private static final int MENU_SHARE = 3;
    private static final int MAX_SHOWN = 300;
    private static final long DAY = 24L * 3600 * 1000;

    private static final String[] DATE_FILTERS = {"全部", "今天", "本周", "本月", "上月", "今年"};
    private static final String[] STATUS_FILTERS = {
            "全部", "未开借记卡", "未开手机银行", "未开信用卡", "礼品未送"};
    private static final String[] OPEN_STATES = {
            MessageParser.NOT_OPENED, MessageParser.OPENED, MessageParser.UNKNOWN};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DecimalFormat money = new DecimalFormat("#,##0.##");
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
    private final SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

    private CustomerDb db;
    private SharedPreferences prefs;

    // 录入区
    private EditText input;
    private LinearLayout preview;
    private TextView previewInfo;
    private LinearLayout phoneBox;
    private EditText phoneInput;
    private FlowLayout debitChips;
    private FlowLayout mobileChips;
    private FlowLayout creditChips;
    private FlowLayout entryActivityChips;
    private AutoCompleteTextView entryGift;
    private EditText entryCost;
    private List<Customer> pending = new ArrayList<>();
    /** 上次识别的文本；文本没变时不重新识别，避免覆盖用户手动点选的开通状态 */
    private String parsedText = null;
    private final String[] entryDebit = {MessageParser.UNKNOWN};
    private final String[] entryMobile = {MessageParser.UNKNOWN};
    private final String[] entryCredit = {MessageParser.UNKNOWN};
    private final String[] entryActivity = {""};

    // 筛选区
    private EditText search;
    private FlowLayout dateChips;
    private FlowLayout statusChips;
    private FlowLayout activityFilterChips;
    private final String[] dateFilter = {DATE_FILTERS[0]};
    private final String[] statusFilter = {STATUS_FILTERS[0]};
    private final String[] activityFilter = {""};
    private TextView stats;
    private LinearLayout listContainer;

    private final Runnable parseRunnable = this::updatePreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new CustomerDb(this);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        String last = prefs.getString("last_activity", "");
        entryActivity[0] = db.findActivity(last) == null ? "" : last;
        setContentView(buildLayout());
        renderActivityChips();
        renderStateChips();
        bindChoices(dateChips, DATE_FILTERS, dateFilter, this::refresh);
        bindChoices(statusChips, STATUS_FILTERS, statusFilter, this::refresh);
        refresh();
        handleShareIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShareIntent(intent);
    }

    /** 从其他 App 分享过来的文字放进输入框，由用户确认保存。 */
    private void handleShareIntent(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) input.setText(text);
        }
    }

    // ================= 界面 =================

    private View buildLayout() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        root.addView(buildEntryCard(), Ui.cardParams(this));
        root.addView(buildFilterCard(), Ui.cardParams(this));
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer, new LinearLayout.LayoutParams(-1, -2));
        return scroll;
    }

    private View buildEntryCard() {
        LinearLayout card = Ui.card(this);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.text(this, "录入客户", 17, Ui.TEXT, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView paste = Ui.chip(this, "粘贴", false);
        paste.setOnClickListener(v -> pasteFromClipboard());
        TextView clear = Ui.chip(this, "清空", false);
        clear.setOnClickListener(v -> clearEntry());
        head.addView(paste);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.leftMargin = dp(8);
        head.addView(clear, clp);
        card.addView(head);

        input = Ui.input(this, "长按这里粘贴银行审批提示信息，可一次粘贴多条");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(3);
        input.setMaxLines(8);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacks(parseRunnable);
                handler.postDelayed(parseRunnable, 300);
            }
        });
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, -2);
        ilp.topMargin = dp(10);
        card.addView(input, ilp);

        preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setVisibility(View.GONE);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.round(this, Ui.GREEN_BG, 10));
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        previewInfo = Ui.text(this, "", 15, Ui.TEXT, false);
        previewInfo.setLineSpacing(dp(2), 1f);
        box.addView(previewInfo);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = dp(10);
        preview.addView(box, blp);

        phoneBox = new LinearLayout(this);
        phoneBox.setOrientation(LinearLayout.VERTICAL);
        phoneBox.addView(Ui.label(this, "手机号"));
        phoneInput = Ui.input(this, "客户手机号（选填）");
        phoneInput.setInputType(InputType.TYPE_CLASS_PHONE);
        phoneBox.addView(phoneInput, new LinearLayout.LayoutParams(-1, -2));
        phoneBox.addView(Ui.label(this, "借记卡"));
        debitChips = new FlowLayout(this);
        phoneBox.addView(debitChips);
        phoneBox.addView(Ui.label(this, "手机银行"));
        mobileChips = new FlowLayout(this);
        phoneBox.addView(mobileChips);
        phoneBox.addView(Ui.label(this, "信用卡"));
        creditChips = new FlowLayout(this);
        phoneBox.addView(creditChips);
        preview.addView(phoneBox);

        preview.addView(Ui.label(this, "扫码活动"));
        entryActivityChips = new FlowLayout(this);
        preview.addView(entryActivityChips);
        LinearLayout giftRow = new LinearLayout(this);
        LinearLayout giftCol = new LinearLayout(this);
        giftCol.setOrientation(LinearLayout.VERTICAL);
        giftCol.addView(Ui.label(this, "礼品"));
        entryGift = new AutoCompleteTextView(this);
        entryGift.setBackground(Ui.round(this, Ui.GRAY_BG, 10));
        entryGift.setPadding(dp(12), dp(10), dp(12), dp(10));
        entryGift.setTextSize(15);
        entryGift.setSingleLine(true);
        entryGift.setThreshold(1);
        entryGift.setHint("无礼品可不填");
        entryGift.setOnItemClickListener((p, v, pos, id) -> {
            // 选了用过的礼品，自动带出对应活动里设的成本
            String g = entryGift.getText().toString();
            for (CustomerDb.Act a : db.activities()) {
                if (a.gift.equals(g) && a.cost > 0) {
                    entryCost.setText(plain(a.cost));
                    break;
                }
            }
        });
        giftCol.addView(entryGift, new LinearLayout.LayoutParams(-1, -2));
        giftRow.addView(giftCol, new LinearLayout.LayoutParams(0, -2, 1.6f));
        LinearLayout costCol = new LinearLayout(this);
        costCol.setOrientation(LinearLayout.VERTICAL);
        costCol.addView(Ui.label(this, "礼品成本（元）"));
        entryCost = Ui.input(this, "0");
        entryCost.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        costCol.addView(entryCost, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams costLp = new LinearLayout.LayoutParams(0, -2, 1f);
        costLp.leftMargin = dp(10);
        giftRow.addView(costCol, costLp);
        preview.addView(giftRow);

        Button save = Ui.button(this, "保存记录", true);
        save.setOnClickListener(v -> saveEntry());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(14);
        preview.addView(save, slp);

        card.addView(preview);
        return card;
    }

    private View buildFilterCard() {
        LinearLayout card = Ui.card(this);
        search = Ui.input(this, "搜索 姓名 / 电话 / 产品 / 活动 / 备注");
        search.setSingleLine(true);
        search.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                refresh();
            }
        });
        card.addView(search, new LinearLayout.LayoutParams(-1, -2));
        card.addView(Ui.label(this, "审批日期"));
        dateChips = new FlowLayout(this);
        card.addView(dateChips);
        card.addView(Ui.label(this, "状态"));
        statusChips = new FlowLayout(this);
        card.addView(statusChips);
        card.addView(Ui.label(this, "扫码活动（长按活动可修改）"));
        activityFilterChips = new FlowLayout(this);
        card.addView(activityFilterChips);
        stats = Ui.text(this, "", 14, Ui.TEXT, false);
        stats.setBackground(Ui.round(this, Ui.BLUE_BG, 10));
        stats.setPadding(dp(12), dp(10), dp(12), dp(10));
        stats.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(14);
        card.addView(stats, lp);
        return card;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_ACTIVITIES, 0, "活动管理").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(0, MENU_EXPORT, 1, "导出Excel(CSV)");
        menu.add(0, MENU_SHARE, 2, "分享当前列表");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ACTIVITIES:
                showActivityManager();
                return true;
            case MENU_EXPORT:
                exportCsv();
                return true;
            case MENU_SHARE:
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, buildCsv(currentData(), ","));
                startActivity(Intent.createChooser(send, "分享客户列表"));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // ================= 选项 =================

    /** 单选标签组：点击后更新 holder[0] 并重新绘制。 */
    private void bindChoices(FlowLayout box, String[] items, String[] holder, Runnable onChange) {
        box.removeAllViews();
        for (String it : items) {
            TextView chip = Ui.chip(this, it, it.equals(holder[0]));
            chip.setOnClickListener(v -> {
                holder[0] = it;
                bindChoices(box, items, holder, onChange);
                if (onChange != null) onChange.run();
            });
            box.addView(chip);
        }
    }

    /** 活动标签组，最后带一个“＋ 新增活动”。 */
    private void bindActivityChips(FlowLayout box, String noneLabel, String[] holder,
                                   boolean manage, Runnable onChange) {
        box.removeAllViews();
        TextView none = Ui.chip(this, noneLabel, holder[0].isEmpty());
        none.setOnClickListener(v -> {
            holder[0] = "";
            bindActivityChips(box, noneLabel, holder, manage, onChange);
            if (onChange != null) onChange.run();
        });
        box.addView(none);
        for (CustomerDb.Act a : db.activities()) {
            TextView chip = Ui.chip(this, a.name, a.name.equals(holder[0]));
            chip.setOnClickListener(v -> {
                holder[0] = a.name;
                bindActivityChips(box, noneLabel, holder, manage, onChange);
                if (onChange != null) onChange.run();
            });
            if (manage) {
                chip.setOnLongClickListener(v -> {
                    String before = a.name;
                    showActivityEditor(a, saved -> {
                        if (holder[0].equals(before)) holder[0] = saved == null ? "" : saved.name;
                        renderActivityChips();
                        refresh();
                    });
                    return true;
                });
            }
            box.addView(chip);
        }
        TextView add = Ui.addChip(this, "＋ 新增活动");
        add.setOnClickListener(v -> showActivityEditor(null, saved -> {
            if (saved != null) holder[0] = saved.name;
            renderActivityChips();
            bindActivityChips(box, noneLabel, holder, manage, onChange);
            if (onChange != null) onChange.run();
        }));
        box.addView(add);
    }

    private void renderActivityChips() {
        bindActivityChips(entryActivityChips, "不参加", entryActivity, false, () -> {
            prefs.edit().putString("last_activity", entryActivity[0]).apply();
            updateEntryGiftInfo();
        });
        bindActivityChips(activityFilterChips, "全部活动", activityFilter, true, this::refresh);
        updateEntryGiftInfo();
    }

    private void renderStateChips() {
        bindChoices(debitChips, OPEN_STATES, entryDebit, null);
        bindChoices(mobileChips, OPEN_STATES, entryMobile, null);
        bindChoices(creditChips, OPEN_STATES, entryCredit, null);
    }

    /** 按所选活动预填礼品和成本，用户可再修改。 */
    private void updateEntryGiftInfo() {
        CustomerDb.Act a = db.findActivity(entryActivity[0]);
        entryGift.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, db.distinct("gift")));
        entryGift.setText(a == null ? "" : a.gift, false);
        entryCost.setText(a == null || a.cost == 0 ? "" : plain(a.cost));
    }

    // ================= 录入 =================

    private void pasteFromClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = cm == null ? null : cm.getPrimaryClip();
        CharSequence text = clip == null || clip.getItemCount() == 0
                ? null : clip.getItemAt(0).coerceToText(this);
        if (text == null || text.length() == 0) {
            toast("剪贴板是空的，请先复制审批提示信息");
            return;
        }
        input.setText(text);
        input.setSelection(input.length());
    }

    private void updatePreview() {
        String text = input.getText().toString();
        if (text.equals(parsedText)) return;
        parsedText = text;
        pending = MessageParser.parseAll(text);
        if (pending.isEmpty()) {
            preview.setVisibility(View.GONE);
            return;
        }
        preview.setVisibility(View.VISIBLE);
        StringBuilder sb = new StringBuilder();
        if (pending.size() > 1) sb.append("识别到 ").append(pending.size()).append(" 位客户\n\n");
        for (int i = 0; i < pending.size(); i++) {
            Customer c = pending.get(i);
            if (i > 0) sb.append("\n\n");
            sb.append("✔ ").append(c.name).append("  ").append(c.product)
                    .append("\n额度 ").append(money.format(c.amount)).append(" 元 · 利率 ")
                    .append(c.rate).append("\n首次审批 ").append(c.approveDate);
        }
        previewInfo.setText(sb);

        boolean single = pending.size() == 1;
        phoneBox.setVisibility(single ? View.VISIBLE : View.GONE);
        if (single) {
            Customer c = pending.get(0);
            if (!c.phone.isEmpty() && phoneInput.length() == 0) phoneInput.setText(c.phone);
            // 按识别结果预选，用户可再点选修改
            entryDebit[0] = c.debitCard;
            entryMobile[0] = c.mobileBank;
            if (!MessageParser.UNKNOWN.equals(c.creditCard)) entryCredit[0] = c.creditCard;
            renderStateChips();
        }
    }

    private void saveEntry() {
        handler.removeCallbacks(parseRunnable);
        updatePreview();
        if (pending.isEmpty()) {
            toast("没有识别到客户信息，请检查内容");
            return;
        }
        CustomerDb.Act act = db.findActivity(entryActivity[0]);
        String gift = entryGift.getText().toString().trim();
        double giftCost = gift.isEmpty() ? 0 : parseMoney(entryCost.getText().toString());
        String phone = phoneInput.getText().toString().trim();
        int updated = 0;
        StringBuilder names = new StringBuilder();
        for (Customer c : pending) {
            if (act != null) c.activity = act.name;
            c.gift = gift;
            c.giftCost = giftCost;
            if (pending.size() == 1) {
                if (!phone.isEmpty()) c.phone = phone;
                c.debitCard = entryDebit[0];
                c.mobileBank = entryMobile[0];
                c.creditCard = entryCredit[0];
            }
            if (!db.upsert(c)) updated++;
            if (names.length() > 0) names.append("、");
            names.append(c.name);
        }
        clearEntry();
        refresh();
        String msg = "已保存：" + names;
        if (updated > 0) msg += "（其中 " + updated + " 位已存在，已更新）";
        toast(msg);
    }

    private void clearEntry() {
        input.setText("");
        handler.removeCallbacks(parseRunnable);
        phoneInput.setText("");
        entryDebit[0] = MessageParser.UNKNOWN;
        entryMobile[0] = MessageParser.UNKNOWN;
        entryCredit[0] = MessageParser.UNKNOWN;
        renderStateChips();
        updateEntryGiftInfo();
        pending.clear();
        parsedText = null;
        preview.setVisibility(View.GONE);
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.clearFocus();
    }

    // ================= 列表 =================

    private long startOfToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /** 当前日期筛选对应的审批日期范围 {起（含）, 止（不含）}，null 表示不限。 */
    private String[] dateRange() {
        long today = startOfToday();
        switch (dateFilter[0]) {
            case "今天":
                return new String[]{day(today), null};
            case "本周":
                Calendar week = Calendar.getInstance();
                int sinceMonday = (week.get(Calendar.DAY_OF_WEEK) + 5) % 7;
                return new String[]{day(today - sinceMonday * DAY), null};
            case "本月":
                return new String[]{thisMonth() + "-01", null};
            case "上月":
                return new String[]{lastMonth() + "-01", thisMonth() + "-01"};
            case "今年":
                return new String[]{thisMonth().substring(0, 4) + "-01-01", null};
            default:
                return new String[]{null, null};
        }
    }

    /** 本月，yyyy-MM */
    private String thisMonth() {
        return day(startOfToday()).substring(0, 7);
    }

    /** 上月，yyyy-MM */
    private String lastMonth() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.add(Calendar.MONTH, -1);
        return day(cal.getTimeInMillis()).substring(0, 7);
    }

    private static String monthOf(Customer c) {
        return c.approveDate.length() >= 7 ? c.approveDate.substring(0, 7) : "";
    }

    private String day(long millis) {
        return dayFormat.format(new Date(millis));
    }

    private List<Customer> currentData() {
        String[] range = dateRange();
        int status = indexOf(STATUS_FILTERS, statusFilter[0]);
        return db.query(search.getText().toString(), status, range[0], range[1], activityFilter[0]);
    }

    private void refresh() {
        if (listContainer == null) return;
        List<Customer> data = currentData();
        double total = 0;
        double cost = 0;
        int giftPending = 0;
        int noCard = 0;
        int noMobile = 0;
        int noCredit = 0;
        Map<String, double[]> months = new LinkedHashMap<>();
        for (Customer c : data) {
            total += c.amount;
            cost += c.giftCost;
            if (!c.gift.isEmpty() && !c.giftGiven) giftPending++;
            if (MessageParser.NOT_OPENED.equals(c.debitCard)) noCard++;
            if (MessageParser.NOT_OPENED.equals(c.mobileBank)) noMobile++;
            if (MessageParser.NOT_OPENED.equals(c.creditCard)) noCredit++;
            String month = monthOf(c);
            double[] d = months.get(month);
            if (d == null) {
                d = new double[2];
                months.put(month, d);
            }
            d[0]++;
            d[1] += c.amount;
        }
        stats.setText("共 " + data.size() + " 位客户 · 总额度 " + wan(total)
                + "\n礼品成本 " + money.format(cost) + " 元 · 礼品未送 " + giftPending + " 人"
                + "\n未开借记卡 " + noCard + " · 未开手机银行 " + noMobile
                + " · 未开信用卡 " + noCredit);

        listContainer.removeAllViews();
        if (data.isEmpty()) {
            TextView empty = Ui.text(this, "暂无记录\n复制银行审批提示，粘贴到上面的输入框即可",
                    14, Ui.TEXT2, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(30), 0, dp(30));
            listContainer.addView(empty);
            return;
        }
        String prevMonth = null;
        for (int i = 0; i < data.size() && i < MAX_SHOWN; i++) {
            Customer c = data.get(i);
            String month = monthOf(c);
            if (!month.equals(prevMonth)) {
                prevMonth = month;
                double[] d = months.get(month);
                listContainer.addView(monthHeader(month, (int) d[0], d[1]));
            }
            listContainer.addView(itemView(c), Ui.cardParams(this));
        }
        if (data.size() > MAX_SHOWN) {
            TextView more = Ui.text(this, "仅显示前 " + MAX_SHOWN + " 条，请用日期或搜索筛选",
                    13, Ui.TEXT2, false);
            more.setGravity(Gravity.CENTER);
            more.setPadding(0, dp(8), 0, dp(16));
            listContainer.addView(more);
        }
    }

    private View monthHeader(String month, int count, double amount) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(10), dp(4), dp(8));
        View bar = new View(this);
        bar.setBackground(Ui.round(this, Ui.PRIMARY, 2));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(4), dp(16));
        blp.rightMargin = dp(8);
        row.addView(bar, blp);
        row.addView(Ui.text(this, monthLabel(month), 16, Ui.TEXT, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(Ui.text(this, count + " 人 · " + wan(amount), 13, Ui.TEXT2, false));
        return row;
    }

    private String monthLabel(String month) {
        if (month.isEmpty()) return "审批日期未填";
        String label = month.substring(0, 4) + "年" + Integer.parseInt(month.substring(5, 7)) + "月";
        if (month.equals(thisMonth())) return label + "  本月";
        if (month.equals(lastMonth())) return label + "  上月";
        return label;
    }

    /** 2026-09-24 显示为 9月24日；格式不对时原样显示 */
    private static String shortDate(String d) {
        if (d.length() != 10) return d.isEmpty() ? "未填" : d;
        try {
            return Integer.parseInt(d.substring(5, 7)) + "月" + Integer.parseInt(d.substring(8)) + "日";
        } catch (NumberFormatException e) {
            return d;
        }
    }

    private View itemView(Customer c) {
        LinearLayout card = Ui.card(this);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.text(this, c.name, 18, Ui.TEXT, true));
        if (!c.product.isEmpty()) {
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
            plp.leftMargin = dp(8);
            top.addView(Ui.tag(this, c.product, Ui.PRIMARY, Ui.RED_BG), plp);
        }
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        top.addView(Ui.text(this, wan(c.amount), 18, Ui.PRIMARY, true));
        card.addView(top);

        TextView info = Ui.text(this, "利率 " + c.rate + "   审批 " + shortDate(c.approveDate),
                13, Ui.TEXT2, false);
        info.setPadding(0, dp(4), 0, 0);
        card.addView(info);

        TextView phone;
        if (c.phone.isEmpty()) {
            phone = Ui.text(this, "＋ 添加电话", 14, Ui.BLUE, false);
            phone.setOnClickListener(v -> quickPhone(c));
        } else {
            phone = Ui.text(this, "☎ " + c.phone + "  拨打", 14, Ui.BLUE, false);
            phone.setOnClickListener(v -> startActivity(
                    new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + c.phone))));
            phone.setOnLongClickListener(v -> {
                quickPhone(c);
                return true;
            });
        }
        phone.setPadding(0, dp(6), dp(12), dp(2));
        card.addView(phone, new LinearLayout.LayoutParams(-2, -2));

        FlowLayout tags = new FlowLayout(this);
        tags.setPadding(0, dp(8), 0, 0);
        tags.addView(stateTag("借记卡", c.debitCard));
        tags.addView(stateTag("手机银行", c.mobileBank));
        tags.addView(stateTag("信用卡", c.creditCard));
        if (!c.activity.isEmpty()) {
            tags.addView(Ui.tag(this, "活动：" + c.activity, Ui.BLUE, Ui.BLUE_BG));
        }
        if (!c.gift.isEmpty()) {
            TextView gift = Ui.tag(this, "礼品：" + c.gift + " ¥" + money.format(c.giftCost)
                            + (c.giftGiven ? " ✓已送" : " · 点击标记已送"),
                    c.giftGiven ? Ui.GREEN : Ui.ORANGE, c.giftGiven ? Ui.GREEN_BG : Ui.ORANGE_BG);
            gift.setOnClickListener(v -> {
                c.giftGiven = !c.giftGiven;
                db.update(c);
                refresh();
                toast(c.name + (c.giftGiven ? "：礼品已送" : "：礼品改为未送"));
            });
            tags.addView(gift);
        }
        card.addView(tags);

        if (!c.note.isEmpty()) {
            TextView note = Ui.text(this, "备注：" + c.note, 13, Ui.TEXT2, false);
            note.setPadding(0, dp(6), 0, 0);
            card.addView(note);
        }

        card.setOnClickListener(v -> showEdit(c));
        card.setOnLongClickListener(v -> {
            confirmDelete(c);
            return true;
        });
        return card;
    }

    private TextView stateTag(String label, String state) {
        if (MessageParser.OPENED.equals(state)) {
            return Ui.tag(this, "✓ " + label, Ui.GREEN, Ui.GREEN_BG);
        } else if (MessageParser.NOT_OPENED.equals(state)) {
            return Ui.tag(this, "✗ " + label + "未开", Ui.RED, Ui.RED_BG);
        }
        return Ui.tag(this, "? " + label, Ui.TEXT2, Ui.GRAY_BG);
    }

    private void quickPhone(Customer c) {
        EditText et = Ui.input(this, "手机号");
        et.setInputType(InputType.TYPE_CLASS_PHONE);
        et.setText(c.phone);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(12), dp(20), 0);
        box.addView(et, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle(c.name + " 的手机号")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    c.phone = et.getText().toString().trim();
                    db.update(c);
                    refresh();
                })
                .setNegativeButton("取消", null)
                .show();
        et.requestFocus();
    }

    // ================= 编辑客户 =================

    private void showEdit(Customer c) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(4), dp(20), dp(8));
        EditText name = field(form, "姓名", c.name, InputType.TYPE_CLASS_TEXT);
        EditText phone = field(form, "手机号", c.phone, InputType.TYPE_CLASS_PHONE);
        EditText product = field(form, "产品", c.product, InputType.TYPE_CLASS_TEXT);
        EditText amount = field(form, "额度（元）", c.amount == 0 ? "" : plain(c.amount),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText rate = field(form, "利率", c.rate, InputType.TYPE_CLASS_TEXT);
        EditText date = field(form, "首次审批通过日期", c.approveDate, InputType.TYPE_CLASS_TEXT);

        String[] debit = {c.debitCard};
        String[] mobile = {c.mobileBank};
        String[] credit = {c.creditCard};
        form.addView(Ui.label(this, "借记卡"));
        FlowLayout debitBox = new FlowLayout(this);
        form.addView(debitBox);
        bindChoices(debitBox, OPEN_STATES, debit, null);
        form.addView(Ui.label(this, "手机银行"));
        FlowLayout mobileBox = new FlowLayout(this);
        form.addView(mobileBox);
        bindChoices(mobileBox, OPEN_STATES, mobile, null);
        form.addView(Ui.label(this, "信用卡"));
        FlowLayout creditBox = new FlowLayout(this);
        form.addView(creditBox);
        bindChoices(creditBox, OPEN_STATES, credit, null);

        String[] activity = {c.activity};
        form.addView(Ui.label(this, "扫码活动"));
        FlowLayout actBox = new FlowLayout(this);
        form.addView(actBox);
        EditText gift = field(form, "礼品", c.gift, InputType.TYPE_CLASS_TEXT);
        EditText cost = field(form, "礼品成本（元）", c.giftCost == 0 ? "" : plain(c.giftCost),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        bindActivityChips(actBox, "不参加", activity, false, () -> {
            CustomerDb.Act a = db.findActivity(activity[0]);
            gift.setText(a == null ? "" : a.gift);
            cost.setText(a == null || a.cost == 0 ? "" : plain(a.cost));
        });
        CheckBox given = new CheckBox(this);
        given.setText("礼品已送");
        given.setChecked(c.giftGiven);
        form.addView(given);

        EditText note = field(form, "备注", c.note,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        TextView raw = Ui.text(this, "记录时间：" + timeFormat.format(new Date(c.createdAt))
                + "\n原始信息：" + c.raw, 12, Ui.TEXT2, false);
        raw.setPadding(0, dp(12), 0, 0);
        raw.setTextIsSelectable(true);
        form.addView(raw);

        ScrollView sv = new ScrollView(this);
        sv.addView(form);
        new AlertDialog.Builder(this)
                .setTitle("客户详情")
                .setView(sv)
                .setPositiveButton("保存", (d, w) -> {
                    c.name = name.getText().toString().trim();
                    c.phone = phone.getText().toString().trim();
                    c.product = product.getText().toString().trim();
                    c.amount = parseMoney(amount.getText().toString());
                    c.rate = rate.getText().toString().trim();
                    c.approveDate = MessageParser.normalizeDate(date.getText().toString());
                    c.debitCard = debit[0];
                    c.mobileBank = mobile[0];
                    c.creditCard = credit[0];
                    c.activity = activity[0];
                    c.gift = gift.getText().toString().trim();
                    c.giftCost = parseMoney(cost.getText().toString());
                    c.giftGiven = given.isChecked();
                    c.note = note.getText().toString().trim();
                    db.update(c);
                    refresh();
                    toast("已保存");
                })
                .setNeutralButton("删除", (d, w) -> confirmDelete(c))
                .setNegativeButton("取消", null)
                .show();
    }

    private EditText field(LinearLayout form, String label, String value, int type) {
        form.addView(Ui.label(this, label));
        EditText et = Ui.input(this, label);
        et.setInputType(type);
        et.setText(value);
        form.addView(et, new LinearLayout.LayoutParams(-1, -2));
        return et;
    }

    private void confirmDelete(Customer c) {
        new AlertDialog.Builder(this)
                .setTitle("删除记录")
                .setMessage("确定删除客户“" + c.name + "”的记录吗？")
                .setPositiveButton("删除", (d, w) -> {
                    db.delete(c.id);
                    refresh();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ================= 活动 =================

    private void showActivityManager() {
        List<CustomerDb.Act> acts = db.activities();
        String[] rows = new String[acts.size()];
        for (int i = 0; i < acts.size(); i++) {
            CustomerDb.Act a = acts.get(i);
            double[] st = db.activityStats(a.name);
            rows[i] = a.name + "\n    礼品：" + (a.gift.isEmpty() ? "无" : a.gift + " ¥"
                    + money.format(a.cost)) + "\n    客户 " + (int) st[0] + " 人 · 礼品成本 "
                    + money.format(st[1]) + " 元 · 未送 " + (int) st[2] + " 人";
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("活动管理（点击修改）")
                .setPositiveButton("＋ 新增活动", (d, w) -> showActivityEditor(null, saved -> {
                    renderActivityChips();
                    showActivityManager();
                }))
                .setNegativeButton("关闭", null);
        if (acts.isEmpty()) {
            b.setMessage("还没有活动，点“新增活动”添加");
        } else {
            b.setItems(rows, (d, which) -> {
                String before = acts.get(which).name;
                showActivityEditor(acts.get(which), saved -> {
                    if (activityFilter[0].equals(before)) {
                        activityFilter[0] = saved == null ? "" : saved.name;
                    }
                    renderActivityChips();
                    refresh();
                    showActivityManager();
                });
            });
        }
        b.show();
    }

    /** 新增/修改活动。done 回调参数为保存后的活动，删除时为 null。 */
    private void showActivityEditor(CustomerDb.Act existing, Consumer<CustomerDb.Act> done) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(4), dp(20), dp(8));
        EditText name = field(form, "活动名称", existing == null ? "" : existing.name,
                InputType.TYPE_CLASS_TEXT);
        form.addView(Ui.label(this, "礼品（选填）"));
        AutoCompleteTextView gift = new AutoCompleteTextView(this);
        gift.setBackground(Ui.round(this, Ui.GRAY_BG, 10));
        gift.setPadding(dp(12), dp(10), dp(12), dp(10));
        gift.setSingleLine(true);
        gift.setThreshold(1);
        gift.setHint("如：大米、食用油");
        gift.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, db.distinct("gift")));
        gift.setOnClickListener(v -> gift.showDropDown());
        if (existing != null) gift.setText(existing.gift);
        form.addView(gift, new LinearLayout.LayoutParams(-1, -2));
        EditText cost = field(form, "每份礼品成本（元）",
                existing == null || existing.cost == 0 ? "" : plain(existing.cost),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "新增扫码活动" : "修改活动")
                .setView(form)
                .setPositiveButton("保存", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) {
                        toast("请填写活动名称");
                        return;
                    }
                    CustomerDb.Act a = existing == null ? new CustomerDb.Act() : existing;
                    String oldName = a.name;
                    a.name = n;
                    a.gift = gift.getText().toString().trim();
                    a.cost = parseMoney(cost.getText().toString());
                    try {
                        db.saveActivity(a, oldName);
                    } catch (android.database.SQLException e) {
                        a.name = oldName;
                        toast("已有同名活动“" + n + "”");
                        return;
                    }
                    if (existing != null && oldName.equals(entryActivity[0])) {
                        entryActivity[0] = n;
                        prefs.edit().putString("last_activity", n).apply();
                    }
                    toast("活动已保存：" + n);
                    done.accept(a);
                })
                .setNegativeButton("取消", null);
        if (existing != null) {
            b.setNeutralButton("删除", (d, w) -> new AlertDialog.Builder(this)
                    .setTitle("删除活动")
                    .setMessage("删除活动“" + existing.name + "”？已记录的客户信息不受影响。")
                    .setPositiveButton("删除", (d2, w2) -> {
                        db.deleteActivity(existing.id);
                        if (existing.name.equals(entryActivity[0])) entryActivity[0] = "";
                        done.accept(null);
                    })
                    .setNegativeButton("取消", null)
                    .show());
        }
        b.show();
        if (existing == null) name.requestFocus();
    }

    // ================= 导出 =================

    private String buildCsv(List<Customer> list, String sep) {
        StringBuilder sb = new StringBuilder();
        sb.append(TextUtils.join(sep, new String[]{"记录时间", "姓名", "手机号", "产品", "审批状态",
                "额度(元)", "利率", "首次审批通过日期", "借记卡", "手机银行", "信用卡", "扫码活动",
                "礼品", "礼品成本(元)", "礼品已送", "备注"})).append("\n");
        for (Customer c : list) {
            sb.append(TextUtils.join(sep, new String[]{
                    csv(timeFormat.format(new Date(c.createdAt))), csv(c.name), csv(c.phone),
                    csv(c.product), csv(c.status), csv(plain(c.amount)), csv(c.rate),
                    csv(c.approveDate), csv(c.debitCard), csv(c.mobileBank), csv(c.creditCard),
                    csv(c.activity), csv(c.gift), csv(plain(c.giftCost)),
                    c.gift.isEmpty() ? "" : (c.giftGiven ? "是" : "否"), csv(c.note)}))
                    .append("\n");
        }
        return sb.toString();
    }

    private static String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private void exportCsv() {
        List<Customer> data = currentData();
        if (data.isEmpty()) {
            toast("没有可导出的记录");
            return;
        }
        String fileName = "客户记录_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)
                .format(new Date()) + ".csv";
        // 带 BOM，Excel 打开中文不乱码
        byte[] bytes = ("﻿" + buildCsv(data, ",")).getBytes(StandardCharsets.UTF_8);
        try {
            String where;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                v.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new IllegalStateException("无法创建文件");
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os == null) throw new IllegalStateException("无法写入文件");
                    os.write(bytes);
                }
                where = "手机“下载(Download)”目录";
            } else {
                File f = new File(getExternalFilesDir(null), fileName);
                try (FileOutputStream os = new FileOutputStream(f)) {
                    os.write(bytes);
                }
                where = f.getAbsolutePath();
            }
            new AlertDialog.Builder(this)
                    .setTitle("导出成功")
                    .setMessage("已导出当前筛选的 " + data.size() + " 条记录到：\n" + where
                            + "\n\n文件名：" + fileName)
                    .setPositiveButton("确定", null)
                    .show();
        } catch (Exception e) {
            toast("导出失败：" + e.getMessage());
        }
    }

    // ================= 工具 =================

    private String wan(double v) {
        if (v >= 10000) return money.format(v / 10000) + " 万";
        return money.format(v) + " 元";
    }

    private static double parseMoney(String s) {
        try {
            return Double.parseDouble(s.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String plain(double v) {
        return new DecimalFormat("0.##").format(v);
    }

    private static int indexOf(String[] items, String s) {
        for (int i = 0; i < items.length; i++) {
            if (items[i].equals(s)) return i;
        }
        return 0;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
