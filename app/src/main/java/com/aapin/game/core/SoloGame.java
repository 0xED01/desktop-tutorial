package com.aapin.game.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Random;

/** 单人游戏逻辑：闯关、无尽、限时三种模式。 */
public final class SoloGame {
    public static final int LEVEL = 0, ENDLESS = 1, TIME = 2;
    public static final int PLAYING = 0, CRASHED = 1, CLEARED = 2, OVER = 3;
    public static final float FLY_TIME = 0.09f;
    public static final float SHOOT_ANGLE = 90f;
    public static final float TIME_LIMIT = 60f;

    public final int mode;
    public final Board board = new Board();
    public final ArrayList<float[]> flying = new ArrayList<>();
    public final ArrayDeque<Event> events = new ArrayDeque<>();

    public Level level;
    public int levelNo;
    public int state;
    public float t, stateTime;
    public int shotsLeft, score, stage, combo, maxCombo, fails, stuck;
    public float timeLeft;
    public float crashLocal = -1;

    private float lastStickT = -10, lastShotT = -10;
    private final Random rnd = new Random();

    public SoloGame(int mode, int levelNo) {
        this.mode = mode;
        this.levelNo = levelNo;
        if (mode == TIME) timeLeft = TIME_LIMIT;
        setupStage();
    }

    private void setupStage() {
        level = mode == LEVEL ? Level.get(levelNo) : Level.stage(stage, rnd);
        board.clear();
        board.rot = mode == LEVEL ? 0 : rnd.nextFloat() * 360f;
        for (float a : level.pre) board.add(a, Board.NEUTRAL);
        shotsLeft = level.shots;
        t = 0;
        lastShotT = lastStickT = -10;
        flying.clear();
        crashLocal = -1;
        state = PLAYING;
        stateTime = 0;
    }

    /** 闯关模式失败后重试。 */
    public void retry() {
        fails++;
        setupStage();
    }

    public int placed() {
        return board.pins.size() - level.pre.length;
    }

    public void shoot() {
        if (state != PLAYING || shotsLeft <= 0) return;
        if (t - lastShotT < 0.07f) return;
        lastShotT = t;
        shotsLeft--;
        flying.add(new float[]{0f});
        events.add(new Event(Event.SHOOT, 0, 0, 0));
    }

    public void update(float dt) {
        stateTime += dt;
        if (mode == TIME && state != OVER) {
            timeLeft -= dt;
            if (timeLeft <= 0) {
                timeLeft = 0;
                state = OVER;
                stateTime = 0;
                flying.clear();
                events.add(new Event(Event.TIMEUP, 0, 0, score));
                return;
            }
        }
        switch (state) {
            case PLAYING:
                t += dt;
                board.rot = Board.norm(board.rot + level.rot.speed(t, placed()) * dt);
                board.age(dt);
                for (int i = 0; i < flying.size(); ) {
                    float[] f = flying.get(i);
                    f[0] += dt / FLY_TIME;
                    if (f[0] >= 1f) {
                        flying.remove(i);
                        land();
                        if (state != PLAYING) break;
                    } else {
                        i++;
                    }
                }
                if (state == PLAYING && shotsLeft == 0 && flying.isEmpty()) {
                    state = CLEARED;
                    stateTime = 0;
                    if (mode != LEVEL) score += 5 + stage;
                    if (mode == TIME) timeLeft += 5f;
                    events.add(new Event(Event.CLEAR, 0, 0, stage));
                }
                break;
            case CLEARED:
                board.age(dt);
                board.rot = Board.norm(board.rot + 360f * dt * Math.min(1f, stateTime * 2f));
                if (mode != LEVEL && stateTime > 1.1f) {
                    stage++;
                    setupStage();
                }
                break;
            case CRASHED:
                if (mode == TIME && stateTime > 0.8f) setupStage();
                break;
            default:
                break;
        }
    }

    private void land() {
        float local = board.localFor(SHOOT_ANGLE);
        if (board.hits(local)) {
            crashLocal = local;
            flying.clear();
            combo = 0;
            stateTime = 0;
            events.add(new Event(Event.CRASH, 0, local, 0));
            if (mode == ENDLESS) {
                state = OVER;
                events.add(new Event(Event.OVER, 0, 0, score));
            } else {
                state = CRASHED;
                if (mode == TIME) timeLeft = Math.max(0.01f, timeLeft - 5f);
            }
            return;
        }
        board.add(local, 0);
        stuck++;
        int gain = 0;
        if (mode != LEVEL) {
            combo = (t - lastStickT < 0.6f) ? combo + 1 : 1;
            maxCombo = Math.max(maxCombo, combo);
            gain = 1 + (combo >= 5 ? 2 : combo >= 3 ? 1 : 0);
            score += gain;
        }
        lastStickT = t;
        events.add(new Event(Event.STICK, 0, local, gain));
    }

    /** 闯关星级：首次通过 3 星，失败 1~2 次 2 星，其余 1 星。 */
    public int stars() {
        return fails == 0 ? 3 : fails <= 2 ? 2 : 1;
    }
}
