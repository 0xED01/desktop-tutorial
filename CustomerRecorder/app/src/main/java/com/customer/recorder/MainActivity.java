package com.customer.recorder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int MENU_EXPORT = 1;
    private static final int MENU_SHARE = 2;
    private static final int MENU_AUTO_CLIP = 3;
    private static final String[] FILTERS = {"全部客户", "未开通借记卡", "未开通手机银行",
            "借记卡或手机银行未开通", "礼品未送"};
    private static final String[] OPEN_STATES = {
            MessageParser.NOT_OPENED, MessageParser.OPENED, MessageParser.UNKNOWN};
    private static final int RED = Color.parseColor("#C62828");
    private static final int GREEN = Color.parseColor("#2E7D32");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DecimalFormat money = new DecimalFormat("#,##0.##");
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);

    private CustomerDb db;
    private SharedPreferences prefs;
    private TextView activityBar;
    private EditText input;
    private EditText search;
    private Spinner filter;
    private TextView stats;
    private CustomerAdapter adapter;
    private List<Customer> data = new ArrayList<>();

    private final Runnable autoSave = () -> saveFromText(input.getText().toString(), true);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new CustomerDb(this);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        setContentView(buildLayout());
        refresh();
        handleShareIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShareIntent(intent);
    }

    /** 回到 App 时自动读取剪贴板，有新的审批信息就直接保存。 */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || !prefs.getBoolean("auto_clip", true)) return;
        String text = clipboardText();
        if (text == null || text.equals(prefs.getString("last_clip", ""))) return;
        if (MessageParser.parseAll(text).isEmpty()) return;
        saveFromText(text, false);
    }

    private String clipboardText() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = cm == null ? null : cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return null;
            CharSequence text = clip.getItemAt(0).coerceToText(this);
            return text == null || text.length() == 0 ? null : text.toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void handleShareIntent(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) saveFromText(text, false);
        }
    }

    // ---------------- 界面 ----------------

    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        root.setPadding(pad, pad, pad, 0);

        activityBar = new TextView(this);
        activityBar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        activityBar.setTextColor(Color.parseColor("#0D47A1"));
        activityBar.setBackgroundColor(Color.parseColor("#E3F2FD"));
        activityBar.setPadding(dp(10), dp(8), dp(10), dp(8));
        activityBar.setOnClickListener(v -> showActivitySettings());
        root.addView(activityBar, new LinearLayout.LayoutParams(-1, -2));
        updateActivityBar();

        input = new EditText(this);
        input.setHint("在这里粘贴审批提示信息，会自动识别并保存（可一次粘贴多条）");
        input.setMinLines(3);
        input.setMaxLines(6);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacks(autoSave);
                if (s.length() > 0) handler.postDelayed(autoSave, 700);
            }
        });
        root.addView(input, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button paste = new Button(this);
        paste.setText("一键粘贴保存");
        paste.setOnClickListener(v -> pasteFromClipboard());
        Button save = new Button(this);
        save.setText("识别保存");
        save.setOnClickListener(v -> saveFromText(input.getText().toString(), false));
        Button clear = new Button(this);
        clear.setText("清空");
        clear.setOnClickListener(v -> input.setText(""));
        buttons.addView(paste, new LinearLayout.LayoutParams(0, -2, 1.4f));
        buttons.addView(save, new LinearLayout.LayoutParams(0, -2, 1f));
        buttons.addView(clear, new LinearLayout.LayoutParams(0, -2, 0.8f));
        root.addView(buttons, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        search = new EditText(this);
        search.setHint("搜索姓名/产品/备注");
        search.setSingleLine(true);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        search.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                refresh();
            }
        });
        filter = new Spinner(this);
        ArrayAdapter<String> fa = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, FILTERS);
        fa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filter.setAdapter(fa);
        filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });
        searchRow.addView(search, new LinearLayout.LayoutParams(0, -2, 1f));
        searchRow.addView(filter, new LinearLayout.LayoutParams(-2, -2));
        root.addView(searchRow, new LinearLayout.LayoutParams(-1, -2));

        stats = new TextView(this);
        stats.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        stats.setPadding(0, dp(4), 0, dp(4));
        root.addView(stats);

        ListView list = new ListView(this);
        adapter = new CustomerAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> showDetail(data.get(pos)));
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            confirmDelete(data.get(pos));
            return true;
        });
        TextView empty = new TextView(this);
        empty.setText("暂无记录\n复制银行审批提示后，打开本App即自动记录\n（或点“一键粘贴保存”）");
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, dp(40), 0, 0);
        root.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        list.setEmptyView(empty);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        return root;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_EXPORT, 0, "导出Excel(CSV)")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(0, MENU_SHARE, 1, "分享当前列表");
        menu.add(0, MENU_AUTO_CLIP, 2, "打开App自动读取剪贴板")
                .setCheckable(true).setChecked(prefs.getBoolean("auto_clip", true));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_EXPORT) {
            exportCsv();
            return true;
        } else if (item.getItemId() == MENU_SHARE) {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, buildCsv(data, ","));
            startActivity(Intent.createChooser(send, "分享客户列表"));
            return true;
        } else if (item.getItemId() == MENU_AUTO_CLIP) {
            boolean on = !item.isChecked();
            item.setChecked(on);
            prefs.edit().putBoolean("auto_clip", on).apply();
            toast(on ? "已开启：打开App自动记录剪贴板里的审批信息" : "已关闭自动读取剪贴板");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---------------- 业务 ----------------

    private void pasteFromClipboard() {
        String text = clipboardText();
        if (text == null) {
            toast("剪贴板是空的，请先复制审批提示信息");
            return;
        }
        handler.removeCallbacks(autoSave);
        saveFromText(text, false);
    }

    // ---------------- 当前活动（新记录自动带上） ----------------

    private String curActivity() {
        return prefs.getString("cur_activity", "");
    }

    private String curGift() {
        return prefs.getString("cur_gift", "");
    }

    private double curCost() {
        return prefs.getFloat("cur_cost", 0f);
    }

    private void updateActivityBar() {
        if (curActivity().isEmpty() && curGift().isEmpty()) {
            activityBar.setText("当前活动：未设置（点这里设置活动/礼品/成本，新记录自动带上）");
        } else {
            activityBar.setText("当前活动：" + or(curActivity(), "无")
                    + "｜礼品：" + or(curGift(), "无")
                    + "｜成本：" + money.format(curCost()) + "元   ✎修改");
        }
    }

    private static String or(String s, String def) {
        return s == null || s.isEmpty() ? def : s;
    }

    private void showActivitySettings() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);
        AutoCompleteTextView activity = suggestField(form, "扫码活动名称", curActivity(), "activity");
        AutoCompleteTextView gift = suggestField(form, "礼品", curGift(), "gift");
        EditText cost = field(form, "礼品成本（元）", curCost() == 0 ? "" : plain(curCost()),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        bindGiftCost(gift, cost);
        TextView tip = new TextView(this);
        tip.setText("设置后，之后粘贴记录的客户都会自动带上这个活动和礼品，不用每次填写。");
        tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        form.addView(tip);

        new AlertDialog.Builder(this)
                .setTitle("设置当前活动")
                .setView(form)
                .setPositiveButton("保存", (d, w) -> {
                    String g = gift.getText().toString().trim();
                    double c = parseMoney(cost.getText().toString());
                    prefs.edit()
                            .putString("cur_activity", activity.getText().toString().trim())
                            .putString("cur_gift", g)
                            .putFloat("cur_cost", (float) c)
                            .apply();
                    rememberGiftCost(g, c);
                    updateActivityBar();
                })
                .setNeutralButton("清除", (d, w) -> {
                    prefs.edit().remove("cur_activity").remove("cur_gift").remove("cur_cost").apply();
                    updateActivityBar();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 选择/输入已用过的礼品时，自动填入上次的成本。 */
    private void bindGiftCost(AutoCompleteTextView gift, EditText cost) {
        gift.setOnItemClickListener((p, v, pos, id) -> fillCost(gift, cost));
        gift.setOnFocusChangeListener((v, has) -> {
            if (!has) fillCost(gift, cost);
        });
    }

    private void fillCost(AutoCompleteTextView gift, EditText cost) {
        String key = "giftcost_" + gift.getText().toString().trim();
        if (prefs.contains(key)) cost.setText(plain(prefs.getFloat(key, 0f)));
    }

    private void rememberGiftCost(String gift, double cost) {
        if (!gift.isEmpty()) prefs.edit().putFloat("giftcost_" + gift, (float) cost).apply();
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

    /**
     * @param auto 由输入框自动触发时为 true：识别不到内容不提示，等待用户继续输入
     */
    private void saveFromText(String text, boolean auto) {
        List<Customer> parsed = MessageParser.parseAll(text);
        if (parsed.isEmpty()) {
            if (!auto) {
                if (input.getText().length() == 0) input.setText(text);
                toast("没有识别到客户信息，请检查内容格式");
            }
            return;
        }
        prefs.edit().putString("last_clip", text).apply();
        int added = 0;
        int updated = 0;
        StringBuilder names = new StringBuilder();
        for (Customer c : parsed) {
            // 新记录自动带上当前活动；已有记录的活动信息保持不变
            c.activity = curActivity();
            c.gift = curGift();
            c.giftCost = curGift().isEmpty() ? 0 : curCost();
            if (db.upsert(c)) added++;
            else updated++;
            if (names.length() > 0) names.append("、");
            names.append(c.name);
        }
        input.setText("");
        handler.removeCallbacks(autoSave);
        search.setText("");
        refresh();
        String msg = "已记录：" + names;
        if (updated > 0) msg += "\n（新增" + added + "条，更新已有" + updated + "条）";
        toast(msg);
    }

    private void refresh() {
        if (adapter == null) return;
        data = db.query(search.getText().toString(), filter.getSelectedItemPosition());
        double total = 0;
        int noCard = 0;
        int noMobile = 0;
        double cost = 0;
        int giftPending = 0;
        for (Customer c : data) {
            total += c.amount;
            cost += c.giftCost;
            if (!c.gift.isEmpty() && !c.giftGiven) giftPending++;
            if (MessageParser.NOT_OPENED.equals(c.debitCard)) noCard++;
            if (MessageParser.NOT_OPENED.equals(c.mobileBank)) noMobile++;
        }
        stats.setText("共 " + data.size() + " 位客户，总额度 " + money.format(total)
                + " 元｜礼品成本 " + money.format(cost) + " 元｜礼品未送 " + giftPending
                + "\n未开借记卡 " + noCard + "｜未开手机银行 " + noMobile);
        adapter.notifyDataSetChanged();
    }

    private void showDetail(Customer c) {
        String msg = "姓名：" + c.name
                + "\n产品：" + c.product
                + "\n审批状态：" + c.status
                + "\n额度：" + money.format(c.amount) + " 元"
                + "\n利率：" + c.rate
                + "\n首次审批通过：" + c.approveDate
                + "\n借记卡：" + c.debitCard
                + "\n手机银行：" + c.mobileBank
                + "\n\n扫码活动：" + c.activity
                + "\n礼品：" + c.gift + (c.gift.isEmpty() ? "" : (c.giftGiven ? "（已送）" : "（未送）"))
                + "\n礼品成本：" + money.format(c.giftCost) + " 元"
                + "\n备注：" + c.note
                + "\n记录时间：" + timeFormat.format(new Date(c.createdAt))
                + "\n\n原始信息：\n" + c.raw;
        new AlertDialog.Builder(this)
                .setTitle(c.name)
                .setMessage(msg)
                .setPositiveButton("编辑", (d, w) -> showEdit(c))
                .setNeutralButton("删除", (d, w) -> confirmDelete(c))
                .setNegativeButton(c.giftGiven ? "改为礼品未送" : "礼品已送", (d, w) -> {
                    c.giftGiven = !c.giftGiven;
                    db.update(c);
                    refresh();
                    toast(c.name + (c.giftGiven ? "：礼品已送" : "：礼品未送"));
                })
                .show();
    }

    private void showEdit(Customer c) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);
        EditText name = field(form, "姓名", c.name, InputType.TYPE_CLASS_TEXT);
        EditText product = field(form, "产品", c.product, InputType.TYPE_CLASS_TEXT);
        EditText amount = field(form, "额度（元）", c.amount == 0 ? "" : money.format(c.amount)
                .replace(",", ""), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText rate = field(form, "利率", c.rate, InputType.TYPE_CLASS_TEXT);
        EditText date = field(form, "首次审批通过日期", c.approveDate, InputType.TYPE_CLASS_TEXT);
        Spinner card = stateSpinner(form, "借记卡", c.debitCard);
        Spinner mobile = stateSpinner(form, "手机银行", c.mobileBank);
        AutoCompleteTextView activity = suggestField(form, "扫码活动名称", c.activity, "activity");
        AutoCompleteTextView gift = suggestField(form, "礼品", c.gift, "gift");
        EditText cost = field(form, "礼品成本（元）", c.giftCost == 0 ? "" : plain(c.giftCost),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        bindGiftCost(gift, cost);
        CheckBox given = new CheckBox(this);
        given.setText("礼品已送");
        given.setChecked(c.giftGiven);
        form.addView(given);
        EditText note = field(form, "备注（如联系情况）", c.note, InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);

        new AlertDialog.Builder(this)
                .setTitle("编辑客户信息")
                .setView(scroll)
                .setPositiveButton("保存", (d, w) -> {
                    c.name = name.getText().toString().trim();
                    c.product = product.getText().toString().trim();
                    c.amount = parseMoney(amount.getText().toString());
                    c.rate = rate.getText().toString().trim();
                    c.approveDate = date.getText().toString().trim();
                    c.debitCard = OPEN_STATES[card.getSelectedItemPosition()];
                    c.mobileBank = OPEN_STATES[mobile.getSelectedItemPosition()];
                    c.activity = activity.getText().toString().trim();
                    c.gift = gift.getText().toString().trim();
                    c.giftCost = parseMoney(cost.getText().toString());
                    c.giftGiven = given.isChecked();
                    c.note = note.getText().toString().trim();
                    rememberGiftCost(c.gift, c.giftCost);
                    db.update(c);
                    refresh();
                    toast("已保存");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private EditText field(LinearLayout form, String label, String value, int type) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        form.addView(tv);
        EditText et = new EditText(this);
        et.setInputType(type);
        et.setText(value);
        form.addView(et, new LinearLayout.LayoutParams(-1, -2));
        return et;
    }

    /** 带历史输入提示的输入框，点一下就能选以前填过的内容。 */
    private AutoCompleteTextView suggestField(LinearLayout form, String label, String value,
                                              String column) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        form.addView(tv);
        AutoCompleteTextView et = new AutoCompleteTextView(this);
        et.setSingleLine(true);
        et.setThreshold(0);
        et.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, db.distinct(column)));
        et.setText(value);
        et.setOnClickListener(v -> et.showDropDown());
        et.setOnFocusChangeListener((v, has) -> {
            if (has) et.post(et::showDropDown);
        });
        form.addView(et, new LinearLayout.LayoutParams(-1, -2));
        return et;
    }

    private Spinner stateSpinner(LinearLayout form, String label, String value) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        form.addView(tv);
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, OPEN_STATES);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(a);
        for (int i = 0; i < OPEN_STATES.length; i++) {
            if (OPEN_STATES[i].equals(value)) sp.setSelection(i);
        }
        form.addView(sp, new LinearLayout.LayoutParams(-1, -2));
        return sp;
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

    private String buildCsv(List<Customer> list, String sep) {
        StringBuilder sb = new StringBuilder();
        sb.append(TextUtils.join(sep, new String[]{"姓名", "产品", "审批状态", "额度(元)", "利率",
                "首次审批通过日期", "借记卡", "手机银行", "扫码活动", "礼品", "礼品成本(元)", "礼品已送",
                "备注", "记录时间"})).append("\n");
        for (Customer c : list) {
            sb.append(TextUtils.join(sep, new String[]{csv(c.name), csv(c.product), csv(c.status),
                    csv(plain(c.amount)), csv(c.rate),
                    csv(c.approveDate), csv(c.debitCard), csv(c.mobileBank), csv(c.activity), csv(c.gift),
                    csv(plain(c.giftCost)), c.giftGiven ? "是" : "否", csv(c.note),
                    csv(timeFormat.format(new Date(c.createdAt)))})).append("\n");
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
                File dir = getExternalFilesDir(null);
                File f = new File(dir, fileName);
                try (FileOutputStream os = new FileOutputStream(f)) {
                    os.write(bytes);
                }
                where = f.getAbsolutePath();
            }
            new AlertDialog.Builder(this)
                    .setTitle("导出成功")
                    .setMessage("已导出 " + data.size() + " 条记录到：\n" + where + "\n\n文件名：" + fileName)
                    .setPositiveButton("确定", null)
                    .show();
        } catch (Exception e) {
            toast("导出失败：" + e.getMessage());
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    // ---------------- 列表 ----------------

    private class CustomerAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public Object getItem(int position) {
            return data.get(position);
        }

        @Override
        public long getItemId(int position) {
            return data.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView title;
            TextView line2;
            TextView line3;
            TextView line4;
            if (convertView == null) {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(4), dp(10), dp(4), dp(10));
                title = new TextView(MainActivity.this);
                title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
                title.setTypeface(Typeface.DEFAULT_BOLD);
                title.setTextColor(Color.BLACK);
                line2 = new TextView(MainActivity.this);
                line2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                line3 = new TextView(MainActivity.this);
                line3.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                row.addView(title);
                row.addView(line2);
                line4 = new TextView(MainActivity.this);
                line4.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                line4.setTextColor(Color.parseColor("#0D47A1"));
                row.addView(line3);
                row.addView(line4);
                row.setTag(new TextView[]{title, line2, line3, line4});
            } else {
                row = (LinearLayout) convertView;
                TextView[] tags = (TextView[]) row.getTag();
                title = tags[0];
                line2 = tags[1];
                line3 = tags[2];
                line4 = tags[3];
            }
            Customer c = data.get(position);
            title.setText(c.name + "  " + c.product);
            line2.setText("额度 " + money.format(c.amount) + " 元   利率 " + c.rate
                    + "\n首次审批通过 " + c.approveDate
                    + (c.note.isEmpty() ? "" : "\n备注：" + c.note));
            line3.setText("借记卡：" + c.debitCard + "    手机银行：" + c.mobileBank);
            boolean missing = MessageParser.NOT_OPENED.equals(c.debitCard)
                    || MessageParser.NOT_OPENED.equals(c.mobileBank);
            line3.setTextColor(missing ? RED : GREEN);
            if (c.activity.isEmpty() && c.gift.isEmpty()) {
                line4.setVisibility(View.GONE);
            } else {
                line4.setVisibility(View.VISIBLE);
                line4.setText("活动：" + or(c.activity, "无") + "  礼品：" + or(c.gift, "无")
                        + "(" + money.format(c.giftCost) + "元)"
                        + (c.gift.isEmpty() ? "" : (c.giftGiven ? " ✔已送" : " ✘未送")));
            }
            return row;
        }
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
