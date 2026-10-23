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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import kotlin.concurrent.thread
import kotlin.math.min
import kotlin.random.Random

/** 前台服务：锁屏/后台也持续监控；同时可以运行网页控制服务器。两者都关掉时服务才退出。 */
class MonitorService : Service(), WebControl {
    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_STOP_ALARM = "stop_alarm"
        const val ACTION_WEB_ON = "web_on"
        const val ACTION_WEB_OFF = "web_off"
        const val WEB_PORT = 8765

        /** 网页控制是否正在运行（给界面显示用）。 */
        @Volatile var webRunning = false
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

        fun setWeb(ctx: Context, on: Boolean) {
            val i = Intent(ctx, MonitorService::class.java).setAction(if (on) ACTION_WEB_ON else ACTION_WEB_OFF)
            if (on) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun webAddress(): String = "http://${WebServer.lanAddress() ?: "手机IP"}:$WEB_PORT"

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
    private var web: WebServer? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannels(this)
        val prefs = Prefs(this)
        when (intent?.action) {
            ACTION_STOP_ALARM -> {
                AlarmPlayer.stop()
                if (worker == null && web == null) stopSelf()
            }
            ACTION_STOP -> {
                prefs.running = false
                stopMonitor()
                stopIfIdle()
            }
            ACTION_WEB_ON -> {
                prefs.webEnabled = true
                ensureForeground()
                startWeb(prefs)
            }
            ACTION_WEB_OFF -> {
                prefs.webEnabled = false
                stopWeb()
                stopIfIdle()
            }
            else -> {
                // ACTION_START，或被系统杀掉后重启（intent == null）：恢复之前开着的功能
                if (intent == null && !prefs.running && !prefs.webEnabled) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                ensureForeground()
                if (prefs.webEnabled) startWeb(prefs)
                if (intent != null || prefs.running) startMonitor(prefs)
            }
        }
        return START_STICKY
    }

    private fun currentText(): String {
        val webText = if (web != null) "网页控制：${webAddress()}" else ""
        return when {
            worker != null && webText.isNotEmpty() -> "${State.status}  |  $webText"
            worker != null -> State.status
            else -> "未在监控  |  $webText"
        }
    }

    private fun ensureForeground() {
        val n = runningNotification(currentText())
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_RUN, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID_RUN, n)
        }
    }

    /** 监控和网页控制都关了才退出服务，否则只更新通知。 */
    private fun stopIfIdle() {
        if (worker == null && web == null) {
            releaseWake()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            updateNotification(currentText())
        }
    }

    private fun acquireWake() {
        if (wakeLock == null) {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AppleStock::monitor")
                .apply { setReferenceCounted(false); acquire() }
        }
    }

    private fun releaseWake() {
        if (worker != null || web != null) return
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun startWeb(prefs: Prefs) {
        if (web != null) return
        val api = WebApi(prefs, this) { assets.open("web/index.html").use { it.readBytes() } }
        val server = WebServer(WEB_PORT, api::handle)
        try {
            server.start()
        } catch (e: Exception) {
            State.log("网页控制启动失败：${e.javaClass.simpleName}（端口 $WEB_PORT 可能被占用）")
            prefs.webEnabled = false
            stopIfIdle()
            return
        }
        web = server
        webRunning = true
        acquireWake()
        State.log("网页控制已开启：同一 Wi-Fi 下用浏览器打开 ${webAddress()} ，访问码 ${prefs.webKey}")
        updateNotification(currentText())
        State.changed()
    }

    private fun stopWeb() {
        web?.stop()
        if (web != null) State.log("网页控制已关闭")
        web = null
        webRunning = false
        releaseWake()
        State.changed()
    }

    // ---------- 网页控制的操作（来自网页服务器线程，切回主线程执行） ----------
    override fun startMonitoring() {
        main.post {
            stopMonitor()
            ensureForeground()
            startMonitor(Prefs(this))
        }
    }

    override fun stopMonitoring() {
        main.post {
            Prefs(this).running = false
            stopMonitor()
            stopIfIdle()
        }
    }

    override fun testAlert() {
        main.post {
            fireAlert(this, AlertInfo("TEST", "测试提醒", "",
                listOf(StoreStatus("R000", "测试门店", "", true, "今天可取货", "")), false))
        }
    }

    override fun stopAlarm() {
        AlarmPlayer.stop()
    }

    private fun runningNotification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CH_RUN)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (worker != null) "苹果库存监控中" else "苹果库存监控：网页控制已开启")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "停止监控", stop).build())
            .build()
    }

    private fun updateNotification(text: String) {
        if (worker == null && web == null) return
        getSystemService(NotificationManager::class.java).notify(ID_RUN, runningNotification(text))
    }

    private fun startMonitor(prefs: Prefs) {
        if (worker != null) return
        val parts = Apple.parseParts(prefs.parts)
        if (parts.isEmpty() || prefs.location.isBlank()) {
            State.log("没有可监控的型号或地区为空")
            prefs.running = false
            stopIfIdle()
            return
        }
        prefs.running = true
        acquireWake()

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
                    updateNotification(currentText())
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
        releaseWake()
        if (State.running) State.log("监控已停止")
        State.running = false
        State.status = "已停止"
        State.changed()
    }

    override fun onDestroy() {
        stopMonitor()
        stopWeb()
        super.onDestroy()
    }
}
