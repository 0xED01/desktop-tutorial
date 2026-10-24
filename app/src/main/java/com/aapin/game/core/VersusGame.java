package com.aapin.game.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Random;

/**
 * 双人对战逻辑：两名玩家从上下两侧向同一个圆盘插针。
 * 撞到任何针的一方输掉本局；先插完自己所有针的一方赢得本局。先赢 3 局者获胜。
 * 局域网模式下由主机运行，状态通过 encode/decode 同步给客户端。
 */
public final class VersusGame {
    public static final int COUNTDOWN = 0, PLAYING = 1, ROUND_OVER = 2, MATCH_OVER = 3;
    public static final int WIN_ROUNDS = 3;
    public static final float FLY_TIME = 0.09f;
    public static final float COOLDOWN = 0.16f;
    public static final float COUNTDOWN_TIME = 3f;
    public static final float ROUND_OVER_TIME = 2.6f;
    public static final float[] SHOOT_ANGLE = {90f, 270f};
    public static final int REASON_FINISH = 0, REASON_CRASH = 1;

    private static final int[] ROUND_TYPES = {RotSpec.CONST, RotSpec.REVERSE, RotSpec.SINE, RotSpec.FLIP,
            RotSpec.STOPGO, RotSpec.WOBBLE, RotSpec.REVERSE, RotSpec.SINE, RotSpec.ACCEL};

    public final Board board = new Board();
    /** 每项为 {owner, progress}。 */
    public final ArrayList<float[]> flying = new ArrayList<>();
    public final ArrayDeque<Event> events = new ArrayDeque<>();
    public final int[] score = new int[2];
    public final int[] left = new int[2];
    public final boolean[] ready = new boolean[2];

    public RotSpec rot = new RotSpec(RotSpec.CONST, 80, 2.4f, 1);
    public int rotType;
    public int phase = COUNTDOWN;
    public int round;
    public int winner = -1, reason, crashOwner = -1;
    public float crashLocal;
    public float t, phaseTime, vel;
    /** 客户端是否已收到过主机状态。 */
    public boolean synced;

    private final float[] lastShot = {-9, -9};
    private int lastBeep = -1;
    private final Random rnd = new Random();

    public void newMatch() {
        score[0] = score[1] = 0;
        round = 0;
        ready[0] = ready[1] = false;
        startRound();
    }

    private void startRound() {
        round++;
        board.clear();
        flying.clear();
        crashOwner = -1;
        winner = -1;
        int r = Math.min(round, 8);
        left[0] = left[1] = 6 + r;
        for (float a : Board.scatter(Math.min(round - 1, 6), rnd, 20f)) board.add(a, Board.NEUTRAL);
        rotType = ROUND_TYPES[(round - 1) % ROUND_TYPES.length];
        float speed = rotType == RotSpec.ACCEL ? 70 : 70 + r * 12;
        float period = rotType == RotSpec.ACCEL ? 4f : 2.4f;
        rot = new RotSpec(rotType, speed, period, rnd.nextBoolean() ? 1 : -1);
        board.rot = rnd.nextFloat() * 360f;
        t = 0;
        lastShot[0] = lastShot[1] = -9;
        phase = COUNTDOWN;
        phaseTime = 0;
        lastBeep = -1;
    }

    public void shoot(int o) {
        if (phase != PLAYING || left[o] <= 0 || t - lastShot[o] < COOLDOWN) return;
        lastShot[o] = t;
        left[o]--;
        flying.add(new float[]{o, 0f});
        events.add(new Event(Event.SHOOT, o, 0, 0));
    }

    public void setReady(int o) {
        if (phase == MATCH_OVER) ready[o] = true;
    }

