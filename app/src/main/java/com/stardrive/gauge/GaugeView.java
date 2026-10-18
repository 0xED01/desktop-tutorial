package com.stardrive.gauge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/** Canvas-drawn speedometer with three dial styles. Speed values are in display units. */
public class GaugeView extends View {
    public static final int STYLE_CLASSIC = 0, STYLE_SPORT = 1, STYLE_HUD = 2;

    private static final float START = 135f, SWEEP = 270f;
    private static final int RED = 0xFFFF3B30;

    private int style = STYLE_CLASSIC;
    private int accent = Cfg.ACCENTS[0];
    private float maxScale = 260f;
    private float target, shown, maxReached, limit;
    private boolean overspeed, extras;
    private String unit = "km/h", note;
    private String tl = "", tr = "", bl = "", br = "";
    private long lastFrame;

    private float cx, cy, R;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chrome = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private final Typeface thin, light, cond;

    public GaugeView(Context c) {
        this(c, null);
    }

    public GaugeView(Context c, AttributeSet a) {
        super(c, a);
        thin = Typeface.create("sans-serif-thin", Typeface.NORMAL);
        light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        cond = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
        stroke.setStyle(Paint.Style.STROKE);
        chrome.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
    }

    // ---- setters -------------------------------------------------------

    public void setSpeed(float v) {
        target = Math.max(0f, v);
        invalidate();
    }

    public void setMax(float v) {
        if (v != maxReached) {
            maxReached = v;
            invalidate();
        }
    }

    public void setStyle(int s) {
        style = s;
        invalidate();
    }

    public void setAccent(int c) {
        accent = c;
        invalidate();
    }

    public void setMaxScale(float m) {
        maxScale = m;
        invalidate();
    }

    public void setUnit(String u) {
        unit = u;
        invalidate();
    }

    public void setLimit(float l) {
        limit = l;
        invalidate();
    }

    public void setOverspeed(boolean o) {
        if (o != overspeed) {
            overspeed = o;
            invalidate();
        }
    }

    public void setNote(String n) {
        if (n == null ? note != null : !n.equals(note)) {
            note = n;
            invalidate();
        }
    }

    public void setExtras(boolean on) {
        extras = on;
        invalidate();
    }

    public void setCorners(String tl, String tr, String bl, String br) {
        this.tl = tl;
        this.tr = tr;
        this.bl = bl;
        this.br = br;
        if (extras) invalidate();
    }

    // ---- geometry ------------------------------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        cx = w / 2f;
        cy = h / 2f;
        R = Math.min(w, h) / 2f * 0.94f;
        if (R <= 0) return;
        bg.setShader(new RadialGradient(cx, cy, R,
                new int[]{0xFF1E2228, 0xFF0B0C0F, 0xFF000000},
                new float[]{0f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        chrome.setShader(new SweepGradient(cx, cy,
                new int[]{0xFF4A4D52, 0xFFF2F4F6, 0xFF5C6066, 0xFFD9DDE1,
                        0xFF3E4146, 0xFFECEFF2, 0xFF4A4D52}, null));
        hub.setShader(new SweepGradient(cx, cy,
                new int[]{0xFF3A3D42, 0xFFE6E9EC, 0xFF505459, 0xFFCDD1D5, 0xFF3A3D42}, null));
    }

    private float frac(float v) {
        return Math.max(0f, Math.min(1f, v / maxScale));
    }

    private float ang(float v) {
        return START + SWEEP * frac(v);
    }

    private float px(float deg, float r) {
        return cx + r * (float) Math.cos(Math.toRadians(deg));
    }

    private float py(float deg, float r) {
        return cy + r * (float) Math.sin(Math.toRadians(deg));
    }

    private int majorStep() {
        return maxScale > 280 ? 40 : 20;
    }

    private static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | (a << 24);
    }

    // ---- drawing -------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        shown += (target - shown) * Math.min(1f, dt * 6f);
        if (Math.abs(target - shown) < 0.02f) shown = target;

        canvas.drawColor(Color.BLACK);
        if (R <= 0) return;
        int acc = overspeed ? RED : accent;
        switch (style) {
            case STYLE_SPORT: drawSport(canvas, acc, now); break;
            case STYLE_HUD: drawHud(canvas, acc, now); break;
            default: drawClassic(canvas, acc, now);
        }
        if (extras) drawCorners(canvas);

        if (shown != target || overspeed) postInvalidateOnAnimation();
        else lastFrame = 0;
    }

