package com.hydro.water.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hydro.water.data.Settings
import com.hydro.water.data.WaterRepo
import java.time.LocalDateTime
import java.time.ZoneId

object ReminderScheduler {

    private fun pendingIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 100, Intent(ctx, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** 进程启动时调用：已有未来的闹钟就不打乱节奏 */
    fun ensureScheduled(ctx: Context) {
        val next = WaterRepo.nextReminder.value
        if (!WaterRepo.settings.value.reminderOn || next < System.currentTimeMillis()) schedule(ctx)
        else setAlarm(ctx, next)
    }

    fun schedule(ctx: Context, delayMinutes: Int? = null) {
        val s = WaterRepo.settings.value
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pendingIntent(ctx))
        if (!s.reminderOn) {
            WaterRepo.setNextReminder(0L)
            return
        }
        val now = LocalDateTime.now()
        val next = if (delayMinutes != null) now.plusMinutes(delayMinutes.toLong()) else computeNext(now, s)
        val ts = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        setAlarm(ctx, ts)
        WaterRepo.setNextReminder(ts)
    }

    private fun setAlarm(ctx: Context, ts: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(ctx)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ts, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ts, pi)
        }
    }

    fun computeNext(now: LocalDateTime, s: Settings): LocalDateTime {
        val day = now.toLocalDate()
        val start = day.atStartOfDay().plusMinutes(s.startMin.toLong())
        val end = day.atStartOfDay().plusMinutes(s.endMin.toLong())
        val candidate = now.plusMinutes(s.interval.toLong())
        return when {
            now.isBefore(start) -> if (candidate.isBefore(start)) start else candidate
            candidate.isAfter(end) -> start.plusDays(1)
            else -> candidate
        }
    }

    fun inWindow(s: Settings, now: LocalDateTime = LocalDateTime.now()): Boolean {
        val m = now.hour * 60 + now.minute
        return m >= s.startMin - 1 && m <= s.endMin
    }

    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
}
