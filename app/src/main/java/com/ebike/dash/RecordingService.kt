package com.ebike.dash

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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * 前台服务：App 打开期间常驻一条安静的通知，让系统在锁屏或切到其它 App 时
 * 继续给我们定位，足迹不中断。定位和记录本身仍在主界面里完成；
 * 从最近任务里划掉 App 时服务随之停止。
 */
class RecordingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "骑行中"
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, build(this, text),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        } catch (_: Exception) {
            // 没有定位权限等情况下系统不允许启动，直接退出，不影响前台使用
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户从最近任务里划掉 App：停止记录
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_TEXT = "text"
        var running = false; private set

        fun start(ctx: Context, text: String) {
            if (running) return
            running = true
            val i = Intent(ctx, RecordingService::class.java).putExtra(EXTRA_TEXT, text)
            try {
                ContextCompat.startForegroundService(ctx, i)
            } catch (_: Exception) {
                running = false
            }
        }

        fun stop(ctx: Context) {
            if (!running) return
            running = false
            ctx.stopService(Intent(ctx, RecordingService::class.java))
        }

        /** 更新通知上的文字（例如本次里程） */
        fun update(ctx: Context, text: String) {
            if (!running) return
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            try { nm.notify(NOTIFICATION_ID, build(ctx, text)) } catch (_: SecurityException) {}
        }

        private fun build(ctx: Context, text: String): Notification {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "足迹记录", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "锁屏或切到其它 App 时继续记录足迹"
                        setShowBadge(false)
                    }
                )
            }
            val open = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("Driver 正在记录足迹")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        }
    }
}
