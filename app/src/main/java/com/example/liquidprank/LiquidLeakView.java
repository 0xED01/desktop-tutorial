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
 * Draws an animated "LCD liquid leak" effect:
 *   - dark ink blots that grow outward from random seed points,
 *   - rainbow-coloured bleeding around each blot edge (classic panel damage),
 *   - thin vertical dead-pixel stripes,
 *   - a few spreading cracks.
 *
 * Pure canvas drawing, no bitmaps, so it scales to any screen size.
 */
public class LiquidLeakView extends View {

    /** One growing ink blot. */
    private static class Blot {
        float cx, cy;          // seed centre, in fractions of width/height (0..1)
        float maxRadius;       // final radius in px
        float growMs;          // time to reach maxRadius
        float startDelayMs;    // when this blot starts leaking
        int wobbleSeed;        // keeps the jagged edge stable per blot
    }

    private final Paint inkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bleedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stripePaint = new Paint();
    private final Paint crackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path blotPath = new Path();

    private final List<Blot> blots = new ArrayList<>();
    private final List<Float> stripes = new ArrayList<>();   // x fractions of dead-pixel columns
    private final List<Path> cracks = new ArrayList<>();

    private final Random random = new Random();
    private long startTime;

    private final int[] rainbow = {
            Color.rgb(255, 0, 90),
            Color.rgb(255, 180, 0),
            Color.rgb(0, 220, 120),
            Color.rgb(0, 160, 255),
            Color.rgb(150, 0, 255)
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
        inkPaint.setStyle(Paint.Style.FILL);
        inkPaint.setColor(Color.BLACK);

        bleedPaint.setStyle(Paint.Style.STROKE);

        stripePaint.setStyle(Paint.Style.FILL);

        crackPaint.setStyle(Paint.Style.STROKE);
        crackPaint.setColor(Color.argb(200, 20, 20, 20));
        crackPaint.setStrokeWidth(2f);

        startTime = System.currentTimeMillis();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        buildScene(w, h);
    }

    /** Lay out the blots, stripes and cracks once we know the screen size. */
    private void buildScene(int w, int h) {
        blots.clear();
        stripes.clear();
        cracks.clear();

        float diag = (float) Math.hypot(w, h);

        // 3-5 leak sources, a couple near the edges where panels usually fail first.
        int blotCount = 3 + random.nextInt(3);
        for (int i = 0; i < blotCount; i++) {
            Blot b = new Blot();
            b.cx = random.nextFloat();
            b.cy = random.nextFloat();
            b.maxRadius = diag * (0.35f + random.nextFloat() * 0.45f);
            b.growMs = 3500f + random.nextFloat() * 4000f;
            b.startDelayMs = random.nextFloat() * 1500f;
            b.wobbleSeed = random.nextInt();
            blots.add(b);
        }

        // Dead-pixel columns.
        int stripeCount = 4 + random.nextInt(5);
        for (int i = 0; i < stripeCount; i++) {
            stripes.add(random.nextFloat());
        }

        // A few branching cracks from a shared impact point.
        float ox = w * (0.3f + random.nextFloat() * 0.4f);
        float oy = h * (0.3f + random.nextFloat() * 0.4f);
        int crackCount = 3 + random.nextInt(4);
        for (int i = 0; i < crackCount; i++) {
            Path p = new Path();
            p.moveTo(ox, oy);
            float angle = (float) (random.nextFloat() * Math.PI * 2);
            float x = ox, y = oy;
            int segments = 4 + random.nextInt(4);
            for (int s = 0; s < segments; s++) {
                angle += (random.nextFloat() - 0.5f) * 0.8f;
                float len = diag * (0.06f + random.nextFloat() * 0.10f);
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

        long elapsed = System.currentTimeMillis() - startTime;

        // Base: screen already looks dead-ish / backlight uneven.
        canvas.drawColor(Color.rgb(8, 8, 10));

        // Dead-pixel columns (appear quickly).
        float stripeAlpha = Math.min(1f, elapsed / 1200f);
        for (float fx : stripes) {
            float x = fx * w;
            float colW = 2f + random.nextFloat() * 3f;
            int c = rainbow[(int) (fx * rainbow.length) % rainbow.length];
            stripePaint.setColor(Color.argb((int) (120 * stripeAlpha), Color.red(c), Color.green(c), Color.blue(c)));
            canvas.drawRect(x, 0, x + colW, h, stripePaint);
        }

        // Growing ink blots with rainbow bleed.
        for (Blot b : blots) {
            float t = (elapsed - b.startDelayMs) / b.growMs;
            if (t <= 0) continue;
            t = Math.min(1f, t);
            // ease-out so it rushes then settles, like liquid spreading.
            float eased = 1f - (1f - t) * (1f - t);
            float radius = b.maxRadius * eased;
            float cx = b.cx * w;
            float cy = b.cy * h;

            drawRainbowBleed(canvas, cx, cy, radius);
            drawBlot(canvas, cx, cy, radius, b.wobbleSeed);
        }

        // Cracks (fade in over the first couple of seconds).
        int crackAlpha = (int) (200 * Math.min(1f, elapsed / 2000f));
        crackPaint.setColor(Color.argb(crackAlpha, 15, 15, 18));
        for (Path p : cracks) {
            canvas.drawPath(p, crackPaint);
        }

        // Keep animating.
        postInvalidateOnAnimation();
    }

    private void drawRainbowBleed(Canvas canvas, float cx, float cy, float radius) {
        // Concentric coloured rings just outside the ink edge.
        for (int i = 0; i < rainbow.length; i++) {
            float r = radius + i * 14f;
            bleedPaint.setColor(Color.argb(70, Color.red(rainbow[i]), Color.green(rainbow[i]), Color.blue(rainbow[i])));
            bleedPaint.setStrokeWidth(10f);
            canvas.drawCircle(cx, cy, r, bleedPaint);
        }
        // Soft glow.
        RadialGradient glow = new RadialGradient(
                cx, cy, Math.max(1f, radius + 60f),
                new int[]{Color.argb(90, 0, 160, 255), Color.TRANSPARENT},
                new float[]{0.8f, 1f},
                Shader.TileMode.CLAMP);
        Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setShader(glow);
        canvas.drawCircle(cx, cy, radius + 60f, glowPaint);
    }

    private void drawBlot(Canvas canvas, float cx, float cy, float radius, int seed) {
        // Jagged, blobby outline so it reads as spilled liquid, not a plain circle.
        blotPath.reset();
        Random r = new Random(seed);
        int points = 24;
        for (int i = 0; i <= points; i++) {
            double ang = (Math.PI * 2 * i) / points;
            float wobble = 0.72f + r.nextFloat() * 0.45f;
            float rr = radius * wobble;
            float x = cx + (float) Math.cos(ang) * rr;
            float y = cy + (float) Math.sin(ang) * rr;
            if (i == 0) {
                blotPath.moveTo(x, y);
            } else {
                blotPath.lineTo(x, y);
            }
        }
        blotPath.close();
        canvas.drawPath(blotPath, inkPaint);
    }
}
