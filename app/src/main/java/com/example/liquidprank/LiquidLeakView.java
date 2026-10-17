package com.example.liquidprank;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Animated "broken LCD / liquid leak" effect, tuned to be obviously visible:
 *   - bright backlight-bleed blooms so the screen never looks merely "off",
 *   - dark ink blots spreading from random points, ringed by vivid neon bleed,
 *   - bright vertical dead-pixel stripes,
 *   - torn horizontal glitch bands,
 *   - bright cracks, plus an overall flicker.
 *
 * Pure Canvas drawing, scales to any screen size.
 */
public class LiquidLeakView extends View {

    private static class Blot {
        float cx, cy;          // seed centre (fractions 0..1)
        float maxRadius;       // final radius (px)
        float growMs;
        float startDelayMs;
        int wobbleSeed;
        int bleedColor;        // vivid ring colour
    }

    private static class Stripe {
        float x;               // px
        float width;           // px
        int color;
    }

    private static class GlitchBand {
        float y;               // fraction
        float height;          // fraction
        int color;
        float startMs;
    }

    private static class Bloom {
        float cx, cy;          // fractions
        float radius;          // px
        int color;
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint plain = new Paint();
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path blotPath = new Path();

    private final List<Blot> blots = new ArrayList<>();
    private final List<Stripe> stripes = new ArrayList<>();
    private final List<GlitchBand> bands = new ArrayList<>();
    private final List<Bloom> blooms = new ArrayList<>();
    private final List<Path> cracks = new ArrayList<>();

    private final Random random = new Random();
    private long startTime;

    // Vivid neon palette for bleeding / stripes / glitches.
    private final int[] neon = {
            Color.rgb(255, 40, 120),   // hot pink
            Color.rgb(255, 150, 0),    // amber
            Color.rgb(40, 255, 160),   // green
            Color.rgb(0, 200, 255),    // cyan
            Color.rgb(180, 60, 255),   // purple
            Color.rgb(255, 255, 255)   // blown-out white
    };

    public LiquidLeakView(Context context) {
        super(context);
        init();
    }

    public LiquidLeakView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        plain.setStyle(Paint.Style.FILL);
        startTime = System.currentTimeMillis();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        buildScene(w, h);
    }

    private int neon(int i) {
        return neon[((i % neon.length) + neon.length) % neon.length];
    }

