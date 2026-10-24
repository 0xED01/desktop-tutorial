package com.aapin.game;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/** 音效（运行时合成 WAV 后用 SoundPool 播放）与振动。 */
public final class Sfx {
    public static final int SHOOT = 0, STICK = 1, CRASH = 2, WIN = 3, LOSE = 4, BEEP = 5, GO = 6,
            CLEAR = 7, CLICK = 8, STAR = 9;
    private static final int COUNT = 10;
    private static final int RATE = 22050;
    private static final int VERSION = 1;

    private static SoundPool pool;
    private static final int[] ids = new int[COUNT];
    private static Context app;

    public static synchronized void init(Context c) {
        if (pool != null) return;
        app = c.getApplicationContext();
        if (Build.VERSION.SDK_INT >= 21) {
            pool = new SoundPool.Builder().setMaxStreams(8).setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        } else {
            pool = new SoundPool(8, AudioManager.STREAM_MUSIC, 0);
        }
        for (int i = 0; i < COUNT; i++) {
            File f = new File(app.getCacheDir(), "sfx" + VERSION + "_" + i + ".wav");
            try {
                if (!f.exists()) writeWav(f, gen(i));
                ids[i] = pool.load(f.getPath(), 1);
            } catch (IOException ignored) {
            }
        }
    }

    public static void play(int s) {
        if (pool == null || app == null || !Prefs.sound(app)) return;
        float vol = s == STICK ? 0.7f : 1f;
        pool.play(ids[s], vol, vol, 1, 0, 1f);
    }

    public static void buzz(long ms) {
        if (app == null || !Prefs.vibrate(app)) return;
        Vibrator v = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(ms);
        }
    }

    // ---------------- 合成 ----------------

    private static float[] gen(int i) {
        switch (i) {
            case SHOOT: return sweep(700, 1600, 0.07f, 0.35f, true);
            case STICK: return mix(sweep(260, 140, 0.09f, 0.6f, false), noise(0.02f, 0.25f));
            case CRASH: return mix(noise(0.5f, 0.7f), sweep(160, 40, 0.5f, 0.6f, false));
            case WIN: return notes(new float[]{523, 659, 784, 1047}, 0.1f, 0.5f);
            case LOSE: return notes(new float[]{392, 311, 262}, 0.16f, 0.5f);
            case BEEP: return sweep(880, 880, 0.12f, 0.45f, false);
            case GO: return sweep(1320, 1320, 0.28f, 0.45f, false);
            case CLEAR: return notes(new float[]{784, 1047, 1319}, 0.08f, 0.45f);
            case CLICK: return sweep(1200, 900, 0.035f, 0.3f, false);
            default: return notes(new float[]{1568, 2093}, 0.09f, 0.35f);
        }
    }

    private static float[] sweep(float f0, float f1, float dur, float amp, boolean square) {
        int n = (int) (RATE * dur);
        float[] out = new float[n];
        double ph = 0;
        for (int i = 0; i < n; i++) {
            float k = i / (float) n;
            double f = f0 + (f1 - f0) * k;
            ph += 2 * Math.PI * f / RATE;
            float s = (float) Math.sin(ph);
            if (square) s = s > 0 ? 0.6f : -0.6f;
            float env = Math.min(1f, i / (RATE * 0.004f)) * (1f - k) * (1f - k);
            out[i] = s * amp * env;
        }
        return out;
    }

    private static float[] noise(float dur, float amp) {
        int n = (int) (RATE * dur);
        float[] out = new float[n];
        Random r = new Random(7);
        float lp = 0;
        for (int i = 0; i < n; i++) {
            float k = i / (float) n;
            lp += (r.nextFloat() * 2 - 1 - lp) * 0.35f;
            out[i] = lp * amp * (1 - k) * (1 - k) * 1.8f;
        }
        return out;
    }

    private static float[] notes(float[] freqs, float each, float amp) {
        float[][] parts = new float[freqs.length][];
        int total = 0;
        for (int i = 0; i < freqs.length; i++) {
            float d = i == freqs.length - 1 ? each * 2.5f : each;
            parts[i] = sweep(freqs[i], freqs[i], d, amp, false);
            total += (int) (RATE * each);
        }
        float[] out = new float[total + parts[parts.length - 1].length];
        int pos = 0;
        for (float[] p : parts) {
            for (int j = 0; j < p.length && pos + j < out.length; j++) out[pos + j] += p[j];
            pos += (int) (RATE * each);
        }
        return out;
    }

    private static float[] mix(float[] a, float[] b) {
        float[] out = new float[Math.max(a.length, b.length)];
        for (int i = 0; i < out.length; i++) out[i] = (i < a.length ? a[i] : 0) + (i < b.length ? b[i] : 0);
        return out;
    }

    private static void writeWav(File f, float[] data) throws IOException {
        int bytes = data.length * 2;
        byte[] buf = new byte[44 + bytes];
        putStr(buf, 0, "RIFF");
        putInt(buf, 4, 36 + bytes);
        putStr(buf, 8, "WAVEfmt ");
        putInt(buf, 16, 16);
        putShort(buf, 20, 1);
        putShort(buf, 22, 1);
        putInt(buf, 24, RATE);
        putInt(buf, 28, RATE * 2);
        putShort(buf, 32, 2);
        putShort(buf, 34, 16);
        putStr(buf, 36, "data");
        putInt(buf, 40, bytes);
        for (int i = 0; i < data.length; i++) {
            int v = (int) (Math.max(-1f, Math.min(1f, data[i])) * 32000);
            putShort(buf, 44 + i * 2, v);
        }
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(buf);
        }
    }

    private static void putStr(byte[] b, int o, String s) {
        for (int i = 0; i < s.length(); i++) b[o + i] = (byte) s.charAt(i);
    }

    private static void putInt(byte[] b, int o, int v) {
        b[o] = (byte) v;
        b[o + 1] = (byte) (v >> 8);
        b[o + 2] = (byte) (v >> 16);
        b[o + 3] = (byte) (v >> 24);
    }

    private static void putShort(byte[] b, int o, int v) {
        b[o] = (byte) v;
        b[o + 1] = (byte) (v >> 8);
    }
}
