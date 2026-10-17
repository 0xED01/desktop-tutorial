package com.hydro.water.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hydro.water.MainActivity
import com.hydro.water.R
import com.hydro.water.data.WaterRepo

object Notifications {
    private const val CH_REMIND = "remind"
    private const val CH_GOAL = "goal"
    private const val ID_REMIND = 1
    private const val ID_GOAL = 2

    private val tips = listOf(
        "喝口水，让大脑重新充满电 💧",
        "久坐一会儿了，起身接杯水吧",
        "身体 60% 是水，记得补充哦",
        "小口慢饮，比一次猛灌更好吸收",
        "口渴时已经轻度缺水啦，现在就喝一杯",
        "喝水能缓解疲劳，提升专注力",
        "一杯温水，照顾一下自己",
        "皮肤也在说：给我点水分吧",
    )

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_REMIND, "喝水提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "定时提醒你补充水分"
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_GOAL, "目标达成", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "完成每日饮水目标时通知"
            }
        )
    }

    private fun canPost(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun showReminder(ctx: Context) {
        if (!canPost(ctx)) return
        val s = WaterRepo.settings.value
        val total = WaterRepo.todayTotal()
        val left = (s.goal - total).coerceAtLeast(0)
        val drink = PendingIntent.getBroadcast(
            ctx, 1,
            Intent(ctx, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_DRINK)
                .putExtra(ActionReceiver.EXTRA_AMOUNT, s.notifyAmount),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snooze = PendingIntent.getBroadcast(
            ctx, 2, Intent(ctx, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (left > 0) "今日已喝 $total ml，还差 $left ml" else "今日目标已完成，保持节奏～"
        val n = NotificationCompat.Builder(ctx, CH_REMIND)
            .setSmallIcon(R.drawable.ic_drop)
            .setColor(0xFF2E7CF6.toInt())
            .setContentTitle(tips.random())
            .setContentText(text)
            .setProgress(s.goal, total.coerceAtMost(s.goal), false)
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "喝了 ${s.notifyAmount}ml", drink)
            .addAction(0, "15 分钟后", snooze)
            .build()
        NotificationManagerCompat.from(ctx).notify(ID_REMIND, n)
    }

    fun showGoalReached(ctx: Context) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_GOAL)
            .setSmallIcon(R.drawable.ic_drop)
            .setColor(0xFF2E7CF6.toInt())
            .setContentTitle("🎉 今日饮水目标达成！")
            .setContentText("已喝够 ${WaterRepo.settings.value.goal} ml，给自己点个赞")
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(ID_GOAL, n)
    }

    fun cancelReminder(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID_REMIND)
    }
}
