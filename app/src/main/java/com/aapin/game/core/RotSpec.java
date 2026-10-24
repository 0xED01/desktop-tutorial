package com.aapin.game.core;

/** 圆盘旋转规则：根据时间和已插针数给出角速度（度/秒）。 */
public final class RotSpec {
    public static final int CONST = 0, REVERSE = 1, SINE = 2, STOPGO = 3, FLIP = 4, WOBBLE = 5, ACCEL = 6;
    public static final String[] NAMES = {"匀速", "反转", "变速", "急停", "逆转", "摇摆", "加速"};

    private static final float RAMP = 0.35f;

    public final int type;
    public final float base;
    /** 周期（秒）；ACCEL 类型下表示每插一针增加的速度。 */
    public final float period;
    public final int dir;

    public RotSpec(int type, float base, float period, int dir) {
        this.type = type;
        this.base = base;
        this.period = period;
        this.dir = dir >= 0 ? 1 : -1;
    }

    public String name() {
        return NAMES[type];
    }

    public float speed(float t, int placed) {
        float s;
        switch (type) {
            case REVERSE:
                s = base * reverseFactor(t);
                break;
            case SINE:
                s = base * (1f + 0.85f * (float) Math.sin(2 * Math.PI * t / period));
                break;
            case STOPGO:
                s = base * stopGoFactor((t % period) / period);
                break;
            case FLIP:
                s = (placed % 2 == 0) ? base : -base;
                break;
            case WOBBLE:
                s = base * (0.5f + 1.5f * (float) Math.sin(2 * Math.PI * t / period));
                break;
            case ACCEL:
                s = base + period * placed;
                break;
            default:
                s = base;
        }
        return s * dir;
    }

    private float reverseFactor(float t) {
        float ph = t % (2 * period);
        float target = ph < period ? 1f : -1f;
        float since = ph < period ? ph : ph - period;
        if (t > period && since < RAMP) {
            float k = smooth(since / RAMP);
            return -target + 2 * target * k;
        }
        return target;
    }

    private static float stopGoFactor(float ph) {
        if (ph < 0.55f) return 1.3f;
        if (ph < 0.65f) return lerp(1.3f, 0.05f, smooth((ph - 0.55f) / 0.1f));
        if (ph < 0.9f) return 0.05f;
        return lerp(0.05f, 1.3f, smooth((ph - 0.9f) / 0.1f));
    }

    private static float smooth(float k) {
        return k * k * (3 - 2 * k);
    }

    private static float lerp(float a, float b, float k) {
        return a + (b - a) * k;
    }
}
