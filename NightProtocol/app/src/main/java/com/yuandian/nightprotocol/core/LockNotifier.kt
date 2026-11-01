package com.yuandian.nightprotocol.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.yuandian.nightprotocol.R
import com.yuandian.nightprotocol.kiosk.SleepKioskActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 锁定期间的常驻通知，带 fullScreenIntent：当后台无法直接拉起 Kiosk 时，
 * 系统在熄屏/锁屏状态下会用它展示锁定界面（Android 14+ 需 USE_FULL_SCREEN_INTENT 授权，见能力检查）。
 */
class LockNotifier(context: Context) {
    private val ctx = context.applicationContext
    private val nm = ctx.getSystemService(NotificationManager::class.java)

    fun canUseFullScreen(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= 34) nm.canUseFullScreenIntent() else true

    fun notificationsEnabled(): Boolean = nm.areNotificationsEnabled()

    fun showLocked(until: Instant?) {
        ensureChannel()
        val pi = PendingIntent.getActivity(ctx, 2,
            Intent(ctx, SleepKioskActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val end = until?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--"
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("晚安协议 · 睡眠中")
            .setContentText("预计 $end 恢复。电话、紧急呼叫与闹钟保持可用。")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
        runCatching { nm.notify(ID, n) }
    }

    fun cancel() = nm.cancel(ID)

    private fun ensureChannel() {
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "睡眠锁定", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "睡眠时段锁定状态"
                setSound(null, null)
                enableVibration(false)
            })
        }
    }

    companion object {
        private const val CHANNEL = "sleep_lock"
        private const val ID = 1001
    }
}
