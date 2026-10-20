package com.applestock.pagewatch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** 闹钟铃声 + 振动，静音模式也会响（走闹钟音量），最长 10 分钟。 */
object AlarmPlayer {
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var playing = false

    fun start(context: Context) = main.post {
        if (playing) return@post
        playing = true
        val ctx = context.applicationContext
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        } catch (_: SecurityException) {
        }
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(ctx, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
            player = null
        }
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 500), 0))
        main.postDelayed({ stop() }, 10 * 60_000L)
    }

    fun stop() = main.post {
        player?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        player = null
        playing = false
        vibrator?.cancel()
        vibrator = null
        main.removeCallbacksAndMessages(null)
    }
}

object Notifier {
    private const val CH_RUN = "run"
    private const val CH_ALERT = "alert"
    const val ID_RUN = 1
    private const val ID_ALERT = 2

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_RUN, "监控运行中", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "有货提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    private fun openApp(ctx: Context, code: Int, stopAlarm: Boolean = false) = PendingIntent.getActivity(
        ctx, code,
        Intent(ctx, MainActivity::class.java).putExtra("stop_alarm", stopAlarm)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun running(ctx: Context, text: String): Notification = Notification.Builder(ctx, CH_RUN)
        .setSmallIcon(R.drawable.ic_stat)
        .setContentTitle("网页有货监控中")
        .setContentText(text)
        .setContentIntent(openApp(ctx, 0))
        .setOngoing(true)
        .build()

    fun updateRunning(ctx: Context, text: String) {
        ctx.getSystemService(NotificationManager::class.java).notify(ID_RUN, running(ctx, text))
    }

    fun alert(ctx: Context, title: String, text: String, urgent: Boolean) {
        val open = openApp(ctx, 3, stopAlarm = false)
        val b = Notification.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
        if (urgent) {
            b.setCategory(Notification.CATEGORY_ALARM)
                .setFullScreenIntent(open, true)
                .addAction(Notification.Action.Builder(null, "停止响铃", openApp(ctx, 4, stopAlarm = true)).build())
        }
        ctx.getSystemService(NotificationManager::class.java).notify(ID_ALERT, b.build())
    }
}

/** 前台服务：只负责让系统别杀掉 App、CPU 别睡；刷新页面的工作在界面里的浏览器完成。 */
class KeepAliveService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifier.channels(this)
        val n = Notifier.running(this, "正在定时刷新页面…")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifier.ID_RUN, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifier.ID_RUN, n)
        }
        if (wakeLock == null) {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PageWatch::keepalive")
                .apply { setReferenceCounted(false); acquire() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }
}
