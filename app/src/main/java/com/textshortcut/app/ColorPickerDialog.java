package com.textshortcut.app;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

/** 简单的颜色选择器：预设色板 + ARGB 滑块 + 十六进制输入。 */
public class ColorPickerDialog {

    public interface OnColorPicked {
        void onPicked(int color);
    }

    private static final int[] PRESETS = {
            0xFFFFFFFF, 0xFF000000, 0xFF9E9E9E, 0xFF607D8B, 0xFFF44336, 0xFFE91E63,
            0xFFFF6CAB, 0xFF9C27B0, 0xFF673AB7, 0xFF5B6CFF, 0xFF2196F3, 0xFF03A9F4,
            0xFF00BCD4, 0xFF009688, 0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39, 0xFFFFEB3B,
            0xFFFFC107, 0xFFFF9800, 0xFFFF5722, 0xFF795548, 0xFFFFD1DC, 0x00000000,
    };

    private int color;
    private boolean updating;

    public static void show(Context ctx, String title, int initial, OnColorPicked cb) {
        new ColorPickerDialog().build(ctx, title, initial, cb);
    }

    private void build(Context ctx, String title, int initial, OnColorPicked cb) {
        color = initial;
        float dp = ctx.getResources().getDisplayMetrics().density;
        int pad = (int) (16 * dp);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, 0);

        View preview = new View(ctx);
        root.addView(preview, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (48 * dp)));

        GridLayout grid = new GridLayout(ctx);
        grid.setColumnCount(6);
        grid.setPadding(0, (int) (8 * dp), 0, (int) (8 * dp));
        int cell = (int) (36 * dp);
        int margin = (int) (4 * dp);

        String[] labels = {"A", "R", "G", "B"};
        SeekBar[] bars = new SeekBar[4];
        EditText hex = new EditText(ctx);

        Runnable refresh = () -> {
            updating = true;
            GradientDrawable d = new GradientDrawable();
            d.setColor(color);
            d.setCornerRadius(8 * dp);
            d.setStroke((int) dp, 0xFF888888);
            preview.setBackground(d);
            bars[0].setProgress(Color.alpha(color));
            bars[1].setProgress(Color.red(color));
            bars[2].setProgress(Color.green(color));
            bars[3].setProgress(Color.blue(color));
            String h = String.format(Locale.US, "%08X", color);
            if (!hex.getText().toString().equalsIgnoreCase(h)) hex.setText(h);
            updating = false;
        };

        for (int pc : PRESETS) {
            View sw = new View(ctx);
            GradientDrawable d = new GradientDrawable();
            d.setColor(pc);
            d.setCornerRadius(cell / 2f);
            d.setStroke((int) dp, 0xFF888888);
            sw.setBackground(d);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cell;
            lp.height = cell;
            lp.setMargins(margin, margin, margin, margin);
            sw.setOnClickListener(v -> {
                color = pc;
                refresh.run();
            });
            grid.addView(sw, lp);
        }
        root.addView(grid);

        for (int i = 0; i < 4; i++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView tv = new TextView(ctx);
            tv.setText(labels[i]);
            tv.setWidth((int) (24 * dp));
            row.addView(tv);
            SeekBar sb = new SeekBar(ctx);
            sb.setMax(255);
            bars[i] = sb;
            row.addView(sb, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            root.addView(row);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                    if (!user || updating) return;
                    color = Color.argb(bars[0].getProgress(), bars[1].getProgress(),
                            bars[2].getProgress(), bars[3].getProgress());
                    refresh.run();
                }
                @Override public void onStartTrackingTouch(SeekBar s) { }
                @Override public void onStopTrackingTouch(SeekBar s) { }
            });
        }

        hex.setHint("AARRGGBB 或 RRGGBB");
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hex.setSingleLine();
        hex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) {
                if (updating) return;
                String t = e.toString().trim().replace("#", "");
                try {
                    if (t.length() == 6) {
                        color = 0xFF000000 | (int) Long.parseLong(t, 16);
                        refresh.run();
                    } else if (t.length() == 8) {
                        color = (int) Long.parseLong(t, 16);
                        refresh.run();
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        });
        root.addView(hex);

        refresh.run();

        new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setView(root)
                .setPositiveButton("确定", (d, w) -> cb.onPicked(color))
                .setNegativeButton("取消", null)
                .show();
    }
}
