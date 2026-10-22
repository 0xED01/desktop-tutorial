package com.textshortcut.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_FONT = 1;
    private static final int REQ_IMAGE = 2;
    private static final String ACTION_PINNED = "com.textshortcut.app.PINNED";

    private static final String[] FONT_NAMES = {
            "系统默认", "无衬线 (黑体)", "衬线 (宋体)", "等宽", "手写体 (英文)",
            "休闲体 (英文)", "细体", "窄体", "自定义字体文件",
    };
    private static final Typeface[] FONT_FACES = {
            Typeface.DEFAULT,
            Typeface.SANS_SERIF,
            Typeface.SERIF,
            Typeface.MONOSPACE,
            Typeface.create("cursive", Typeface.NORMAL),
            Typeface.create("casual", Typeface.NORMAL),
            Typeface.create("sans-serif-light", Typeface.NORMAL),
            Typeface.create("sans-serif-condensed", Typeface.NORMAL),
    };
    private static final String[] SHAPE_NAMES = {"圆形", "圆角方形", "方形", "无背景 (透明)"};

    private IconStyle style;
    private Typeface customFont;
    private Bitmap bgImage;

    private LinearLayout previewRow;
    private TextView status;
    private Button btnNext;
    private View textColorView, bgColorView, bgColor2View, borderColorView;
    private TextView fontName;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable previewTask = this::refreshPreview;

    // 依次添加的队列
    private final List<String> queue = new ArrayList<>();
    private int queueIndex = 0;
    private long batchId;

    private final BroadcastReceiver pinnedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // 桌面确认添加成功后，自动请求下一个
            if (!queue.isEmpty() && queueIndex < queue.size()) {
                handler.postDelayed(MainActivity.this::pinNextInQueue, 400);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        style = IconStyle.load(this);
        loadCustomFont();
        loadBgImage();

        previewRow = findViewById(R.id.preview);
        status = findViewById(R.id.status);
        btnNext = findViewById(R.id.btnNext);
        fontName = findViewById(R.id.fontName);

        EditText text = findViewById(R.id.text);
        text.setText(style.text);
        text.addTextChangedListener(new SimpleWatcher(s -> {
            style.text = s;
            changed();
        }));

        EditText url = findViewById(R.id.url);
        url.setText(style.url);
        url.addTextChangedListener(new SimpleWatcher(s -> {
            style.url = s;
            style.save(this);
        }));

        textColorView = findViewById(R.id.textColor);
        bgColorView = findViewById(R.id.bgColor);
        bgColor2View = findViewById(R.id.bgColor2);
        borderColorView = findViewById(R.id.borderColor);
        textColorView.setOnClickListener(v -> ColorPickerDialog.show(this, "文字颜色", style.textColor, c -> {
            style.textColor = c;
            changed();
        }));
        bgColorView.setOnClickListener(v -> ColorPickerDialog.show(this, "背景颜色", style.bgColor, c -> {
            style.bgColor = c;
            changed();
        }));
        bgColor2View.setOnClickListener(v -> ColorPickerDialog.show(this, "渐变第二种颜色", style.bgColor2, c -> {
            style.bgColor2 = c;
            style.gradient = true;
            ((CheckBox) findViewById(R.id.gradient)).setChecked(true);
            changed();
        }));
        borderColorView.setOnClickListener(v -> ColorPickerDialog.show(this, "边框颜色", style.borderColor, c -> {
            style.borderColor = c;
            if (style.borderWidth == 0) {
                style.borderWidth = 4;
                ((SeekBar) findViewById(R.id.borderWidth)).setProgress(4);
            }
            changed();
        }));

        Spinner font = findViewById(R.id.font);
        font.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, FONT_NAMES));
        font.setSelection(style.fontIndex == IconStyle.FONT_CUSTOM ? FONT_NAMES.length - 1 : style.fontIndex);
        font.setOnItemSelectedListener(new SimpleSelect(pos -> {
            if (pos == FONT_NAMES.length - 1) {
                if (customFont == null) {
                    pickFont();
                    return;
                }
                style.fontIndex = IconStyle.FONT_CUSTOM;
            } else {
                style.fontIndex = pos;
            }
            changed();
        }));
        findViewById(R.id.btnFont).setOnClickListener(v -> pickFont());

        Spinner shape = findViewById(R.id.shape);
        shape.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, SHAPE_NAMES));
        shape.setSelection(style.shape);
        shape.setOnItemSelectedListener(new SimpleSelect(pos -> {
            style.shape = pos;
            changed();
        }));

        bindCheck(R.id.bold, style.bold, b -> style.bold = b);
        bindCheck(R.id.shadow, style.shadow, b -> style.shadow = b);
        bindCheck(R.id.gradient, style.gradient, b -> style.gradient = b);
        bindCheck(R.id.useBgImage, style.useBgImage, b -> {
            style.useBgImage = b;
            if (b && bgImage == null) pickImage();
        });
        bindCheck(R.id.showLabel, style.showLabel, b -> style.showLabel = b);
        bindCheck(R.id.adaptive, style.adaptive, b -> style.adaptive = b);

        bindSeek(R.id.textScale, style.textScale - 20, p -> style.textScale = p + 20);
        bindSeek(R.id.textOffset, style.textOffset + 30, p -> style.textOffset = p - 30);
        bindSeek(R.id.borderWidth, style.borderWidth, p -> style.borderWidth = p);

        findViewById(R.id.btnBgImage).setOnClickListener(v -> pickImage());
        findViewById(R.id.btnAddAll).setOnClickListener(v -> startPinAll());
        btnNext.setOnClickListener(v -> pinNextInQueue());
        findViewById(R.id.btnPermission).setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null));
            startActivity(i);
        });

        IntentFilter filter = new IntentFilter(ACTION_PINNED);
        registerReceiver(pinnedReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        updateSwatches();
        updateFontName();
        refreshPreview();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(pinnedReceiver);
    }

    // ---------- 预览 ----------

    private void changed() {
        style.save(this);
        updateSwatches();
        handler.removeCallbacks(previewTask);
        handler.postDelayed(previewTask, 120);
    }

    private Typeface currentFace() {
        if (style.fontIndex == IconStyle.FONT_CUSTOM && customFont != null) return customFont;
        if (style.fontIndex >= 0 && style.fontIndex < FONT_FACES.length) return FONT_FACES[style.fontIndex];
        return Typeface.DEFAULT;
    }

    private void refreshPreview() {
        previewRow.removeAllViews();
        float dp = getResources().getDisplayMetrics().density;
        int size = (int) (64 * dp);
        List<String> chars = IconRenderer.splitChars(style.text);
        for (String ch : chars) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setPadding((int) (6 * dp), 0, (int) (6 * dp), 0);
            ImageView iv = new ImageView(this);
            Bitmap bmp = IconRenderer.render(ch, style, currentFace(), bgImage, size, style.adaptive);
            if (style.adaptive) {
                // 预览时模拟桌面的圆形裁剪
                iv.setClipToOutline(true);
                GradientDrawable mask = new GradientDrawable();
                mask.setShape(GradientDrawable.OVAL);
                iv.setBackground(mask);
            }
            iv.setImageBitmap(bmp);
            cell.addView(iv, new LinearLayout.LayoutParams(size, size));
            if (style.showLabel) {
                TextView tv = new TextView(this);
                tv.setText(ch);
                tv.setTextSize(12);
                tv.setGravity(android.view.Gravity.CENTER);
                cell.addView(tv, new LinearLayout.LayoutParams(size, LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            cell.setOnClickListener(v -> {
                queue.clear();
                pinOne(ch, System.currentTimeMillis(), 0, false);
            });
            previewRow.addView(cell);
        }
        if (chars.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("先在上面输入文字");
            previewRow.addView(tv);
        }
    }

    private void updateSwatches() {
        setSwatch(textColorView, style.textColor);
        setSwatch(bgColorView, style.bgColor);
        setSwatch(bgColor2View, style.bgColor2);
        setSwatch(borderColorView, style.borderColor);
    }

    private void setSwatch(View v, int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(8 * getResources().getDisplayMetrics().density);
        d.setStroke(2, 0xFF888888);
        v.setBackground(d);
    }

    private void updateFontName() {
        String name = getSharedPreferences("style", MODE_PRIVATE).getString("fontFileName", null);
        fontName.setText(customFont != null && name != null ? "已导入：" + name : "未导入自定义字体");
    }

    // ---------- 添加快捷方式 ----------

    private ShortcutManager shortcutManager() {
        ShortcutManager sm = getSystemService(ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) {
            new AlertDialog.Builder(this)
                    .setTitle("当前桌面不支持")
                    .setMessage("当前桌面（启动器）不支持添加快捷方式，或者本应用没有“桌面快捷方式”权限。请到权限设置中开启后重试。")
                    .setPositiveButton("去设置", (d, w) -> findViewById(R.id.btnPermission).performClick())
                    .setNegativeButton("取消", null)
                    .show();
            return null;
        }
        return sm;
    }

    private void startPinAll() {
        List<String> chars = IconRenderer.splitChars(style.text);
        if (chars.isEmpty()) {
            Toast.makeText(this, "请先输入文字", Toast.LENGTH_SHORT).show();
            return;
        }
        queue.clear();
        queue.addAll(chars);
        queueIndex = 0;
        batchId = System.currentTimeMillis();
        pinNextInQueue();
    }

    private void pinNextInQueue() {
        if (queueIndex >= queue.size()) {
            finishQueue();
            return;
        }
        int i = queueIndex++;
        if (pinOne(queue.get(i), batchId, i, true)) {
            status.setText("正在添加第 " + (i + 1) + " / " + queue.size() + " 个：「" + queue.get(i) + "」");
            if (queueIndex < queue.size()) {
                btnNext.setVisibility(View.VISIBLE);
                btnNext.setText("继续添加下一个：「" + queue.get(queueIndex) + "」");
            } else {
                btnNext.setVisibility(View.GONE);
            }
        } else {
            finishQueue();
        }
    }

    private void finishQueue() {
        queue.clear();
        queueIndex = 0;
        btnNext.setVisibility(View.GONE);
        status.setText("全部请求已发送，回到桌面查看吧");
    }

    private boolean pinOne(String ch, long batch, int index, boolean withCallback) {
        ShortcutManager sm = shortcutManager();
        if (sm == null) return false;

        Intent target = new Intent(this, ShortcutActivity.class);
        target.setAction(Intent.ACTION_VIEW);
        target.putExtra(ShortcutActivity.EXTRA_URL, style.url);
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        Icon icon;
        if (style.adaptive) {
            icon = Icon.createWithAdaptiveBitmap(IconRenderer.render(ch, style, currentFace(), bgImage, 432, true));
        } else {
            icon = Icon.createWithBitmap(IconRenderer.render(ch, style, currentFace(), bgImage, 256, false));
        }

        String label = style.showLabel ? ch : " ";
        ShortcutInfo info = new ShortcutInfo.Builder(this, "t_" + batch + "_" + index + "_" + System.nanoTime())
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(icon)
                .setIntent(target)
                .build();

        PendingIntent callback = null;
        if (withCallback) {
            Intent cb = new Intent(ACTION_PINNED).setPackage(getPackageName());
            callback = PendingIntent.getBroadcast(this, index, cb,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        try {
            sm.requestPinShortcut(info, callback == null ? null : callback.getIntentSender());
            if (!withCallback) status.setText("已请求添加「" + ch + "」");
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "添加失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    // ---------- 导入字体 / 背景图 ----------

    private void pickFont() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_FONT);
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, REQ_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Spinner font = findViewById(R.id.font);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (requestCode == REQ_FONT && customFont == null) {
                font.setSelection(Math.max(0, style.fontIndex));
            }
            if (requestCode == REQ_IMAGE && bgImage == null) {
                style.useBgImage = false;
                ((CheckBox) findViewById(R.id.useBgImage)).setChecked(false);
            }
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQ_FONT) {
            File f = new File(getFilesDir(), "custom_font");
            try {
                copy(uri, f);
                Typeface tf = Typeface.createFromFile(f);
                if (tf == null || tf == Typeface.DEFAULT) throw new Exception("不是有效的字体文件");
                customFont = tf;
                getSharedPreferences("style", MODE_PRIVATE).edit()
                        .putString("fontFileName", displayName(uri)).apply();
                style.fontIndex = IconStyle.FONT_CUSTOM;
                font.setSelection(FONT_NAMES.length - 1);
                updateFontName();
                changed();
            } catch (Exception e) {
                Toast.makeText(this, "字体导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                font.setSelection(Math.max(0, style.fontIndex));
            }
        } else if (requestCode == REQ_IMAGE) {
            try {
                Bitmap b = decodeScaled(uri, 512);
                if (b == null) throw new Exception("无法读取图片");
                try (OutputStream out = new FileOutputStream(new File(getFilesDir(), "bg.png"))) {
                    b.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                bgImage = b;
                style.useBgImage = true;
                ((CheckBox) findViewById(R.id.useBgImage)).setChecked(true);
                changed();
            } catch (Exception e) {
                Toast.makeText(this, "图片导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadCustomFont() {
        File f = new File(getFilesDir(), "custom_font");
        if (f.exists()) {
            try {
                customFont = Typeface.createFromFile(f);
            } catch (Exception ignored) {
            }
        }
        if (customFont == null && style.fontIndex == IconStyle.FONT_CUSTOM) style.fontIndex = 0;
    }

    private void loadBgImage() {
        File f = new File(getFilesDir(), "bg.png");
        if (f.exists()) bgImage = BitmapFactory.decodeFile(f.getAbsolutePath());
    }

    private void copy(Uri uri, File dest) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) throw new Exception("无法打开文件");
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private Bitmap decodeScaled(Uri uri, int maxSize) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, o);
        }
        int sample = 1;
        while (o.outWidth / (sample * 2) >= maxSize && o.outHeight / (sample * 2) >= maxSize) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, o2);
        }
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        }
        return uri.getLastPathSegment();
    }

    // ---------- 小工具 ----------

    private interface BoolSetter { void set(boolean b); }
    private interface IntSetter { void set(int v); }
    private interface StrSetter { void set(String s); }

    private void bindCheck(int id, boolean initial, BoolSetter setter) {
        CheckBox cb = findViewById(id);
        cb.setChecked(initial);
        cb.setOnCheckedChangeListener((v, b) -> {
            setter.set(b);
            changed();
        });
    }

    private void bindSeek(int id, int initial, IntSetter setter) {
        SeekBar sb = findViewById(id);
        sb.setProgress(initial);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (!user) return;
                setter.set(p);
                changed();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
    }

    private static class SimpleWatcher implements TextWatcher {
        private final StrSetter setter;
        SimpleWatcher(StrSetter setter) { this.setter = setter; }
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
        @Override public void afterTextChanged(Editable e) { setter.set(e.toString()); }
    }

    private static class SimpleSelect implements AdapterView.OnItemSelectedListener {
        private final IntSetter setter;
        private boolean first = true;
        SimpleSelect(IntSetter setter) { this.setter = setter; }
        @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
            // 忽略初始化时自动触发的第一次回调
            if (first) { first = false; return; }
            setter.set(pos);
        }
        @Override public void onNothingSelected(AdapterView<?> p) { }
    }
}
