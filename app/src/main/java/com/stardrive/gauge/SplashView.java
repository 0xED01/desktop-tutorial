package com.stardrive.gauge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Startup animation: light streaks converge, the chrome bezel draws itself,
 * ticks light up, the needle does a full sweep and the title fades in with a shine.
 */
public class SplashView extends View {
    public interface Listener {
        void onSplashDone();
        void onNeedlePeak();
    }

    private static final float START = 135f, SWEEP = 270f, MAX = 260f;
    private static final float TOTAL = 5.0f;
    private static final int STREAKS = 70;

    private final int accent;
    private final Listener listener;
    private long t0;
    private boolean done, peaked;

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chrome = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final float[] sAng = new float[STREAKS], sDelay = new float[STREAKS], sLen = new float[STREAKS];
    private final Typeface thin, light, cond;
    private float cx, cy, R;

    public SplashView(Context c, int accent, Listener l) {
        super(c);
        this.accent = accent;
        this.listener = l;
        stroke.setStyle(Paint.Style.STROKE);
        chrome.setStyle(Paint.Style.STROKE);
        chrome.setStrokeCap(Paint.Cap.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        thin = Typeface.create("sans-serif-thin", Typeface.NORMAL);
        light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        cond = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
        Random r = new Random(7);
        for (int i = 0; i < STREAKS; i++) {
            sAng[i] = r.nextFloat() * 360f;
            sDelay[i] = r.nextFloat() * 0.7f;
            sLen[i] = 0.15f + r.nextFloat() * 0.35f;
        }
    }

    public void skip() {
        if (!done) {
            done = true;
            listener.onSplashDone();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        R = Math.min(w * 0.34f, h * 0.27f);
        cx = w / 2f;
        cy = h * 0.40f;
        chrome.setShader(new SweepGradient(cx, cy,
                new int[]{0xFF4A4D52, 0xFFF2F4F6, 0xFF5C6066, 0xFFD9DDE1,
                        0xFF3E4146, 0xFFECEFF2, 0xFF4A4D52}, null));
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float phase(float t, float a, float b) {
        return clamp((t - a) / (b - a));
    }

    private static float easeOut(float p) {
        float q = 1 - p;
        return 1 - q * q * q;
    }

    private static float easeInOut(float p) {
        return p < 0.5f ? 4 * p * p * p : 1 - (float) Math.pow(-2 * p + 2, 3) / 2;
    }

    private static int alpha(int color, float a) {
        return (color & 0x00FFFFFF) | ((int) (clamp(a) * 255) << 24);
    }

    private float px(float deg, float r) {
        return cx + r * (float) Math.cos(Math.toRadians(deg));
    }

    private float py(float deg, float r) {
        return cy + r * (float) Math.sin(Math.toRadians(deg));
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        if (t0 == 0) t0 = now;
        float t = (now - t0) / 1000f;
        float w = getWidth(), h = getHeight();
        c.drawColor(Color.BLACK);
        if (R <= 0) return;

        // ambient glow behind the dial
        float glow = phase(t, 0.3f, 1.6f) * (1f - 0.4f * phase(t, 3.2f, 4.2f));
        if (glow > 0) {
            fill.setShader(new RadialGradient(cx, cy, R * 2.2f,
                    new int[]{alpha(accent, 0.22f * glow), alpha(accent, 0.06f * glow), 0},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, fill);
            fill.setShader(null);
        }

        // converging light streaks
        float far = (float) Math.hypot(w, h);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < STREAKS; i++) {
            float p = phase(t, sDelay[i], sDelay[i] + 0.9f);
            if (p <= 0 || p >= 1) continue;
            float e = p * p;
            float head = far * 0.7f + (R * 1.02f - far * 0.7f) * e;
            float tail = head + far * sLen[i] * (1 - p);
            stroke.setStrokeWidth(R * (i % 3 == 0 ? 0.012f : 0.006f));
            stroke.setColor(alpha(i % 4 == 0 ? 0xFFFFFFFF : accent, (float) Math.sin(Math.PI * p)));
            c.drawLine(px(sAng[i], head), py(sAng[i], head), px(sAng[i], tail), py(sAng[i], tail), stroke);
        }

        // chrome bezel draws on
        float ring = easeOut(phase(t, 0.5f, 1.5f));
        if (ring > 0) {
            float rr = R;
            oval.set(cx - rr, cy - rr, cx + rr, cy + rr);
            chrome.setStrokeWidth(R * 0.05f);
            c.drawArc(oval, -90f, 360f * ring, false, chrome);
        }
        // flash when the ring closes
        float flash = phase(t, 1.45f, 2.1f);
        if (flash > 0 && flash < 1) {
            stroke.setStrokeWidth(R * 0.04f * (1 - flash));
            stroke.setColor(alpha(0xFFFFFFFF, 0.8f * (1 - flash)));
            c.drawCircle(cx, cy, R * (1f + 0.35f * flash), stroke);
        }

        // ticks light up in order
        float ticks = phase(t, 1.0f, 1.8f);
        float needleV = needleValue(t);
        int n = 52;
        stroke.setStrokeCap(Paint.Cap.BUTT);
        text.setTypeface(light);
        text.setTextSize(R * 0.085f);
        text.setLetterSpacing(0f);
        float numAlpha = phase(t, 1.3f, 1.9f);
        for (int i = 0; i <= n; i++) {
            if ((float) i / n > ticks) break;
            float v = MAX * i / n;
            float deg = START + SWEEP * i / n;
            boolean major = i % 4 == 0, mid = i % 2 == 0;
            float r0 = major ? R * 0.75f : mid ? R * 0.79f : R * 0.82f;
            boolean lit = needleV > 0.5f && v <= needleV;
            stroke.setStrokeWidth(major ? R * 0.014f : R * 0.007f);
            stroke.setColor(lit ? accent : major ? 0xFFE8E8E8 : 0xFF7E848A);
            c.drawLine(px(deg, r0), py(deg, r0), px(deg, R * 0.855f), py(deg, R * 0.855f), stroke);
            if (major && numAlpha > 0) {
                text.setColor(alpha(0xFFC9CDD1, numAlpha));
                c.drawText(String.valueOf(Math.round(v)), px(deg, R * 0.64f),
                        py(deg, R * 0.64f) + text.getTextSize() * 0.35f, text);
            }
        }

        // lit arc + needle sweep
        float needleIn = phase(t, 1.4f, 1.7f);
        if (needleIn > 0) {
            float a = START + SWEEP * needleV / MAX;
            float arcR = R * 0.895f;
            oval.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR);
            if (a > START) {
                stroke.setStrokeWidth(R * 0.07f);
                stroke.setColor(alpha(accent, 0.15f));
                c.drawArc(oval, START, a - START, false, stroke);
                stroke.setStrokeWidth(R * 0.02f);
                stroke.setColor(accent);
                c.drawArc(oval, START, a - START, false, stroke);
            }
            c.save();
            c.rotate(a, cx, cy);
            stroke.setStrokeWidth(R * 0.035f);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setColor(alpha(accent, 0.35f * needleIn));
            c.drawLine(cx - R * 0.15f, cy, cx + R * 0.80f, cy, stroke);
            stroke.setStrokeWidth(R * 0.016f);
            stroke.setColor(alpha(accent, needleIn));
            c.drawLine(cx - R * 0.15f, cy, cx + R * 0.80f, cy, stroke);
            stroke.setStrokeWidth(R * 0.005f);
            stroke.setColor(alpha(0xFFFFFFFF, needleIn));
            c.drawLine(cx + R * 0.1f, cy, cx + R * 0.77f, cy, stroke);
            c.restore();
            fill.setColor(alpha(0xFFD0D4D8, needleIn));
            c.drawCircle(cx, cy, R * 0.08f, fill);
            fill.setColor(alpha(0xFF0A0B0D, needleIn));
            c.drawCircle(cx, cy, R * 0.05f, fill);

            text.setTypeface(thin);
            text.setTextSize(R * 0.26f);
            text.setColor(alpha(0xFFFFFFFF, needleIn));
            c.drawText(String.valueOf(Math.round(needleV)), cx, cy + R * 0.47f, text);
            text.setTypeface(cond);
            text.setTextSize(R * 0.07f);
            text.setColor(alpha(accent, needleIn));
            c.drawText("km/h", cx, cy + R * 0.60f, text);
        }
        if (!peaked && t >= 2.35f) {
            peaked = true;
            listener.onNeedlePeak();
        }

        // title with shine sweep
        float title = phase(t, 2.7f, 3.6f);
        float titleY = cy + R * 1.42f;
        if (title > 0) {
            text.setTypeface(light);
            text.setTextSize(R * 0.26f);
            text.setLetterSpacing(0.9f - 0.55f * easeOut(title));
            float tw = text.measureText("STAR DRIVE");
            float shine = phase(t, 3.1f, 4.0f);
            float sx = cx - tw / 2f + tw * 1.4f * shine - tw * 0.2f;
            text.setShader(new LinearGradient(sx - tw * 0.18f, 0, sx + tw * 0.18f, 0,
                    new int[]{0xFFA3A9AF, 0xFFFFFFFF, 0xFFA3A9AF}, null, Shader.TileMode.CLAMP));
            text.setAlpha((int) (255 * title));
            c.drawText("STAR DRIVE", cx, titleY, text);
            text.setShader(null);
            text.setAlpha(255);
        }
        float line = easeOut(phase(t, 3.0f, 3.7f));
        if (line > 0) {
            float lw = Math.min(w * 0.7f, R * 2.6f) * line;
            float ly = titleY + R * 0.12f;
            fill.setShader(new LinearGradient(cx - lw / 2, 0, cx + lw / 2, 0,
                    new int[]{0, accent, 0}, null, Shader.TileMode.CLAMP));
            c.drawRect(cx - lw / 2, ly, cx + lw / 2, ly + Math.max(2f, R * 0.008f), fill);
            fill.setShader(null);
        }
        float sub = phase(t, 3.3f, 4.0f);
        if (sub > 0) {
            text.setTypeface(cond);
            text.setTextSize(R * 0.075f);
            text.setLetterSpacing(0.35f);
            text.setColor(alpha(0xFF9AA3AD, sub));
            c.drawText("PERFORMANCE · LUXURY · PRECISION", cx, titleY + R * 0.28f, text);
            text.setLetterSpacing(0.2f);
            text.setTextSize(R * 0.08f);
            text.setColor(alpha(accent, sub));
            c.drawText("奔驰风格 · 数字仪表", cx, titleY + R * 0.42f, text);
            text.setLetterSpacing(0f);
        }

        // fade out
        float out = phase(t, 4.4f, TOTAL);
        if (out > 0) {
            fill.setColor(alpha(0xFF000000, out));
            c.drawRect(0, 0, w, h, fill);
        }

        if (t >= TOTAL) skip();
        else postInvalidateOnAnimation();
    }

    /** Classic cluster self-test sweep: up to full scale, then back to zero. */
    private float needleValue(float t) {
        if (t < 1.6f) return 0f;
        if (t < 2.35f) return MAX * easeOut(phase(t, 1.6f, 2.35f));
        return MAX * (1f - easeInOut(phase(t, 2.35f, 3.1f)));
    }
}
