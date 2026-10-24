package com.aapin.game;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.Random;

/** 粒子与飘字特效。 */
public final class Fx {
    private static final class P {
        float x, y, vx, vy, life, max, size;
        int color;
    }

    private static final class T {
        float x, y, life;
        String s;
        int color;
        float size;
        boolean flip;
    }

    private final ArrayList<P> ps = new ArrayList<>();
    private final ArrayList<T> ts = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random r = new Random();

    public Fx() {
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
    }

    public void burst(float x, float y, int color, int n, float speed, float size) {
        for (int i = 0; i < n; i++) {
            P p = new P();
            double a = r.nextDouble() * Math.PI * 2;
            float s = speed * (0.3f + r.nextFloat());
            p.x = x;
            p.y = y;
            p.vx = (float) Math.cos(a) * s;
            p.vy = (float) Math.sin(a) * s;
            p.max = p.life = 0.35f + r.nextFloat() * 0.45f;
            p.size = size * (0.5f + r.nextFloat());
            p.color = color;
            ps.add(p);
        }
    }

    public void confetti(float w, float h, int[] colors, int n) {
        for (int i = 0; i < n; i++) {
            P p = new P();
            p.x = r.nextFloat() * w;
            p.y = -r.nextFloat() * h * 0.3f;
            p.vx = (r.nextFloat() - 0.5f) * w * 0.2f;
            p.vy = h * (0.25f + r.nextFloat() * 0.35f);
            p.max = p.life = 1.6f + r.nextFloat();
            p.size = w * (0.006f + r.nextFloat() * 0.008f);
            p.color = colors[r.nextInt(colors.length)];
            ps.add(p);
        }
    }

    public void text(String s, float x, float y, int color, float size, boolean flip) {
        T t = new T();
        t.s = s;
        t.x = x;
        t.y = y;
        t.color = color;
        t.size = size;
        t.life = 0.9f;
        t.flip = flip;
        ts.add(t);
    }

    public void update(float dt) {
        for (int i = ps.size() - 1; i >= 0; i--) {
            P p = ps.get(i);
            p.life -= dt;
            if (p.life <= 0) {
                ps.remove(i);
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vx *= 1f - dt * 1.5f;
            p.vy *= 1f - dt * 1.5f;
        }
        for (int i = ts.size() - 1; i >= 0; i--) {
            T t = ts.get(i);
            t.life -= dt;
            t.y += (t.flip ? 1 : -1) * t.size * dt;
            if (t.life <= 0) ts.remove(i);
        }
    }

    public void draw(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        for (P p : ps) {
            paint.setColor(p.color);
            paint.setAlpha((int) (255 * Math.min(1f, p.life / p.max * 1.5f)));
            c.drawCircle(p.x, p.y, p.size, paint);
        }
        for (T t : ts) {
            paint.setColor(t.color);
            paint.setAlpha((int) (255 * Math.min(1f, t.life * 2f)));
            paint.setTextSize(t.size);
            if (t.flip) {
                c.save();
                c.rotate(180, t.x, t.y);
                c.drawText(t.s, t.x, t.y, paint);
                c.restore();
            } else {
                c.drawText(t.s, t.x, t.y, paint);
            }
        }
        paint.setAlpha(255);
    }

    public void clear() {
        ps.clear();
        ts.clear();
    }
}
