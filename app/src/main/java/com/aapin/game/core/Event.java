package com.aapin.game.core;

/** 游戏逻辑产生的事件，界面据此播放音效和特效。 */
public final class Event {
    public static final int SHOOT = 0, STICK = 1, CRASH = 2, CLEAR = 3, TIMEUP = 4,
            ROUND_END = 5, MATCH_END = 6, BEEP = 7, GO = 8, OVER = 9;

    public final int type;
    public final int owner;
    /** 圆盘局部角度（STICK / CRASH）。 */
    public final float a;
    public final int value;

    public Event(int type, int owner, float a, int value) {
        this.type = type;
        this.owner = owner;
        this.a = a;
        this.value = value;
    }
}
