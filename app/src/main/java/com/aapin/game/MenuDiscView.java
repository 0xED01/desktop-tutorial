package com.aapin.game;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import com.aapin.game.core.Board;

/** 主菜单上装饰用的旋转圆盘。 */
public final class MenuDiscView extends View {
    private final Painter pt;
    private final Board board = new Board();
    private long lastNs;
    private float t;

    public MenuDiscView(Context c, Theme th) {
        super(c);
        pt = new Painter(th);
        for (int i = 0; i < 12; i++) board.add(i * 30f + (i % 3) * 6f, i % 4 == 0 ? 0 : i % 4 == 2 ? 1 : 2);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        pt.layout(w, h, 0.5f, 1f);
        float s = h / (pt.L * 2.3f);
        pt.layout(w, h, 0.5f, Math.min(1f, s));
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0 : Math.min(0.05f, (now - lastNs) / 1e9f);
        lastNs = now;
        t += dt;
        board.rot = Board.norm(board.rot + (60f + 50f * (float) Math.sin(t * 0.8f)) * dt);
        for (Board.Pin p : board.pins) {
            int col = p.owner == 0 ? pt.th.p1 : p.owner == 1 ? pt.th.p2 : pt.th.pin;
            pt.pin(c, p.a + board.rot, col, 255);
        }
        pt.disc(c, 0.03f * (float) Math.sin(t * 3));
        pt.discLabel(c, "针", 0.85f);
        postInvalidateOnAnimation();
    }
}
