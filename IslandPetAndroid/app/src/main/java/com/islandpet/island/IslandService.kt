package com.islandpet.island

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.islandpet.MainActivity
import com.islandpet.R
import com.islandpet.model.GameState
import com.islandpet.model.Mood
import com.islandpet.model.PetRepo
import com.islandpet.model.alertEmoji
import com.islandpet.model.speech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 决定由谁来显示灵动岛：增强模式（无障碍）优先，否则用普通悬浮窗 */
object OverlayHost {
    private var controller: IslandController? = null

    val isShowing get() = controller?.isShowing == true
    val isEnhanced get() = controller?.windowType == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

    fun reconcile(ctx: Context) {
        val app = ctx.applicationContext
        val wanted = IslandPrefs.enabled(app) && PetRepo.state.value.pet != null
        val a11y = IslandAccessibilityService.instance
        when {
            !wanted -> detach()
            a11y != null -> attach(a11y, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            Settings.canDrawOverlays(app) -> attach(app, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            else -> detach()
        }
    }

    private fun attach(ctx: Context, type: Int) {
        val c = controller
        if (c != null && c.windowType == type && c.isShowing) return
        c?.hide()
        controller = IslandController(ctx, type).also { it.show() }
    }

    fun detach() {
        controller?.hide()
        controller = null
    }

    fun update(s: GameState) {
        controller?.update(s)
    }

    fun relayout() {
        controller?.relayout()
    }
}

/**
 * 常驻前台服务：
 * 1. 每 30 秒结算一次宠物状态，灵动岛实时变化
 * 2. 托管灵动岛悬浮窗
 * 3. 发布一条「实时活动」通知（Android 16 Live Update），在支持的系统上会显示到状态栏胶囊 / 原子岛
 */
class IslandService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastMood: Mood? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        PetRepo.init(this)
        createChannels(this)
        val n = buildNotification(this, PetRepo.state.value)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
        running = true
        scope.launch {
            PetRepo.state.collect { s ->
                OverlayHost.reconcile(this@IslandService)
                OverlayHost.update(s)
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(this@IslandService, s))
                alertIfNeeded(s)
            }
        }
        scope.launch {
            while (isActive) {
                delay(30_000)
                PetRepo.refresh()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RELAYOUT) OverlayHost.relayout()
        OverlayHost.reconcile(this)
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        if (!OverlayHost.isEnhanced) OverlayHost.detach()
        super.onDestroy()
    }

