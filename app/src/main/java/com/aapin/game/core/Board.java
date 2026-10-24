package com.aapin.game.core;

import java.util.ArrayList;
import java.util.Random;

/** 圆盘与已插入的针。角度单位为度，屏幕坐标系（0 度向右，90 度向下）。 */
public final class Board {
    /** 两根针头恰好相切时的夹角，绘制时据此计算针头半径。 */
    public static final float MIN_SEP = 7f;
    /** 判定碰撞的夹角，略小于 MIN_SEP 留一点容错。 */
    public static final float HIT_SEP = 6.5f;

    public static final int NEUTRAL = 2;

    public static final class Pin {
        public final float a;
        public final int owner;
        public float age;

        Pin(float a, int owner) {
            this.a = a;
            this.owner = owner;
        }
    }

    public float rot;
    public final ArrayList<Pin> pins = new ArrayList<>();

    public void clear() {
        pins.clear();
    }

    public Pin add(float local, int owner) {
        Pin p = new Pin(norm(local), owner);
        pins.add(p);
        return p;
    }

    public void age(float dt) {
        for (Pin p : pins) p.age += dt;
    }

    public float localFor(float screenAngle) {
        return norm(screenAngle - rot);
    }

    public boolean hits(float local) {
        return nearest(local) < HIT_SEP;
    }

    public float nearest(float local) {
        float best = 360f;
        for (Pin p : pins) best = Math.min(best, diff(p.a, local));
        return best;
    }

    public static float norm(float a) {
        a %= 360f;
        if (a < 0) a += 360f;
        return a;
    }

    public static float diff(float a, float b) {
        float d = norm(a - b);
        return d > 180f ? 360f - d : d;
    }

    /** 随机生成 n 个相互间隔至少 minGap 度的角度。 */
    public static float[] scatter(int n, Random r, float minGap) {
        float[] out = new float[n];
        int count = 0;
        for (int tries = 0; count < n && tries < 4000; tries++) {
            float a = r.nextFloat() * 360f;
            boolean ok = true;
            for (int i = 0; i < count; i++) {
                if (diff(out[i], a) < minGap) {
                    ok = false;
                    break;
                }
            }
            if (ok) out[count++] = a;
        }
        if (count < n) {
            // 退化情况：均匀分布
            for (int i = 0; i < n; i++) out[i] = 360f * i / n;
        }
        return out;
    }
}
