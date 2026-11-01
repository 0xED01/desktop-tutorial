package com.yuandian.nightprotocol.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant

/**
 * 只为“下一次状态切换”设置一个闹钟（开始或结束），不做轮询。
 * 优先 setExactAndAllowWhileIdle（Doze 中亦可触发）；无精确闹钟权限时退化为
 * setAndAllowWhileIdle（系统可能推迟数分钟，结果会如实记录）。
 */
class TransitionScheduler(context: Context) {
    private val ctx = context.applicationContext
    private val am = ctx.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    /** @return 描述实际使用的调度方式。 */
    fun schedule(at: Instant?): String {
        val pi = pendingIntent()
        am.cancel(pi)
        if (at == null) return "none"
        val t = at.toEpochMilli()
        return try {
            if (canScheduleExact()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi); "exact@$at"
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi); "inexact@$at(无精确闹钟权限)"
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi); "inexact@$at($e)"
        }
    }

    fun cancel() = am.cancel(pendingIntent())

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, TransitionReceiver::class.java).setAction(TransitionReceiver.ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
