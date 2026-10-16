package com.applestock.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import kotlin.concurrent.thread
import kotlin.math.min
import kotlin.random.Random

/** 前台服务：锁屏/后台也持续监控。 */
class MonitorService : Service() {
    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_STOP_ALARM = "stop_alarm"
        private const val CH_RUN = "monitor"
        private const val CH_ALERT = "alert"
        private const val ID_RUN = 1
        private var alertId = 100

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, MonitorService::class.java).setAction(ACTION_START))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, MonitorService::class.java).setAction(ACTION_STOP))
        }

        fun createChannels(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH_RUN, "监控运行中", NotificationManager.IMPORTANCE_LOW)
            )
            nm.createNotificationChannel(
                NotificationChannel(CH_ALERT, "有货提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null) // 铃声由 AlarmPlayer 播放
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                }
            )
        }

        /** 有货提醒：响铃振动 + 全屏/高优先级通知 + 推送。也用于“测试提醒”。 */
        fun fireAlert(ctx: Context, info: AlertInfo) {
            val prefs = Prefs(ctx)
            val (title, body) = info.text()
            State.log("★★★ $title ★★★ ${body.replace("\n", " ")}")
            AlarmPlayer.start(ctx, prefs.maxVolume)
            Push.send(prefs, title, body, info.url)

            val alertIntent = Intent(ctx, AlertActivity::class.java)
                .putExtra("title", title).putExtra("body", body).putExtra("url", info.url)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val id = alertId++
            val full = PendingIntent.getActivity(ctx, id, alertIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val go = PendingIntent.getActivity(ctx, id + 10000,
                Intent(alertIntent).putExtra("go", true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val stopAlarm = PendingIntent.getService(ctx, 2,
                Intent(ctx, MonitorService::class.java).setAction(ACTION_STOP_ALARM),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = Notification.Builder(ctx, CH_ALERT)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(full)
                .setFullScreenIntent(full, true)
                .setDeleteIntent(stopAlarm)
                .addAction(Notification.Action.Builder(null, "去抢购", go).build())
                .addAction(Notification.Action.Builder(null, "停止响铃", stopAlarm).build())
                .setAutoCancel(true)
                .build()
            ctx.getSystemService(NotificationManager::class.java).notify(id, n)
        }
    }

    @Volatile private var worker: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannels(this)
        val prefs = Prefs(this)
        when (intent?.action) {
            ACTION_STOP_ALARM -> {
                AlarmPlayer.stop()
                if (worker == null) stopSelf()
            }
            ACTION_STOP -> {
                prefs.running = false
                stopMonitor()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                // ACTION_START，或被系统杀掉后重启（intent == null）
                if (intent == null && !prefs.running) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startMonitor(prefs)
            }
        }
        return START_STICKY
    }

    private fun runningNotification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CH_RUN)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("苹果库存监控中")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "停止监控", stop).build())
            .build()
    }

    private fun updateNotification(text: String) {
        if (worker == null) return
        getSystemService(NotificationManager::class.java).notify(ID_RUN, runningNotification(text))
    }

    private fun startMonitor(prefs: Prefs) {
        val n = runningNotification("准备中…")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_RUN, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID_RUN, n)
        }
        if (worker != null) return
        val parts = Apple.parseParts(prefs.parts)
        if (parts.isEmpty() || prefs.location.isBlank()) {
            State.log("没有可监控的型号或地区为空")
            prefs.running = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        prefs.running = true
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AppleStock::monitor")
            .apply { setReferenceCounted(false); acquire() }

        val interval = prefs.interval.coerceAtLeast(15)
        val monitor = Monitor(parts, prefs.location, prefs.storeFilter, prefs.repeatMinutes, object : MonitorEvents {
            override fun log(text: String) = State.log(text)
            override fun row(row: PartRow) = State.putRow(row)
            override fun alert(info: AlertInfo) = fireAlert(this@MonitorService, info)
        })
        State.clearRows()
        State.running = true
        State.log("开始监控 ${parts.size} 个型号，地区：${prefs.location}，间隔约 $interval 秒")
        worker = thread(name = "monitor") {
            var errors = 0
            var rounds = 0
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val ok = monitor.checkRound()
                    rounds++
                    State.status = "监控中… 已检查 $rounds 轮，最近 ${timeNow()}"
                    updateNotification(State.status)
                    State.changed()
                    val wait = if (ok) {
                        errors = 0
                        interval * Random.nextDouble(0.85, 1.15)
                    } else {
                        errors++
                        (interval * min(1 shl min(errors, 4), 10)).toDouble().also {
                            State.log("连续出错 $errors 次，${it.toInt()} 秒后重试")
                        }
                    }
                    Thread.sleep((wait * 1000).toLong())
                }
            } catch (_: InterruptedException) {
            }
        }
    }

    private fun stopMonitor() {
        worker?.interrupt()
        worker = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        if (State.running) State.log("监控已停止")
        State.running = false
        State.status = "已停止"
        State.changed()
    }

    override fun onDestroy() {
        stopMonitor()
        super.onDestroy()
    }
}