    public void update(float dt) {
        phaseTime += dt;
        switch (phase) {
            case COUNTDOWN: {
                vel = rot.speed(0, 0) * 0.5f;
                board.rot = Board.norm(board.rot + vel * dt);
                int n = 3 - (int) phaseTime;
                if (n != lastBeep && n > 0) {
                    lastBeep = n;
                    events.add(new Event(Event.BEEP, 0, 0, n));
                }
                if (phaseTime >= COUNTDOWN_TIME) {
                    phase = PLAYING;
                    phaseTime = 0;
                    events.add(new Event(Event.GO, 0, 0, 0));
                }
                break;
            }
            case PLAYING:
                t += dt;
                vel = rot.speed(t, board.pins.size());
                board.rot = Board.norm(board.rot + vel * dt);
                board.age(dt);
                for (int i = 0; i < flying.size(); ) {
                    float[] f = flying.get(i);
                    f[1] += dt / FLY_TIME;
                    if (f[1] >= 1f) {
                        flying.remove(i);
                        land((int) f[0]);
                        if (phase != PLAYING) break;
                    } else {
                        i++;
                    }
                }
                if (phase == PLAYING) {
                    for (int o = 0; o < 2; o++) {
                        if (left[o] == 0 && !hasFlying(o)) {
                            endRound(o, REASON_FINISH);
                            break;
                        }
                    }
                }
                break;
            case ROUND_OVER:
                vel *= Math.max(0f, 1f - dt * 3f);
                board.rot = Board.norm(board.rot + vel * dt);
                if (phaseTime >= ROUND_OVER_TIME) {
                    if (score[0] >= WIN_ROUNDS || score[1] >= WIN_ROUNDS) {
                        phase = MATCH_OVER;
                        phaseTime = 0;
                        events.add(new Event(Event.MATCH_END, score[0] > score[1] ? 0 : 1, 0, 0));
                    } else {
                        startRound();
                    }
                }
                break;
            case MATCH_OVER:
                vel = 30;
                board.rot = Board.norm(board.rot + vel * dt);
                if (ready[0] && ready[1]) newMatch();
                break;
        }
    }

    private boolean hasFlying(int o) {
        for (float[] f : flying) if ((int) f[0] == o) return true;
        return false;
    }

    private void land(int o) {
        float local = board.localFor(SHOOT_ANGLE[o]);
        if (board.hits(local)) {
            crashOwner = o;
            crashLocal = local;
            events.add(new Event(Event.CRASH, o, local, 0));
            endRound(1 - o, REASON_CRASH);
            return;
        }
        board.add(local, o);
        events.add(new Event(Event.STICK, o, local, 0));
    }

    private void endRound(int w, int why) {
        winner = w;
        reason = why;
        score[w]++;
        phase = ROUND_OVER;
        phaseTime = 0;
        flying.clear();
        events.add(new Event(Event.ROUND_END, w, 0, why));
    }

    // ---------------- 网络同步 ----------------

    private static float r1(float v) {
        return Math.round(v * 10f) / 10f;
    }

    public String encode() {
        StringBuilder sb = new StringBuilder(320);
        sb.append("S|").append(r1(board.rot)).append('|').append(r1(vel)).append('|').append(phase)
                .append('|').append(Math.round(phaseTime * 100f) / 100f).append('|').append(round)
                .append('|').append(score[0]).append('|').append(score[1])
                .append('|').append(left[0]).append('|').append(left[1])
                .append('|').append(winner).append('|').append(reason)
                .append('|').append(crashOwner).append('|').append(r1(crashLocal))
                .append('|').append(ready[0] ? 1 : 0).append('|').append(ready[1] ? 1 : 0)
                .append('|').append(rotType).append('|');
        for (int i = 0; i < board.pins.size(); i++) {
            Board.Pin p = board.pins.get(i);
            if (i > 0) sb.append(',');
            sb.append(r1(p.a)).append(':').append(p.owner);
        }
        sb.append('|');
        for (int i = 0; i < flying.size(); i++) {
            float[] f = flying.get(i);
            if (i > 0) sb.append(',');
            sb.append((int) f[0]).append(':').append(Math.round(f[1] * 100f) / 100f);
        }
        return sb.toString();
    }

