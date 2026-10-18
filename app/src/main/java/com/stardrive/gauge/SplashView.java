package com.stardrive.gauge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Startup animation: light streaks converge, the logo ring draws itself while a halo of
 * ticks lights up, the star spins into place, a light sweep crosses the emblem and the
 * Mercedes-Benz lettering fades in with a shine.
 */
public class SplashView extends View {
    public interface Listener {
        void onSplashDone();
        void onLogoLanded();
    }

    private static final float TOTAL = 5.0f;
    private static final int STREAKS = 70, HALO_TICKS = 72;

    private final int accent;
    private final Listener listener;
    private long t0;
    private boolean done, landed;

    private final StarLogo logo = new StarLogo();
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final float[] sAng = new float[STREAKS], sDelay = new float[STREAKS], sLen = new float[STREAKS];
    private final Typeface light, cond;
    private float cx, cy, R;

    public SplashView(Context c, int accent, Listener l) {
        super(c);
        this.accent = accent;
        this.listener = l;
        stroke.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
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
        R = Math.min(w * 0.2f, h * 0.22f);
        cx = w / 2f;
        cy = h * 0.38f;
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

    private static float easeOutBack(float p) {
        float c1 = 1.70158f, c3 = c1 + 1f, q = p - 1f;
        return 1 + c3 * q * q * q + c1 * q * q;
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

        // ambient glow behind the emblem
        float glow = phase(t, 0.3f, 1.6f) * (1f - 0.4f * phase(t, 3.2f, 4.2f));
        if (glow > 0) {
            fill.setShader(new RadialGradient(cx, cy, R * 3f,
                    new int[]{alpha(accent, 0.24f * glow), alpha(accent, 0.06f * glow), 0},
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
            float head = far * 0.7f + (R * 1.4f - far * 0.7f) * p * p;
            float tail = head + far * sLen[i] * (1 - p);
            stroke.setStrokeWidth(R * (i % 3 == 0 ? 0.014f : 0.007f));
            stroke.setColor(alpha(i % 4 == 0 ? 0xFFFFFFFF : accent, (float) Math.sin(Math.PI * p)));
            c.drawLine(px(sAng[i], head), py(sAng[i], head), px(sAng[i], tail), py(sAng[i], tail), stroke);
        }

        // halo of gauge ticks lighting up around the emblem
        float halo = phase(t, 0.9f, 2.0f);
        float haloFade = 1f - 0.5f * phase(t, 3.0f, 4.0f);
        stroke.setStrokeCap(Paint.Cap.BUTT);
        for (int i = 0; i < HALO_TICKS; i++) {
            float f = (float) i / HALO_TICKS;
            if (f > halo) break;
            float deg = -90f + 360f * f;
            boolean major = i % 6 == 0;
            float r0 = R * (major ? 1.22f : 1.27f), r1 = R * 1.34f;
            stroke.setStrokeWidth(R * (major ? 0.022f : 0.01f));
            float recent = clamp((halo - f) * 6f);
            int col = major ? 0xFFFFFFFF : accent;
            stroke.setColor(alpha(col, haloFade * (0.45f + 0.55f * (1f - recent))));
            c.drawLine(px(deg, r0), py(deg, r0), px(deg, r1), py(deg, r1), stroke);
        }

        // emblem: ring draws on, then the star spins in with a bounce
        float ringP = easeOut(phase(t, 0.4f, 1.4f));
        float starIn = phase(t, 1.3f, 2.2f);
        float starP = starIn > 0 ? easeOutBack(starIn) : 0f;
        float rot = (1f - easeOut(starIn)) * -240f;
        int logoAlpha = (int) (255 * clamp(t / 0.4f));
        logo.draw(c, cx, cy, R, ringP, starP, rot, logoAlpha);
        if (!landed && t >= 2.2f) {
            landed = true;
            listener.onLogoLanded();
        }

        // impact flash
        float flash = phase(t, 2.1f, 2.8f);
        if (flash > 0 && flash < 1) {
            stroke.setStrokeWidth(R * 0.05f * (1 - flash));
            stroke.setColor(alpha(0xFFFFFFFF, 0.8f * (1 - flash)));
            c.drawCircle(cx, cy, R * (1f + 0.6f * flash), stroke);
        }

        // light sweep across the emblem
        float sweep = phase(t, 2.3f, 3.1f);
        if (sweep > 0 && sweep < 1) {
            c.save();
            clip.reset();
            clip.addCircle(cx, cy, R, Path.Direction.CW);
            c.clipPath(clip);
            float sx = cx - R * 1.6f + R * 3.2f * sweep;
            fill.setShader(new LinearGradient(sx - R * 0.35f, cy - R, sx + R * 0.35f, cy + R,
                    new int[]{0, 0x99FFFFFF, 0}, null, Shader.TileMode.CLAMP));
            c.drawRect(cx - R, cy - R, cx + R, cy + R, fill);
            fill.setShader(null);
            c.restore();
        }

        // Mercedes-Benz lettering with shine
        float title = phase(t, 2.6f, 3.5f);
        float titleY = cy + R * 1.78f;
        if (title > 0) {
            text.setTypeface(light);
            text.setTextSize(R * 0.34f);
            text.setLetterSpacing(0.6f - 0.48f * easeOut(title));
            float tw = text.measureText("Mercedes-Benz");
            float shine = phase(t, 3.0f, 3.9f);
            float sx = cx - tw / 2f + tw * 1.4f * shine - tw * 0.2f;
            text.setShader(new LinearGradient(sx - tw * 0.18f, 0, sx + tw * 0.18f, 0,
                    new int[]{0xFFB4BAC0, 0xFFFFFFFF, 0xFFB4BAC0}, null, Shader.TileMode.CLAMP));
            text.setAlpha((int) (255 * title));
            c.drawText("Mercedes-Benz", cx, titleY, text);
            text.setShader(null);
            text.setAlpha(255);
        }
        float line = easeOut(phase(t, 3.0f, 3.7f));
        if (line > 0) {
            float lw = Math.min(w * 0.6f, R * 4.2f) * line;
            float ly = titleY + R * 0.16f;
            fill.setShader(new LinearGradient(cx - lw / 2, 0, cx + lw / 2, 0,
                    new int[]{0, accent, 0}, null, Shader.TileMode.CLAMP));
            c.drawRect(cx - lw / 2, ly, cx + lw / 2, ly + Math.max(2f, R * 0.01f), fill);
            fill.setShader(null);
        }
        float sub = phase(t, 3.3f, 4.0f);
        if (sub > 0) {
            text.setTypeface(cond);
            text.setTextSize(R * 0.1f);
            text.setLetterSpacing(0.35f);
            text.setColor(alpha(0xFF9AA3AD, sub));
            c.drawText("PERFORMANCE · LUXURY · PRECISION", cx, titleY + R * 0.38f, text);
            text.setLetterSpacing(0.2f);
            text.setTextSize(R * 0.11f);
            text.setColor(alpha(accent, sub));
            c.drawText("梅赛德斯-奔驰 · 数字仪表", cx, titleY + R * 0.56f, text);
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
}