    private void drawClassic(Canvas c, int acc, long now) {
        c.drawCircle(cx, cy, R, bg);
        chrome.setStrokeWidth(R * 0.05f);
        c.drawCircle(cx, cy, R * 0.975f, chrome);
        stroke.setShader(null);
        stroke.setStrokeCap(Paint.Cap.BUTT);
        stroke.setStrokeWidth(R * 0.005f);
        stroke.setColor(0x40FFFFFF);
        c.drawCircle(cx, cy, R * 0.93f, stroke);

        // lit arc with layered glow
        float a = ang(shown);
        float arcR = R * 0.895f;
        oval.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR);
        stroke.setStrokeWidth(R * 0.022f);
        stroke.setColor(0x16FFFFFF);
        c.drawArc(oval, START, SWEEP, false, stroke);
        if (a > START) {
            stroke.setStrokeWidth(R * 0.075f);
            stroke.setColor(alpha(acc, 0x22));
            c.drawArc(oval, START, a - START, false, stroke);
            stroke.setStrokeWidth(R * 0.045f);
            stroke.setColor(alpha(acc, 0x44));
            c.drawArc(oval, START, a - START, false, stroke);
            stroke.setStrokeWidth(R * 0.02f);
            stroke.setColor(acc);
            c.drawArc(oval, START, a - START, false, stroke);
        }

        // ticks and numerals
        int major = majorStep();
        float minor = major / 4f;
        int n = Math.round(maxScale / minor);
        text.setTypeface(light);
        text.setTextSize(R * 0.085f);
        text.setLetterSpacing(0f);
        for (int i = 0; i <= n; i++) {
            float v = i * minor;
            float deg = ang(v);
            boolean isMajor = i % 4 == 0, isMid = i % 2 == 0;
            float r0 = isMajor ? R * 0.75f : isMid ? R * 0.79f : R * 0.82f;
            float r1 = R * 0.855f;
            boolean lit = shown > 0.5f && v <= shown;
            stroke.setStrokeWidth(isMajor ? R * 0.014f : R * 0.007f);
            stroke.setColor(lit ? acc : isMajor ? 0xFFE8E8E8 : 0xFF7E848A);
            c.drawLine(px(deg, r0), py(deg, r0), px(deg, r1), py(deg, r1), stroke);
            if (isMajor) {
                text.setColor(lit ? 0xFFFFFFFF : 0xFFC9CDD1);
                c.drawText(String.valueOf(Math.round(v)), px(deg, R * 0.64f),
                        py(deg, R * 0.64f) + text.getTextSize() * 0.35f, text);
            }
        }

        drawLimitMarker(c, R * 0.86f, R * 0.95f);

        // needle
        c.save();
        c.rotate(a, cx, cy);
        path.reset();
        path.moveTo(cx + R * 0.80f, cy);
        path.lineTo(cx, cy - R * 0.024f);
        path.lineTo(cx - R * 0.17f, cy - R * 0.013f);
        path.lineTo(cx - R * 0.17f, cy + R * 0.013f);
        path.lineTo(cx, cy + R * 0.024f);
        path.close();
        stroke.setColor(alpha(acc, 0x55));
        stroke.setStrokeWidth(R * 0.035f);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        c.drawPath(path, stroke);
        fill.setShader(null);
        fill.setColor(acc);
        c.drawPath(path, fill);
        stroke.setColor(0xDDFFFFFF);
        stroke.setStrokeWidth(R * 0.006f);
        c.drawLine(cx + R * 0.10f, cy, cx + R * 0.77f, cy, stroke);
        c.restore();
        c.drawCircle(cx, cy, R * 0.085f, hub);
        fill.setColor(0xFF0A0B0D);
        c.drawCircle(cx, cy, R * 0.052f, fill);
        stroke.setColor(acc);
        stroke.setStrokeWidth(R * 0.006f);
        c.drawCircle(cx, cy, R * 0.052f, stroke);