    /** 灵动岛没显示时，宠物突然变饿 / 生病就发一条提醒通知 */
    private fun alertIfNeeded(s: GameState) {
        val pet = s.pet ?: return
        val mood = pet.mood()
        if (mood.urgent && mood != lastMood && lastMood != null && !OverlayHost.isShowing) {
            val n = Notification.Builder(this, ALERT_CHANNEL)
                .setSmallIcon(R.drawable.ic_paw)
                .setContentTitle("${mood.emoji} ${pet.name}${mood.title}")
                .setContentText(s.speech())
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(this))
                .build()
            getSystemService(NotificationManager::class.java).notify(ALERT_ID, n)
        }
        lastMood = mood
    }

    companion object {
        const val CHANNEL = "island"
        const val ALERT_CHANNEL = "alerts"
        const val NOTIFICATION_ID = 1
        const val ALERT_ID = 2
        const val ACTION_RELAYOUT = "com.islandpet.RELAYOUT"

        @Volatile
        var running = false
            private set

        fun start(ctx: Context, relayout: Boolean = false) {
            val i = Intent(ctx, IslandService::class.java)
            if (relayout) i.action = ACTION_RELAYOUT
            runCatching { ctx.startForegroundService(i) }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, IslandService::class.java))
            OverlayHost.detach()
        }

        fun createChannels(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "灵动岛宠物（常驻）", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "宠物的实时状态，可以直接喂食、摸摸"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            })
            nm.createNotificationChannel(NotificationChannel(ALERT_CHANNEL, "宠物提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "宠物饿了、生病了时提醒你"
            })
        }

        fun openAppIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun actionIntent(ctx: Context, a: IslandAction): PendingIntent = PendingIntent.getBroadcast(
            ctx, a.ordinal + 10,
            Intent(ctx, ActionReceiver::class.java).setAction(ActionReceiver.ACTION).putExtra("action", a.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun buildNotification(ctx: Context, s: GameState): Notification {
            val icon = Icon.createWithResource(ctx, R.drawable.ic_paw)
            val b = Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_paw)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(openAppIntent(ctx))
            val pet = s.pet
            if (pet == null) {
                b.setContentTitle("灵动岛宠物").setContentText("快去领养一只宠物吧")
            } else {
                val mood = pet.mood()
                b.setContentTitle("${pet.name} · ${mood.emoji} ${mood.title} · Lv.${pet.level}")
                    .setContentText(
                        if (pet.isEgg) "轻敲蛋壳帮它孵化（${pet.eggTaps}/6）"
                        else "🍖${pet.hunger.toInt()}  💗${pet.happiness.toInt()}  ⚡${pet.energy.toInt()}  🧽${pet.cleanliness.toInt()}   ${s.speech()}"
                    )
                    .setSubText("💰 ${s.coins}")
                if (!pet.isEgg) b.setProgress(100, pet.hunger.toInt(), false)
                val actions = when {
                    pet.isEgg -> listOf(IslandAction.PAT to "👆 敲敲蛋")
                    pet.isSleeping -> listOf(IslandAction.PAT to "🤲 摸摸", IslandAction.SLEEP to "☀️ 叫醒")
                    else -> listOf(
                        IslandAction.FEED to "🍖 喂食",
                        IslandAction.PAT to "🤲 摸摸",
                        if (pet.isSick) IslandAction.HEAL to "💊 吃药"
                        else if (pet.poops > 0) IslandAction.CLEAN to "🧼 清洁"
                        else IslandAction.PLAY to "🎾 玩耍",
                    )
                }
                actions.forEach { (a, label) -> b.addAction(Notification.Action.Builder(icon, label, actionIntent(ctx, a)).build()) }
                // 状态栏胶囊上的短文字（Android 16 起支持）
                if (Build.VERSION.SDK_INT >= 36) {
                    val chip = if (pet.isEgg) "🥚" else "${s.alertEmoji()}${pet.hunger.toInt()}%"
                    runCatching {
                        Notification.Builder::class.java.getMethod("setShortCriticalText", String::class.java).invoke(b, chip)
                    }
                }
            }
            // 申请成为「实时活动」(Live Update) 通知
            b.extras.putBoolean("android.requestPromotedOngoing", true)
            return b.build()
        }
    }
}

/** 通知上按钮的处理 */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        PetRepo.init(ctx)
        val action = intent.getStringExtra("action")?.let { runCatching { IslandAction.valueOf(it) }.getOrNull() } ?: return
        performIslandAction(ctx, action)
        if (!IslandService.running && IslandPrefs.enabled(ctx)) IslandService.start(ctx)
    }

    companion object {
        const val ACTION = "com.islandpet.ACTION"
    }
}

/** 开机 / 升级后自动恢复灵动岛 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (IslandPrefs.enabled(ctx)) IslandService.start(ctx)
    }
}

/**
 * 增强模式：通过无障碍服务显示灵动岛。
 * 无障碍悬浮窗层级比状态栏高，可以准确盖住摄像头区域并且能点击；
 * 本服务不读取任何屏幕内容。
 */
class IslandAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        PetRepo.init(this)
        OverlayHost.reconcile(this)
        OverlayHost.update(PetRepo.state.value)
        if (IslandPrefs.enabled(this)) IslandService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        release()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun release() {
        if (instance === this) {
            instance = null
            if (OverlayHost.isEnhanced) OverlayHost.detach()
            OverlayHost.reconcile(applicationContext)
            OverlayHost.update(PetRepo.state.value)
        }
    }

    companion object {
        @Volatile
        var instance: IslandAccessibilityService? = null
            private set
    }
}