    /** 客户端应用主机状态，并根据差异生成音效事件。返回是否解析成功。 */
    public boolean decode(String msg) {
        String[] s = msg.split("\\|", -1);
        if (s.length < 19 || !"S".equals(s[0])) return false;
        try {
            int newPhase = Integer.parseInt(s[3]);
            float newPhaseTime = Float.parseFloat(s[4]);
            int newRound = Integer.parseInt(s[5]);
            boolean roundChanged = newRound != round;

            board.rot = Float.parseFloat(s[1]);
            vel = Float.parseFloat(s[2]);
            score[0] = Integer.parseInt(s[6]);
            score[1] = Integer.parseInt(s[7]);
            left[0] = Integer.parseInt(s[8]);
            left[1] = Integer.parseInt(s[9]);
            winner = Integer.parseInt(s[10]);
            reason = Integer.parseInt(s[11]);
            crashOwner = Integer.parseInt(s[12]);
            crashLocal = Float.parseFloat(s[13]);
            ready[0] = "1".equals(s[14]);
            ready[1] = "1".equals(s[15]);
            rotType = Integer.parseInt(s[16]);

            int oldCount = roundChanged ? 0 : board.pins.size();
            if (roundChanged) board.clear();
            ArrayList<float[]> pins = new ArrayList<>();
            if (!s[17].isEmpty()) {
                for (String p : s[17].split(",")) {
                    int c = p.indexOf(':');
                    pins.add(new float[]{Float.parseFloat(p.substring(0, c)), Integer.parseInt(p.substring(c + 1))});
                }
            }
            if (pins.size() < oldCount) oldCount = 0;
            ArrayList<Board.Pin> keep = new ArrayList<>(board.pins);
            board.clear();
            for (int i = 0; i < pins.size(); i++) {
                float[] p = pins.get(i);
                Board.Pin np = board.add(p[0], (int) p[1]);
                if (i < oldCount && i < keep.size()) np.age = keep.get(i).age;
                else if (synced && np.owner != Board.NEUTRAL && newPhase == PLAYING)
                    events.add(new Event(Event.STICK, np.owner, np.a, 0));
            }

            int[] oldFly = new int[2];
            for (float[] f : flying) oldFly[(int) f[0]]++;
            flying.clear();
            int[] newFly = new int[2];
            if (!s[18].isEmpty()) {
                for (String f : s[18].split(",")) {
                    int c = f.indexOf(':');
                    int o = Integer.parseInt(f.substring(0, c));
                    flying.add(new float[]{o, Float.parseFloat(f.substring(c + 1))});
                    newFly[o]++;
                }
            }
            if (synced) {
                for (int o = 0; o < 2; o++)
                    if (newFly[o] > oldFly[o]) events.add(new Event(Event.SHOOT, o, 0, 0));
                if (newPhase == COUNTDOWN) {
                    int n = 3 - (int) newPhaseTime;
                    if (n != lastBeep && n > 0) events.add(new Event(Event.BEEP, 0, 0, n));
                    lastBeep = n;
                }
                if (newPhase != phase) {
                    if (newPhase == PLAYING) events.add(new Event(Event.GO, 0, 0, 0));
                    if (newPhase == ROUND_OVER) {
                        if (crashOwner >= 0) events.add(new Event(Event.CRASH, crashOwner, crashLocal, 0));
                        events.add(new Event(Event.ROUND_END, winner, 0, reason));
                    }
                    if (newPhase == MATCH_OVER)
                        events.add(new Event(Event.MATCH_END, score[0] > score[1] ? 0 : 1, 0, 0));
                }
            }
            if (newPhase != COUNTDOWN) lastBeep = -1;
            phase = newPhase;
            phaseTime = newPhaseTime;
            round = newRound;
            synced = true;
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 客户端在两次状态包之间做外推，使画面平滑。 */
    public void extrapolate(float dt) {
        if (!synced) return;
        board.rot = Board.norm(board.rot + vel * dt);
        board.age(dt);
        phaseTime += dt;
        for (float[] f : flying) f[1] = Math.min(1f, f[1] + dt / FLY_TIME);
    }
}
