package com.aapin.game;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import com.aapin.game.core.Board;
import com.aapin.game.core.Event;
import com.aapin.game.core.RotSpec;
import com.aapin.game.core.VersusGame;
import com.aapin.game.net.NetLink;

import java.util.Random;

/**
 * 双人对战画面。
 * LOCAL：同一台手机，下方蓝方、上方红方。
 * HOST：局域网主机，运行游戏逻辑并广播状态，本机为下方玩家 0。
 * CLIENT：局域网客户端，只渲染主机状态，本机为玩家 1，画面旋转 180° 使自己位于下方。
 */
@SuppressLint("ViewConstructor")
public final class VersusView extends View {
    public static final int LOCAL = 0, HOST = 1, CLIENT = 2;
    private static final int CRASH_COLOR = 0xFFE53935;

    private final GameActivity act;
    private final int net;
    private final NetLink link;
    private final Theme th;
    private final Painter pt;
    private final Fx fx = new Fx();
    /** 屏幕坐标特效（不随客户端画面旋转）。 */
    private final Fx fxUi = new Fx();
    private final VersusGame g = new VersusGame();
    private final Random rnd = new Random();
    private final String myName;

    private long lastNs;
    private float clock, sendAcc, pulse, shake, discTimer;
    private boolean disconnected;

    public VersusView(GameActivity act, int net, NetLink link) {
        super(act);
        this.act = act;
        this.net = net;
        this.link = link;
        this.th = Theme.current(act);
        this.pt = new Painter(th);
        this.myName = Prefs.nick(act);
        if (net != CLIENT) g.newMatch();
    }

    private int me() {
        return net == CLIENT ? 1 : 0;
    }

    private boolean flipped() {
        return net == CLIENT;
    }

    private int color(int owner) {
        return owner == 0 ? th.p1 : owner == 1 ? th.p2 : th.pin;
    }

    private String name(int o) {
        if (net == LOCAL) return o == 0 ? "蓝方" : "红方";
        if (o == me()) return "你";
        return link != null ? link.peerName : "对手";
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        pt.layout(w, h, 0.5f, 0.92f);
        pt.safeTop = Painter.safeTop(this);
    }

    public void leave() {
        if (link != null) link.bye();
    }

