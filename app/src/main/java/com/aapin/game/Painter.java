package com.aapin.game;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.aapin.game.core.Board;

/** 负责绘制背景、圆盘、针、发射队列以及通用面板和按钮。 */
public final class Painter {
    public Theme th;
    public float w, h, cx, cy, u, discR, L, headR, gap;
    /** 刘海/挖孔屏顶部安全区高度。 */
    public float safeTop;

    public final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bg = new Paint();
    private float bgH = -1;
    private Theme bgTheme;
    private final RectF tmp = new RectF();

    public Painter(Theme th) {
        this.th = th;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    }

    /** cyFrac：圆盘中心位于屏幕高度的比例。 */
    public void layout(float w, float h, float cyFrac, float scale) {
        this.w = w;
        this.h = h;
        u = Math.min(w, h * 0.58f) * scale;
        cx = w / 2f;
        cy = h * cyFrac;
        discR = u * 0.13f;
        L = u * 0.33f;
        headR = (float) (L * Math.sin(Math.toRadians(Board.MIN_SEP / 2f)));
        gap = u * 0.2f;
        line.setStrokeWidth(Math.max(2f, u * 0.0055f));
    }

    public float hudTop() {
        return Math.max(h * 0.035f, safeTop + h * 0.01f);
    }

    public static float safeTop(android.view.View v) {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            android.view.WindowInsets in = v.getRootWindowInsets();
            if (in != null && in.getDisplayCutout() != null) return in.getDisplayCutout().getSafeInsetTop();
        }
        return 0;
    }

    public void background(Canvas c) {
        if (bgH != h || bgTheme != th) {
            bg.setShader(new LinearGradient(0, 0, 0, h, th.bgTop, th.bgBottom, Shader.TileMode.CLAMP));
            bgH = h;
            bgTheme = th;
        }
        c.drawRect(0, 0, w, h, bg);
    }

    public float px(float screenDeg, float r) {
        return cx + (float) Math.cos(Math.toRadians(screenDeg)) * r;
    }

    public float py(float screenDeg, float r) {
        return cy + (float) Math.sin(Math.toRadians(screenDeg)) * r;
    }

    public void pin(Canvas c, float screenDeg, int color, int alpha) {
        float x = px(screenDeg, L), y = py(screenDeg, L);
        line.setColor(color);
        line.setAlpha(alpha);
        c.drawLine(cx, cy, x, y, line);
        fill.setColor(color);
        fill.setAlpha(alpha);
        c.drawCircle(x, y, headR, fill);
        fill.setAlpha(255);
        line.setAlpha(255);
    }

    /** 飞行中的针。dir=+1 从下方飞来，-1 从上方飞来。 */
    public void flying(Canvas c, int dir, float p, int color) {
        float hy = cy + dir * (L + gap * (1f - Math.min(1f, p)));
        float ty = hy - dir * (L - discR * 0.5f);
        line.setColor(color);
        c.drawLine(cx, hy, cx, ty, line);
        fill.setColor(color);
        c.drawCircle(cx, hy, headR, fill);
    }

    public void disc(Canvas c, float pulse) {
        fill.setColor(th.disc);
        c.drawCircle(cx, cy, discR * (1f + pulse), fill);
    }

    public void discLabel(Canvas c, String s, float sizeK) {
        text.setColor(th.discText);
        text.setTextSize(discR * sizeK);
        c.drawText(s, cx, cy - (text.descent() + text.ascent()) / 2f, text);
    }

    /** 待发射的针队列。flipLabel：数字倒转（给对面玩家看）。 */
    public void queue(Canvas c, int dir, int count, int color, boolean flipLabel) {
        float y0 = cy + dir * (L + gap);
        float step = headR * 2.7f;
        int show = Math.min(count, 4);
        text.setTextSize(headR * 1.15f);
        for (int i = show - 1; i >= 0; i--) {
            float y = y0 + dir * i * step;
            int alpha = 255 - i * 50;
            fill.setColor(color);
            fill.setAlpha(alpha);
            c.drawCircle(cx, y, headR * 1.25f, fill);
            fill.setAlpha(255);
            text.setColor(th.discText);
            text.setAlpha(alpha);
            float ty = y - (text.descent() + text.ascent()) / 2f;
            if (flipLabel) {
                c.save();
                c.rotate(180, cx, y);
                c.drawText(String.valueOf(count - i), cx, ty, text);
                c.restore();
            } else {
                c.drawText(String.valueOf(count - i), cx, ty, text);
            }
            text.setAlpha(255);
        }
    }

    public void label(Canvas c, String s, float x, float y, float size, int color, Paint.Align align) {
        text.setTextAlign(align);
        text.setTextSize(size);
        text.setColor(color);
        c.drawText(s, x, y, text);
        text.setTextAlign(Paint.Align.CENTER);
    }

    public void panel(Canvas c, RectF r) {
        fill.setColor(0x33000000);
        tmp.set(r.left, r.top + u * 0.012f, r.right, r.bottom + u * 0.012f);
        c.drawRoundRect(tmp, u * 0.05f, u * 0.05f, fill);
        fill.setColor(th.panel);
        c.drawRoundRect(r, u * 0.05f, u * 0.05f, fill);
    }

    public void button(Canvas c, RectF r, String s, int color, boolean pressed) {
        fill.setColor(color);
        if (pressed) fill.setAlpha(190);
        c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, fill);
        fill.setAlpha(255);
        text.setColor(0xFFFFFFFF);
        text.setTextSize(r.height() * 0.42f);
        c.drawText(s, r.centerX(), r.centerY() - (text.descent() + text.ascent()) / 2f, text);
    }

    public void dim(Canvas c, int alpha) {
        fill.setColor(0x000000);
        fill.setAlpha(alpha);
        c.drawRect(0, 0, w, h, fill);
        fill.setAlpha(255);
    }

    public static int blend(int a, int b, float k) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bgc = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int) (ar + (br - ar) * k) << 16) | ((int) (ag + (bgc - ag) * k) << 8)
                | (int) (ab + (bb - ab) * k);
    }
}