        // texts
        text.setTypeface(cond);
        text.setTextSize(R * 0.048f);
        text.setLetterSpacing(0.35f);
        text.setColor(0xFFB8BEC4);
        c.drawText("STAR DRIVE", cx, cy - R * 0.40f, text);
        drawTopNote(c, acc, cy - R * 0.27f, R * 0.058f);

        text.setLetterSpacing(0f);
        text.setTypeface(thin);
        text.setTextSize(R * 0.26f);
        text.setColor(0xFFFFFFFF);
        c.drawText(String.valueOf(Math.round(shown)), cx, cy + R * 0.47f, text);
        text.setTypeface(cond);
        text.setTextSize(R * 0.07f);
        text.setColor(acc);
        c.drawText(unit, cx, cy + R * 0.60f, text);

        drawOverspeedRing(c, now, R * 0.995f);
    }

    private void drawSport(Canvas c, int acc, long now) {
        c.drawCircle(cx, cy, R, bg);
        stroke.setShader(null);
        stroke.setStrokeCap(Paint.Cap.BUTT);
        stroke.setStrokeWidth(R * 0.008f);
        stroke.setColor(alpha(accent, 0x77));
        c.drawCircle(cx, cy, R * 0.985f, stroke);
        stroke.setColor(0x22FFFFFF);
        c.drawCircle(cx, cy, R * 0.70f, stroke);

        float ringR = R * 0.83f;
        oval.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
        float f = frac(shown);
        if (f > 0) {
            stroke.setStrokeWidth(R * 0.19f);
            stroke.setColor(alpha(acc, 0x1E));
            c.drawArc(oval, START, SWEEP * f, false, stroke);
        }
        int segs = 60;
        float seg = SWEEP / segs, gap = seg * 0.3f;
        stroke.setStrokeWidth(R * 0.11f);
        for (int i = 0; i < segs; i++) {
            boolean lit = (i + 0.5f) / segs <= f;
            int col;
            if (!lit) col = 0x1CFFFFFF;
            else if ((float) i / segs >= 0.86f) col = RED;
            else col = acc;
            stroke.setColor(col);
            c.drawArc(oval, START + i * seg + gap / 2, seg - gap, false, stroke);
        }

        int major = majorStep();
        text.setTypeface(cond);
        text.setTextSize(R * 0.062f);
        text.setLetterSpacing(0f);
        for (int v = 0; v <= maxScale; v += major) {
            float deg = ang(v);
            text.setColor(v <= shown && shown > 0.5f ? 0xFFFFFFFF : 0xFF8E959C);
            c.drawText(String.valueOf(v), px(deg, R * 0.62f),
                    py(deg, R * 0.62f) + text.getTextSize() * 0.35f, text);
        }
        if (limit > 0 && limit <= maxScale) {
            float deg = ang(limit);
            stroke.setColor(RED);
            stroke.setStrokeWidth(R * 0.012f);
            c.drawLine(px(deg, R * 0.75f), py(deg, R * 0.75f), px(deg, R * 0.95f), py(deg, R * 0.95f), stroke);
        }

        text.setTypeface(cond);
        text.setTextSize(R * 0.06f);
        text.setLetterSpacing(0.4f);
        text.setColor(acc);
        c.drawText("SPORT+", cx, cy - R * 0.30f, text);

        text.setLetterSpacing(0f);
        text.setTypeface(thin);
        text.setTextSize(R * 0.40f);
        text.setColor(0xFFFFFFFF);
        c.drawText(String.valueOf(Math.round(shown)), cx, cy + R * 0.13f, text);
        text.setTypeface(cond);
        text.setTextSize(R * 0.075f);
        text.setColor(acc);
        c.drawText(unit, cx, cy + R * 0.29f, text);
        drawTopNote(c, acc, cy + R * 0.55f, R * 0.058f);

        drawOverspeedRing(c, now, R * 0.995f);
    }

    private void drawHud(Canvas c, int acc, long now) {
        float w = getWidth(), h = getHeight();
        String s = String.valueOf(Math.round(shown));
        text.setTypeface(thin);
        text.setLetterSpacing(0f);
        float size = h * 0.55f;
        text.setTextSize(size);
        float tw = text.measureText("888".length() > s.length() ? "888" : s);
        if (tw > w * 0.88f) {
            size *= w * 0.88f / tw;
            text.setTextSize(size);
        }
        text.setColor(acc);
        text.setShadowLayer(size * 0.06f, 0, 0, acc);
        float base = h * 0.46f + size * 0.36f;
        c.drawText(s, w / 2f, base, text);
        text.setColor(0xFFFFFFFF);
        text.setShadowLayer(size * 0.02f, 0, 0, acc);
        c.drawText(s, w / 2f, base, text);
        text.clearShadowLayer();

        text.setTypeface(cond);
        text.setTextSize(size * 0.14f);
        text.setLetterSpacing(0.3f);
        text.setColor(acc);
        c.drawText(unit.toUpperCase(), w / 2f, base + size * 0.22f, text);
        text.setLetterSpacing(0f);

        float bw = w * 0.7f, bx = (w - bw) / 2f, by = h * 0.88f, bh = Math.max(4f, h * 0.014f);
        fill.setShader(null);
        fill.setColor(0x22FFFFFF);
        c.drawRect(bx, by, bx + bw, by + bh, fill);
        fill.setColor(acc);
        c.drawRect(bx, by, bx + bw * frac(shown), by + bh, fill);
        if (limit > 0 && limit <= maxScale) {
            fill.setColor(RED);
            float lx = bx + bw * frac(limit);
            c.drawRect(lx - bh * 0.3f, by - bh, lx + bh * 0.3f, by + bh * 2, fill);
        }
        drawTopNote(c, acc, h * 0.13f, Math.min(w, h) * 0.05f);

        if (overspeed) {
            float p = 0.5f + 0.5f * (float) Math.sin(now * 0.012);
            stroke.setShader(null);
            stroke.setColor(alpha(RED, (int) (90 + 120 * p)));
            stroke.setStrokeWidth(Math.min(w, h) * 0.02f);
            c.drawRect(0, 0, w, h, stroke);
        }
    }

    /** Small line above/below the digits: 0-100 timer if active, otherwise max speed. */
    private void drawTopNote(Canvas c, int acc, float y, float size) {
        text.setTypeface(cond);
        text.setTextSize(size);
        text.setLetterSpacing(0.12f);
        if (note != null) {
            text.setColor(acc);
            c.drawText(note, getWidth() / 2f, y, text);
        } else {
            text.setColor(0xFF8E959C);
            c.drawText("MAX " + Math.round(maxReached), getWidth() / 2f, y, text);
        }
        text.setLetterSpacing(0f);
    }

    private void drawLimitMarker(Canvas c, float rIn, float rOut) {
        if (limit <= 0 || limit > maxScale) return;
        float deg = ang(limit);
        float half = 2.2f;
        path.reset();
        path.moveTo(px(deg, rIn), py(deg, rIn));
        path.lineTo(px(deg - half, rOut), py(deg - half, rOut));
        path.lineTo(px(deg + half, rOut), py(deg + half, rOut));
        path.close();
        fill.setShader(null);
        fill.setColor(RED);
        c.drawPath(path, fill);
    }

    private void drawOverspeedRing(Canvas c, long now, float r) {
        if (!overspeed) return;
        float p = 0.5f + 0.5f * (float) Math.sin(now * 0.012);
        stroke.setShader(null);
        stroke.setColor(alpha(RED, (int) (60 + 150 * p)));
        stroke.setStrokeWidth(R * 0.03f);
        c.drawCircle(cx, cy, r, stroke);
    }

    private void drawCorners(Canvas c) {
        float w = getWidth(), h = getHeight();
        float size = Math.min(w, h) * 0.042f, m = size * 1.1f;
        text.setTypeface(cond);
        text.setTextSize(size);
        text.setLetterSpacing(0.05f);
        text.setColor(0xFFB0B8C0);
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText(tl, m, m + size, text);
        c.drawText(bl, m, h - m, text);
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText(tr, w - m, m + size, text);
        c.drawText(br, w - m, h - m, text);
        text.setTextAlign(Paint.Align.CENTER);
        text.setLetterSpacing(0f);
    }
}
