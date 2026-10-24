package com.aapin.game;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import com.aapin.game.core.Board;
import com.aapin.game.core.Event;
import com.aapin.game.core.Level;
import com.aapin.game.core.SoloGame;

import java.util.ArrayList;
import java.util.Random;

/** 单人模式画面：闯关 / 无尽 / 限时。 */
@SuppressLint("ViewConstructor")
public final class SoloView extends View {
    private static final int CRASH_COLOR = 0xFFE53935;

    private final GameActivity act;
    private final Theme th;
    private final Painter pt;
    private final Fx fx = new Fx();
    private final Random rnd = new Random();
    private final int mode;
    private SoloGame g;

    private long lastNs;
    private float clock, pulse, shake, flash;
    private boolean paused, newRecord;
    private int unflushedPins;
    private final ArrayList<Btn> buttons = new ArrayList<>();
    private Btn pressed;
    private final RectF pauseRect = new RectF();
    private final RectF tmp = new RectF();

    public SoloView(GameActivity act, int mode, int level) {
        super(act);
        this.act = act;
        this.mode = mode;
        this.th = Theme.current(act);
        this.pt = new Painter(th);
        g = new SoloGame(mode, level);
    }

    private String bestKey() {
        return mode == SoloGame.ENDLESS ? "endless" : "time";
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        pt.layout(w, h, 0.38f, 1f);
        float s = pt.u * 0.11f;
        pauseRect.set(w - s * 1.4f, h * 0.035f, w - s * 0.4f, h * 0.035f + s);
    }

    public void pause() {
        if (g.state == SoloGame.PLAYING) paused = true;
        flushStats();
    }

    /** 返回键：先暂停，暂停中再按则退出。 */
    public boolean onBack() {
        if (!paused && g.state == SoloGame.PLAYING) {
            paused = true;
            return true;
        }
        return false;
    }

    public void flushStats() {
        Prefs.addStat(getContext(), "pins", unflushedPins);
        unflushedPins = 0;
    }

    private void restart(int level) {
        flushStats();
        g = new SoloGame(mode, level);
        fx.clear();
        paused = false;
        newRecord = false;
        if (mode == SoloGame.LEVEL) Prefs.addStat(getContext(), "games", 1);
    }

