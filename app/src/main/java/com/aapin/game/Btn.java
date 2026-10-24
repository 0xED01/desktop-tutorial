package com.aapin.game;

import android.graphics.RectF;

/** 画布上绘制的按钮。 */
final class Btn {
    final RectF r = new RectF();
    final String label;
    final int color;
    final Runnable action;

    Btn(String label, int color, Runnable action) {
        this.label = label;
        this.color = color;
        this.action = action;
    }
}
