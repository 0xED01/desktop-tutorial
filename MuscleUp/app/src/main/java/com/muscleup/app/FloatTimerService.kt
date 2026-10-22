package com.muscleup.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.muscleup.app.data.Store
import com.muscleup.app.data.TimerLog
import com.muscleup.app.data.formatDuration
import com.muscleup.app.ui.vibrate
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * 训练计时状态。以墙钟时间保存到 SharedPreferences，
 * 即使进程被系统回收，重新打开后计时也能接着算。
 */
object FloatTimer {
    var running by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
        private set
    var label by mutableStateOf("训练")
    private var accumulatedMs by mutableLongStateOf(0L)
    private var segmentStart by mutableLongStateOf(0L)
    var sessionStart by mutableLongStateOf(0L)
        private set
    var restEndMs by mutableLongStateOf(0L)
    var restSec by mutableIntStateOf(90)

    fun elapsedMs(now: Long = System.currentTimeMillis()): Long =
        if (!running) 0 else accumulatedMs + if (paused) 0 else (now - segmentStart).coerceAtLeast(0)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("float_timer", Context.MODE_PRIVATE)

    fun restore(ctx: Context) {
        val p = prefs(ctx)
        running = p.getBoolean("running", false)
        paused = p.getBoolean("paused", false)
        accumulatedMs = p.getLong("acc", 0)
        segmentStart = p.getLong("seg", 0)
        sessionStart = p.getLong("start", 0)
        label = p.getString("label", "训练") ?: "训练"
        restSec = p.getInt("restSec", 90)
    }

    private fun persist(ctx: Context) {
        prefs(ctx).edit().putBoolean("running", running).putBoolean("paused", paused).putLong("acc", accumulatedMs)
            .putLong("seg", segmentStart).putLong("start", sessionStart).putString("label", label).putInt("restSec", restSec).apply()
    }

    fun start(ctx: Context) {
        if (running) return
        val now = System.currentTimeMillis()
        running = true; paused = false; accumulatedMs = 0; segmentStart = now; sessionStart = now; restEndMs = 0
        persist(ctx)
    }

    fun togglePause(ctx: Context) {
        if (!running) return
        val now = System.currentTimeMillis()
        if (paused) {
            segmentStart = now; paused = false
        } else {
            accumulatedMs += now - segmentStart; paused = true; restEndMs = 0
        }
        persist(ctx)
    }

    fun setRest(ctx: Context, sec: Int) {
        restSec = sec
        persist(ctx)
    }

    /** 结束计时；超过 10 秒的记录保存到训练时长历史，返回保存的记录 */
    fun stop(ctx: Context, save: Boolean): TimerLog? {
        if (!running) return null
        val sec = (elapsedMs() / 1000).toInt()
        var log: TimerLog? = null
        if (save && sec >= 10) {
            Store.ensureInit(ctx)
            val date = Instant.ofEpochMilli(sessionStart).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
            log = TimerLog(System.currentTimeMillis(), date, sessionStart, sec, label.ifBlank { "训练" })
            Store.addTimerLog(log)
        }
        running = false; paused = false; accumulatedMs = 0; restEndMs = 0
        persist(ctx)
        return log
    }

    fun canDrawOverlays(ctx: Context) = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(ctx)

    fun overlayPermissionIntent(ctx: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))

    /** 打开悬浮窗（如未计时则同时开始计时） */
    fun launch(ctx: Context) {
        start(ctx)
        val i = Intent(ctx, FloatTimerService::class.java).setAction(FloatTimerService.ACTION_SHOW)
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
    }

    fun send(ctx: Context, action: String) {
        if (!FloatTimerService.alive) return
        ctx.startService(Intent(ctx, FloatTimerService::class.java).setAction(action))
    }
}

class FloatTimerService : Service() {
    companion object {
        const val ACTION_SHOW = "show"
        const val ACTION_TOGGLE = "toggle"
        const val ACTION_STOP = "stop"
        const val ACTION_DISCARD = "discard"
        const val ACTION_HIDE = "hide"
        const val ACTION_REFRESH = "refresh"
        private const val CHANNEL = "float_timer"
        private const val NOTIF_ID = 42
        var alive = false
            private set
    }

    private lateinit var wm: WindowManager
    private var root: LinearLayout? = null
    private lateinit var timeText: TextView
    private lateinit var subText: TextView
    private lateinit var pauseBtn: TextView
    private lateinit var buttons: LinearLayout
    private lateinit var params: WindowManager.LayoutParams
    private var collapsed = false
    private val handler = Handler(Looper.getMainLooper())
    private var lastPaused: Boolean? = null