    // ---------------- 主循环 ----------------

    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0 : Math.min(0.05f, (now - lastNs) / 1e9f);
        lastNs = now;
        if (!paused) {
            clock += dt;
            g.update(dt);
            handleEvents();
            fx.update(dt);
            pulse = Math.max(0, pulse - dt * 0.5f);
            shake = Math.max(0, shake - dt);
            flash = Math.max(0, flash - dt * 2f);
        }
        render(c);
        postInvalidateOnAnimation();
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
                    Sfx.buzz(12);
                    pulse = 0.05f;
                    unflushedPins++;
                    fx.burst(pt.px(sa, pt.L), pt.py(sa, pt.L), th.accent, 8, pt.u * 0.5f, pt.u * 0.008f);
                    if (e.value > 1) {
                        fx.text("连击 x" + g.combo + "  +" + e.value, pt.cx, pt.cy + pt.L + pt.gap * 0.5f,
                                th.accent, pt.u * 0.055f, false);
                    }
                    break;
                case Event.CRASH:
                    Sfx.play(Sfx.CRASH);
                    Sfx.buzz(220);
                    shake = 0.35f;
                    flash = 1f;
                    fx.burst(pt.px(sa, pt.L), pt.py(sa, pt.L), CRASH_COLOR, 30, pt.u * 0.9f, pt.u * 0.012f);
                    if (mode == SoloGame.TIME) {
                        fx.text("-5 秒", pt.cx, pt.cy - pt.L - pt.u * 0.05f, CRASH_COLOR, pt.u * 0.08f, false);
                    }
                    break;
                case Event.CLEAR:
                    if (mode == SoloGame.LEVEL) {
                        Sfx.play(Sfx.WIN);
                        Prefs.setStars(getContext(), g.levelNo, g.stars());
                        Prefs.unlock(getContext(), g.levelNo + 1);
                        fx.confetti(pt.w, pt.h, new int[]{th.accent, th.p1, th.p2, th.pin}, 90);
                    } else {
                        Sfx.play(Sfx.CLEAR);
                        fx.burst(pt.cx, pt.cy, th.accent, 40, pt.u * 1.2f, pt.u * 0.01f);
                        fx.text(mode == SoloGame.TIME ? "清盘！+5 秒" : "清盘！+" + (5 + e.value),
                                pt.cx, pt.cy - pt.L - pt.u * 0.05f, th.accent, pt.u * 0.08f, false);
                    }
                    break;
                case Event.OVER:
                case Event.TIMEUP:
                    newRecord = Prefs.submitBest(getContext(), bestKey(), g.score);
                    Prefs.addStat(getContext(), "games", 1);
                    Sfx.play(newRecord ? Sfx.WIN : Sfx.LOSE);
                    if (newRecord) fx.confetti(pt.w, pt.h, new int[]{th.accent, th.p1, th.p2}, 70);
                    flushStats();
                    break;
            }
        }
    }

    // ---------------- 绘制 ----------------

    private void render(Canvas c) {
        pt.background(c);
        c.save();
        if (shake > 0) c.translate((rnd.nextFloat() - 0.5f) * pt.u * 0.04f * shake / 0.35f,
                (rnd.nextFloat() - 0.5f) * pt.u * 0.04f * shake / 0.35f);
        drawBoard(c);
        c.restore();
        fx.draw(c);
        if (flash > 0) {
            pt.fill.setColor(CRASH_COLOR);
            pt.fill.setAlpha((int) (90 * flash));
            c.drawRect(0, 0, pt.w, pt.h, pt.fill);
            pt.fill.setAlpha(255);
        }
        drawHud(c);
        drawOverlay(c);
    }

    private void drawBoard(Canvas c) {
        Board b = g.board;
        for (Board.Pin p : b.pins) {
            int alpha = 255;
            if (g.level.ghost && p.age > 0.6f) {
                float k = 0.5f + 0.5f * (float) Math.sin(clock * 2.6f + p.a * 0.07f);
                alpha = (int) (12 + 243 * k * k);
            }
            int color = th.pin;
            if (p.owner == 0 && p.age < 0.3f) color = Painter.blend(th.accent, th.pin, p.age / 0.3f);
            pt.pin(c, p.a + b.rot, color, alpha);
        }
        if (g.crashLocal >= 0 && g.state != SoloGame.PLAYING) {
            pt.pin(c, g.crashLocal + b.rot, CRASH_COLOR, 255);
        }
        for (float[] f : g.flying) pt.flying(c, 1, f[0], th.pin);
        pt.disc(c, pulse);
        String label = mode == SoloGame.LEVEL ? String.valueOf(g.levelNo) : String.valueOf(g.score);
        pt.discLabel(c, label, label.length() > 2 ? 0.62f : 0.8f);
        if (g.state == SoloGame.PLAYING) pt.queue(c, 1, g.shotsLeft, th.pin, false);
    }

    private void drawHud(Canvas c) {
        float m = pt.u * 0.06f;
        float top = pt.h * 0.035f;
        float big = pt.u * 0.075f, small = pt.u * 0.042f;
        int sub = Painter.blend(th.text, th.bgTop, 0.4f);
        String mech = g.level.rot.name() + (g.level.ghost ? " · 幽灵" : "");
        if (mode == SoloGame.LEVEL) {
            pt.label(c, "第 " + g.levelNo + " 关", m, top + big, big, th.text, Paint.Align.LEFT);
            pt.label(c, Level.CHAPTERS[Level.chapterOf(g.levelNo)] + " · " + mech, m, top + big + small * 1.6f,
                    small, sub, Paint.Align.LEFT);
        } else if (mode == SoloGame.ENDLESS) {
            pt.label(c, "第 " + (g.stage + 1) + " 盘", m, top + big, big, th.text, Paint.Align.LEFT);
            pt.label(c, "最佳 " + Prefs.best(getContext(), "endless") + " · " + mech, m, top + big + small * 1.6f,
                    small, sub, Paint.Align.LEFT);
        } else {
            boolean low = g.timeLeft < 10f;
            String ts = String.format(java.util.Locale.US, "%.1f", g.timeLeft);
            pt.label(c, ts + " 秒", m, top + big, big, low ? CRASH_COLOR : th.text, Paint.Align.LEFT);
            pt.label(c, "最佳 " + Prefs.best(getContext(), "time") + " · " + mech, m, top + big + small * 1.6f,
                    small, sub, Paint.Align.LEFT);
            float bw = pt.w - m * 2, by = top + big + small * 2.6f;
            pt.fill.setColor(sub);
            pt.fill.setAlpha(60);
            tmp.set(m, by, m + bw, by + pt.u * 0.012f);
            c.drawRoundRect(tmp, 8, 8, pt.fill);
            pt.fill.setColor(low ? CRASH_COLOR : th.accent);
            pt.fill.setAlpha(255);
            tmp.right = m + bw * Math.min(1f, g.timeLeft / SoloGame.TIME_LIMIT);
            c.drawRoundRect(tmp, 8, 8, pt.fill);
        }
        // 暂停按钮
        pt.fill.setColor(th.text);
        pt.fill.setAlpha(40);
        c.drawCircle(pauseRect.centerX(), pauseRect.centerY(), pauseRect.width() / 2f, pt.fill);
        pt.fill.setAlpha(255);
        float bw2 = pauseRect.width() * 0.09f, bh = pauseRect.height() * 0.17f;
        pt.fill.setColor(th.text);
        c.drawRect(pauseRect.centerX() - bw2 * 2, pauseRect.centerY() - bh, pauseRect.centerX() - bw2,
                pauseRect.centerY() + bh, pt.fill);
        c.drawRect(pauseRect.centerX() + bw2, pauseRect.centerY() - bh, pauseRect.centerX() + bw2 * 2,
                pauseRect.centerY() + bh, pt.fill);

        if (g.state == SoloGame.PLAYING && g.stuck == 0 && g.t < 3f && !paused) {
            float a = 0.5f + 0.5f * (float) Math.sin(clock * 5);
            pt.label(c, "点击屏幕任意位置发射", pt.cx, pt.h * 0.95f, small,
                    Painter.blend(th.text, th.bgBottom, a * 0.6f), Paint.Align.CENTER);
        }
    }

    private void drawOverlay(Canvas c) {
        buttons.clear();
        String title = null;
        ArrayList<String> lines = new ArrayList<>();
        int stars = -1;
        final Theme t = th;
        if (paused) {
            title = "已暂停";
            buttons.add(new Btn("继续游戏", t.accent, () -> paused = false));
            buttons.add(new Btn("重新开始", t.p1, () -> restart(g.levelNo)));
            buttons.add(new Btn("返回菜单", 0xFF888888, act::finish));
        } else if (mode == SoloGame.LEVEL && g.state == SoloGame.CRASHED && g.stateTime > 0.45f) {
            title = "撞针了！";
            lines.add("第 " + g.levelNo + " 关 · 还差 " + (g.shotsLeft + 1) + " 针");
            buttons.add(new Btn("重试", t.accent, () -> {
                g.retry();
                fx.clear();
            }));
            buttons.add(new Btn("返回关卡", 0xFF888888, act::finish));
        } else if (mode == SoloGame.LEVEL && g.state == SoloGame.CLEARED && g.stateTime > 0.8f) {
            boolean last = g.levelNo >= Level.COUNT;
            title = last ? "恭喜全部通关！" : "第 " + g.levelNo + " 关 通过！";
            stars = g.stars();
            lines.add(g.fails == 0 ? "一次通过，完美！" : "失败 " + g.fails + " 次");
            if (!last) buttons.add(new Btn("下一关", t.accent, () -> restart(g.levelNo + 1)));
            buttons.add(new Btn("再玩一次", t.p1, () -> restart(g.levelNo)));
            buttons.add(new Btn("返回关卡", 0xFF888888, act::finish));
        } else if (g.state == SoloGame.OVER && g.stateTime > 0.7f) {
            title = mode == SoloGame.TIME ? "时间到！" : "游戏结束";
            lines.add("得分  " + g.score);
            lines.add(newRecord ? "新纪录！" : "最佳  " + Prefs.best(getContext(), bestKey()));
            lines.add("清盘 " + g.stage + " 个 · 最高连击 " + g.maxCombo);
            buttons.add(new Btn("再来一局", t.accent, () -> restart(0)));
            buttons.add(new Btn("返回菜单", 0xFF888888, act::finish));
        }
        if (title == null) return;

        pt.dim(c, 110);
        float pw = pt.w * 0.8f;
        float bh = pt.u * 0.13f, gapB = pt.u * 0.035f;
        float titleH = pt.u * 0.16f, lineH = pt.u * 0.075f, starH = stars >= 0 ? pt.u * 0.15f : 0;
        float ph = pt.u * 0.08f + titleH + starH + lines.size() * lineH + pt.u * 0.04f
                + buttons.size() * (bh + gapB) + pt.u * 0.04f;
        float left = (pt.w - pw) / 2f, top = (pt.h - ph) / 2f;
        tmp.set(left, top, left + pw, top + ph);
        pt.panel(c, tmp);
        float y = top + pt.u * 0.08f + titleH * 0.6f;
        int fg = th.dark() ? 0xFFFFFFFF : 0xFF222222;
        pt.label(c, title, pt.cx, y, pt.u * 0.085f, fg, Paint.Align.CENTER);
        y += titleH * 0.4f;
        if (stars >= 0) {
            float sz = pt.u * 0.12f;
            for (int i = 0; i < 3; i++) {
                pt.label(c, "★", pt.cx + (i - 1) * sz * 1.1f, y + sz, sz,
                        i < stars ? 0xFFFFC107 : 0x55888888, Paint.Align.CENTER);
            }
            y += starH;
        }
        for (String s : lines) {
            y += lineH;
            pt.label(c, s, pt.cx, y - lineH * 0.25f, pt.u * 0.05f, Painter.blend(fg, th.panel, 0.25f),
                    Paint.Align.CENTER);
        }
        y += pt.u * 0.04f;
        for (Btn b : buttons) {
            b.r.set(left + pw * 0.12f, y, left + pw * 0.88f, y + bh);
            pt.button(c, b.r, b.label, b.color, b == pressed);
            y += bh + gapB;
        }
    }

    // ---------------- 触摸 ----------------

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
            float x = e.getX(e.getActionIndex()), y = e.getY(e.getActionIndex());
            if (!buttons.isEmpty()) {
                for (Btn b : buttons) if (b.r.contains(x, y)) pressed = b;
                return true;
            }
            if (pauseRect.contains(x, y) || (x > pauseRect.left - pt.u * 0.05f && y < pauseRect.bottom + pt.u * 0.05f)) {
                if (g.state == SoloGame.PLAYING) {
                    Sfx.play(Sfx.CLICK);
                    paused = true;
                }
                return true;
            }
            g.shoot();
        } else if (a == MotionEvent.ACTION_UP) {
            Btn p = pressed;
            pressed = null;
            if (p != null && p.r.contains(e.getX(), e.getY()) && buttons.contains(p)) {
                Sfx.play(Sfx.CLICK);
                p.action.run();
            }
        } else if (a == MotionEvent.ACTION_CANCEL) {
            pressed = null;
        }
        return true;
    }
}
