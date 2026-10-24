package com.aapin.game.core;

import java.util.Random;

/** 关卡配置。闯关模式 60 关按章节确定性生成；无尽/限时模式按盘数随机生成。 */
public final class Level {
    public static final int COUNT = 60;
    public static final String[] CHAPTERS = {"初出茅庐", "左右为难", "忽快忽慢", "急刹车", "幽灵之针", "登峰造极"};
    public static final String[] CHAPTER_TIPS = {
            "匀速旋转，熟悉节奏",
            "圆盘会周期性反向旋转",
            "转速时快时慢，抓住时机",
            "突然停顿，或每插一针就反转",
            "针会忽隐忽现，考验记忆",
            "各种机关混合，极限挑战"
    };

    public final int number;
    public final int shots;
    public final float[] pre;
    public final RotSpec rot;
    public final boolean ghost;

    private Level(int number, int shots, float[] pre, RotSpec rot, boolean ghost) {
        this.number = number;
        this.shots = shots;
        this.pre = pre;
        this.rot = rot;
        this.ghost = ghost;
    }

    public static int chapterOf(int n) {
        return (n - 1) / 10;
    }

    public static Level get(int n) {
        n = Math.max(1, Math.min(COUNT, n));
        int ch = chapterOf(n);
        int k = (n - 1) % 10;
        boolean boss = k == 9;
        Random r = new Random(n * 7919L + 13);
        int dir = r.nextBoolean() ? 1 : -1;
        int shots = 6 + k / 2 + ch + (boss ? 3 : 0);
        int pre = Math.min(2 + k / 3 + ch, 10);
        int type;
        float speed;
        float period = 2.4f;
        boolean ghost = false;
        switch (ch) {
            case 0:
                type = RotSpec.CONST;
                speed = 70 + k * 5;
                break;
            case 1:
                type = RotSpec.REVERSE;
                speed = 95 + k * 4;
                period = 2.6f - k * 0.08f;
                break;
            case 2:
                type = RotSpec.SINE;
                speed = 95 + k * 4;
                period = 3f - k * 0.1f;
                break;
            case 3:
                type = (k % 2 == 0) ? RotSpec.STOPGO : RotSpec.FLIP;
                speed = 100 + k * 4;
                period = 2.4f - k * 0.05f;
                break;
            case 4: {
                int[] t = {RotSpec.CONST, RotSpec.REVERSE, RotSpec.SINE, RotSpec.FLIP, RotSpec.STOPGO};
                type = t[k % t.length];
                speed = 95 + k * 4;
                ghost = true;
                break;
            }
            default: {
                int[] t = {RotSpec.WOBBLE, RotSpec.ACCEL, RotSpec.REVERSE, RotSpec.SINE, RotSpec.STOPGO,
                        RotSpec.FLIP, RotSpec.WOBBLE, RotSpec.ACCEL, RotSpec.SINE, RotSpec.REVERSE};
                type = t[k];
                speed = 120 + k * 5;
                period = 2.2f;
                ghost = boss || k == 4;
            }
        }
        if (type == RotSpec.ACCEL) {
            speed = 70 + ch * 5;
            period = 6f;
        }
        if (boss) speed += 15;
        return new Level(n, shots, Board.scatter(pre, r, 16f), new RotSpec(type, speed, period, dir), ghost);
    }

    /** 无尽 / 限时模式的第 s 盘（从 0 开始）。 */
    public static Level stage(int s, Random r) {
        int[] pool;
        if (s < 2) pool = new int[]{RotSpec.CONST};
        else if (s < 5) pool = new int[]{RotSpec.CONST, RotSpec.REVERSE, RotSpec.SINE};
        else pool = new int[]{RotSpec.CONST, RotSpec.REVERSE, RotSpec.SINE, RotSpec.STOPGO,
                RotSpec.FLIP, RotSpec.WOBBLE, RotSpec.ACCEL};
        int type = pool[r.nextInt(pool.length)];
        float speed = Math.min(80 + s * 8, 210);
        float period = 2.6f - Math.min(s, 10) * 0.06f;
        if (type == RotSpec.ACCEL) {
            speed = Math.min(70 + s * 3, 130);
            period = 6f;
        }
        int shots = Math.min(6 + s, 16);
        int pre = Math.min(1 + s / 2, 10);
        boolean ghost = s >= 8 && r.nextInt(4) == 0;
        return new Level(s + 1, shots, Board.scatter(pre, r, 16f),
                new RotSpec(type, speed, period, r.nextBoolean() ? 1 : -1), ghost);
    }
}