    private val tick = object : Runnable {
        override fun run() {
            refreshView()
            handler.postDelayed(this, 250)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        alive = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        Store.ensureInit(applicationContext)
        FloatTimer.restore(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        when (intent?.action) {
            ACTION_TOGGLE -> FloatTimer.togglePause(this)
            ACTION_STOP -> { FloatTimer.stop(this, true); shutdown(); return START_NOT_STICKY }
            ACTION_DISCARD -> { FloatTimer.stop(this, false); shutdown(); return START_NOT_STICKY }
            ACTION_HIDE -> removeOverlay()
            ACTION_SHOW -> showOverlay()
            ACTION_REFRESH -> {}
            else -> if (FloatTimer.running) showOverlay() // 被系统重启
        }
        if (!FloatTimer.running) {
            shutdown(); return START_NOT_STICKY
        }
        updateNotification()
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    private fun startInForeground() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun pi(action: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, FloatTimerService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, "训练计时", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val paused = FloatTimer.paused
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle("${FloatTimer.label} · ${if (paused) "已暂停" else "计时中"}")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(0, if (paused) "继续" else "暂停", pi(ACTION_TOGGLE, 1))
            .addAction(0, "结束并保存", pi(ACTION_STOP, 2))
            .addAction(0, if (root == null) "显示悬浮窗" else "隐藏悬浮窗", pi(if (root == null) ACTION_SHOW else ACTION_HIDE, 3))
        if (paused) {
            b.setContentText("已训练 " + formatDuration(FloatTimer.elapsedMs() / 1000))
        } else {
            b.setUsesChronometer(true).setWhen(System.currentTimeMillis() - FloatTimer.elapsedMs()).setShowWhen(true)
        }
        return b.build()
    }

    private fun updateNotification() {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification()) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun btn(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x33FFFFFF) }
        layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginStart = dp(6) }
        setOnClickListener { onClick() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        if (root != null || !FloatTimer.canDrawOverlays(this)) return
        val prefs = getSharedPreferences("float_timer", MODE_PRIVATE)
        timeText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        subText = TextView(this).apply {
            setTextColor(0xDDFFFFFF.toInt())
            textSize = 11f
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(timeText); addView(subText)
            setPadding(dp(4), 0, dp(4), 0)
        }
        pauseBtn = btn("⏸") { FloatTimer.togglePause(this); updateNotification(); refreshView() }
        buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(pauseBtn)
            addView(btn("休") {
                if (FloatTimer.restEndMs > System.currentTimeMillis()) FloatTimer.restEndMs = 0
                else {
                    if (FloatTimer.paused) FloatTimer.togglePause(this@FloatTimerService)
                    FloatTimer.restEndMs = System.currentTimeMillis() + FloatTimer.restSec * 1000L
                }
                refreshView()
            })
            addView(btn("■") { FloatTimer.stop(this@FloatTimerService, true); shutdown() })
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply { cornerRadius = dp(28).toFloat(); setColor(0xE6E8590C.toInt()) }
            elevation = dp(6).toFloat()
            addView(texts); addView(buttons)
        }
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", dp(16))
            y = prefs.getInt("y", dp(120))
        }

        // 拖动时间区域移动悬浮窗；轻点暂停/继续；长按收起/展开按钮
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        val longPress = Runnable {
            if (!moved) {
                collapsed = !collapsed
                buttons.visibility = if (collapsed) View.GONE else View.VISIBLE
                vibrate(this, 30)
                moved = true // 防止抬起时再触发点击
            }
        }
        texts.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; moved = false
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) { moved = true; handler.removeCallbacks(longPress) }
                    if (moved) {
                        params.x = startX + dx.toInt(); params.y = (startY + dy.toInt()).coerceAtLeast(0)
                        root?.let { wm.updateViewLayout(it, params) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    if (!moved && e.action == MotionEvent.ACTION_UP) {
                        FloatTimer.togglePause(this); updateNotification(); refreshView()
                    }
                    prefs.edit().putInt("x", params.x).putInt("y", params.y).apply()
                }
            }
            true
        }
        runCatching { wm.addView(root, params) }.onFailure { root = null }
        refreshView()
    }

    private fun refreshView() {
        val now = System.currentTimeMillis()
        // 组间休息结束提醒（悬浮窗隐藏时也生效）
        if (FloatTimer.restEndMs in 1..now) {
            FloatTimer.restEndMs = 0
            vibrate(this)
        }
        if (lastPaused != FloatTimer.paused) {
            lastPaused = FloatTimer.paused
            updateNotification()
        }
        if (root == null) return
        timeText.text = formatDuration(FloatTimer.elapsedMs(now) / 1000)
        pauseBtn.text = if (FloatTimer.paused) "▶" else "⏸"
        val rest = FloatTimer.restEndMs - now
        subText.text = when {
            FloatTimer.paused -> "已暂停 · 轻点继续"
            rest > 0 -> "休息 %d 秒".format((rest + 999) / 1000)
            else -> FloatTimer.label
        }
        (root?.background as? GradientDrawable)?.setColor(
            when {
                FloatTimer.paused -> 0xE6495057.toInt()
                rest > 0 -> 0xE61C7ED6.toInt()
                else -> 0xE6E8590C.toInt()
            },
        )
    }

    private fun removeOverlay() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        updateNotification()
    }

    private fun shutdown() {
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        alive = false
        super.onDestroy()
    }
}
