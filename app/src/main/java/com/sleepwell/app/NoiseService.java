package com.sleepwell.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.Random;

/** 实时合成的助眠噪音（白/粉红/棕色/海浪），带定时淡出。锁屏也能持续播放。 */
public class NoiseService extends Service {
    public static final String[] NAMES = {"白噪音", "粉红噪音", "棕色噪音", "海浪"};
    public static final String START = "start", UPDATE = "update", STOP = "stop";
    private static final int RATE = 44100;
    private static final long FADE_MS = 60_000;

    public static volatile boolean running;
    private static volatile int type = 1;
    private static volatile float volume = 0.5f;
    private static volatile long endAt;

    /** 当前的播放线程；置为 null 即通知它退出 */
    private volatile Thread thread;

    public static void start(Context c, int type, int vol, int timerMin) {
        Intent i = new Intent(c, NoiseService.class).setAction(START)
                .putExtra("type", type).putExtra("vol", vol).putExtra("timer", timerMin);
        c.startForegroundService(i);
    }

    public static void update(int newType, int vol) {
        type = newType;
        volume = vol / 100f;
    }

    public static void stop(Context c) {
        c.startService(new Intent(c, NoiseService.class).setAction(STOP));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && START.equals(intent.getAction())) {
            startForegroundNow();
            type = intent.getIntExtra("type", 1);
            volume = intent.getIntExtra("vol", 50) / 100f;
            int t = intent.getIntExtra("timer", 60);
            endAt = t > 0 ? System.currentTimeMillis() + t * 60_000L : 0;
            if (thread == null) {
                running = true;
                thread = new Thread(this::loop, "noise");
                thread.start();
            }
        } else {
            thread = null;
            running = false;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void startForegroundNow() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel("noise") == null) {
            nm.createNotificationChannel(new NotificationChannel("noise", "助眠声音", NotificationManager.IMPORTANCE_LOW));
        }
        PendingIntent stopPi = PendingIntent.getService(this, 1,
                new Intent(this, NoiseService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent openPi = PendingIntent.getActivity(this, 2,
                new Intent(this, MainActivity.class).putExtra("tab", 3), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, "noise")
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("正在播放助眠声音")
                .setContentText("到时间会自动淡出停止")
                .setContentIntent(openPi)
                .addAction(new Notification.Action.Builder(null, "停止", stopPi).build())
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(10, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(10, n);
        }
    }

    private void loop() {
        int minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(Math.max(minBuf * 2, RATE))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        track.play();

        Thread me = Thread.currentThread();
        Random rnd = new Random();
        short[] buf = new short[2048];
        float b0 = 0, b1 = 0, b2 = 0, b3 = 0, b4 = 0, b5 = 0, b6 = 0, brown = 0;
        float gain = 0; // 平滑音量，避免切换时爆音
        long started = System.currentTimeMillis();
        double wavePeriod = 9.0, wavePhase = 0;

        while (thread == me) {
            long now = System.currentTimeMillis();
            float fade = Math.min(1f, (now - started) / 3000f);
            long end = endAt;
            if (end > 0) {
                long rem = end - now;
                if (rem <= 0) break;
                fade = Math.min(fade, rem / (float) FADE_MS);
            }
            int t = type;
            float target = volume * volume * fade; // 平方曲线更符合听感
            for (int i = 0; i < buf.length; i++) {
                float w = rnd.nextFloat() * 2f - 1f;
                b0 = 0.99886f * b0 + w * 0.0555179f;
                b1 = 0.99332f * b1 + w * 0.0750759f;
                b2 = 0.96900f * b2 + w * 0.1538520f;
                b3 = 0.86650f * b3 + w * 0.3104856f;
                b4 = 0.55000f * b4 + w * 0.5329522f;
                b5 = -0.7616f * b5 - w * 0.0168980f;
                float pink = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362f) * 0.11f;
                b6 = w * 0.115926f;
                brown = (brown + 0.02f * w) / 1.02f;
                float br = brown * 3.5f;

                float v;
                switch (t) {
                    case 0: v = w * 0.35f; break;
                    case 2: v = br * 0.9f; break;
                    case 3: {
                        wavePhase += 1.0 / (RATE * wavePeriod);
                        if (wavePhase >= 1) {
                            wavePhase -= 1;
                            wavePeriod = 7.0 + rnd.nextDouble() * 5.0; // 每个浪长短略有不同，更自然
                        }
                        double env = 0.5 * (1 - Math.cos(2 * Math.PI * wavePhase));
                        env = 0.12 + 0.88 * env * env;
                        v = (float) ((pink * 0.55f + br * 0.6f) * env);
                        break;
                    }
                    default: v = pink * 0.8f;
                }
                gain += (target - gain) * 0.0005f;
                v *= gain;
                if (v > 1f) v = 1f; else if (v < -1f) v = -1f;
                buf[i] = (short) (v * 32767);
            }
            track.write(buf, 0, buf.length);
        }
        track.stop();
        track.release();
        new Handler(Looper.getMainLooper()).post(() -> {
            if (thread == me) { // 定时结束（而不是被手动停止或已重新开始）
                thread = null;
                running = false;
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        });
    }

    @Override
    public void onDestroy() {
        thread = null;
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