    // ---------------- 主循环 ----------------

    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0 : Math.min(0.05f, (now - lastNs) / 1e9f);
        lastNs = now;
        clock += dt;
        step(dt);
        handleEvents();
        fx.update(dt);
        fxUi.update(dt);
        pulse = Math.max(0, pulse - dt * 0.5f);
        shake = Math.max(0, shake - dt);
        render(c);
        postInvalidateOnAnimation();
    }

    private void step(float dt) {
        if (net == CLIENT) {
            String latest = null, m;
            while ((m = link.poll()) != null) {
                if (m.startsWith("S|")) latest = m;
            }
            if (latest != null) g.decode(latest);
            else g.extrapolate(dt);
        } else {
            if (net == HOST) {
                String m;
                while ((m = link.poll()) != null) {
                    if ("SHOOT".equals(m)) g.shoot(1);
                    else if ("READY".equals(m)) g.setReady(1);
                }
            }
            if (!disconnected) g.update(dt);
            if (net == HOST) {
                sendAcc += dt;
                if (sendAcc >= 1f / 30f) {
                    sendAcc = 0;
                    link.send(g.encode());
                }
            }
        }
        if (link != null && link.isClosed() && !disconnected) {
            disconnected = true;
            Sfx.play(Sfx.LOSE);
        }
        if (disconnected) discTimer += dt;
    }

    private void handleEvents() {
        Event e;
        while ((e = g.events.poll()) != null) {
            float sa = e.a + g.board.rot;
            switch (e.type) {
                case Event.SHOOT:
                    Sfx.play(Sfx.SHOOT);
                    break;
                case Event.STICK:
                    Sfx.play(Sfx.STICK);
                    if (net == LOCAL || e.owner == me()) Sfx.buzz(12);
                    pulse = 0.04f;
                    fx.burst(pt.px(sa, pt.L), pt.py(sa, pt.L), color(e.owner), 8, pt.u * 0.5f, pt.u * 0.008f);
                    if (net == LOCAL || e.owner == me()) Prefs.addStat(getContext(), "pins", 1);
                    break;
                case Event.CRASH:
                    Sfx.play(Sfx.CRASH);
                    Sfx.buzz(200);
                    shake = 0.35f;
                    fx.burst(pt.px(sa, pt.L), pt.py(sa, pt.L), CRASH_COLOR, 30, pt.u * 0.9f, pt.u * 0.012f);
                    break;
                case Event.ROUND_END:
                    if (e.value == VersusGame.REASON_FINISH) Sfx.play(Sfx.CLEAR);
                    break;
                case Event.MATCH_END: {
                    boolean good = net == LOCAL || e.owner == me();
                    Sfx.play(good ? Sfx.WIN : Sfx.LOSE);
                    if (good) fxUi.confetti(pt.w, pt.h, new int[]{th.p1, th.p2, th.accent}, 90);
                    if (net != LOCAL) {
                        Prefs.addStat(getContext(), "lanGames", 1);
                        if (e.owner == me()) Prefs.addStat(getContext(), "lanWins", 1);
                    }
                    break;
                }
                case Event.BEEP:
                    Sfx.play(Sfx.BEEP);
                    break;
                case Event.GO:
                    Sfx.play(Sfx.GO);
                    break;
            }
        }
    }

    // ---------------- 绘制 ----------------

    private void render(Canvas c) {
        pt.background(c);
        // 双方半场淡色底
        pt.fill.setColor(color(flipped() ? 1 : 0));
        pt.fill.setAlpha(18);
        c.drawRect(0, pt.cy, pt.w, pt.h, pt.fill);
        pt.fill.setColor(color(flipped() ? 0 : 1));
        pt.fill.setAlpha(18);
        c.drawRect(0, 0, pt.w, pt.cy, pt.fill);
        pt.fill.setAlpha(255);

        if (net == CLIENT && !g.synced) {
            pt.disc(c, 0);
            pt.label(c, "等待主机开始…", pt.cx, pt.cy + pt.L, pt.u * 0.06f, th.text, Paint.Align.CENTER);
            drawDisconnected(c);
            return;
        }

        c.save();
        if (shake > 0) c.translate((rnd.nextFloat() - 0.5f) * pt.u * 0.04f * shake / 0.35f,
                (rnd.nextFloat() - 0.5f) * pt.u * 0.04f * shake / 0.35f);
        if (flipped()) c.rotate(180, pt.cx, pt.cy);
        Board b = g.board;
        for (Board.Pin p : b.pins) {
            int col = color(p.owner);
            if (p.age < 0.3f) col = Painter.blend(0xFFFFFFFF, col, Math.min(1f, p.age / 0.3f + 0.4f));
            pt.pin(c, p.a + b.rot, col, 255);
        }
        if (g.crashOwner >= 0 && g.phase >= VersusGame.ROUND_OVER) {
            pt.pin(c, g.crashLocal + b.rot, CRASH_COLOR, 255);
        }
        for (float[] f : g.flying) {
            int o = (int) f[0];
            pt.flying(c, o == 0 ? 1 : -1, f[1], color(o));
        }
        pt.disc(c, pulse);
        if (g.phase == VersusGame.PLAYING || g.phase == VersusGame.COUNTDOWN) {
            pt.queue(c, 1, g.left[0], th.p1, false);
            pt.queue(c, -1, g.left[1], th.p2, true);
        }
        fx.draw(c);
        c.restore();

        fxUi.draw(c);
        drawDiscText(c);
        drawHud(c);
        drawBanner(c);
        drawDisconnected(c);
    }

    private void drawDiscText(Canvas c) {
        if (g.phase == VersusGame.COUNTDOWN) {
            int n = Math.max(1, 3 - (int) g.phaseTime);
            float k = g.phaseTime % 1f;
            pt.text.setColor(th.discText);
            pt.text.setTextSize(pt.discR * (1.1f - k * 0.3f));
            float y = pt.cy - (pt.text.descent() + pt.text.ascent()) / 2f;
            c.drawText(String.valueOf(n), pt.cx, y, pt.text);
        } else {
            pt.discLabel(c, g.score[0] + ":" + g.score[1], 0.55f);
        }
    }

    /** 在屏幕下方（或上方倒转）绘制一名玩家的信息栏。 */
    private void drawPlayerBar(Canvas c, int owner, boolean top, boolean rotate) {
        float y = top ? Math.max(pt.h * 0.05f, pt.safeTop + pt.h * 0.015f) : pt.h * 0.95f;
        if (rotate) {
            c.save();
            c.rotate(180, pt.cx, y);
        }
        float size = pt.u * 0.055f;
        float m = pt.u * 0.06f;
        boolean flipCoords = top && !rotate;
        float base = flipCoords ? y + size * 0.9f : y;
        pt.label(c, name(owner), m, base, size, color(owner), Paint.Align.LEFT);
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < VersusGame.WIN_ROUNDS; i++) dots.append(i < g.score[owner] ? "●" : "○");
        pt.label(c, dots.toString(), pt.w - m, base, size, color(owner), Paint.Align.RIGHT);
        if (rotate) c.restore();
    }

    private void drawHud(Canvas c) {
        int bottom = flipped() ? 1 : 0;
        drawPlayerBar(c, bottom, false, false);
        drawPlayerBar(c, 1 - bottom, true, net == LOCAL);
        // 局数与机关
        if (g.phase != VersusGame.MATCH_OVER) {
            String info = "第 " + g.round + " 局 · " + RotSpec.NAMES[g.rotType];
            int sub = Painter.blend(th.text, th.bgTop, 0.45f);
            float size = pt.u * 0.038f;
            pt.label(c, info, pt.w - pt.u * 0.06f, pt.cy + size * 0.35f, size, sub, Paint.Align.RIGHT);
        }
    }

    private void banner(Canvas c, String big, String small, int color, boolean top, boolean rotate) {
        float y = top ? pt.h * 0.17f : pt.h * 0.83f;
        if (rotate) {
            c.save();
            c.rotate(180, pt.cx, y);
        }
        pt.label(c, big, pt.cx, y, pt.u * 0.085f, color, Paint.Align.CENTER);
        if (small != null) {
            pt.label(c, small, pt.cx, y + pt.u * 0.08f, pt.u * 0.045f,
                    Painter.blend(th.text, th.bgTop, 0.2f), Paint.Align.CENTER);
        }
        if (rotate) c.restore();
    }

    private void drawBanner(Canvas c) {
        if (g.phase == VersusGame.ROUND_OVER && g.winner >= 0) {
            String why;
            if (g.reason == VersusGame.REASON_CRASH) why = name(1 - g.winner) + "撞针了！";
            else why = name(g.winner) + "率先插完！";
            if (net == LOCAL) {
                banner(c, name(g.winner) + "赢下本局", why, color(g.winner), false, false);
                banner(c, name(g.winner) + "赢下本局", why, color(g.winner), true, true);
            } else {
                boolean win = g.winner == me();
                banner(c, win ? "你赢下本局！" : "对手赢下本局", why, win ? th.accent : th.text, false, false);
            }
        } else if (g.phase == VersusGame.MATCH_OVER) {
            int w = g.score[0] > g.score[1] ? 0 : 1;
            float blink = 0.55f + 0.45f * (float) Math.sin(clock * 4);
            String score = g.score[0] + " : " + g.score[1];
            if (net == LOCAL) {
                for (int o = 0; o < 2; o++) {
                    boolean top = o == 1;
                    String big = o == w ? "你赢了！" : "你输了";
                    String small = score + "   " + (g.ready[o] ? "已准备，等待对方" : "点击这里再来一局");
                    banner(c, big, small, o == w ? color(o) : th.text, top, top);
                }
            } else {
                boolean win = w == me();
                String small = score + "   " + (g.ready[me()] ? "已准备，等待对方…" : "点击屏幕再来一局");
                banner(c, win ? "你赢了！" : "你输了", small, win ? th.accent : th.text, false, false);
                if (g.ready[1 - me()]) {
                    pt.label(c, "对手已准备", pt.cx, pt.h * 0.2f, pt.u * 0.05f,
                            Painter.blend(th.text, th.bgTop, blink * 0.5f), Paint.Align.CENTER);
                }
            }
        } else if (g.phase == VersusGame.COUNTDOWN && g.round == 1) {
            String tip = "撞针判负 · 先插完获胜 · 先赢 3 局";
            if (net == LOCAL) {
                banner(c, "点击自己半场发射", tip, th.text, false, false);
                banner(c, "点击自己半场发射", tip, th.text, true, true);
            } else {
                banner(c, "点击屏幕发射", tip, th.text, false, false);
            }
        }
    }

    private void drawDisconnected(Canvas c) {
        if (!disconnected) return;
        pt.dim(c, 150);
        pt.label(c, "连接已断开", pt.cx, pt.h * 0.47f, pt.u * 0.09f, 0xFFFFFFFF, Paint.Align.CENTER);
        pt.label(c, "点击屏幕返回", pt.cx, pt.h * 0.47f + pt.u * 0.1f, pt.u * 0.05f, 0xCCFFFFFF, Paint.Align.CENTER);
    }

    // ---------------- 触摸 ----------------

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a != MotionEvent.ACTION_DOWN && a != MotionEvent.ACTION_POINTER_DOWN) return true;
        if (disconnected) {
            if (discTimer > 0.5f) act.finish();
            return true;
        }
        float y = e.getY(e.getActionIndex());
        int player = net == LOCAL ? (y > pt.cy ? 0 : 1) : me();
        if (net == CLIENT) {
            if (g.phase == VersusGame.MATCH_OVER) link.send("READY");
            else link.send("SHOOT");
        } else {
            if (g.phase == VersusGame.MATCH_OVER) {
                if (g.phaseTime > 0.8f) {
                    if (!g.ready[player]) Sfx.play(Sfx.CLICK);
                    g.setReady(player);
                }
            } else {
                g.shoot(player);
            }
        }
        return true;
    }
}
