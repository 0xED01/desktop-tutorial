package com.customer.recorder;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 界面样式工具：卡片、标签、按钮等。 */
final class Ui {

    /** 主色：蓝 */
    static final int PRIMARY = 0xFF1A56B0;
    static final int PRIMARY_DARK = 0xFF0F2F66;
    /** 辅色：黄 */
    static final int YELLOW = 0xFFFFC629;
    static final int YELLOW_BG = 0xFFFFF6D6;
    static final int YELLOW_TEXT = 0xFF8A5A00;
    static final int TEXT = 0xFF202124;
    static final int TEXT2 = 0xFF6B7280;
    static final int GREEN = 0xFF2E7D32;
    static final int GREEN_BG = 0xFFE8F5E9;
    static final int RED = 0xFFC62828;
    static final int RED_BG = 0xFFFDECEA;
    static final int BLUE = 0xFF1565C0;
    static final int BLUE_BG = 0xFFE3F2FD;
    static final int ORANGE = 0xFFE65100;
    static final int ORANGE_BG = 0xFFFFF3E0;
    static final int GRAY_BG = 0xFFF0F3F8;

    private Ui() {
    }

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    static GradientDrawable round(Context c, int fill, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    private static Drawable ripple(Context c, int fill, float radiusDp, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor),
                round(c, fill, radiusDp), null);
    }

    static LinearLayout card(Context c) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(ripple(c, Color.WHITE, 14, 0x14000000));
        int p = dp(c, 14);
        card.setPadding(p, p, p, p);
        card.setElevation(dp(c, 1.5f));
        return card;
    }

    static LinearLayout.LayoutParams cardParams(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(c, 10);
        return lp;
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    static TextView label(Context c, String s) {
        TextView tv = text(c, s, 13, TEXT2, false);
        tv.setPadding(0, dp(c, 12), 0, dp(c, 6));
        return tv;
    }

    static EditText input(Context c, String hint) {
        EditText et = new EditText(c);
        et.setHint(hint);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        et.setTextColor(TEXT);
        et.setHintTextColor(0xFF9AA0A6);
        et.setBackground(round(c, GRAY_BG, 10));
        et.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        return et;
    }

    static TextView chip(Context c, String s, boolean selected) {
        TextView tv = text(c, s, 14, selected ? Color.WHITE : TEXT, selected);
        tv.setBackground(ripple(c, selected ? PRIMARY : GRAY_BG, 18, 0x22000000));
        tv.setPadding(dp(c, 14), dp(c, 7), dp(c, 14), dp(c, 7));
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    static TextView addChip(Context c, String s) {
        TextView tv = text(c, s, 14, YELLOW_TEXT, true);
        GradientDrawable d = round(c, YELLOW_BG, 18);
        d.setStroke(dp(c, 1), YELLOW);
        tv.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), d, null));
        tv.setPadding(dp(c, 14), dp(c, 7), dp(c, 14), dp(c, 7));
        return tv;
    }

    static TextView tag(Context c, String s, int fg, int bg) {
        TextView tv = text(c, s, 12, fg, false);
        tv.setBackground(round(c, bg, 6));
        tv.setPadding(dp(c, 8), dp(c, 3), dp(c, 8), dp(c, 3));
        return tv;
    }

    static Button button(Context c, String s, boolean primary) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTextColor(primary ? PRIMARY_DARK : PRIMARY);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(ripple(c, primary ? YELLOW : BLUE_BG, 10, 0x33000000));
        b.setStateListAnimator(null);
        b.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        return b;
    }
}
