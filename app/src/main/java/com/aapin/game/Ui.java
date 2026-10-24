package com.aapin.game;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 代码构建界面的小工具。 */
final class Ui {
    private Ui() {
    }

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    static Drawable background(Theme th) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{th.bgTop, th.bgBottom});
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static TextView button(Context c, String s, int bg, int fg, View.OnClickListener l) {
        TextView t = text(c, s, 18, fg, true);
        t.setGravity(Gravity.CENTER);
        float r = dp(c, 28);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, round(Painter.blend(bg, 0xFF000000, 0.18f), r));
        sl.addState(new int[]{}, round(bg, r));
        t.setBackground(sl);
        t.setMinHeight(dp(c, 54));
        t.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
        t.setClickable(true);
        t.setOnClickListener(v -> {
            Sfx.play(Sfx.CLICK);
            l.onClick(v);
        });
        return t;
    }

    static LinearLayout.LayoutParams lp(int w, int h, int vMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = vMargin;
        p.bottomMargin = vMargin;
        return p;
    }

    static int sub(Theme th) {
        return Painter.blend(th.text, th.bgTop, 0.35f);
    }
}