    private void buildScene(int w, int h) {
        blots.clear();
        stripes.clear();
        bands.clear();
        blooms.clear();
        cracks.clear();

        float diag = (float) Math.hypot(w, h);

        // Bright backlight blooms - visible from the very first frame.
        int bloomCount = 3 + random.nextInt(3);
        for (int i = 0; i < bloomCount; i++) {
            Bloom b = new Bloom();
            b.cx = random.nextFloat();
            b.cy = random.nextFloat();
            b.radius = diag * (0.18f + random.nextFloat() * 0.25f);
            b.color = neon(random.nextInt(neon.length));
            blooms.add(b);
        }

        // Ink blots with vivid ring colour.
        int blotCount = 3 + random.nextInt(3);
        for (int i = 0; i < blotCount; i++) {
            Blot b = new Blot();
            b.cx = random.nextFloat();
            b.cy = random.nextFloat();
            b.maxRadius = diag * (0.30f + random.nextFloat() * 0.40f);
            b.growMs = 2500f + random.nextFloat() * 3000f;
            b.startDelayMs = random.nextFloat() * 700f;
            b.wobbleSeed = random.nextInt();
            b.bleedColor = neon(i);
            blots.add(b);
        }

        // Bright dead-pixel columns.
        int stripeCount = 6 + random.nextInt(6);
        for (int i = 0; i < stripeCount; i++) {
            Stripe s = new Stripe();
            s.x = random.nextFloat() * w;
            s.width = 2f + random.nextFloat() * 6f;
            s.color = neon(random.nextInt(neon.length));
            stripes.add(s);
        }

        // Torn horizontal glitch bands.
        int bandCount = 5 + random.nextInt(5);
        for (int i = 0; i < bandCount; i++) {
            GlitchBand g = new GlitchBand();
            g.y = random.nextFloat();
            g.height = 0.01f + random.nextFloat() * 0.05f;
            g.color = neon(random.nextInt(neon.length));
            g.startMs = random.nextFloat() * 1500f;
            bands.add(g);
        }

        // Bright branching cracks from one impact point.
        float ox = w * (0.3f + random.nextFloat() * 0.4f);
        float oy = h * (0.3f + random.nextFloat() * 0.4f);
        int crackCount = 4 + random.nextInt(4);
        for (int i = 0; i < crackCount; i++) {
            Path p = new Path();
            p.moveTo(ox, oy);
            float angle = (float) (random.nextFloat() * Math.PI * 2);
            float x = ox, y = oy;
            int segments = 4 + random.nextInt(5);
            for (int s = 0; s < segments; s++) {
                angle += (random.nextFloat() - 0.5f) * 0.9f;
                float len = diag * (0.05f + random.nextFloat() * 0.10f);
                x += (float) Math.cos(angle) * len;
                y += (float) Math.sin(angle) * len;
                p.lineTo(x, y);
            }
            cracks.add(p);
        }

        startTime = System.currentTimeMillis();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        if (blooms.isEmpty() && stripes.isEmpty()) {
            // Scene not built yet (e.g. onSizeChanged not delivered): build now.
            buildScene(w, h);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        float secs = elapsed / 1000f;

        // Dark, slightly bluish base - clearly a damaged panel, not an off screen.
        canvas.drawColor(Color.rgb(16, 14, 22));

        // Bright backlight blooms (pulsing) - immediate "something is very wrong".
        for (int i = 0; i < blooms.size(); i++) {
            Bloom b = blooms.get(i);
            float pulse = 0.6f + 0.4f * (float) Math.sin(secs * 3f + i);
            int a = (int) (150 * pulse);
            RadialGradient g = new RadialGradient(
                    b.cx * w, b.cy * h, Math.max(1f, b.radius),
                    new int[]{Color.argb(a, Color.red(b.color), Color.green(b.color), Color.blue(b.color)),
                            Color.TRANSPARENT},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP);
            glowPaint.setShader(g);
            canvas.drawCircle(b.cx * w, b.cy * h, b.radius, glowPaint);
        }
        glowPaint.setShader(null);

        // Bright vertical dead-pixel stripes (fade in within ~0.4s).
        float stripeAlpha = Math.min(1f, elapsed / 400f);
        for (Stripe s : stripes) {
            plain.setColor(Color.argb((int) (190 * stripeAlpha),
                    Color.red(s.color), Color.green(s.color), Color.blue(s.color)));
            canvas.drawRect(s.x, 0, s.x + s.width, h, plain);
        }

        // Torn horizontal glitch bands, jittering horizontally.
        for (int i = 0; i < bands.size(); i++) {
            GlitchBand g = bands.get(i);
            if (elapsed < g.startMs) continue;
            float jitter = (float) Math.sin(secs * 9f + i * 1.7f) * w * 0.04f;
            float top = g.y * h;
            float bot = top + g.height * h;
            plain.setColor(Color.argb(170, Color.red(g.color), Color.green(g.color), Color.blue(g.color)));
            canvas.drawRect(jitter, top, w + jitter, bot, plain);
        }

        // Growing ink blots with thick neon bleed.
        for (Blot b : blots) {
            float t = (elapsed - b.startDelayMs) / b.growMs;
            if (t <= 0) continue;
            t = Math.min(1f, t);
            float eased = 1f - (1f - t) * (1f - t);
            float radius = b.maxRadius * eased;
            float cx = b.cx * w;
            float cy = b.cy * h;

            // Vivid bleed rings just outside the ink edge.
            for (int i = 0; i < 6; i++) {
                int c = neon((b.wobbleSeed + i));
                stroke.setStrokeWidth(12f);
                stroke.setColor(Color.argb(120, Color.red(c), Color.green(c), Color.blue(c)));
                canvas.drawCircle(cx, cy, radius + i * 12f, stroke);
            }
            // Dark ink body.
            drawBlot(canvas, cx, cy, radius, b.wobbleSeed);
        }

        // Bright cracks fade in.
        int crackAlpha = (int) (230 * Math.min(1f, elapsed / 1500f));
        stroke.setStrokeWidth(2.5f);
        stroke.setColor(Color.argb(crackAlpha, 220, 230, 255));
        for (Path p : cracks) {
            canvas.drawPath(p, stroke);
        }

        // Occasional whole-screen flicker.
        float flick = (float) Math.sin(secs * 23f);
        if (flick > 0.85f) {
            canvas.drawColor(Color.argb(40, 255, 255, 255));
        } else if (flick < -0.9f) {
            canvas.drawColor(Color.argb(60, 0, 0, 0));
        }

        postInvalidateOnAnimation();
    }

    private void drawBlot(Canvas canvas, float cx, float cy, float radius, int seed) {
        blotPath.reset();
        Random r = new Random(seed);
        int points = 24;
        for (int i = 0; i <= points; i++) {
            double ang = (Math.PI * 2 * i) / points;
            float wobble = 0.72f + r.nextFloat() * 0.45f;
            float rr = radius * wobble;
            float x = cx + (float) Math.cos(ang) * rr;
            float y = cy + (float) Math.sin(ang) * rr;
            if (i == 0) blotPath.moveTo(x, y);
            else blotPath.lineTo(x, y);
        }
        blotPath.close();
        fill.setColor(Color.rgb(6, 4, 10));
        canvas.drawPath(blotPath, fill);
    }
}
